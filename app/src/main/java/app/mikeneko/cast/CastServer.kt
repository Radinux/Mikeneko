package app.mikeneko.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Le petit serveur web du téléphone pour le Chromecast (3.4.0), comme VLC : le Chromecast ne lit pas
 * YouTube lui-même, il lit ce que le téléphone lui sert sur le Wi-Fi (le son, l'image, le manifeste DASH,
 * un fichier du téléphone ou un morceau téléchargé). Avec les plages d'octets (le Chromecast lit par
 * morceaux) et le CORS (son lecteur est une page web d'une autre origine).
 */
@OptIn(UnstableApi::class)
object CastServer {
    /** Ce qu'une adresse sert : un flux lu par [factory] (cache, réseau, fichier), ou un texte. */
    class Resource(val mime: String, val factory: DataSource.Factory? = null, val uri: Uri? = null, val key: String? = null, val text: String? = null) {
        @Volatile var length: Long = C.LENGTH_UNSET.toLong()
    }

    private val resources = ConcurrentHashMap<String, Resource>()
    private var server: ServerSocket? = null
    private var host: String? = null

    /** Publie [resource] et renvoie son adresse (http://ip-du-téléphone:port/…), ou null sans Wi-Fi. */
    @Synchronized
    fun publish(context: Context, resource: Resource, extension: String): String? {
        val ip = localAddress(context) ?: return null
        val socket = server?.takeIf { !it.isClosed && host == ip } ?: start(ip)
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        resources[id] = resource
        return "http://$ip:${socket.localPort}/$id.$extension"
    }

    /** Oublie tout ce qui a été servi (fin de la session Cast). */
    @Synchronized
    fun stop() {
        resources.clear()
        runCatching { server?.close() }
        server = null
    }

    /** Ne garde que les adresses de [keep] (un nouveau morceau : les anciens ne sont plus servis). */
    fun retain(keep: Collection<String>) {
        val ids = keep.map { it.substringAfterLast('/').substringBefore('.') }.toSet()
        resources.keys.retainAll(ids)
    }

    private fun start(ip: String): ServerSocket {
        runCatching { server?.close() }
        val socket = ServerSocket(0, 16, java.net.InetAddress.getByName(ip))
        server = socket
        host = ip
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({ serve(client) }, "cast-client").apply { isDaemon = true }.start()
            }
        }, "cast-serveur").apply { isDaemon = true }.start()
        return socket
    }

    private fun serve(client: Socket) {
        client.use { s ->
            runCatching {
                s.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val out = s.getOutputStream()
                // Une connexion peut porter plusieurs requêtes (keep-alive)
                while (true) {
                    val requestLine = reader.readLine() ?: return
                    if (requestLine.isBlank()) continue
                    val parts = requestLine.split(' ')
                    val method = parts.getOrNull(0) ?: return
                    val path = parts.getOrNull(1) ?: return
                    var range: String? = null
                    while (true) {
                        val line = reader.readLine() ?: return
                        if (line.isEmpty()) break
                        if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                    }
                    if (!handle(method, path, range, out)) return
                }
            }
        }
    }

    private fun cors() = "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
        "Access-Control-Allow-Headers: Range, Content-Type\r\n" +
        "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n"

    /** Répond à une requête ; false : la connexion se ferme. */
    private fun handle(method: String, path: String, range: String?, out: OutputStream): Boolean {
        if (method == "OPTIONS") {
            out.write("HTTP/1.1 204 No Content\r\n${cors()}Content-Length: 0\r\n\r\n".toByteArray())
            out.flush()
            return true
        }
        val id = path.trimStart('/').substringBefore('.').substringBefore('?')
        val resource = resources[id]
        if (resource == null) {
            out.write("HTTP/1.1 404 Not Found\r\n${cors()}Content-Length: 0\r\n\r\n".toByteArray())
            out.flush()
            return true
        }
        resource.text?.let { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 200 OK\r\n${cors()}Content-Type: ${resource.mime}\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
            if (method != "HEAD") out.write(bytes)
            out.flush()
            return true
        }
        val factory = resource.factory ?: return false
        val total = totalLength(resource, factory)
        // « bytes=début-fin » (fin facultative)
        val (start, end) = range?.removePrefix("bytes=")?.split('-')?.let { r ->
            val a = r.getOrNull(0)?.toLongOrNull() ?: 0L
            val b = r.getOrNull(1)?.toLongOrNull()
            a to (b ?: (if (total > 0) total - 1 else -1L))
        } ?: (0L to (if (total > 0) total - 1 else -1L))
        val partial = range != null && total > 0
        val length = if (end >= start) end - start + 1 else C.LENGTH_UNSET.toLong()
        val header = buildString {
            append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append(cors())
            append("Content-Type: ${resource.mime}\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (length > 0) append("Content-Length: $length\r\n")
            if (partial) append("Content-Range: bytes $start-$end/$total\r\n")
            if (length <= 0) append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray())
        if (method == "HEAD") {
            out.flush()
            return length > 0
        }
        val source = factory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(resource.uri!!).setPosition(start).setLength(length).apply { resource.key?.let(::setKey) }.build())
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = source.read(buffer, 0, buffer.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, n)
            }
            out.flush()
        } catch (e: SocketException) {
            return false // le Chromecast a coupé (il saute ailleurs) : normal
        } finally {
            runCatching { source.close() }
        }
        return length > 0
    }

    /** La taille du fichier (une fois pour toutes) : en ouvrant sans limite et en lisant la longueur annoncée. */
    private fun totalLength(resource: Resource, factory: DataSource.Factory): Long {
        if (resource.length > 0) return resource.length
        val source = factory.createDataSource()
        return try {
            val n = source.open(DataSpec.Builder().setUri(resource.uri!!).apply { resource.key?.let(::setKey) }.build())
            if (n > 0) resource.length = n
            n
        } catch (e: Exception) {
            C.LENGTH_UNSET.toLong()
        } finally {
            runCatching { source.close() }
        }
    }

    /** L'adresse IPv4 du téléphone sur le Wi-Fi. */
    private fun localAddress(context: Context): String? = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return null
        manager.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
