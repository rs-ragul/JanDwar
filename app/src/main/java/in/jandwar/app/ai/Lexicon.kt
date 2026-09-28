package `in`.jandwar.app.ai

import android.util.Log
import org.json.JSONObject

/**
 * The multilingual surface forms the on-device NLU matches against, loaded
 * from `assets/lexicon.json`.
 *
 * Two levels of matching:
 *
 *  1. **Substring** — fast, exact, handles multi-word phrases
 *     ("anywhere in my district").
 *  2. **Token-level fuzzy** — Levenshtein distance against each word of the
 *     utterance, with a tolerance that scales with length.
 *
 * Level 2 is what makes the difference for Indian-language speech. A
 * recogniser transcribing Tamil returned *டிப்ளமோ* where the dictionary form
 * is *டிப்ளோமா*; that is an edit distance of 2 on an 8-character word, so it
 * now resolves instead of triggering "I didn't hear you". The same applies to
 * every regional pronunciation the ASR spells its own way.
 *
 * Adding slang is a data change: append to `lexicon.json` and rebuild. No
 * Kotlin edit, and `tools/check_nlu.py` validates against the same file.
 */
class Lexicon(json: JSONObject?) {

    private val forms: Map<String, List<String>>
    private val phrases: Map<String, List<String>>
    private val singles: Map<String, List<String>>

    init {
        val map = LinkedHashMap<String, List<String>>()
        try {
            val obj = json?.optJSONObject("forms")
            if (obj != null) {
                val it = obj.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    val arr = obj.optJSONArray(key) ?: continue
                    val list = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i).trim().lowercase()
                        if (v.isNotEmpty()) list.add(v)
                    }
                    if (list.isNotEmpty()) map[key] = list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "lexicon parse failed: ${e.message}")
        }
        forms = map
        // Split once so the hot path does no string inspection.
        phrases = map.mapValues { (_, v) -> v.filter { it.contains(' ') } }
        singles = map.mapValues { (_, v) -> v.filter { !it.contains(' ') } }
        Log.i(TAG, "lexicon: ${map.size} categories, ${map.values.sumOf { it.size }} forms")
    }

    val isEmpty: Boolean get() = forms.isEmpty()

    fun formsOf(key: String): List<String> = forms[key].orEmpty()

    /** Exact substring match only — use when a false positive is costly. */
    fun hasExact(text: String, key: String): Boolean =
        forms[key]?.any { text.contains(it) } == true

    /** Substring match, then token-level fuzzy match. */
    fun has(text: String, key: String): Boolean {
        if (hasExact(text, key)) return true
        val candidates = singles[key] ?: return false
        if (candidates.isEmpty()) return false
        val tokens = tokenise(text)
        if (tokens.isEmpty()) return false
        for (form in candidates) {
            val tol = tolerance(form.length)
            if (tol == 0) continue
            // "10th" and "12th" are one edit apart but mean different years
            // of schooling. Anything carrying a digit must match exactly.
            if (hasDigit(form)) continue
            for (t in tokens) {
                if (hasDigit(t)) continue
                // Cheap length gate before the O(n*m) comparison.
                if (kotlin.math.abs(t.length - form.length) > tol) continue
                if (withinDistance(t, form, tol)) return true
            }
        }
        return false
    }

    /** First category from [keys] that matches [text]. */
    fun firstMatch(text: String, keys: List<String>): String? =
        keys.firstOrNull { has(text, it) }

    companion object {
        private const val TAG = "Lexicon"

        fun hasDigit(s: String): Boolean = s.any { it in '0'..'9' }

        fun tokenise(text: String): List<String> =
            text.split(' ').filter { it.isNotBlank() }

        /**
         * How many edits to forgive. Short words must match exactly — at three
         * characters, one edit reaches a different word entirely.
         */
        fun tolerance(len: Int): Int = when {
            len <= 3 -> 0
            len <= 6 -> 1
            else -> 2
        }

        /** Bounded Levenshtein: stops as soon as every cell exceeds [max]. */
        fun withinDistance(a: String, b: String, max: Int): Boolean {
            if (a == b) return true
            val n = a.length
            val m = b.length
            if (kotlin.math.abs(n - m) > max) return false
            if (n == 0) return m <= max
            if (m == 0) return n <= max

            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                var rowBest = cur[0]
                val ac = a[i - 1]
                for (j in 1..m) {
                    val cost = if (ac == b[j - 1]) 0 else 1
                    cur[j] = minOf(
                        cur[j - 1] + 1,
                        prev[j] + 1,
                        prev[j - 1] + cost
                    )
                    if (cur[j] < rowBest) rowBest = cur[j]
                }
                if (rowBest > max) return false
                val swap = prev
                prev = cur
                cur = swap
            }
            return prev[m] <= max
        }
    }
}
