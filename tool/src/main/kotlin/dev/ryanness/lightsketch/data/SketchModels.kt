package dev.ryanness.lightsketch.data

import kotlinx.serialization.Serializable

@Serializable
data class SketchPointData(val x: Float, val y: Float)

@Serializable
data class SketchStrokeData(
    val points: List<SketchPointData>,
    val widthPx: Float,
)

@Serializable
data class SketchDocumentData(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val paginationEnabled: Boolean = false,
    val strokes: List<SketchStrokeData> = emptyList(),
    val deletedAtEpochMs: Long? = null,
)

@Serializable
data class SketchDocumentMeta(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
)

@Serializable
data class SketchIndexData(
    val documents: List<SketchDocumentMeta> = emptyList(),
    val autoEmptyTrashEnabled: Boolean = true,
)
