package app.mikeneko.data

import android.content.Context
import app.mikeneko.player.PlayerHolder
import app.mikeneko.settings.AppSettings

/**
 * Les réglages dans le fichier `.mikeneko` (3.7.0) : une liste fixe de clés, pour ne jamais y mettre ce
 * qui est propre à l'appareil ou secret (identifiant SponsorBlock, comptes, clés de synchro). La langue
 * n'y est pas non plus : un nouveau téléphone garde la sienne.
 */
object SettingsBackup {
    private val KEYS = mapOf(
        "reglages" to setOf(
            "region", "pip_auto", "barre_etat_masquee", "sponsorblock", "historique_pause", "dodo", "dodo_minutes",
            "shorts_masques", "musique_qualite_wifi", "musique_qualite_mobile", "musique_qualite_telechargement",
            "fondu_secondes", "accent", "accent_perso", "accents_perso", "amoled",
        ),
        "lecteur" to setOf("ambiant", "volume_stable", "qualite", "sponsorblock_auto", "lecture_auto", "effet_audio"),
    )

    fun capture(context: Context): Map<String, Map<String, Any>> = KEYS.mapValues { (file, keys) ->
        context.getSharedPreferences(file, Context.MODE_PRIVATE).all
            .filterKeys { it in keys }
            .mapNotNull { (k, v) -> v?.let { k to it } }
            .toMap()
    }.filterValues { it.isNotEmpty() }

    /** Écrit les réglages du fichier (seulement les clés connues) et les applique tout de suite. */
    fun apply(context: Context, values: Map<String, Map<String, Any>>) {
        values.forEach { (file, entries) ->
            val allowed = KEYS[file] ?: return@forEach
            val editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            entries.forEach { (key, value) ->
                if (key !in allowed) return@forEach
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
        AppSettings.init(context)
        PlayerHolder.readSettings(context)
    }
}
