package dev.ryanness.lightsketch.ui

enum class PenTool(val strokeWidthPx: Float) {
    THIN(4f),
    MEDIUM(9f),
    THICK(16f),
    ERASER(0f),
}

/** Radius, in screen px (independent of zoom), of the eraser's touch target. */
const val ERASER_SCREEN_RADIUS_PX = 32f
