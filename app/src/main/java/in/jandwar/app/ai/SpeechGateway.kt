package `in`.jandwar.app.ai

interface SpeechGateway {
    fun start()
    fun stop()
    fun setListener(listener: Listener)

    interface Listener {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(error: String)
    }
}

interface Speaker {
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
}

interface Translator {
    fun translate(text: String, fromLang: String, toLang: String, callback: (String) -> Unit)
}
