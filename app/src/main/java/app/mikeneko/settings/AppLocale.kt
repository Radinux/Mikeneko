package app.mikeneko.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

/**
 * La langue de l'appli. « Système » suit le téléphone (français si le téléphone est en
 * français, sinon anglais : c'est la langue par défaut des ressources).
 * Pour ajouter une langue : un dossier res/values-xx/strings.xml + une entrée dans [LANGUAGES].
 */
object AppLocale {
    /** Langues proposées : code → nom dans sa propre langue. */
    val LANGUAGES = linkedMapOf("fr" to "Français", "en" to "English")

    /** Régions des contenus proposées (codes pays). */
    val REGIONS = listOf("FR", "BE", "CH", "CA", "LU", "MA", "SN", "US", "GB", "IE", "AU", "DE", "ES", "IT", "PT", "BR", "JP")

    private lateinit var appContext: Context
    private var localized: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        localized = null
        applyContentLocalization()
    }

    /** La langue réellement utilisée (« fr » ou « en »). */
    fun language(): String {
        val chosen = AppSettings.language.value
        if (chosen != AppSettings.LANGUAGE_SYSTEM && chosen in LANGUAGES) return chosen
        val system = Resources.getSystem().configuration.locales[0].language
        return if (system in LANGUAGES) system else "en"
    }

    fun locale(): Locale = Locale.forLanguageTag(language())

    /** Le pays des contenus : celui choisi, sinon celui du téléphone. */
    fun region(): String {
        val chosen = AppSettings.region.value
        if (chosen != AppSettings.REGION_AUTO) return chosen
        return Resources.getSystem().configuration.locales[0].country.ifEmpty { if (language() == "fr") "FR" else "US" }
    }

    /**
     * Pour l'activité : seulement la langue choisie (null = celle du téléphone). Une configuration
     * VIDE à part la langue : copier toute celle du téléphone figerait aussi l'orientation et la
     * taille de l'écran, et l'appli se croirait toujours en portrait (plus de plein écran en tournant).
     */
    fun overrideConfiguration(base: Context): Configuration? {
        val chosen = AppSettings.languageFrom(base)
        if (chosen == AppSettings.LANGUAGE_SYSTEM || chosen !in LANGUAGES) return null
        return Configuration().apply { setLocale(Locale.forLanguageTag(chosen)) }
    }

    /** Textes hors de l'interface (dates, erreurs, recherches) dans la bonne langue. */
    private fun context(): Context = localized ?: run {
        val config = Configuration(appContext.resources.configuration).apply { setLocale(locale()) }
        appContext.createConfigurationContext(config).also { localized = it }
    }

    fun string(@StringRes id: Int, vararg args: Any): String = context().getString(id, *args)

    fun stringArray(@androidx.annotation.ArrayRes id: Int): List<String> = context().resources.getStringArray(id).toList()

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context().resources.getQuantityString(id, count, *args.ifEmpty { arrayOf(count) })

    /** Après un changement de langue ou de région. */
    fun refresh() {
        localized = null
        applyContentLocalization()
    }

    /** NewPipe : langue et pays des contenus (recherches, directs, dates des commentaires). */
    private fun applyContentLocalization() {
        val country = region()
        // Langue seule (« fr ») : YouTube ne connaît pas « fr-FR » et répond alors en anglais, avec des
        // titres traduits automatiquement. Le pays passe à part (ContentCountry).
        NewPipe.setupLocalization(Localization(language()), ContentCountry(country))
    }

    /** Nom d'un pays dans la langue de l'appli (« Belgique » / « Belgium »). */
    fun regionName(code: String): String =
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale()).replaceFirstChar { it.uppercase(locale()) }
}
