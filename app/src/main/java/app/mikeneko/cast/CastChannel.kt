package app.mikeneko.cast

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Le protocole Cast (« CASTV2 »), parlé directement (3.4.0) : comme VLC, sans les services Google.
 * Une connexion TLS au port 8009 de l'appareil ; chaque message est un `CastMessage` protobuf précédé
 * de sa taille (4 octets). Les messages utiles sont du JSON (payload_utf8) dans un « espace de noms ».
 */
object CastMessage {
    const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
    const val NS_MEDIA = "urn:x-cast:com.google.cast.media"

    /** Un message reçu ou à envoyer. */
    data class Message(val source: String, val destination: String, val namespace: String, val payload: String)

    /** Encode en protobuf : 1 version (0), 2 source, 3 destination, 4 espace de noms, 5 type (0 = texte), 6 texte. */
    fun encode(m: Message): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(v: Long) {
            var x = v
            while (x and 0x7FL.inv() != 0L) {
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            out.write(x.toInt())
        }
        fun string(field: Int, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            varint(((field shl 3) or 2).toLong())
            varint(bytes.size.toLong())
            out.write(bytes)
        }
        varint((1 shl 3).toLong()); varint(0)
        string(2, m.source)
        string(3, m.destination)
        string(4, m.namespace)
        varint((5 shl 3).toLong()); varint(0)
        string(6, m.payload)
        return out.toByteArray()
    }

    /** Décode un `CastMessage` (les champs binaires et inconnus sont sautés). */
    fun decode(bytes: ByteArray): Message {
        var i = 0
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        var source = ""
        var destination = ""
        var namespace = ""
        var payload = ""
        while (i < bytes.size) {
            val key = varint().toInt()
            val field = key ushr 3
            when (key and 7) {
                0 -> varint()
                2 -> {
                    val length = varint().toInt()
                    val value = String(bytes, i, length, Charsets.UTF_8)
                    i += length
                    when (field) {
                        2 -> source = value
                        3 -> destination = value
                        4 -> namespace = value
                        6 -> payload = value
                    }
                }
                5 -> i += 4
                1 -> i += 8
                else -> return Message(source, destination, namespace, payload)
            }
        }
        return Message(source, destination, namespace, payload)
    }
}

/**
 * Une connexion ouverte à un appareil Cast. [onMessage] reçoit chaque message (sur le fil de lecture) ;
 * [request] envoie un message avec un `requestId` et attend la réponse qui le porte.
 */
class CastChannel(private val host: String, private val port: Int, private val onMessage: (CastMessage.Message, JSONObject) -> Unit, private val onClosed: () -> Unit) {
    private lateinit var socket: SSLSocket
    private lateinit var output: OutputStream
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    @Volatile var open = false
        private set

    /** Ouvre la connexion (sur un fil d'arrière-plan) et lance la lecture et le battement de cœur. */
    fun connect() {
        // Les appareils Cast ont un certificat à eux, signé par Google : on ne peut pas le vérifier comme un
        // site web. La connexion reste chiffrée, sur le réseau local (comme le font VLC et les autres)
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val context = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        val s = context.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), 5_000)
        s.soTimeout = 0
        s.startHandshake()
        socket = s
        output = s.outputStream
        open = true
        Thread({ readLoop() }, "cast-lecture").apply { isDaemon = true }.start()
        Thread({ heartbeat() }, "cast-pouls").apply { isDaemon = true }.start()
    }

    fun send(destination: String, namespace: String, payload: JSONObject, source: String = SENDER) {
        if (!open) return
        val bytes = CastMessage.encode(CastMessage.Message(source, destination, namespace, payload.toString()))
        synchronized(this) {
            runCatching {
                val header = byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte())
                output.write(header)
                output.write(bytes)
                output.flush()
            }.onFailure { close() }
        }
    }

    /** Envoie avec un nouveau `requestId` et attend la réponse (ou l'erreur) qui le reprend. */
    suspend fun request(destination: String, namespace: String, payload: JSONObject, timeoutMs: Long = 10_000): JSONObject {
        val id = ids.getAndIncrement()
        val answer = CompletableDeferred<JSONObject>()
        pending[id] = answer
        send(destination, namespace, payload.put("requestId", id))
        return try {
            withTimeout(timeoutMs) { answer.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Un `requestId` pour un message dont on n'attend pas la réponse. */
    fun nextId(): Int = ids.getAndIncrement()

    fun close() {
        if (!open) return
        open = false
        runCatching { socket.close() }
        pending.values.forEach { it.completeExceptionally(IllegalStateException("connexion fermée")) }
        pending.clear()
        onClosed()
    }

    private fun readLoop() {
        try {
            val input = DataInputStream(socket.inputStream)
            while (open) {
                val size = input.readInt()
                if (size <= 0 || size > 1 shl 20) break
                val bytes = ByteArray(size)
                input.readFully(bytes)
                val message = CastMessage.decode(bytes)
                val json = runCatching { JSONObject(message.payload) }.getOrElse { JSONObject() }
                if (message.namespace == CastMessage.NS_HEARTBEAT && json.optString("type") == "PING") {
                    send(message.source, CastMessage.NS_HEARTBEAT, JSONObject().put("type", "PONG"))
                    continue
                }
                val id = json.optInt("requestId", 0)
                if (id != 0) pending.remove(id)?.complete(json)
                runCatching { onMessage(message, json) }
            }
        } catch (_: Exception) {
            // (connexion coupée)
        }
        close()
    }

    private fun heartbeat() {
        while (open) {
            send(RECEIVER, CastMessage.NS_HEARTBEAT, JSONObject().put("type", "PING"))
            Thread.sleep(5_000)
        }
    }

    companion object {
        const val SENDER = "sender-mikeneko"
        const val RECEIVER = "receiver-0"
    }
}
