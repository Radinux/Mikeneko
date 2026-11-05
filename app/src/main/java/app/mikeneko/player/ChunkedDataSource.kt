package app.mikeneko.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Lit les flux de YouTube par morceaux de [chunkSize] octets.
 *
 * ExoPlayer demande un fichier progressif « du point X jusqu'à la fin » en une seule requête.
 * Les serveurs de YouTube (googlevideo.com) brident ces requêtes ouvertes : quelques Mo vite,
 * puis presque plus rien (d'où ~20 s de blocage au démarrage et après chaque changement de
 * qualité). Des requêtes bornées de 2 Mo, elles, arrivent en ~0,2 s chacune. On découpe donc
 * la lecture en requêtes successives, sans que le lecteur s'en aperçoive.
 * Les autres adresses (sous-titres, direct HLS…) passent telles quelles.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = 2L * 1024 * 1024,
) : DataSource {

    class Factory(private val upstream: DataSource.Factory, private val chunkSize: Long = 2L * 1024 * 1024) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }

    private var spec: DataSpec? = null
    private var passthrough = false
    private var position = 0L // prochain octet à lire (position absolue dans le fichier)
    private var end = UNSET // fin (exclue) de ce qui a été demandé, une fois connue
    private var chunkRemaining = 0L // octets restants dans le morceau ouvert

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        passthrough = dataSpec.uri.host?.endsWith("googlevideo.com") != true
        if (passthrough) return upstream.open(dataSpec)

        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != UNSET) dataSpec.position + dataSpec.length else UNSET
        openChunk()
        if (end == UNSET) {
            // La taille totale est dans « Content-Range: bytes 0-2097151/80911999 »
            totalLength()?.let { end = it }
        }
        return if (end != UNSET) end - position else UNSET
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (end != UNSET && position >= end) return C.RESULT_END_OF_INPUT

        if (chunkRemaining <= 0L) {
            upstream.close()
            openChunk()
            if (chunkRemaining <= 0L) return C.RESULT_END_OF_INPUT
        }

        val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            // Morceau plus court que prévu : c'était la fin du fichier
            chunkRemaining = 0L
            if (end == UNSET) return C.RESULT_END_OF_INPUT
            end = position
            return C.RESULT_END_OF_INPUT
        }
        position += read
        chunkRemaining -= read
        return read
    }

    override fun getUri(): Uri? = if (passthrough) upstream.uri else spec?.uri ?: upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
        chunkRemaining = 0L
    }

    private fun openChunk() {
        val base = spec ?: return
        val chunkEnd = if (end != UNSET) minOf(end, position + chunkSize) else position + chunkSize
        if (chunkEnd <= position) {
            chunkRemaining = 0L
            return
        }
        val opened = upstream.open(base.subrange(position - base.position, chunkEnd - position))
        chunkRemaining = if (opened == UNSET) chunkEnd - position else opened
    }

    private fun totalLength(): Long? =
        upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.substringAfterLast('/')
            ?.trim()
            ?.toLongOrNull()

    private companion object {
        const val UNSET = C.LENGTH_UNSET.toLong()
    }
}
