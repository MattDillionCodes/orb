package com.orbdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin

/** Hand-drawn line icons, so every pod and setting has a picture as well as a word. */
enum class Ico { PLAY, NOTE, PAD, GRID, GEAR, RESUME, WIFI, DISPLAY, SPEAKER, CLOCK, INFO, STORE, WAVE }

@Composable
fun IconView(ico: Ico, modifier: Modifier, color: Color = Dash.Text) {
    Canvas(modifier) { drawIco(ico, color) }
}

fun DrawScope.drawIco(ico: Ico, color: Color) {
    val w = size.minDimension
    val o = Offset((size.width - w) / 2, (size.height - w) / 2)
    val sw = w * 0.085f
    val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(o.x + x * w, o.y + y * w)
    fun sz(x: Float, y: Float) = Size(x * w, y * w)
    fun poly(vararg pts: Float) = Path().apply {
        moveTo(o.x + pts[0] * w, o.y + pts[1] * w)
        for (i in 2 until pts.size step 2) lineTo(o.x + pts[i] * w, o.y + pts[i + 1] * w)
        close()
    }

    when (ico) {
        Ico.PLAY -> {
            drawRoundRect(color, p(.06f, .14f), sz(.88f, .62f), CornerRadius(.1f * w), style = st)
            drawPath(poly(.42f, .3f, .64f, .45f, .42f, .6f), color)
            drawLine(color, p(.3f, .9f), p(.7f, .9f), sw, StrokeCap.Round)
        }
        Ico.NOTE -> {
            drawOval(color, p(.12f, .62f), sz(.32f, .25f))
            drawOval(color, p(.56f, .52f), sz(.32f, .25f))
            drawLine(color, p(.42f, .74f), p(.42f, .14f), sw, StrokeCap.Round)
            drawLine(color, p(.86f, .64f), p(.86f, .06f), sw, StrokeCap.Round)
            drawLine(color, p(.42f, .14f), p(.86f, .06f), sw * 1.6f, StrokeCap.Round)
        }
        Ico.PAD -> {
            drawRoundRect(color, p(.04f, .26f), sz(.92f, .5f), CornerRadius(.25f * w), style = st)
            drawLine(color, p(.18f, .51f), p(.38f, .51f), sw, StrokeCap.Round)
            drawLine(color, p(.28f, .41f), p(.28f, .61f), sw, StrokeCap.Round)
            drawCircle(color, w * .06f, p(.66f, .45f))
            drawCircle(color, w * .06f, p(.78f, .58f))
        }
        Ico.GRID -> for (x in listOf(.08f, .56f)) for (y in listOf(.08f, .56f)) {
            drawRoundRect(color, p(x, y), sz(.36f, .36f), CornerRadius(.08f * w))
        }
        Ico.GEAR -> {
            val c = p(.5f, .5f)
            for (k in 0 until 8) rotate(k * 45f, c) { drawLine(color, p(.5f, .1f), p(.5f, .24f), sw * 1.8f, StrokeCap.Round) }
            drawCircle(color, w * .27f, c, style = Stroke(sw * 1.3f))
            drawCircle(color, w * .08f, c)
        }
        Ico.RESUME -> {
            drawPath(poly(.18f, .14f, .62f, .5f, .18f, .86f), color)
            drawRoundRect(color, p(.68f, .14f), sz(.15f, .72f), CornerRadius(.05f * w))
        }
        Ico.WIFI -> {
            for (r in listOf(.22f, .44f, .66f)) drawArc(color, -135f, 90f, false, p(.5f - r, .84f - r), sz(r * 2, r * 2), style = st)
            drawCircle(color, w * .07f, p(.5f, .84f))
        }
        Ico.DISPLAY -> {
            drawRoundRect(color, p(.06f, .12f), sz(.88f, .6f), CornerRadius(.08f * w), style = st)
            drawLine(color, p(.5f, .72f), p(.5f, .86f), sw, StrokeCap.Round)
            drawLine(color, p(.28f, .88f), p(.72f, .88f), sw, StrokeCap.Round)
            drawLine(color, p(.22f, .58f), p(.4f, .3f), sw * .7f, StrokeCap.Round)
        }
        Ico.SPEAKER -> {
            drawPath(poly(.08f, .38f, .28f, .38f, .5f, .16f, .5f, .84f, .28f, .62f, .08f, .62f), color)
            for (r in listOf(.16f, .3f)) drawArc(color, -45f, 90f, false, p(.52f - r, .5f - r), sz(r * 2, r * 2), style = st)
        }
        Ico.CLOCK -> {
            drawCircle(color, w * .4f, p(.5f, .5f), style = st)
            drawLine(color, p(.5f, .5f), p(.5f, .24f), sw, StrokeCap.Round)
            drawLine(color, p(.5f, .5f), p(.68f, .6f), sw, StrokeCap.Round)
        }
        Ico.INFO -> {
            drawCircle(color, w * .4f, p(.5f, .5f), style = st)
            drawCircle(color, w * .06f, p(.5f, .3f))
            drawLine(color, p(.5f, .45f), p(.5f, .72f), sw * 1.2f, StrokeCap.Round)
        }
        Ico.STORE -> {
            drawRoundRect(color, p(.14f, .32f), sz(.72f, .58f), CornerRadius(.08f * w), style = st)
            drawArc(color, 180f, 180f, false, p(.32f, .12f), sz(.36f, .4f), style = st)
            drawPath(poly(.42f, .48f, .62f, .61f, .42f, .74f), color)
        }
        Ico.WAVE -> {
            val path = Path()
            for (i in 0..40) {
                val x = i / 40f
                val y = .5f + .28f * sin(x * 4 * PI.toFloat()) * sin(x * PI.toFloat())
                if (i == 0) path.moveTo(o.x + x * w, o.y + y * w) else path.lineTo(o.x + x * w, o.y + y * w)
            }
            drawPath(path, color, style = st)
        }
    }
}
