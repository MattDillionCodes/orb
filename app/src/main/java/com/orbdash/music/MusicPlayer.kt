package com.orbdash.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.hypot
import kotlin.math.sin

/** One playable track: from the TV's music library, or the built-in demo. */
data class Track(val title: String, val artist: String, val album: String, val durationMs: Long, val uri: Uri)

/**
 * Plays a list of tracks and analyses the sound for the visualizer.
 *
 * Analysis uses Android's Visualizer on our own player's audio session. Android requires the
 * RECORD_AUDIO permission for that even though it only reads this app's output. Without the
 * permission (or if the TV refuses), [bands] falls back to a gentle synthetic pulse so the light
 * show still runs, just not in time with the music.
 *
 * It can also follow another music app instead ([followOther]): the remote then controls that app
 * through [others], and analysis listens to everything the TV plays (audio session 0), which some
 * TVs refuse. In that case the synthetic pulse takes over, the same as above.
 */
class MusicPlayer(private val context: Context) {
    val tracks = mutableStateOf<List<Track>>(emptyList())
    val current = mutableIntStateOf(-1)
    val playing = mutableStateOf(false)
    /** Set when the TV's player fails, so the screen can say so instead of a misleading "Paused". */
    val error = mutableStateOf<String?>(null)
    val others = OtherApps(context)
    /** True while the light show follows another app rather than one of our tracks. */
    val followingOther = mutableStateOf(false)

    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    @Volatile private var fft: ByteArray? = null
    @Volatile private var wave: ByteArray? = null
    @Volatile var analysing = false
        private set

    // Smoothed band levels, 0..~1: fast attack, slower release, like a VU meter.
    private var bass = 0f; private var mid = 0f; private var high = 0f
    private var bassAvg = 0.2f

    /** Called whenever playback starts or stops, e.g. to silence the dashboard's ambient hum. */
    var onPlayingChanged: (Boolean) -> Unit = {}

    fun play(index: Int) {
        val list = tracks.value
        if (index !in list.indices) return
        others.pause() // one song at a time: hush the other app
        followingOther.value = false
        release()
        current.intValue = index
        error.value = null
        val track = list[index]
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(context, track.uri)
            mp.setOnPreparedListener { it.start(); setPlaying(true); attachVisualizer(it.audioSessionId) }
            mp.setOnCompletionListener { next() }
            mp.setOnErrorListener { _, what, extra -> failed(track, "player error what=$what extra=$extra"); true }
            mp.prepareAsync()
        }.onFailure { failed(track, "couldn't open: $it") }
    }

    /** Show the light show for whatever another app is playing; our own track stops. */
    fun followOther() {
        release()
        current.intValue = -1
        followingOther.value = true
        attachVisualizer(0) // session 0 = the TV's whole output mix
    }

    /** Whether the music the light show follows is playing: ours, or the other app's. */
    fun audible() = if (followingOther.value) others.playing.value else playing.value

    fun toggle() {
        if (followingOther.value) return others.toggle()
        val mp = player ?: return play(current.intValue.coerceAtLeast(0))
        runCatching { if (mp.isPlaying) { mp.pause(); setPlaying(false) } else { mp.start(); setPlaying(true) } }
    }

    /** [reason] goes to the log, so an unexpected pause can be traced later (adb logcat -s OrbDashMusic). */
    fun pause(reason: String = "pause pressed") {
        if (playing.value) Log.i(TAG, "Paused: $reason")
        runCatching { player?.takeIf { it.isPlaying }?.pause() }; setPlaying(false) }
    fun next() { if (followingOther.value) return others.next(); val n = tracks.value.size; if (n > 0) play((current.intValue + 1) % n) }
    fun previous() { if (followingOther.value) return others.previous(); val n = tracks.value.size; if (n > 0) play((current.intValue - 1 + n) % n) }

    fun positionMs(): Int = if (followingOther.value) others.positionMs() else runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    fun durationMs(): Int = if (followingOther.value) others.durationMs() else runCatching { player?.duration ?: 0 }.getOrDefault(0)

    /** Re-try analysis after the user grants the permission mid-song. */
    fun retryAnalysis() { if (followingOther.value) attachVisualizer(0) else player?.let { attachVisualizer(it.audioSessionId) } }

    fun release() {
        runCatching { visualizer?.release() }; visualizer = null; analysing = false
        runCatching { player?.release() }; player = null
        setPlaying(false)
    }

    private fun failed(track: Track, why: String) {
        Log.w(TAG, "Couldn't play \"${track.title}\" (${track.uri}): $why")
        error.value = "Couldn't play this song"
        // A failed player answers every later call (like the time bar asking its position) with another
        // error, so close it now. Pressing OK on the song makes a fresh one and tries again.
        runCatching { visualizer?.release() }; visualizer = null; analysing = false
        runCatching { player?.release() }; player = null
        setPlaying(false)
    }

    private fun setPlaying(on: Boolean) {
        if (playing.value != on) { playing.value = on; onPlayingChanged(on) }
    }

    private fun attachVisualizer(session: Int) {
        runCatching { visualizer?.release() }
        visualizer = runCatching {
            Visualizer(session).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) { wave = data.copyOf() }
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) { fft = data.copyOf() }
                }, Visualizer.getMaxCaptureRate(), true, true)
                enabled = true
            }
        }.getOrNull()
        analysing = visualizer != null
    }

    /**
     * Current bass / mid / high levels and a beat flag, for the visualizer to call once per frame.
     * [t] is only used by the synthetic fallback.
     */
    fun bands(t: Float): Bands {
        val f = fft
        val (b, m, h) = if (analysing && f != null && audible()) {
            // Visualizer FFT: pairs of (real, imaginary) bytes; bin k ≈ k * sampleRate / captureSize.
            fun mag(k: Int) = hypot(f[2 * k].toFloat(), f[2 * k + 1].toFloat()) / 128f
            fun avg(from: Int, to: Int) = (from until to.coerceAtMost(f.size / 2)).map(::mag).average().toFloat()
            Triple(avg(1, 5) * 1.6f, avg(5, 40) * 2.6f, avg(40, 160) * 5f)
        } else if (audible()) {
            // No analysis available: a calm ~96 BPM pulse so it still breathes.
            val beat = (t * 1.6f) % 1f
            Triple(0.55f * kotlin.math.exp(-beat * 6f) + 0.15f, 0.3f + 0.15f * sin(t * 2.3f), 0.2f + 0.1f * sin(t * 5.1f))
        } else Triple(0.04f, 0.04f, 0.03f)
        fun follow(cur: Float, target: Float) = if (target > cur) cur + (target - cur) * 0.6f else cur + (target - cur) * 0.12f
        bass = follow(bass, b.coerceIn(0f, 1.5f)); mid = follow(mid, m.coerceIn(0f, 1.5f)); high = follow(high, h.coerceIn(0f, 1.5f))
        val beat = bass > bassAvg * 1.45f && bass > 0.25f
        bassAvg += (bass - bassAvg) * 0.04f
        return Bands(bass, mid, high, beat, wave)
    }
}

private const val TAG = "OrbDashMusic"

class Bands(val bass: Float, val mid: Float, val high: Float, val beat: Boolean, val wave: ByteArray?)
