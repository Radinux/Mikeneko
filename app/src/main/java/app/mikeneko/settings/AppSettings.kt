package app.mikeneko.settings

import android.content.Context
import android.content.SharedPreferences
import app.mikeneko.ui.theme.Accent
import androidx.compose.ui.graphics.toArgb
import app.mikeneko.ui.theme.Appearance
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les réglages de l'appli, gardés sur le téléphone. Les réglages du lecteur déjà existants
 * (mode ambiant, volume stable, qualité) restent dans PlayerHolder.
 */
object AppSettings {
    private lateinit var prefs: SharedPreferences

    /** Toutes les catégories SponsorBlock, dans l'ordre affiché. */
    val SPONSOR_CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "music_offtopic", "filler")
    private val DEFAULT_SKIP = setOf("sponsor", "selfpromo", "interaction")

    /** « system », ou un code de langue (« fr », « en »). */
    private val _language = MutableStateFlow(LANGUAGE_SYSTEM)
    val language: StateFlow<String> = _language.asStateFlow()

    /** « auto », ou un code pays (« FR », « BE », « US »…). */
    private val _region = MutableStateFlow(REGION_AUTO)
    val region: StateFlow<String> = _region.asStateFlow()

    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    /** Barre d'état du téléphone (heure, batterie, notifications) masquée dans l'appli. */
    private val _hideStatusBar = MutableStateFlow(true)
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar.asStateFlow()

    private val _skipCategories = MutableStateFlow(DEFAULT_SKIP)
    val skipCategories: StateFlow<Set<String>> = _skipCategories.asStateFlow()

    private val _historyPaused = MutableStateFlow(false)
    val historyPaused: StateFlow<Boolean> = _historyPaused.asStateFlow()

    private val _sleepReminder = MutableStateFlow(true)
    val sleepReminder: StateFlow<Boolean> = _sleepReminder.asStateFlow()

    /** Plus aucun short nulle part (accueil, recherche, abonnements, suggestions, onglet des chaînes). */
    private val _hideShorts = MutableStateFlow(false)
    val hideShorts: StateFlow<Boolean> = _hideShorts.asStateFlow()

    private val _sleepMinutes = MutableStateFlow(60)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("reglages", Context.MODE_PRIVATE)
        _language.value = prefs.getString("langue", LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        _region.value = prefs.getString("region", REGION_AUTO) ?: REGION_AUTO
        _autoPip.value = prefs.getBoolean("pip_auto", true)
        _hideStatusBar.value = prefs.getBoolean("barre_etat_masquee", true)
        _skipCategories.value = prefs.getStringSet("sponsorblock", DEFAULT_SKIP)?.toSet() ?: DEFAULT_SKIP
        _historyPaused.value = prefs.getBoolean("historique_pause", false)
        _sleepReminder.value = prefs.getBoolean("dodo", true)
        _sleepMinutes.value = prefs.getInt("dodo_minutes", 60)
        _hideShorts.value = prefs.getBoolean("shorts_masques", false)
        Appearance.custom = prefs.getInt("accent_perso", 0).takeIf { it != 0 }?.let { androidx.compose.ui.graphics.Color(it) }
        Appearance.accent = Accent.of(prefs.getString("accent", null), Appearance.custom)
        Appearance.amoled = prefs.getBoolean("amoled", false)
    }

    /** La couleur d'accent (2.9.0). */
    fun setAccent(value: Accent) = save("accent", value.id) { Appearance.accent = value }

    /** La couleur perso, choisie au cercle (2.16.0) : gardée, et prise comme accent. */
    fun setCustomAccent(color: androidx.compose.ui.graphics.Color) {
        prefs.edit().putInt("accent_perso", color.toArgb()).apply()
        Appearance.custom = color
        setAccent(Accent.custom(color))
    }

    /** Fond noir pur (2.9.0). */
    fun setAmoled(value: Boolean) = save("amoled", value) { Appearance.amoled = value }

    /** Lu avant init() par attachBaseContext de l'activité. */
    fun languageFrom(context: Context): String =
        context.getSharedPreferences("reglages", Context.MODE_PRIVATE).getString("langue", LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM

    fun setLanguage(value: String) = save("langue", value) { _language.value = value }
    fun setRegion(value: String) = save("region", value) { _region.value = value }
    fun setAutoPip(value: Boolean) = save("pip_auto", value) { _autoPip.value = value }
    fun setHideStatusBar(value: Boolean) = save("barre_etat_masquee", value) { _hideStatusBar.value = value }
    fun setHistoryPaused(value: Boolean) = save("historique_pause", value) { _historyPaused.value = value }
    fun setSleepReminder(value: Boolean) = save("dodo", value) { _sleepReminder.value = value }
    fun setSleepMinutes(value: Int) = save("dodo_minutes", value) { _sleepMinutes.value = value }
    fun setHideShorts(value: Boolean) = save("shorts_masques", value) { _hideShorts.value = value }

    fun setSkipCategory(category: String, skip: Boolean) {
        val next = if (skip) _skipCategories.value + category else _skipCategories.value - category
        _skipCategories.value = next
        prefs.edit().putStringSet("sponsorblock", next).apply()
    }

    /**
     * Identifiant SponsorBlock de l'appareil : aléatoire, créé au premier envoi, jamais affiché
     * (c'est la version privée ; SponsorBlock n'en publie qu'un hachage).
     */
    fun sponsorBlockUserId(): String =
        prefs.getString("sponsorblock_id", null) ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .replace("-", "")
            .also { prefs.edit().putString("sponsorblock_id", it).apply() }

    private fun save(key: String, value: Any, apply: () -> Unit) {
        apply()
        prefs.edit().apply {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
            }
        }.apply()
    }

    const val LANGUAGE_SYSTEM = "system"
    const val REGION_AUTO = "auto"
}
