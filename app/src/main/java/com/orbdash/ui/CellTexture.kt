package com.orbdash.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The Membrane motif, generated in code: a tileable Voronoi "cell wall" texture.
 * Each pixel finds its nearest and second-nearest seed; where those distances are almost equal
 * the pixel sits on a wall between two cells, and glows. Computed once on a background thread.
 */

private const val SIZE = 256
private const val SEEDS = 26

@Volatile private var cached: ImageBitmap? = null

/** White cell walls on transparent, tileable in both directions; tint it when drawing. Built once per process. */
@Composable
fun rememberCellTexture(): ImageBitmap? {
    val img by produceState(cached) {
        if (value == null) value = withContext(Dispatchers.Default) { cached ?: buildCellTexture().also { cached = it } }
    }
    return img
}

private fun buildCellTexture(): ImageBitmap {
    val rnd = Random(11) // fixed seed: the same cells every time, so it's "yours", not random noise
    val sx = FloatArray(SEEDS) { rnd.nextFloat() * SIZE }
    val sy = FloatArray(SEEDS) { rnd.nextFloat() * SIZE }
    val px = IntArray(SIZE * SIZE)
    for (y in 0 until SIZE) for (x in 0 until SIZE) {
        var d1 = Float.MAX_VALUE
        var d2 = Float.MAX_VALUE
        for (i in 0 until SEEDS) {
            // Wrap-around distance makes the texture tile seamlessly.
            var dx = kotlin.math.abs(x - sx[i]); if (dx > SIZE / 2) dx = SIZE - dx
            var dy = kotlin.math.abs(y - sy[i]); if (dy > SIZE / 2) dy = SIZE - dy
            val d = dx * dx + dy * dy
            if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
        }
        val edge = sqrt(d2) - sqrt(d1) // 0 on a wall
        val wall = exp(-edge * edge / 3f)          // crisp wall
        val glow = exp(-edge / 9f) * 0.35f         // soft light bleeding into the cell
        val a = ((wall + glow).coerceAtMost(1f) * 255).toInt()
        px[y * SIZE + x] = (a shl 24) or 0xFFFFFF
    }
    return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}
