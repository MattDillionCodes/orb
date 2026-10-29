package com.orbdash.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.orbdash.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** "Living" is the procedural backdrop; the rest are the hand-made wallpapers in res/drawable-nodpi. */
data class Wallpaper(val id: String, val name: String, val res: Int?)

val WALLPAPERS = listOf(
    Wallpaper("living", "Living", null),
    Wallpaper("core", "Core", R.drawable.wp_core),
    Wallpaper("tubes", "Tubes", R.drawable.wp_tubes),
    Wallpaper("membrane", "Membrane", R.drawable.wp_membrane),
    Wallpaper("rings", "Rings", R.drawable.wp_rings),
    Wallpaper("haze", "Haze", R.drawable.wp_haze),
    Wallpaper("plasma", "Plasma", R.drawable.wp_plasma),
)

/** Decodes the chosen wallpaper off the main thread; null while loading or for "Living". */
@Composable
fun rememberWallpaper(wp: Wallpaper): ImageBitmap? {
    val res = LocalContext.current.resources
    val img by produceState<ImageBitmap?>(null, wp.res) {
        value = wp.res?.let { id -> withContext(Dispatchers.IO) { BitmapFactory.decodeResource(res, id)?.asImageBitmap() } }
    }
    return img
}

/** Seconds since first composition; drives every ambient animation from one clock. */
@Composable
fun rememberClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t.floatValue = (it - start) / 1000f }
    }
    return t
}

/** A speck of light at depth [d] (0 = far, 1 = near): near ones are bigger, faster, and parallax more. */
private class Mote(val x: Float, val y: Float, val d: Float, val phase: Float)

/**
 * The living backdrop, painted far-to-near so the layers separate as the camera moves:
 * wallpaper or void → hex tunnel → fog → veins → motes → vignette and scanlines.
 *
 * [focus]   where (0..1 of the screen) the veins and tunnel converge.
 * [camera]  small offset (fractions of the screen) that every layer shifts by, scaled by its depth.
 * [tunnel]  0..1 visibility of the hex tunnel, strong in sub-menus, faint at home.
 */
@Composable
fun Ambience(clock: State<Float>, focus: Offset, camera: Offset, tunnel: Float, wallpaper: ImageBitmap?, modifier: Modifier = Modifier.fillMaxSize()) {
    val motes = remember {
        val r = Random(7)
        List(60) { Mote(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
    }
    Canvas(modifier) {
        val t = clock.value
        fun shift(depth: Float) = Offset(camera.x * size.width * depth, camera.y * size.height * depth)
        val c = Offset(size.width * focus.x, size.height * focus.y)
        val busy = if (wallpaper != null) 0.4f else 1f // calm procedural layers over a picture

        drawRect(Dash.Void)
        if (wallpaper != null) {
            drawWallpaper(wallpaper, t, shift(-0.6f))
            // Scrim keeps text readable over the brighter wallpapers.
            drawRect(Color.Black.copy(alpha = 0.32f))
        } else {
            drawRect(Brush.radialGradient(listOf(Dash.Moss.copy(alpha = 0.85f), Dash.Deep.copy(alpha = 0.5f), Color.Transparent), c + shift(-0.3f), size.height * 0.95f))
        }

        drawTunnel(c + shift(-0.25f), t, tunnel * busy)

        if (wallpaper == null) for (i in 0 until 4) {
            val f = Offset(size.width * (0.5f + 0.42f * sin(t * 0.045f + i * 1.9f)), size.height * (0.5f + 0.38f * cos(t * 0.037f + i * 2.6f))) + shift(-0.4f)
            drawCircle(Brush.radialGradient(listOf(Dash.Green.copy(alpha = 0.10f), Color.Transparent), f, size.height * 0.55f), size.height * 0.55f, f)
        }

        drawVeins(c + shift(-0.5f), t, busy)

        for (m in motes) {
            val speed = 0.004f + 0.02f * m.d
            val y = ((m.y - t * speed) % 1f + 1f) % 1f
            val x = m.x + 0.012f * sin(t * 0.6f + m.phase)
            val p = Offset(x * size.width, y * size.height) + shift(-(0.3f + 1.4f * m.d))
            val a = (0.15f + 0.4f * m.d) * (0.6f + 0.4f * sin(t * 1.3f + m.phase))
            val r = 0.8f + 3.2f * m.d
            if (m.d > 0.82f) drawCircle(Brush.radialGradient(listOf(Dash.Glow.copy(alpha = a * 0.5f), Color.Transparent), p, r * 5), r * 5, p)
            drawCircle(Dash.Glow.copy(alpha = a), r, p)
        }

        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.78f)), center, size.maxDimension * 0.62f))
        // CRT scanlines: one tiled gradient instead of hundreds of lines.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), 0f, 4f, TileMode.Repeated))
    }
}

