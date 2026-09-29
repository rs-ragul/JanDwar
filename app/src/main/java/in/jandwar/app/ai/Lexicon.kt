package `in`.jandwar.app.ai

import android.util.Log
import org.json.JSONObject

/**
 * The multilingual surface forms the on-device NLU matches against, loaded
 * from `assets/lexicon.json`.
 *
 * Matching runs in a strict order, and the order matters more than any single
 * rule:
 *
 *  1. **Fold** the form and the utterance through the *same* normaliser.
 *  2. **Phrase match** — multi-word forms match as substrings
 *     ("anywhere in my district").
 *  3. **Word match** — a single-word Latin form must match a whole token; an
 *     Indic form may match as a substring, because Tamil, Telugu, Kannada and
 *     Malayalam glue case and tense suffixes straight onto the stem.
 *  4. **Exact wins** — if a token is itself a known surface form anywhere in
 *     the lexicon, it means *that* and nothing else. Fuzzy matching is
 *     reserved for words the lexicon has never seen.
 *  5. **Fuzzy**, for unseen tokens only: Levenshtein within a script-aware
 *     tolerance, and only when the first character already agrees.
 *
 * ### Why the rules are this fussy
 *
 * Fuzzy matching tuned on English is actively harmful on Indic scripts. One
 * codepoint is a whole syllable, so an edit distance of 1 on a six-character
 * Tamil word is an enormous semantic jump. Auditing every surface form against
 * its own detector found 35 cross-category collisions at the naive tolerances:
 *
 * | form | means | was also matching |
 * |---|---|---|
 * | ஐந்தாம் | 5th standard | பத்தாம் — 10th standard |
 * | பட்டம் | degree | வட்டம் — district |
 * | எட்டாம் | 8th standard | பட்டம் — degree |
 * | करना | to do | करघा — loom |
 * | यहीं | right here | नहीं — no |
 * | pathu | ten | pashu — cattle |
 * | jilla | district | illa — no |
 * | inter | Intermediate | painter |
 *
 * Reading "I finished 5th" as "I finished 10th" changes which NSQF courses a
 * person is eligible for. That is a correctness bug, not a polish one.
 *
 * Tightening length alone cut 35 collisions to 13; the first-character guard
 * alone cut them to 17. Neither was enough on its own. Exact-wins plus the
 * first-character guard plus script-aware tolerance removes all 35 while still
 * resolving the genuine ASR drift this exists for (*டிப்ளமோ* for *டிப்ளோமா*).
 *
 * `tools/check_nlu.py` reads this same JSON and asserts that every surface
 * form still resolves to its own detector.
 *
 * Adding slang is a data change: append to `lexicon.json` and rebuild.
 */
class Lexicon(json: JSONObject?) {

    private val forms: Map<String, List<String>>
    private val phrases: Map<String, List<String>>
    private val singles: Map<String, List<String>>

    /**
     * Single-word form -> every category that literally contains it.
     *
     * This is what makes "exact wins" cheap: one hash lookup says whether a
     * token is a word the lexicon already knows.
     */
    private val owner: Map<String, Set<String>>

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
                        // Fold exactly as the utterance is folded. Without
                        // this, every punctuated form (பி.ஏ, பி.எஸ்.சி, பி.காம்)
                        // was unreachable dead data: the utterance had its dots
                        // stripped and the form kept them.
                        val v = fold(arr.optString(i))
                        if (v.isNotEmpty() && !list.contains(v)) list.add(v)
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

