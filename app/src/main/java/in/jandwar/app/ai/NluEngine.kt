package `in`.jandwar.app.ai

/**
 * Contract for turning one spoken/typed utterance into profile data plus the
 * assistant's next line.
 *
 * Deliberately has **no error path**: the hybrid implementation always returns
 * a usable result, falling back to the on-device engine when the cloud is
 * unreachable. A beneficiary must never see an API error.
 */
interface NluEngine {

    fun understand(
        utterance: String,
        lang: String,
        current: ProfileFragment,
        askedSlot: ProfileFragment.Slot?,
        callback: Callback
    )

    fun interface Callback {
        fun onResult(result: Result)
    }

    data class Result(
        val fragment: ProfileFragment,
        val source: Source,
        /** True when the interview should end and matching should run. */
        val finished: Boolean
    )

    enum class Source { CLOUD, ON_DEVICE }
}
