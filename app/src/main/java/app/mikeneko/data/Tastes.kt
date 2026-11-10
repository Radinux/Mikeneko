package app.mikeneko.data

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import app.mikeneko.R
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les goûts choisis pour le « Pour toi » (2.7.0) : au premier lancement, on touche quelques thèmes
 * et le fil part de là, avant d'apprendre de ce qu'on regarde. Modifiables dans
 * Réglages › Pour toi. Chaque thème a quelques recherches (traduites), tirées au hasard.
 *
 * Garde aussi un nombre tiré au sort une fois pour toutes sur ce téléphone ([seed]) : deux
 * téléphones avec les mêmes goûts n'ont pas le même fil (avant, des recherches fixes montraient les
 * mêmes vidéos à tout le monde).
 */
class Tastes private constructor(context: Context) {
    data class Taste(val id: String, val emoji: String, @StringRes val label: Int, @ArrayRes val queries: Int)

    private val prefs = context.getSharedPreferences("gouts", Context.MODE_PRIVATE)
    private val _chosen = MutableStateFlow(prefs.getString("choisis", "").orEmpty().split(',').filter { id -> ALL.any { it.id == id } }.toSet())
    private val _skipped = MutableStateFlow(prefs.getBoolean("passe", false))

    /** Les thèmes choisis (ids de [ALL]). */
    val chosen: StateFlow<Set<String>> = _chosen.asStateFlow()

    /** « Passer » touché sur la page des goûts : on ne la remontre plus. */
    val skipped: StateFlow<Boolean> = _skipped.asStateFlow()

    /** Propre à ce téléphone, tiré une fois. */
    val seed: Long = prefs.getLong("graine", 0L).takeIf { it != 0L }
        ?: Random.nextLong().also { prefs.edit().putLong("graine", it).apply() }

    fun set(ids: Set<String>) {
        _chosen.value = ids.filter { id -> ALL.any { it.id == id } }.toSet()
        prefs.edit().putString("choisis", _chosen.value.joinToString(",")).putBoolean("passe", true).apply()
        _skipped.value = true
    }

    fun toggle(id: String) = set(if (id in _chosen.value) _chosen.value - id else _chosen.value + id)

    /** Les sujets d'un autre appareil (2.19.1) : ajoutés aux nôtres ; la page des sujets ne revient plus. */
    fun merge(ids: Set<String>, skipped: Boolean) {
        val known = ids.filter { id -> ALL.any { it.id == id } }.toSet()
        when {
            (known - _chosen.value).isNotEmpty() -> set(_chosen.value + known)
            (known.isNotEmpty() || skipped) && !_skipped.value -> skip()
        }
    }

    fun skip() {
        _skipped.value = true
        prefs.edit().putBoolean("passe", true).apply()
    }

    companion object {
        val ALL = listOf(
            Taste("cooking", "🍳", R.string.taste_cooking, R.array.taste_q_cooking),
            Taste("gaming", "🎮", R.string.taste_gaming, R.array.taste_q_gaming),
            Taste("science", "🔬", R.string.taste_science, R.array.taste_q_science),
            Taste("tech", "💻", R.string.taste_tech, R.array.taste_q_tech),
            Taste("music", "🎵", R.string.taste_music, R.array.taste_q_music),
            Taste("comedy", "😂", R.string.taste_comedy, R.array.taste_q_comedy),
            Taste("sport", "⚽", R.string.taste_sport, R.array.taste_q_sport),
            Taste("travel", "✈️", R.string.taste_travel, R.array.taste_q_travel),
            Taste("nature", "🦊", R.string.taste_nature, R.array.taste_q_nature),
            Taste("history", "🏛️", R.string.taste_history, R.array.taste_q_history),
            Taste("diy", "🔨", R.string.taste_diy, R.array.taste_q_diy),
            Taste("art", "🎨", R.string.taste_art, R.array.taste_q_art),
            Taste("cars", "🚗", R.string.taste_cars, R.array.taste_q_cars),
            Taste("movies", "🎬", R.string.taste_movies, R.array.taste_q_movies),
            Taste("anime", "🍥", R.string.taste_anime, R.array.taste_q_anime),
            Taste("fitness", "💪", R.string.taste_fitness, R.array.taste_q_fitness),
            Taste("space", "🚀", R.string.taste_space, R.array.taste_q_space),
            Taste("podcasts", "🎙️", R.string.taste_podcasts, R.array.taste_q_podcasts),
            Taste("fashion", "👗", R.string.taste_fashion, R.array.taste_q_fashion),
            Taste("learning", "📚", R.string.taste_learning, R.array.taste_q_learning),
        )

        @Volatile private var instance: Tastes? = null
        fun get(context: Context): Tastes =
            instance ?: synchronized(this) { instance ?: Tastes(context.applicationContext).also { instance = it } }
    }
}
