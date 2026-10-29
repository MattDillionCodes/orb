package com.orbdash.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.orbdash.R

/** Palette lifted from the 2001 dashboard: near-black green void, radioactive lime glow, pale mint text. */
object Dash {
    val Void = Color(0xFF010601)
    val Deep = Color(0xFF062106)
    val Moss = Color(0xFF0E3B0A)
    val Green = Color(0xFF3FAE1A)
    val Lime = Color(0xFF8CE234)
    val Glow = Color(0xFFC8FF6E)
    val Core = Color(0xFFF2FFD0)
    val Text = Color(0xFFD9F7B4)
    val TextDim = Color(0xFF7FA867)
    val PanelFill = Color(0x6610320B)
    val PanelEdge = Color(0xAA5ED11F)

    // Controller-style prompt colours.
    val ButtonA = Color(0xFF55C21B)
    val ButtonB = Color(0xFFD52B1E)

    val Font = FontFamily(
        Font(R.font.dash_regular, FontWeight.Normal),
        Font(R.font.dash_bold, FontWeight.Bold),
    )
}
