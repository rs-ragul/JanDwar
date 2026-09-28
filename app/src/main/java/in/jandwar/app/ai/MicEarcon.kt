package `in`.jandwar.app.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
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

    // ── System beep suppression ─────────────────────────────────────────────

    /** Silences the recogniser's own earcons. Always pair with [unmuteSystem]. */
    fun muteSystem() {
        if (muted) return
        muted = true
        MUTED_STREAMS.forEach { setMute(it, true) }
    }

    fun unmuteSystem() {
        if (!muted) return
        muted = false
        MUTED_STREAMS.forEach { setMute(it, false) }
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
