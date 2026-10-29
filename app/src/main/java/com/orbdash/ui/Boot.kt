package com.orbdash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val BOOT_MS = 3400

/**
 * Intro, after the 2001 boot: green fog in a black void, tendrils reach in from the dark and
 * knot into a writhing blob, a green-white flash with a shockwave, then a wordmark rises with
 * a glint. Pure Compose, so it's a little different every time it runs.
 */
@Composable
fun Boot(clock: State<Float>, onStart: () -> Unit, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        onStart()
        p.animateTo(1f, tween(BOOT_MS, easing = LinearEasing))
        onDone()
    }
    val v = p.value
    fun seg(a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)
    val fadeOut = 1f - seg(0.9f, 1f)

    val reduced = LocalReducedEffects.current
    Box(Modifier.fillMaxSize().alpha(fadeOut), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black)
            // On slow TVs skip the full-screen fog and flash; the orb, tendrils and shockwave carry it.
            if (reduced) {
                val wave = seg(0.47f, 0.7f)
                if (wave in 0.001f..0.999f) drawCircle(Dash.Glow.copy(alpha = 0.7f * (1f - wave)), size.maxDimension * 0.6f * wave, center, style = Stroke(10f * (1f - wave) + 2f))
                return@Canvas
            }
            val t = clock.value
            val fog = seg(0f, 0.3f)
            for (i in 0 until 5) {
                val c = Offset(center.x + size.width * 0.25f * sin(t * 0.7f + i * 1.3f), center.y + size.height * 0.2f * cos(t * 0.5f + i * 2f))
                drawCircle(Brush.radialGradient(listOf(Dash.Green.copy(alpha = 0.22f * fog), Color.Transparent), c, size.height * 0.6f), size.height * 0.6f, c)
            }

            // Tendrils crawl in from off-screen and converge on the centre.
            val reach = seg(0.05f, 0.45f)
            val gone = seg(0.47f, 0.55f)
            if (reach > 0f && gone < 1f) for (i in 0 until 9) {
                val a = i * 2f * PI.toFloat() / 9f + 0.4f * sin(t * 0.8f + i)
                val far = Offset(center.x + cos(a) * size.maxDimension * 0.7f, center.y + sin(a) * size.maxDimension * 0.7f)
                val tip = far + (center - far) * reach
                val bend = Offset(-sin(a), cos(a)) * (90f * sin(t * 2f + i))
                val path = Path().apply { moveTo(far.x, far.y); quadraticBezierTo((far.x + tip.x) / 2 + bend.x, (far.y + tip.y) / 2 + bend.y, tip.x, tip.y) }
                drawPath(path, Dash.Green.copy(alpha = 0.25f * (1f - gone)), style = Stroke(16f, cap = StrokeCap.Round))
                drawPath(path, Dash.Glow.copy(alpha = 0.6f * (1f - gone)), style = Stroke(3f, cap = StrokeCap.Round))
            }

            // Shockwave ring racing outward from the flash.
            val wave = seg(0.47f, 0.7f)
            if (wave in 0.001f..0.999f) {
                drawCircle(Dash.Glow.copy(alpha = 0.7f * (1f - wave)), size.maxDimension * 0.6f * wave, center, style = Stroke(10f * (1f - wave) + 2f))
            }
            val flash = seg(0.45f, 0.5f) * (1f - seg(0.5f, 0.66f))
            drawRect(Dash.Glow.copy(alpha = 0.85f * flash))
        }

        // Blob gathers from nothing, then shrinks back as the wordmark takes over.
        val grow = seg(0.12f, 0.47f)
        val recede = seg(0.5f, 0.75f)
        Orb(clock, energy = grow, modifier = Modifier.size(630.dp).scale((0.05f + 0.95f * grow) * (1f - 0.55f * recede)).offset(y = (-60 * recede).dp))

        val word = seg(0.55f, 0.75f)
        val glint = seg(0.7f, 0.88f)
        BasicText(
            "O R B   U I",
            style = dashText(56.sp, Dash.Core, bold = true, spacing = 6.sp),
            modifier = Modifier
                .offset(y = (110 + 30 * (1f - word)).dp)
                .alpha(word)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // A bright bar sweeps across the letters only (SrcAtop keeps it inside the glyphs).
                    val x = -size.width * 0.2f + size.width * 1.4f * glint
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White, Color.Transparent), Offset(x - 60f, 0f), Offset(x + 60f, size.height)), blendMode = BlendMode.SrcAtop)
                },
        )
        BasicText(
            "SYSTEM READY",
            style = dashText(16.sp, Dash.TextDim, bold = true, spacing = 8.sp),
            modifier = Modifier.offset(y = 170.dp).alpha(seg(0.78f, 0.88f)),
        )
    }
}
