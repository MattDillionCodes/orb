package com.orbdash.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbdash.TvApp

private val TileW = 112.dp
private val TileH = 63.dp // 16:9, the shape of an Android TV app banner

/**
 * Every installed app, A to Z, in a glassy tray along the bottom of the home screen.
 * The caption line above it always says what's focused, in words, so the tiles can be
 * pure pictures (TV banners) without anyone having to guess what an icon is.
 */
@Composable
fun Dock(apps: List<TvApp>, index: Int, focused: Boolean, heading: String, caption: String, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    // Keep the focused tile a few slots in from the left edge, so neighbours on both sides stay visible.
    LaunchedEffect(index) { state.animateScrollToItem((index - 3).coerceAtLeast(0)) }
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "dockLift")

    Column(modifier) {
        Row(Modifier.padding(start = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(heading, style = dashText(15.sp, Dash.Glow, bold = true, spacing = 3.sp))
            Spacer(Modifier.width(16.dp))
            BasicText(caption, style = dashText(18.sp, Dash.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val shape = RoundedCornerShape(40.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0x8820561A).copy(alpha = 0.45f + 0.25f * lift), Color(0xAA061A06), Color(0xCC031003))),
                    shape,
                )
                .border(if (focused) 2.dp else 1.dp, if (focused) Dash.Glow.copy(alpha = 0.8f) else Dash.PanelEdge.copy(alpha = 0.5f), shape)
                .drawBehind {
                    // Glassy top gloss, matching the pods.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f + 0.08f * lift), Color.Transparent), 0f, size.height * 0.45f),
                        Offset(30.dp.toPx(), 3.dp.toPx()), size.copy(width = size.width - 60.dp.toPx(), height = size.height * 0.4f), CornerRadius(size.height),
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (apps.isEmpty()) {
                BasicText("No apps installed yet.", style = dashText(18.sp, Dash.TextDim), modifier = Modifier.padding(start = 28.dp))
            } else {
                LazyRow(
                    state = state,
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                        DockTile(app, selected = focused && i == index, dockFocused = focused)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTile(app: TvApp, selected: Boolean, dockFocused: Boolean) {
    val grow by animateFloatAsState(if (selected) 1.14f else 1f, tween(160), label = "tileGrow")
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(TileW, TileH)
            // Unfocused tiles sit back a little; the whole row brightens when the dock has focus.
            .graphicsLayer { scaleX = grow; scaleY = grow; alpha = if (selected) 1f else if (dockFocused) 0.75f else 0.6f }
            .clip(shape)
            .background(Brush.radialGradient(listOf(Dash.Green.copy(alpha = 0.55f), Dash.Deep, Color(0xFF020A02))))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Dash.PanelEdge.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val art = app.art
        when {
            art != null && app.artIsBanner -> Image(art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            app.icon != null -> Image(app.icon, app.label, Modifier.size(40.dp))
            else -> BasicText(app.label.take(1).uppercase(), style = dashText(30.sp, Dash.Glow, bold = true))
        }
        // Glass sheen across the top of every tile.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (selected) 0.22f else 0.1f), 0.5f to Color.Transparent)))
    }
}
