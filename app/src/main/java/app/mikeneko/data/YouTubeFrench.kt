package app.mikeneko.data

/**
 * Les réponses de YouTube en français, rendues lisibles pour NewPipeExtractor (pensé pour l'anglais).
 *
 * Dans les listes « nouveau format » (À suivre, une partie des fils), YouTube abrège les vues et les
 * dates : « 831 k », « 1,2 M », « 1,6 Md », « il y a 9 j », « il y a 2 a ». NewPipe ne comprend pas
 * ces abréviations (« 1,2 M » devenait « 1 vue »). Chaque morceau est suivi de sa version complète
 * pour l'accessibilité (« 831 mille vues », « il y a 9 jours ») : on s'en sert pour réécrire le texte
 * affiché en une forme que NewPipe sait lire. Les titres ne sont jamais touchés.
 */
object YouTubeFrench {
    /** La version complète, pour l'accessibilité : `"accessibilityLabel":"831 mille vues"`. */
    private val LABEL = Regex(""""accessibilityLabel":"((?:[^"\\]|\\u[0-9a-fA-F]{4}){1,80})"""")
    private const val CONTENT = "\"content\":\""
    private const val LOOK_BACK = 400 // le texte affiché est juste avant sa version complète
    private val COMPACT = Regex("""^(\d[\d ]*(?:,\d+)?) ?(k|M|Md|Mrd)?\b""")

    /** Abonnés et vues abrégés sans version longue (chaînes trouvées, page d'une chaîne) : `"simpleText":"271 k abonnés"`. */
    // Les millions s'écrivent avec « de » / « d' » : « 3,58 M d'abonnés », « 1,2 M de vues »
    private val SUBSCRIBERS =
        Regex(""""(simpleText|content)":"((?:[^"\\]|\\u[0-9a-fA-F]{4}){1,30}?)( |\\u00a0|\\u202f| | )(?:de |d'|d’|d\\u0027|d\\u2019)?(abonnés|abonné|vues|vue)"""")

    fun fix(body: String): String = fixSubscribers(fixParts(body))

    /** « 271 k abonnés » → « 271000 abonnés », « 211 k vues » → « 211000 vues » (« @pseudo » et le reste ne bougent pas). */
    private fun fixSubscribers(body: String): String {
        if (!body.contains("abonné") && !body.contains("vue")) return body
        return SUBSCRIBERS.replace(body) { m ->
            val number = count(m.groupValues[2]) ?: return@replace m.value
            "\"" + m.groupValues[1] + "\":\"" + number + m.groupValues[3] + m.groupValues[4] + "\""
        }
    }

    private fun fixParts(body: String): String {
        if (!body.contains("\"accessibilityLabel\":\"")) return body
        val out = StringBuilder(body.length + 1024)
        var copied = 0
        var labelEnd = 0 // fin du label précédent : on ne remonte jamais avant
        for (m in LABEL.findAll(body)) {
            val previousLabelEnd = labelEnd
            labelEnd = m.range.last + 1
            val label = spaces(m.groupValues[1])
            val ago = label.indexOf("il y a ")
            val wanted = when {
                // Date : la version longue (« il y a 9 jours ») est lue par NewPipe en français
                // (sans « Diffusé » devant, pour les anciens directs)
                ago >= 0 -> { _: String -> label.substring(ago) }
                // Vues : le nombre en chiffres (« 831 k » → « 831000 vues »)
                label.endsWith(" vues") || label.endsWith(" vue") -> { shown: String -> count(shown)?.let { "$it vues" } }
                else -> continue
            }
            // Le texte affiché : le dernier "content":"…" avant ce label (et après le label précédent)
            val from = maxOf(copied, previousLabelEnd, m.range.first - LOOK_BACK)
            val at = body.lastIndexOf(CONTENT, m.range.first).takeIf { it >= from } ?: continue
            val valueStart = at + CONTENT.length
            val valueEnd = body.indexOf('"', valueStart).takeIf { it in valueStart..m.range.first } ?: continue
            val fixed = wanted(body.substring(valueStart, valueEnd)) ?: continue
            out.append(body, copied, valueStart).append(fixed)
            copied = valueEnd
        }
        if (copied == 0) return body
        out.append(body, copied, body.length)
        return out.toString()
    }

    /** Espaces insécables (tels quels ou échappés dans le JSON) → espaces simples. */
    private fun spaces(text: String) = text
        .replace("\\u00a0", " ").replace("\\u202f", " ")
        .replace('\u00a0', ' ').replace('\u202f', ' ')

    /** « 831 k » → 831000, « 1,2 M » → 1200000, « 1,6 Md » → 1600000000, « 1 234 » → 1234. */
    private fun count(text: String): Long? {
        val clean = spaces(text).trim()
        val m = COMPACT.find(clean) ?: return null
        val number = m.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (m.groupValues[2]) {
            "k" -> 1e3
            "M" -> 1e6
            "Md", "Mrd" -> 1e9
            else -> 1.0
        }
        return (number * factor).toLong()
    }
}
