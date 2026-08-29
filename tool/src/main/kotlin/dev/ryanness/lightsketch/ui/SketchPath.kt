package dev.ryanness.lightsketch.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** Builds a smoothed path through midpoints, so freehand strokes don't look faceted. */
fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    if (points.size == 1) {
        val p = points[0]
        path.moveTo(p.x, p.y)
        path.lineTo(p.x, p.y)
        return path
    }

    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}
