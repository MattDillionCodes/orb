package com.orbdash

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Tiny synthesized UI sounds in the spirit of the 2001 dashboard: soft, airy, slightly metallic.
 * Each sound is rendered once into a static AudioTrack, so playback is instant on D-pad presses.
 * Rendering (~330k samples) happens on a background thread so the first frame isn't held up;
 * any sound requested before it finishes is simply skipped.
 */
class Sfx(private val enabled: () -> Boolean, private val humEnabled: () -> Boolean) {
    private val rate = 44100
    @Volatile private var bank: Bank? = null
    @Volatile private var humWanted = false
    @Volatile private var bootWanted = false

    init {
        thread(name = "sfx-render", isDaemon = true) {
            bank = Bank()
            if (bootWanted) boot() // the intro asks for this before rendering is done
            if (humWanted) startHum()
        }
    }

    private inner class Bank {

        // Short glassy tick when the highlight moves.
        val move = render(0.07) { t -> tone(t, 1760.0, 2640.0) * env(t, 0.004, 28.0) * 0.22 }

        // Rising two-partial chime for "open/select".
        val select = render(0.32) { t ->
            val f = 520.0 + 900.0 * (t / 0.32)
            (tone(t, f, f * 1.5) * 0.6 + sin(2 * PI * f * 2.01 * t) * 0.2) * env(t, 0.006, 9.0) * 0.30
        }

        // Falling, darker sweep for "back".
        val back = render(0.26) { t ->
            val f = 900.0 - 560.0 * (t / 0.26)
            tone(t, f, f * 1.33) * env(t, 0.005, 11.0) * 0.28
        }

        // Boot: sub-bass swell, a rumble at the flash (~1.6s), then a bright rising three-note figure.
        val boot = run {
            val rnd = Random(3)
            var lp = 0.0
            render(3.4) { t ->
                val swell = if (t < 1.6) (t / 1.6) * (t / 1.6) else exp(-(t - 1.6) * 1.4)
                val bass = sin(2 * PI * (38.0 + 12.0 * t) * t) * 0.55 * swell
                lp += (rnd.nextDouble(-1.0, 1.0) - lp) * 0.03 // one-pole low-pass turns white noise into rumble
                val rumble = if (t > 1.55) lp * 6.0 * exp(-(t - 1.55) * 2.2) else 0.0
                var bell = 0.0
                for ((i, f) in listOf(1318.5, 1975.5, 2637.0).withIndex()) {
                    val s = 1.62 + i * 0.16
                    if (t > s) bell += (sin(2 * PI * f * (t - s)) + 0.3 * sin(2 * PI * f * 2.0 * (t - s))) * exp(-(t - s) * 3.5) * 0.12
                }
                (bass + rumble + bell) * 0.6
            }
        }

        // Ambient hum: a 4-second loop. Every frequency completes whole cycles in 4s so the loop is seamless.
        val hum = render(4.0, loop = true) { t ->
            val lfo = 0.5 + 0.5 * sin(2 * PI * 0.25 * t)
            val drone = sin(2 * PI * 55.0 * t) * 0.5 + sin(2 * PI * 110.0 * t + 0.3) * 0.25 + sin(2 * PI * 165.0 * t) * 0.08
            val shimmer = sin(2 * PI * 880.0 * t) * sin(2 * PI * 0.5 * t) * 0.03
            (drone * (0.7 + 0.3 * lfo) + shimmer) * 0.09
        }

    }

    fun move() = play(bank?.move)
    fun select() = play(bank?.select)
    fun back() = play(bank?.back)
    fun boot() {
        val b = bank ?: run { bootWanted = true; return }
        bootWanted = false
        play(b.boot)
    }

    fun startHum() {
        humWanted = true
        val h = bank?.hum ?: return
        if (!humEnabled()) return stopHum()
        runCatching { if (h.playState != AudioTrack.PLAYSTATE_PLAYING) h.play() }
    }

    fun stopHum() {
        humWanted = false
        runCatching { bank?.hum?.pause() }
    }

    // Restarting an AudioTrack can block for tens of ms on TV audio stacks, so it never runs on the
    // UI thread: a key press just queues the sound.
    private val player = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "sfx-play").apply { isDaemon = true } }

    private fun play(track: AudioTrack?) {
        if (track == null || !enabled()) return
        player.execute {
            runCatching {
                track.stop()
                track.reloadStaticData()
                track.play()
            }
        }
    }

    private fun tone(t: Double, f1: Double, f2: Double) =
        sin(2 * PI * f1 * t) * 0.7 + sin(2 * PI * f2 * t) * 0.3

    /** Quick attack, exponential decay. */
    private fun env(t: Double, attack: Double, decay: Double) =
        if (t < attack) t / attack else exp(-(t - attack) * decay)

    private fun render(seconds: Double, loop: Boolean = false, wave: (Double) -> Double): AudioTrack? = runCatching {
        val n = (seconds * rate).toInt()
        val pcm = ShortArray(n) { i -> (wave(i.toDouble() / rate).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
            .apply {
                write(pcm, 0, n)
                if (loop) setLoopPoints(0, n, -1)
            }
    }.getOrNull()
}
