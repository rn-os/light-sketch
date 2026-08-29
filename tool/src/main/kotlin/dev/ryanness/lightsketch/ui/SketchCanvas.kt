package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import com.thelightphone.sdk.ui.LightColors
import com.thelightphone.sdk.ui.LightThemeTokens
import dev.ryanness.lightsketch.data.SketchStrokeData
import kotlin.math.floor
import kotlin.math.ceil

private enum class GestureMode { DRAW_OR_ERASE, TRANSFORM }

@Composable
fun SketchCanvas(
    state: SketchViewState,
    strokes: List<SketchStrokeData>,
    tool: PenTool,
    paginationEnabled: Boolean,
    onStrokeCommitted: (List<Offset>, Float) -> Unit,
    onEraseGestureBegin: () -> Unit,
    onErase: (Offset, Float) -> Unit,
    onInteractionStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors

    val cachedPaths = remember(strokes) {
        strokes.map { stroke -> buildSmoothPath(stroke.points.map { Offset(it.x, it.y) }) to stroke.widthPx }
    }
    val livePoints = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.canvasSize = it }
            .pointerInput(tool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onInteractionStart()

                    var mode = GestureMode.DRAW_OR_ERASE
                    val activeId = down.id
                    var previousCentroid = down.position
                    var previousSpread = 0f
                    livePoints.clear()

                    if (tool == PenTool.ERASER) {
                        onEraseGestureBegin()
                        onErase(state.screenToDoc(down.position), ERASER_SCREEN_RADIUS_PX / state.zoom)
                    } else {
                        livePoints.add(state.screenToDoc(down.position))
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2 && mode == GestureMode.DRAW_OR_ERASE) {
                            mode = GestureMode.TRANSFORM
                            livePoints.clear()
                            previousCentroid = centroidOf(pressed)
                            previousSpread = spreadOf(pressed, previousCentroid)
                        }

                        if (mode == GestureMode.TRANSFORM) {
                            if (pressed.size < 2) {
                                pressed.forEach { it.consume() }
                                break
                            }
                            val centroid = centroidOf(pressed)
                            val spread = spreadOf(pressed, centroid)
                            val zoomChange = if (previousSpread > 1f) spread / previousSpread else 1f
                            val docPointUnderFinger = state.screenToDoc(previousCentroid)
                            state.applyPinch(centroid, docPointUnderFinger, zoomChange)
                            previousCentroid = centroid
                            previousSpread = spread
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed.firstOrNull { it.id == activeId } ?: break
                            if (change.positionChanged()) {
                                val docPoint = state.screenToDoc(change.position)
                                if (tool == PenTool.ERASER) {
                                    onErase(docPoint, ERASER_SCREEN_RADIUS_PX / state.zoom)
                                } else {
                                    livePoints.add(docPoint)
                                }
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                    }

                    if (mode == GestureMode.DRAW_OR_ERASE && tool != PenTool.ERASER && livePoints.isNotEmpty()) {
                        onStrokeCommitted(livePoints.toList(), tool.strokeWidthPx)
                    }
                    livePoints.clear()
                }
            },
    ) {
        if (paginationEnabled) {
            drawPaginationGuides(state, colors)
        }

        translate(left = state.pan.x, top = state.pan.y) {
            scale(scale = state.zoom, pivot = Offset.Zero) {
                cachedPaths.forEach { (path, width) ->
                    drawPath(
                        path = path,
                        color = colors.content,
                        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                if (livePoints.isNotEmpty()) {
                    drawPath(
                        path = buildSmoothPath(livePoints.toList()),
                        color = colors.content,
                        style = Stroke(width = tool.strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPaginationGuides(state: SketchViewState, colors: LightColors) {
    val pageHeight = state.pageHeight
    if (pageHeight <= 0f) return

    val topDocY = (0f - state.pan.y) / state.zoom
    val bottomDocY = (state.canvasSize.height - state.pan.y) / state.zoom
    val startPage = floor(topDocY / pageHeight).toInt() - 1
    val endPage = ceil(bottomDocY / pageHeight).toInt() + 1
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), 0f)

    for (n in startPage..endPage) {
        if (n <= 0) continue
        val docY = n * pageHeight
        val screenY = docY * state.zoom + state.pan.y
        drawLine(
            color = colors.contentSecondary,
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = 2f,
            pathEffect = dashEffect,
        )
    }
}

private fun centroidOf(changes: List<PointerInputChange>): Offset {
    var x = 0f
    var y = 0f
    for (change in changes) {
        x += change.position.x
        y += change.position.y
    }
    return Offset(x / changes.size, y / changes.size)
}

private fun spreadOf(changes: List<PointerInputChange>, centroid: Offset): Float {
    if (changes.isEmpty()) return 0f
    var total = 0f
    for (change in changes) {
        total += (change.position - centroid).getDistance()
    }
    return total / changes.size
}
