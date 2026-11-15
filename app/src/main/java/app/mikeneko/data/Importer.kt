package app.mikeneko.data

/**
 * Un seul bouton « Importer » (3.7.0) : on choisit n'importe quel fichier, et Mikeneko reconnaît ce que
 * c'est, d'après son contenu (pas son nom ni son extension, que les applis de partage changent souvent) :
 * - une sauvegarde Mikeneko : le nouveau `.mikeneko` ([BackupFile]) ou l'ancien `.xml` ;
 * - des abonnements : NewPipe (.json), FreeTube (.db), Google Takeout (.csv) ;
 * - des playlists d'une autre appli de musique : un export TuneMyMusic (CSV ou TXT).
 *
 * Rien n'est importé ici : l'écran montre d'abord ce qui a été trouvé.
 */
object Importer {
    sealed interface Found {
        class Backup(val content: MikenekoBackup.Content) : Found
        class Subscriptions(val list: List<Subscription>) : Found
        class Playlists(val preview: PlaylistImport.Preview) : Found
        data object Unknown : Found
    }

    /** Au-delà, ce n'est pas un fichier qu'on sait lire (et on ne le charge pas en entier). */
    const val MAX_BYTES = 64L * 1024 * 1024

    fun detect(bytes: ByteArray, fileName: String): Found {
        // 1. Le nouveau fichier de sauvegarde (une archive ZIP) ; une autre archive : pas pour nous
        BackupFile.read(bytes)?.let { (_, content) -> return Found.Backup(content) }
        if (BackupFile.isZip(bytes)) return Found.Unknown
        val text = bytes.decodeToString().removePrefix("﻿")
        // Un fichier binaire (image, vidéo…) : pas la peine de chercher des morceaux dedans
        if (text.isEmpty() || text.take(4096).count { it == '\u0000' || it == '�' } > 8) return Found.Unknown
        // 2. L'ancienne sauvegarde (XML)
        if (MikenekoBackup.looksLike(text)) {
            return runCatching { Found.Backup(MikenekoBackup.read(text)) }.getOrDefault(Found.Unknown)
        }
        // 3. Des abonnements (chaque ligne utile contient un identifiant de chaîne UC…)
        val subscriptions = runCatching { SubscriptionImport.parse(text) }.getOrDefault(emptyList())
        if (subscriptions.isNotEmpty()) return Found.Subscriptions(subscriptions)
        // 4. Des playlists de musique (en dernier : un TXT « Artiste - Titre » ressemble à beaucoup de choses)
        val preview = runCatching { PlaylistImport.preview(text, fileName) }.getOrNull()
        if (preview != null && preview.items.isNotEmpty()) return Found.Playlists(preview)
        return Found.Unknown
    }
}
