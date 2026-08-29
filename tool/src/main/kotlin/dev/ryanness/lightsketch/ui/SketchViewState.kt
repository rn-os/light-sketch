package dev.ryanness.lightsketch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 6f
private const val DEFAULT_EXTENT_PX = 1200f

/** Tracks the infinite canvas's pan/zoom, in the same px space `SketchCanvas` draws in. */
class SketchViewState {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var canvasSize by mutableStateOf(IntSize.Zero)

    val pageWidth: Float
        get() = if (canvasSize.width > 0) canvasSize.width.toFloat() else DEFAULT_EXTENT_PX

    val pageHeight: Float
        get() = if (canvasSize.height > 0) canvasSize.height.toFloat() else DEFAULT_EXTENT_PX

    fun screenToDoc(point: Offset): Offset = (point - pan) / zoom

    fun applyPinch(centroid: Offset, docPointUnderFinger: Offset, zoomChange: Float) {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = newZoom
        pan = centroid - docPointUnderFinger * newZoom
    }

    fun currentPageIndex(): Int {
        val docYAtCenter = (canvasSize.height / 2f - pan.y) / zoom
        return floor(docYAtCenter / pageHeight).toInt().coerceAtLeast(0)
    }

    fun goToPage(index: Int) {
        val target = index.coerceAtLeast(0)
        zoom = 1f
        pan = Offset(0f, canvasSize.height / 2f - (target * pageHeight + pageHeight / 2f))
    }
}

@Composable
fun rememberSketchViewState(): SketchViewState = remember { SketchViewState() }
