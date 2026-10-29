package com.orbdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A Canvas for soft, slowly-moving artwork (glow, fog, the orb) that TV graphics chips struggle with.
 *
 * [content] is rendered into an offscreen texture at [resolution] of full size (e.g. 0.33 = a ninth
 * of the pixels) and only re-rendered [fps] times a second, or when [keys] change. Every other frame
 * just stretches the cached texture: one cheap blit instead of many full-screen gradient passes.
 *
 * Rebuilding the surrounding UI does NOT re-render it: [content] is a fresh lambda on every
 * recomposition, so it's held in a plain (unobserved) slot and only [keys] decide what's "new".
 *
 * Inside [content], `size` and `center` report the full size, so drawing code is unchanged;
 * the time argument replaces reading the clock directly.
 */
@Composable
fun CachedCanvas(
    clock: State<Float>, fps: Float, resolution: Float, modifier: Modifier,
    keys: List<Any?> = emptyList(), content: DrawScope.(t: Float) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val latest = remember { arrayOfNulls<DrawScope.(Float) -> Unit>(1) }
    latest[0] = content
    // Structural equality: setting an equal list is a no-op, so only real changes invalidate.
    val keyState = remember { mutableStateOf(keys) }
    keyState.value = keys
    // Only changes fps times a second, so the Canvas below is only invalidated that often.
    val tick = remember(fps) { derivedStateOf { if (fps <= 0f) 0 else floor(clock.value * fps).toInt() } }
    val onDraw: DrawScope.() -> Unit = remember(layer, resolution) {
        {
            keyState.value // read so a key change re-renders
            val t = if (fps <= 0f) 0f else tick.value / fps
            val full = size
            layer.compositingStrategy = CompositingStrategy.Offscreen // keeps the rendered texture between frames
            layer.record(IntSize(ceil(full.width * resolution).toInt().coerceAtLeast(1), ceil(full.height * resolution).toInt().coerceAtLeast(1))) {
                scale(resolution, resolution, Offset.Zero) {
                    val small = drawContext.size
                    drawContext.size = full // so content can draw in full-size coordinates
                    latest[0]?.invoke(this, t)
                    drawContext.size = small
                }
            }
            scale(1f / resolution, 1f / resolution, Offset.Zero) { drawLayer(layer) }
        }
    }
    Canvas(modifier, onDraw)
}
