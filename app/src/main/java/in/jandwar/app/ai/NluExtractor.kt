package `in`.jandwar.app.ai

interface NluExtractor {
    fun extract(
        text: String,
        langCode: String,
        currentProfile: ProfileFragment?,
        isOnline: Boolean,
        callback: Callback
    )

    interface Callback {
        fun onResult(fragment: ProfileFragment)
        fun onError(reason: String)
    }
}
