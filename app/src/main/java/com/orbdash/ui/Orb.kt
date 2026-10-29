package com.orbdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun blobPath(c: Offset, r: Float, t: Float, phase: Float): Path {
    val path = Path()
    val steps = 96
    for (i in 0..steps) {
        val a = i.toFloat() / steps * 2f * PI.toFloat()
        val wob = 0.018f * sin(3 * a + t * 0.9f + phase) + 0.011f * sin(4 * a - t * 1.3f + phase) + 0.006f * sin(6 * a + t * 0.5f)
        val rr = r * (1f + wob)
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * The gel orb, built back-to-front like a real translucent object:
 * floor light pool → halo → outer shell (bright at the rim, like fresnel) → body → inner shadow →
 * membrane cell skin → bubbles → core → caustic and speculars on the surface.
 * [energy] (0..1) flares the core when something is selected.
 */
@Composable
fun Orb(clock: State<Float>, energy: Float, modifier: Modifier) {
    // The orb is all gradients too, so it's rendered into a cached texture a limited number of times
    // a second; on slow TVs, less often and at lower resolution.
    val reduced = LocalReducedEffects.current
    val cells = rememberCellTexture()
    CachedCanvas(clock, fps = if (reduced) 10f else 30f, resolution = if (reduced) 0.5f else 0.75f, modifier = modifier, keys = listOf(energy, cells != null)) { t ->
        val c = center
        // The canvas is 1.5x the orb so its halo and floor glow fade out fully inside the cached
        // texture; anything past the texture edge would be cut off in a visible square.
        val r = size.minDimension * 0.24f * (1f + 0.025f * sin(t * 1.6f))

        // Light the orb casts on an imaginary floor; squashed into an ellipse for perspective.
        val floor = Offset(c.x, c.y + r * 1.42f)
        withTransform({ scale(1f, 0.16f, floor) }) {
            drawCircle(Brush.radialGradient(listOf(Dash.Lime.copy(alpha = 0.55f + 0.3f * energy), Dash.Green.copy(alpha = 0.2f), Color.Transparent), floor, r * 1.3f), r * 1.3f, floor)
        }

        drawCircle(Brush.radialGradient(listOf(Dash.Lime.copy(alpha = 0.35f + 0.15f * energy), Dash.Green.copy(alpha = 0.12f), Color.Transparent), c, r * 2.0f), r * 2.0f, c)

        val shell = blobPath(c, r * 1.1f, t, 1.7f)
        drawPath(shell, Brush.radialGradient(0f to Color.Transparent, 0.78f to Dash.Lime.copy(alpha = 0.06f), 0.97f to Dash.Glow.copy(alpha = 0.32f), 1f to Color.Transparent, center = c, radius = r * 1.13f))

        val blob = blobPath(c, r, t, 0f)
        drawPath(blob, Brush.radialGradient(listOf(Dash.Glow.copy(alpha = 0.95f), Dash.Lime.copy(alpha = 0.85f), Dash.Green.copy(alpha = 0.78f), Dash.Moss.copy(alpha = 0.92f)), Offset(c.x - r * 0.25f, c.y - r * 0.3f), r * 1.35f))

        clipPath(blob) {
            // Inner shadow on the side away from the light gives the ball its volume.
            drawCircle(Brush.radialGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xCC021202), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 1.75f), r * 2f, c)

            // Membrane skin: a cell-wall texture (generated in CellTexture.kt), drifting slowly
            // so the globe looks alive.
            cells?.let { tex ->
                val tile = r * 0.9f
                val drift = Offset((t * r * 0.03f) % tile, (t * r * 0.017f) % tile)
                var y = c.y - r - tile + drift.y
                while (y < c.y + r) {
                    var x = c.x - r - tile + drift.x
                    while (x < c.x + r) {
                        drawImage(
                            tex, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(tile.roundToInt() + 1, tile.roundToInt() + 1),
                            alpha = 0.22f, colorFilter = ColorFilter.tint(Dash.Core, BlendMode.SrcIn),
                        )
                        x += tile
                    }
                    y += tile
                }
            }

            // Bubbles orbit the core in 3D: ones "in front" (sin > 0) are bigger and brighter.
            for (b in 0 until 9) {
                val th = t * (0.25f + 0.04f * b) + b * 2.1f
                val z = sin(th)
                val p = Offset(c.x + cos(th) * r * (0.45f + 0.04f * b), c.y + (b - 4) * r * 0.13f + z * r * 0.06f)
                val br = r * 0.028f * (1.4f + z)
                drawCircle(Dash.Core.copy(alpha = 0.18f + 0.22f * (z + 1f) / 2f), br, p, style = Stroke(br * 0.35f))
                drawCircle(Color.White.copy(alpha = 0.35f * (z + 1f) / 2f), br * 0.3f, p - Offset(br * 0.35f, br * 0.35f))
            }
        }

        val coreR = r * (0.32f + 0.05f * sin(t * 2.2f) + 0.12f * energy)
        drawCircle(Brush.radialGradient(listOf(Color.White, Dash.Core.copy(alpha = 0.9f), Dash.Glow.copy(alpha = 0f)), c, coreR), coreR, c)

        // Caustic: light focused through the gel onto its lower inside surface.
        drawArc(Dash.Glow.copy(alpha = 0.22f), 55f, 70f, false, Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f), style = Stroke(r * 0.03f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

        drawPath(blob, Dash.Glow.copy(alpha = 0.55f), style = Stroke(width = r * 0.025f))
        val hl = Offset(c.x - r * 0.38f, c.y - r * 0.42f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), hl, r * 0.3f), r * 0.3f, hl)
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.045f, hl + Offset(-r * 0.05f, -r * 0.04f))
    }
}
