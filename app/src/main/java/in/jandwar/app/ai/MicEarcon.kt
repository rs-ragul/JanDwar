package `in`.jandwar.app.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * Replaces the platform recogniser's start/stop beeps with a soft cue.
 *
 * Android's `SpeechRecognizer` plays loud system earcons every time it opens
 * and closes the microphone. In a conversation that opens the mic once per
 * question, that "tup-tup" becomes the dominant sound of the app.
 *
 * Losing the cue entirely is worse, though: a first-time or low-literacy user
 * needs to know *when to start talking*. So the system beeps are muted for
 * the duration of recognition and replaced with a short, quiet sine blip —
 * rising when the mic opens, falling when it closes — synthesised here so no
 * audio asset has to ship.
 *
 * Muting is scoped tightly: only the streams that carry the earcon, only
 * while listening, and always restored in a `finally`-style unmute. Speech
 * playback happens on the assistant stream and is never muted.
 */
@Singleton
class MicEarcon @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** User preference — a cue can be turned off entirely in Settings. */
    @Volatile
    var enabled: Boolean = true

    private val audio: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var muted = false

    private val main = Handler(Looper.getMainLooper())

    /** A scheduled "unmute + closing blip", held so it can be cancelled. */
    private var pendingRelease: Runnable? = null

    // ── System beep suppression ─────────────────────────────────────────────

    /** Silences the recogniser's own earcons. Always pair with a release. */
    fun muteSystem() {
        cancelPending()
        if (muted) return
        muted = true
        MUTED_STREAMS.forEach { setMute(it, true) }
    }

    fun unmuteSystem() {
        if (!muted) return
        muted = false
        MUTED_STREAMS.forEach { setMute(it, false) }
    }

    /**
     * End a recognition session.
     *
     * The platform plays its *stop* earcon at end-of-speech -- the same instant
     * we want to sound our own closing blip. Unmuting first, which is what the
     * previous version did, let that stop beep straight through: the user heard
     * our soft cue when the mic opened and the old harsh one when it closed.
     *
     * So the streams stay muted a moment longer than the session, until the
     * platform's beep has been emitted into silence and discarded. Only then do
     * we unmute and play our own falling blip.
     */
    fun releaseAfterCue() {
        cancelPending()
        val r = object : Runnable {
            override fun run() {
                synchronized(this@MicEarcon) {
                    if (pendingRelease === this) pendingRelease = null
                }
                unmuteSystem()
                playClose()
            }
        }
        synchronized(this) { pendingRelease = r }
        main.postDelayed(r, RELEASE_DELAY_MS)
    }

    /** Restore audio immediately with no cue -- cancel, error and teardown. */
    fun releaseNow() {
        cancelPending()
        unmuteSystem()
    }

    /**
     * Called by [TtsSpeaker] before it speaks. Speech must never be swallowed
     * by our own mute, and the closing blip must not play *over* the reply, so
     * a cue still in flight is brought forward to right now instead.
     */
    fun releaseForSpeech() {
        val hadCue = cancelPending()
        unmuteSystem()
        if (hadCue) playClose()
    }

    /** @return true if a closing cue was scheduled and has now been dropped. */
    private fun cancelPending(): Boolean {
        val r = synchronized(this) {
            val p = pendingRelease
            pendingRelease = null
            p
        } ?: return false
        main.removeCallbacks(r)
        return true
    }

    private fun setMute(stream: Int, mute: Boolean) {
        val am = audio ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(
                    stream,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    0
                )
            } else {
                @Suppress("DEPRECATION")
                am.setStreamMute(stream, mute)
            }
        } catch (e: SecurityException) {
            // Do Not Disturb access can be required on some OEM builds.
            Log.d(TAG, "stream $stream mute refused: ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "stream $stream mute failed: ${e.message}")
        }
    }

    // ── Our own soft cue ────────────────────────────────────────────────────

    /** Gentle rising blip: "your turn to speak". */
    fun playOpen() = play(660f, 880f)

    /** Gentle falling blip: "got it, I am thinking". */
    fun playClose() = play(660f, 495f)

    private fun play(fromHz: Float, toHz: Float) {
        if (!enabled) return
        try {
            val samples = tone(fromHz, toHz)
            val track = buildTrack(samples.size * 2)
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) {
                        runCatching { t?.stop() }
                        runCatching { t?.release() }
                    }

                    override fun onPeriodicNotification(t: AudioTrack?) = Unit
                }
            )
            track.play()
        } catch (e: Exception) {
            Log.d(TAG, "earcon failed: ${e.message}")
        }
    }

    private fun buildTrack(sizeBytes: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(sizeBytes, if (min > 0) min else sizeBytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Sonification, not media: respects the user's
                        // notification behaviour rather than the music stream.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_NOTIFICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STATIC
            )
        }
    }

    /**
     * A short sine sweep with a raised-cosine envelope. The envelope is what
     * makes it soft — a bare sine switched on and off is a click, which is
     * exactly the harshness we are removing.
     */
    private fun tone(fromHz: Float, toHz: Float): ShortArray {
        val n = (SAMPLE_RATE * DURATION_S).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = fromHz + (toHz - fromHz) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            // Raised cosine in and out: no edge click.
            val envelope = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * t))
            out[i] = (sin(phase) * envelope * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    companion object {
        private const val TAG = "MicEarcon"

        /**
         * How long the streams stay muted after recognition ends. Long enough
         * to swallow the platform's stop earcon, short enough that nothing
         * else is noticeably held back -- and [releaseForSpeech] pre-empts it
         * anyway the moment the assistant starts talking.
         */
        private const val RELEASE_DELAY_MS = 260L

        private const val SAMPLE_RATE = 22050
        private const val DURATION_S = 0.11f

        /** Quiet on purpose: a cue, not an alert. */
        private const val AMPLITUDE = 0.16

        /** The streams Android recognisers use for their start/stop beeps. */
        private val MUTED_STREAMS = intArrayOf(
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC
        )
    }
}
