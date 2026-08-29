package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.ryanness.lightsketch.data.SketchDocumentData
import dev.ryanness.lightsketch.data.SketchPointData
import dev.ryanness.lightsketch.data.SketchRepository
import dev.ryanness.lightsketch.data.SketchStrokeData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SAVE_DEBOUNCE_MS = 500L
private const val MAX_UNDO_DEPTH = 25

class SketchScreenViewModel(
    private val repository: SketchRepository,
    private val documentId: String,
) : LightViewModel<Unit>() {

    private val _document = MutableStateFlow<SketchDocumentData?>(null)
    val document: StateFlow<SketchDocumentData?> = _document.asStateFlow()

    private val _tool = MutableStateFlow(PenTool.MEDIUM)
    val tool: StateFlow<PenTool> = _tool.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val undoStack = ArrayDeque<List<SketchStrokeData>>()
    private var eraseSnapshotPushed = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _document.value = repository.loadDocument(documentId)
        }
    }

    fun selectTool(tool: PenTool) {
        _tool.value = tool
    }

    fun beginEraseGesture() {
        eraseSnapshotPushed = false
    }

    fun commitStroke(points: List<Offset>, widthPx: Float) {
        val doc = _document.value ?: return
        if (points.isEmpty()) return
        pushUndo(doc.strokes)
        val stroke = SketchStrokeData(points.map { SketchPointData(it.x, it.y) }, widthPx)
        updateDocument(doc.copy(strokes = doc.strokes + stroke))
    }

    fun eraseAt(point: Offset, radius: Float) {
        val doc = _document.value ?: return
        val radiusSq = radius * radius
        val before = doc.strokes
        val after = before.filterNot { stroke -> strokeHits(stroke, point, radiusSq) }
        if (after.size != before.size) {
            if (!eraseSnapshotPushed) {
                pushUndo(before)
                eraseSnapshotPushed = true
            }
            updateDocument(doc.copy(strokes = after))
        }
    }

    fun undo() {
        val doc = _document.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        _canUndo.value = undoStack.isNotEmpty()
        updateDocument(doc.copy(strokes = previous))
    }

    fun togglePagination() {
        val doc = _document.value ?: return
        updateDocument(doc.copy(paginationEnabled = !doc.paginationEnabled))
    }

    fun rename(newName: String) {
        val doc = _document.value ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == doc.name) return
        updateDocument(doc.copy(name = trimmed))
    }

    fun clearPage(pageIndex: Int, pageHeight: Float) {
        val doc = _document.value ?: return
        val top = pageIndex * pageHeight
        val bottom = top + pageHeight
        val before = doc.strokes
        val after = before.filterNot { stroke -> stroke.points.any { it.y in top..bottom } }
        if (after.size != before.size) {
            pushUndo(before)
            updateDocument(doc.copy(strokes = after))
        }
    }

    private fun pushUndo(strokes: List<SketchStrokeData>) {
        undoStack.addLast(strokes)
        if (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        _canUndo.value = true
    }

    private fun updateDocument(doc: SketchDocumentData) {
        val stamped = doc.copy(modifiedAtEpochMs = System.currentTimeMillis())
        _document.value = stamped
        scheduleSave(stamped)
    }

    private fun scheduleSave(doc: SketchDocumentData) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            repository.saveDocument(doc)
        }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        saveJob?.cancel()
        _document.value?.let { doc ->
            viewModelScope.launch { repository.saveDocument(doc) }
        }
    }

    private fun strokeHits(stroke: SketchStrokeData, point: Offset, radiusSq: Float): Boolean =
        stroke.points.any { p ->
            val dx = p.x - point.x
            val dy = p.y - point.y
            dx * dx + dy * dy <= radiusSq
        }
}

class SketchScreen(
    sealedActivity: SealedLightActivity,
    private val documentId: String,
) : LightScreen<Unit, SketchScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<SketchScreenViewModel>
        get() = SketchScreenViewModel::class.java

    override fun createViewModel(): SketchScreenViewModel =
        SketchScreenViewModel(SketchRepository(lightContext.filesDir), documentId)

    @Composable
    override fun Content() {
        val document by viewModel.document.collectAsState()
        val tool by viewModel.tool.collectAsState()
        val canUndo by viewModel.canUndo.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val viewState = rememberSketchViewState()

        var trayExpanded by remember { mutableStateOf(false) }
        var currentPage by remember { mutableStateOf(1) }
        var renaming by remember { mutableStateOf(false) }
        var renameSessionId by remember { mutableStateOf(0) }
        var pendingClearPage by remember { mutableStateOf(false) }

        LaunchedEffect(viewState.pan, viewState.zoom) {
            currentPage = viewState.currentPageIndex() + 1
        }

        LightTheme(colors = themeColors) {
            val doc = document

            when {
                renaming && doc != null -> {
                    val nameFieldState = rememberTextFieldState(doc.name)
                    val keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) }
                    LightTextInputEditor(
                        title = "Rename Sketch",
                        state = nameFieldState,
                        onSubmit = {
                            viewModel.rename(it.toString())
                            renaming = false
                        },
                        onBack = { renaming = false },
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        singleLine = true,
                        editorKey = renameSessionId,
                    )
                }

                pendingClearPage -> {
                    ConfirmDialog(
                        message = "Clear page $currentPage? Everything drawn on this page will be removed.",
                        confirmLabel = "Clear Page",
                        onConfirm = {
                            viewModel.clearPage(currentPage - 1, viewState.pageHeight)
                            pendingClearPage = false
                        },
                        onCancel = { pendingClearPage = false },
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LightTopBar(
                                leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                                center = doc?.let { LightTopBarCenter.Text(it.name) },
                                rightButton = LightBarButton.Text(
                                    text = if (doc?.paginationEnabled == true) "Exit" else "Pages",
                                    contentDescription = if (doc?.paginationEnabled == true) "Turn off pages" else "Turn on pages",
                                    onClick = { viewModel.togglePagination() },
                                ),
                            )

                            if (doc != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.5f)
                                        .height(3f.gridUnitsAsDp())
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {},
                                            onLongClick = {
                                                renameSessionId += 1
                                                renaming = true
                                            },
                                        ),
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (doc != null) {
                                SketchCanvas(
                                    state = viewState,
                                    strokes = doc.strokes,
                                    tool = tool,
                                    paginationEnabled = doc.paginationEnabled,
                                    onStrokeCommitted = { points, width -> viewModel.commitStroke(points, width) },
                                    onEraseGestureBegin = { viewModel.beginEraseGesture() },
                                    onErase = { point, radius -> viewModel.eraseAt(point, radius) },
                                    onInteractionStart = { trayExpanded = false },
                                )
                            }

                            if (doc?.paginationEnabled == true) {
                                PageNavPill(
                                    pageNumber = currentPage,
                                    onPrevious = { viewState.goToPage(currentPage - 2) },
                                    onNext = { viewState.goToPage(currentPage) },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 1f.gridUnitsAsDp()),
                                )
                            }

                            PenTrayOverlay(
                                expanded = trayExpanded,
                                tool = tool,
                                canUndo = canUndo,
                                onToggleExpanded = { trayExpanded = !trayExpanded },
                                onSelectTool = { viewModel.selectTool(it) },
                                onUndo = { viewModel.undo() },
                                onEraserLongPress = {
                                    if (doc?.paginationEnabled == true) pendingClearPage = true
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(1f.gridUnitsAsDp()),
                            )
                        }
                    }
                }
            }
        }
    }
}
