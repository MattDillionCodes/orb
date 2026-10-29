package com.orbdash.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun dashText(size: TextUnit, color: Color = Dash.Text, bold: Boolean = false, spacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = Dash.Font, fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = spacing)

/**
 * The glossy "jelly" pill, layered like a real piece of gel:
 * drop shadow → bloom (focused only) → body gradient → light caught in the bottom → top gloss → rim.
 * [depth] 0..1 pushes unfocused pills back: smaller and dimmer the further they are from focus.
 * A leading [icon] or app [thumb] sits in a recessed round socket.
 */
@Composable
fun JellyPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    textSize: TextUnit = 24.sp,
    sub: String? = null,
    depth: Float = 0f,
    icon: Ico? = null,
    thumb: ImageBitmap? = null,
) {
    val grow by animateFloatAsState(if (selected) 1.08f else 1f - 0.06f * depth, tween(180), label = "grow")
    val fade by animateFloatAsState(1f - 0.4f * depth, tween(180), label = "fade")
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "glow")
    val textColor by animateColorAsState(if (selected) Dash.Deep else Dash.Text, tween(180), label = "text")
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer { scaleX = grow; scaleY = grow; alpha = fade }
            .drawBehind {
                val cr = CornerRadius(size.height / 2)
                // Soft drop shadow, offset down: the pill floats above the scene.
                for (k in 1..4) {
                    val pad = k * 2.dp.toPx()
                    drawRoundRect(Color.Black.copy(alpha = 0.13f), Offset(-pad / 2, 5.dp.toPx() + pad / 2), size.copy(width = size.width + pad, height = size.height + pad / 2), cr)
                }
                // Fake blur: stacked, widening translucent outlines fade into a soft bloom.
                if (glow > 0f) for (k in 1..7) {
                    val pad = k * 3.dp.toPx()
                    drawRoundRect(Dash.Lime.copy(alpha = 0.09f * glow * (1f - k / 8f)), Offset(-pad, -pad), size.copy(width = size.width + pad * 2, height = size.height + pad * 2), CornerRadius(size.height / 2 + pad))
                }
            }
            .background(
                Brush.verticalGradient(
                    if (selected) listOf(Dash.Core, Dash.Glow, Dash.Lime, Dash.Green)
                    else listOf(Color(0xCC1C5A12), Color(0xBB0A2C08), Color(0xDD072507)),
                ),
                shape,
            )
            .drawBehind {
                val cr = CornerRadius(size.height)
                // Light refracted through the gel pools along the bottom edge.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.Transparent, (if (selected) Color.White else Dash.Lime).copy(alpha = if (selected) 0.35f else 0.22f)), size.height * 0.55f, size.height),
                    Offset(size.height * 0.2f, size.height * 0.5f), size.copy(width = size.width - size.height * 0.4f, height = size.height * 0.45f), cr,
                )
                // Glassy top gloss.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.5f else 0.16f), Color.Transparent), 0f, size.height * 0.5f),
                    Offset(size.height * 0.25f, 3.dp.toPx()), size.copy(width = size.width - size.height * 0.5f, height = size.height * 0.42f), cr,
                )
            }
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.9f) else Dash.PanelEdge.copy(alpha = 0.6f), shape)
            .padding(start = if (icon != null || thumb != null) 8.dp else 26.dp, end = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null || thumb != null) {
                val s = height - 14.dp
                Box(
                    Modifier
                        .size(s)
                        .background(Brush.radialGradient(listOf(Color(0xFF031003), Color(0xFF0B3209))), CircleShape)
                        .border(1.5.dp, if (selected) Dash.Deep.copy(alpha = 0.6f) else Dash.PanelEdge.copy(alpha = 0.7f), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        thumb != null -> Image(thumb, null, Modifier.size(s * 0.72f))
                        icon != null -> IconView(icon, Modifier.size(s * 0.56f), if (selected) Dash.Glow else Dash.Text)
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            BasicText(text, style = dashText(textSize, textColor, bold = true, spacing = 2.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (sub != null) {
                BasicText("  $sub", style = dashText((textSize.value * 0.62f).sp, textColor.copy(alpha = 0.7f)), maxLines = 1)
            }
        }
    }
}

/** Round controller-style glyph, e.g. green OK and red BACK. */
@Composable
fun Glyph(symbol: String, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .drawBehind { drawCircle(Color.Black.copy(alpha = 0.4f), size.minDimension / 2, center + Offset(0f, 3.dp.toPx())) }
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), color, color.copy(red = color.red * 0.5f, green = color.green * 0.5f, blue = color.blue * 0.5f)), Offset(30f, 24f), 70f), CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(symbol, style = dashText(if (symbol.length > 1) 12.sp else 18.sp, Color.White, bold = true).copy(textAlign = TextAlign.Center))
        }
        BasicText(label, style = dashText(18.sp, Dash.Text, bold = true, spacing = 2.sp), modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Remote hints along the bottom: how to move, what OK will do *here*, and how to go back.
 * Pass null for [okLabel] when OK does nothing on the focused item, so the hint never lies.
 */
@Composable
fun Legend(modifier: Modifier = Modifier, okLabel: String? = "SELECT", backLabel: String? = "BACK") {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        MoveGlyph()
        if (okLabel != null) Glyph("OK", Dash.ButtonA, okLabel)
        if (backLabel != null) Glyph("◀", Dash.ButtonB, backLabel)
    }
}

