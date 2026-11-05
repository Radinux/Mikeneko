package app.mikeneko.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Petit cache en mémoire avec durée de vie : revenir sur un onglet ne relance pas
 * toutes les requêtes. Les erreurs et les listes vides ne sont jamais gardées.
 */
class TimedCache<T>(private val ttlMs: Long) {
    private class Entry<T>(val value: T, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    /** La valeur si elle est encore fraîche, sans rien charger. */
    fun peek(key: String): T? = entries[key]
        ?.takeIf { System.currentTimeMillis() - it.at < ttlMs }
        ?.value

    fun clear() = entries.clear()

    /** [fresh] : ignore la valeur gardée et recharge (tirer pour rafraîchir). */
    suspend fun get(key: String, fresh: Boolean = false, load: suspend () -> T): T {
        if (!fresh) peek(key)?.let { return it }
        val value = load()
        if (value !is Collection<*> || value.isNotEmpty()) {
            entries[key] = Entry(value, System.currentTimeMillis())
        }
        return value
    }
}
