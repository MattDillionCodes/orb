package com.orbdash.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The music light show, in the spirit of the original Xbox's visualizer.
 *
 * The classic trick is *feedback*: every frame starts by redrawing the previous frame slightly
 * zoomed, rotated and faded (and nudged towards green), then draws the live sound on top. Old
 * shapes are carried along and decay, which is what makes the swirling, pouring trails.
 *
 * It renders on its own thread into a small image (384x216) that the UI stretches to full screen:
 * cheap on a TV, and the upscale softens everything into a glow. Three buffers rotate so the UI
 * is never shown a half-drawn frame.
 */
class VisualRenderer(private val player: MusicPlayer) {
    companion object {
        const val W = 384
        const val H = 216
        val STYLES = listOf("VORTEX", "RIBBONS", "STARBURST")
    }

    private val buffers = Array(3) { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) } }
    private val images: List<ImageBitmap> = buffers.map { it.asImageBitmap() }
    @Volatile private var latest = 0
    /** Bumped after every finished frame, so the UI redraws exactly when there's something new. */
    val frame = mutableIntStateOf(0)
    @Volatile var style = 0
    @Volatile private var running = false
    private var worker: Thread? = null

    fun image(): ImageBitmap = images[latest]

    // Each start gets a new generation; an old loop that's still finishing its frame sees the change
    // and exits, so a quick stop-then-start never leaves two threads drawing into the same buffers.
    @Volatile private var generation = 0

    fun start() {
        if (running) return
        running = true
        val gen = ++generation
        player.setAnalysisWanted(true)
        worker = thread(name = "visualizer", isDaemon = true) { loop(gen) }
    }

    fun stop() { running = false; generation++; worker = null; player.setAnalysisWanted(false) }

    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    private fun loop(gen: Int) {
        val start = SystemClock.uptimeMillis()
        val feedback = Paint(Paint.FILTER_BITMAP_FLAG)
        val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
        val matrix = Matrix()
        val path = Path()
        val sparks = ArrayList<Spark>()
        val rnd = Random(9)
        var cur = 0
        while (running && gen == generation) {
            val frameStart = SystemClock.uptimeMillis()
            val t = (frameStart - start) / 1000f
            val b = player.bands(t)
            val playing = player.audible()
            val src = buffers[cur]
            val next = (cur + 1) % 3
            val c = Canvas(buffers[next])
            c.drawColor(Color.BLACK)

            // 1. Feedback: the last frame, transformed per style, faded and tinted green.
            val cx = W / 2f; val cy = H / 2f
            matrix.reset()
            when (style) {
                0 -> { matrix.postScale(1.035f + b.bass * 0.03f, 1.035f + b.bass * 0.03f, cx, cy); matrix.postRotate(0.8f + b.mid * 2.2f, cx, cy) }
                1 -> { matrix.postScale(1.012f, 1.0f, cx, cy); matrix.postTranslate(0f, -2.2f - b.bass * 3f) }
                else -> { matrix.postScale(1.06f + b.bass * 0.05f, 1.06f + b.bass * 0.05f, cx, cy); matrix.postRotate(-1.4f, cx, cy) }
            }
            // Starburst zooms outward fastest, so its trails must fade faster or they flood the screen.
            val decay = (if (playing) 1f else 0.9f) * (if (style == 2) 0.9f else 1f)
            feedback.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0.86f * decay, 0f, 0f, 0f, 0f,
                0f, 0.955f * decay, 0f, 0f, 0f,
                0f, 0f, 0.84f * decay, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
            c.drawBitmap(src, matrix, feedback)

            // 2. The live sound, added on top as light.
            val wave = b.wave
            fun w(i: Int, n: Int): Float = if (wave == null || wave.isEmpty()) 0f else ((wave[i * wave.size / n].toInt() and 0xFF) - 128) / 128f
            when (style) {
                0 -> { // Vortex: a ring bent by the waveform, breathing with the bass.
                    val r = H * 0.17f * (1f + b.bass * 0.6f)
                    path.reset()
                    val n = 96
                    for (i in 0..n) {
                        val a = i * 2 * PI.toFloat() / n + t * 0.4f
                        val rr = r + w(i % n, n) * H * 0.14f + sin(a * 5 + t * 2) * b.high * 6f
                        val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    add.strokeWidth = 2.4f; add.color = Color.argb(230, 200, 255, 110); c.drawPath(path, add)
                    add.strokeWidth = 1.1f; add.color = Color.argb(255, 242, 255, 208); c.drawPath(path, add)
                }
                1 -> { // Ribbons: three waveform lines low on the screen, flowing upward through the feedback.
                    for (k in 0 until 3) {
                        path.reset()
                        val base = H * (0.66f + k * 0.09f)
                        val n = 80
                        for (i in 0..n) {
                            val x = i * W / n.toFloat()
                            val y = base + w((i * 3 + k * 17) % n, n) * H * (0.10f + b.mid * 0.12f) + sin(i * 0.15f + t * (1.2f + k)) * 3f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        add.strokeWidth = 1.8f
                        add.color = when (k) { 0 -> Color.argb(220, 140, 226, 52); 1 -> Color.argb(220, 200, 255, 110); else -> Color.argb(200, 63, 174, 26) }
                        c.drawPath(path, add)
                    }
                }
                else -> { // Starburst: a spinning seven-point star that swells on the beat.
                    path.reset()
                    val pts = 14
                    val r = H * 0.11f * (1f + b.bass * 0.9f)
                    for (i in 0..pts) {
                        val a = i * PI.toFloat() / 7 + t * 0.9f
                        val rr = if (i % 2 == 0) r else r * (0.42f + b.mid * 0.3f)
                        val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    add.strokeWidth = 2f; add.color = Color.argb(230, 200, 255, 110); c.drawPath(path, add)
                    dot.color = Color.argb((90 + b.high * 120).toInt().coerceAtMost(255), 242, 255, 208)
                    repeat((b.high * 10).toInt()) { c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, 1.2f, dot) }
                }
            }

            // 3. Sparks thrown out on each beat.
            if (b.beat && sparks.size < 120) repeat(16) {
                val a = rnd.nextFloat() * 2 * PI.toFloat(); val sp = 1.5f + rnd.nextFloat() * 3f
                sparks += Spark(cx, cy, cos(a) * sp, sin(a) * sp, 1f)
            }
            val it = sparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.x += s.vx; s.y += s.vy; s.life -= 0.03f
                if (s.life <= 0f) { it.remove(); continue }
                dot.color = Color.argb((s.life * 255).toInt(), 230, 255, 160)
                c.drawCircle(s.x, s.y, 1.4f, dot)
            }

            cur = next
            latest = cur
            frame.intValue++
            // ~30 fps while playing, slower when paused.
            val budget = if (playing) 33L else 66L
            val spent = SystemClock.uptimeMillis() - frameStart
            if (spent < budget) Thread.sleep(budget - spent)
        }
    }
}