/** Neutral round glyph with up/down arrows: "use the D-pad to move". */
@Composable
private fun MoveGlyph() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(
            Modifier
                .size(34.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF3C5A33), Color(0xFF16261A))), CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            val c = center; val s = size.minDimension
            fun tri(dir: Float) = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y + dir * s * 0.34f)
                lineTo(c.x - s * 0.16f, c.y + dir * s * 0.1f)
                lineTo(c.x + s * 0.16f, c.y + dir * s * 0.1f)
                close()
            }
            drawPath(tri(-1f), Color.White)
            drawPath(tri(1f), Color.White)
        }
        BasicText("MOVE", style = dashText(18.sp, Dash.Text, bold = true, spacing = 2.sp), modifier = Modifier.padding(start = 10.dp))
    }
}

fun networkLabel(context: Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "Offline"
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Online"
        else -> "Offline"
    }
}

/** Top-right status: big time, then date and a connection lamp underneath. */
@Composable
fun StatusCluster(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var net by remember { mutableStateOf(networkLabel(context)) }
    LaunchedEffect(Unit) { while (true) { now = Date(); net = networkLabel(context); delay(5_000) } }
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val ampm = remember(now) { SimpleDateFormat("a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE  MMM d", Locale.getDefault()).format(now).uppercase() }
    val online = net != "Offline"
    // One row, so the home pods can glide up underneath without colliding with it.
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(date, style = dashText(16.sp, Dash.TextDim, bold = true, spacing = 2.sp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(8.dp).background(if (online) Dash.Lime else Dash.ButtonB, CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
        BasicText(" ${net.uppercase()}", style = dashText(16.sp, if (online) Dash.TextDim else Dash.ButtonB, bold = true, spacing = 2.sp))
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(width = 1.dp, height = 26.dp).background(Dash.PanelEdge.copy(alpha = 0.6f)))
        Spacer(Modifier.width(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(time, style = dashText(34.sp, Dash.Text, bold = true))
            BasicText(" $ampm", style = dashText(16.sp, Dash.TextDim, bold = true), modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

/** Top-left screen title on a green bar with a light glint that sweeps across now and then. */
@Composable
fun Header(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "glint").animateFloat(
        -0.3f, 2.2f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "glint",
    )
    Column(modifier) {
        Box(
            Modifier
                .width(520.dp)
                // Offscreen layer lets the glint use SrcAtop: it only lights pixels the bar already covers.
                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                .background(Brush.horizontalGradient(listOf(Dash.Green.copy(alpha = 0.9f), Dash.Moss.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
                .drawWithContent {
                    drawContent()
                    val x = sweep * size.width
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent), Offset(x - 80f, 0f), Offset(x + 80f, size.height)), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    // Top gloss line, matching the pills.
                    drawLine(Color.White.copy(alpha = 0.25f), Offset(30.dp.toPx(), 2.dp.toPx()), Offset(size.width * 0.6f, 2.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }
                .padding(start = 28.dp, top = 6.dp, bottom = 6.dp),
        ) {
            BasicText(title.uppercase(), style = dashText(40.sp, Dash.Core, bold = true, spacing = 6.sp))
        }
        if (subtitle != null) {
            BasicText(subtitle.uppercase(), style = dashText(16.sp, Dash.TextDim, bold = true, spacing = 3.sp), modifier = Modifier.padding(start = 32.dp, top = 6.dp))
        }
    }
}

/** Sci-fi corner brackets just outside a panel's edges. */
fun Modifier.bracketFrame(color: Color = Dash.Glow, len: Dp = 18.dp, gap: Dp = 6.dp): Modifier = drawWithContent {
    drawContent()
    val l = len.toPx(); val g = gap.toPx(); val w = 2.5.dp.toPx()
    val (x0, y0, x1, y1) = listOf(-g, -g, size.width + g, size.height + g)
    for ((cx, cy, sx, sy) in listOf(listOf(x0, y0, 1f, 1f), listOf(x1, y0, -1f, 1f), listOf(x0, y1, 1f, -1f), listOf(x1, y1, -1f, -1f))) {
        drawLine(color, Offset(cx, cy), Offset(cx + l * sx, cy), w, StrokeCap.Round)
        drawLine(color, Offset(cx, cy), Offset(cx, cy + l * sy), w, StrokeCap.Round)
    }
}