/** Cover-fit with a 10% overscan margin so the slow drift and parallax never reveal an edge. */
private fun DrawScope.drawWallpaper(img: ImageBitmap, t: Float, parallax: Offset) {
    val scale = maxOf(size.width / img.width, size.height / img.height) * 1.1f
    val w = img.width * scale
    val h = img.height * scale
    val slackX = (w - size.width) / 2
    val slackY = (h - size.height) / 2
    val dx = (-slackX + slackX * 0.5f * sin(t * 0.031f) + parallax.x).coerceIn(-2 * slackX, 0f)
    val dy = (-slackY + slackY * 0.5f * cos(t * 0.027f) + parallax.y).coerceIn(-2 * slackY, 0f)
    drawImage(img, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
}

/** Hexagonal rings that fly towards the viewer from the vanishing point, like the old sub-menu tunnels. */
private fun DrawScope.drawTunnel(vp: Offset, t: Float, amount: Float) {
    if (amount < 0.01f) return
    val rings = 10
    val spin = t * 0.02f
    for (i in 0 until rings) {
        val z = (i + (t * 0.18f) % 1f) / rings
        val r = size.maxDimension * (0.03f + 1.1f * z * z)
        val a = amount * 0.32f * (4f * z * (1f - z))
        val hex = Path()
        for (k in 0..6) {
            val ang = spin + k * PI.toFloat() / 3f + PI.toFloat() / 6f
            val p = Offset(vp.x + cos(ang) * r, vp.y + sin(ang) * r * 0.86f)
            if (k == 0) hex.moveTo(p.x, p.y) else hex.lineTo(p.x, p.y)
        }
        drawPath(hex, Dash.Lime.copy(alpha = a), style = Stroke(1f + 3f * z))
    }
    for (k in 0 until 6) {
        val ang = spin + k * PI.toFloat() / 3f + PI.toFloat() / 6f
        val far = Offset(vp.x + cos(ang) * size.maxDimension, vp.y + sin(ang) * size.maxDimension * 0.86f)
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Dash.Green.copy(alpha = 0.22f * amount)), vp, far), vp, far, 2f)
    }
}

private fun DrawScope.drawVeins(c: Offset, t: Float, strength: Float) {
    val count = 11
    val reach = size.maxDimension * 0.75f
    for (i in 0 until count) {
        val base = (i.toFloat() / count) * 2f * PI.toFloat() + 0.3f
        val wobble = 0.22f * sin(t * 0.21f + i * 1.7f)
        val path = Path().apply {
            moveTo(c.x, c.y)
            var px = c.x; var py = c.y
            // Three curved segments, each bending a little more, so veins look grown rather than drawn.
            for (s in 1..3) {
                val a = base + wobble * s + 0.35f * sin(i * 3.1f + s)
                val d = reach * s / 3f
                val nx = c.x + cos(a) * d
                val ny = c.y + sin(a) * d * 0.8f
                val bend = a + 0.9f * sin(t * 0.13f + i + s)
                cubicTo(
                    px + cos(bend) * d * 0.25f, py + sin(bend) * d * 0.2f,
                    nx - cos(a + 0.6f) * d * 0.18f, ny - sin(a + 0.6f) * d * 0.15f,
                    nx, ny,
                )
                px = nx; py = ny
            }
        }
        val pulse = 0.5f + 0.5f * sin(t * 0.8f - i * 0.9f)
        drawPath(path, Dash.Green.copy(alpha = (0.06f + 0.05f * pulse) * strength), style = Stroke(width = 18f, cap = StrokeCap.Round))
        drawPath(path, Dash.Lime.copy(alpha = (0.10f + 0.12f * pulse) * strength), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        // A spark of light travelling outward along each vein.
        val sp = (t * 0.12f + i * 0.37f) % 1f
        val pm = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val pos = pm.getPosition(pm.length * sp)
        drawCircle(Brush.radialGradient(listOf(Dash.Glow.copy(alpha = 0.7f * (1f - sp) * strength), Color.Transparent), pos, 14f), 14f, pos)
    }
}