        val own = HashMap<String, MutableSet<String>>()
        for ((key, list) in map) {
            for (f in list) {
                if (!f.contains(' ')) own.getOrPut(f) { HashSet() }.add(key)
            }
        }
        owner = own
        Log.i(TAG, "lexicon: ${map.size} categories, " +
                "${map.values.sumOf { it.size }} forms, ${owner.size} known words")
    }

    val isEmpty: Boolean get() = forms.isEmpty()

    fun formsOf(key: String): List<String> = forms[key].orEmpty()

    /**
     * Is [form] genuinely present in [text]?
     *
     * Phrases and Indic words: substring. Latin words: whole token only.
     * Without the token rule, "inter" (Intermediate) matched inside
     * "interested" and quietly recorded the speaker as Class 12.
     */
    private fun present(form: String, text: String, tokens: Set<String>): Boolean {
        if (form.contains(' ')) return text.contains(form)
        if (isIndic(form)) {
            // Indic scripts glue case and tense endings onto the stem, which
            // is what lets மாடு reach மாடுகள். But a *short* stem then also
            // fires inside an unrelated word: ஹொல / ಹೊಲ (field, farming) sits
            // inside ಹೊಲಿಗೆ (sewing), so "ಹೊಲಿಗೆ ಕೆಲಸ ಗೊತ್ತು" -- I know
            // tailoring -- also reported an interest in agriculture. Below
            // four codepoints the form must stand as its own token.
            return if (form.length >= 4) text.contains(form) else tokens.contains(form)
        }
        return tokens.contains(form)
    }

    /** No fuzz at all. Use where a false positive is costly. */
    fun hasExact(text: String, key: String): Boolean {
        val list = forms[key] ?: return false
        val t = fold(text)
        val tokens = tokenise(t).toHashSet()
        return list.any { present(it, t, tokens) }
    }

    /** Phrase, then whole-word, then fuzzy for unseen tokens only. */
    fun has(text: String, key: String): Boolean {
        val t = fold(text)
        if (t.isEmpty()) return false
        val tokens = tokenise(t)
        val tokenSet = tokens.toHashSet()

        phrases[key]?.forEach { if (t.contains(it)) return true }
        val candidates = singles[key] ?: return false
        candidates.forEach { if (present(it, t, tokenSet)) return true }

        for (tok in tokens) {
            // Exact wins: a word the lexicon already knows means itself. Had it
            // belonged to this category we would have matched it above.
            if (owner.containsKey(tok)) continue
            if (hasDigit(tok)) continue
            for (form in candidates) {
                // "10th" and "12th" are one edit apart but mean different
                // years of schooling. Anything with a digit must match exactly.
                if (hasDigit(form)) continue
                val tol = tolerance(tok, form)
                if (tol == 0) continue
                // A transcription slip almost never lands on the first
                // character, but "pathu"/"pashu" and "करना"/"करघा" differ only
                // later. Requiring the first character to agree is cheap and
                // removes most cross-category noise.
                if (tok[0] != form[0]) continue
                if (kotlin.math.abs(tok.length - form.length) > tol) continue
                if (withinDistance(tok, form, tol)) return true
            }
        }
        return false
    }

    /** Length of the longest surface form of [key] present in [text]. */
    fun matchLen(text: String, key: String): Int {
        val list = forms[key] ?: return 0
        val t = fold(text)
        val tokens = tokenise(t).toHashSet()
        var best = 0
        for (f in list) if (f.length > best && present(f, t, tokens)) best = f.length
        return best
    }

    /**
     * The category whose *longest* surface form appears in [text].
     *
     * A precedence list cannot express specificity: "pre university" contains
     * "university", so an ordered scan returned `graduate` for a Class 12
     * answer. Longest-match resolves it; ties fall back to the caller's order.
     */
    fun bestMatch(text: String, keys: List<String>): String? {
        var bestKey: String? = null
        var bestLen = 0
        for (key in keys) {
            val n = matchLen(text, key)
            if (n > bestLen) {
                bestLen = n
                bestKey = key
            }
        }
        if (bestKey != null) return bestKey
        return keys.firstOrNull { has(text, it) }
    }

    /** First category from [keys] that matches [text]. */
    fun firstMatch(text: String, keys: List<String>): String? =
        keys.firstOrNull { has(text, it) }

    companion object {
        private const val TAG = "Lexicon"

        private val PUNCT = Regex("[\\p{Punct}&&[^+]]")
        private val SPACES = Regex("\\s+")

        /**
         * The one normaliser. Both the utterance and every lexicon form go
         * through it, which is the only way they can ever meet.
         */
        fun fold(s: String): String =
            s.lowercase().replace(PUNCT, " ").replace(SPACES, " ").trim()

        fun hasDigit(s: String): Boolean = s.any { it in '0'..'9' }

        fun tokenise(text: String): List<String> =
            text.split(' ').filter { it.isNotBlank() }

        /** Devanagari through Malayalam — the five Indic scripts we support. */
        fun isIndic(s: String): Boolean = s.any { it.code in 0x0900..0x0D7F }

        /**
         * How many edits to forgive, by script.
         *
         * An Indic codepoint carries a whole syllable, so one edit is a far
         * bigger semantic jump than one Latin letter. Indic forms therefore
         * have to be substantially longer before any fuzz is allowed.
         */
        fun tolerance(a: String, b: String): Int {
            // maxOf, not minOf: the Python engine that the 2,597-form audit
            // runs against uses the longer string, and the two matchers have
            // to agree or the app and the IVR line classify the same sentence
            // differently.
            val len = maxOf(a.length, b.length)
            return if (isIndic(a) || isIndic(b)) {
                when {
                    len <= 5 -> 0
                    len <= 8 -> 1
                    else -> 2
                }
            } else {
                // Romanised Indic vocabulary made the old Latin floor unsafe.
                // The lexicon now holds hundreds of short transliterations --
                // pasu (cow), aadu (goat), kada (shop) -- and at tolerance 1 a
                // four-letter word lands on top of them: "pass" in "PUC pass
                // aagiruken" matched "pasu" and recorded a cow-rearing
                // interest. At tolerance 2, "appuram" (afterwards) matched
                // "appalam" (a snack). Both are two edits or fewer yet
                // unrelated, so the floor must sit above a common word.
                when {
                    len <= 4 -> 0
                    len <= 7 -> 1
                    else -> 2
                }
            }
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
