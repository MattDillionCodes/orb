package com.orbdash.music

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * "Orb Drift": a built-in demo track synthesized in code, so the player and its light show work on
 * a TV with no music on it, and without shipping anyone's copyrighted audio.
 * 96 BPM, A minor – F – C – G, kick / off-beat hats / bass / pad / arpeggio. Rendered once to a WAV
 * in the app's cache (about 2 MB) on a background thread.
 */
object DemoTrack {
    private const val RATE = 22050
    private const val BPM = 96.0
    private const val BARS = 16

    // Two Music-screen scans can overlap; only one renders, the other waits for it.
    @Synchronized
    fun get(context: Context): Track {
        val file = File(context.cacheDir, "orb_drift_v1.wav")
        val total = (BARS * 4 * (60.0 / BPM) * RATE).toInt()
        if (file.length() != 44L + total * 2L) render(file) // missing, or cut short by a crash mid-write
        val ms = (BARS * 4 * 60_000.0 / BPM).toLong()
        return Track("Orb Drift", "ORB UI", "Built-in demo", ms, Uri.fromFile(file))
    }

    private fun render(file: File) {
        val beat = 60.0 / BPM
        val total = (BARS * 4 * beat * RATE).toInt()
        val out = FloatArray(total)
        // Chord roots (Hz) per bar, cycling: Am, F, C, G.
        val chords = listOf(
            doubleArrayOf(220.0, 261.63, 329.63), doubleArrayOf(174.61, 220.0, 261.63),
            doubleArrayOf(261.63, 329.63, 392.0), doubleArrayOf(196.0, 246.94, 293.66),
        )
        val rnd = Random(5)
        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val beatPos = t / beat
            val bar = (beatPos / 4).toInt()
            val chord = chords[bar % 4]
            val inBeat = (beatPos % 1.0) * beat // seconds since last beat
            val inEighth = (beatPos * 2 % 1.0) * beat / 2
            val inSixteenth = (beatPos * 4 % 1.0) * beat / 4
            val intro = (bar / 2.0).coerceAtMost(1.0) // fade the drums in over the first two bars

            // Kick on every beat: a pitch-dropping sine thump.
            val kf = 45 + 95 * exp(-inBeat * 30)
            val kick = sin(2 * PI * kf * inBeat) * exp(-inBeat * 9) * 0.9 * intro
            // Hats on the off-beats: short bursts of noise.
            val offBeat = ((beatPos + 0.5) % 1.0) * beat
            val hat = (rnd.nextDouble() * 2 - 1) * exp(-offBeat * 60) * 0.18 * intro
            // Bass: chord root an octave down, eighth notes, soft-clipped for some grit.
            val bf = chord[0] / 2
            val bass = tanh(sin(2 * PI * bf * t) * 2.2) * exp(-inEighth * 6) * 0.35
            // Pad: the whole chord, slow swell each bar.
            val barT = (beatPos % 4) * beat
            val pad = chord.sumOf { sin(2 * PI * it * t) + 0.3 * sin(2 * PI * it * 2.005 * t) } / 3.0 *
                (1 - exp(-barT * 1.5)) * 0.16
            // Arpeggio: chord tones two octaves up, sixteenth notes, from bar 4.
            val note = chord[(beatPos * 4).toInt() % 3] * 2
            val arp = if (bar >= 4) (2 / PI) * kotlin.math.asin(sin(2 * PI * note * t)) * exp(-inSixteenth * 14) * 0.14 else 0.0

            out[i] = tanh((kick + hat + bass + pad + arp) * 0.9).toFloat()
        }
        // Short fade at both ends so the loop doesn't click.
        val fade = RATE / 20
        for (k in 0 until fade) { val g = k.toFloat() / fade; out[k] *= g; out[total - 1 - k] *= g }
        writeWav(file, out)
    }

    /** Writes to a temporary file and renames it into place, so a half-written WAV is never left behind. */
    private fun writeWav(file: File, samples: FloatArray) {
        val dataBytes = samples.size * 2
        val tmp = File(file.path + ".tmp")
        RandomAccessFile(tmp, "rw").use { f ->
            f.setLength(0)
            fun i32(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun i16(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            f.writeBytes("RIFF"); i32(36 + dataBytes); f.writeBytes("WAVE")
            f.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
            f.writeBytes("data"); i32(dataBytes)
            val buf = ByteArray(dataBytes)
            samples.forEachIndexed { k, s ->
                val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
                buf[2 * k] = v.toByte(); buf[2 * k + 1] = (v shr 8).toByte()
            }
            f.write(buf)
        }
        tmp.renameTo(file)
    }
}
