package dev.ryanness.lightsketch

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.ryanness.lightsketch.data.SketchDocumentMeta
import dev.ryanness.lightsketch.data.SketchRepository
import dev.ryanness.lightsketch.ui.SketchScreen
import dev.ryanness.lightsketch.ui.TrashScreen
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeScreenViewModel(private val repository: SketchRepository) : LightViewModel<Unit>() {

    private val _documents = MutableStateFlow<List<SketchDocumentMeta>>(emptyList())
    val documents: StateFlow<List<SketchDocumentMeta>> = _documents.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (repository.isAutoEmptyTrashEnabled()) {
                repository.purgeExpiredTrash()
            }
            _documents.value = repository.listDocuments()
        }
    }

    fun createDocument(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val doc = repository.createDocument(defaultDocumentName())
            onCreated(doc.id)
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
            refresh()
        }
    }

    fun rename(id: String, newName: String) {
        viewModelScope.launch {
            val doc = repository.loadDocument(id) ?: return@launch
            val trimmed = newName.trim()
            if (trimmed.isNotEmpty() && trimmed != doc.name) {
                repository.saveDocument(doc.copy(name = trimmed, modifiedAtEpochMs = System.currentTimeMillis()))
            }
            refresh()
        }
    }

    private fun defaultDocumentName(): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel =
        HomeScreenViewModel(SketchRepository(lightContext.filesDir))

    @Composable
    override fun Content() {
        val documents by viewModel.documents.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        var renamingDoc by remember { mutableStateOf<SketchDocumentMeta?>(null) }
        var renameSessionId by remember { mutableStateOf(0) }

        LightTheme(colors = themeColors) {
            val target = renamingDoc
            if (target != null) {
                val nameFieldState = rememberTextFieldState(target.name)
                val keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) }
                LightTextInputEditor(
                    title = "Rename Sketch",
                    state = nameFieldState,
                    onSubmit = {
                        viewModel.rename(target.id, it.toString())
                        renamingDoc = null
                    },
                    onBack = { renamingDoc = null },
                    keyboardOptionsFlow = keyboardOptionsFlow,
                    singleLine = true,
                    editorKey = renameSessionId,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "Sketch",
                            variant = LightTextVariant.Title,
                            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                        )

                        if (documents.isEmpty()) {
                            LightText(
                                text = "No sketches yet — tap + to start one.",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        } else {
                            documents.forEach { doc ->
                                DocumentRow(
                                    doc = doc,
                                    onOpen = {
                                        navigateTo({ activity -> SketchScreen(activity, doc.id) })
                                    },
                                    onDelete = { viewModel.deleteDocument(doc.id) },
                                    onRename = {
                                        renameSessionId += 1
                                        renamingDoc = doc
                                    },
                                )
                            }
                        }
                    }

                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.TRASH,
                                contentDescription = "Trash",
                                onClick = { navigateTo(::TrashScreen) { viewModel.refresh() } },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.ADD,
                                contentDescription = "New sketch",
                                onClick = {
                                    viewModel.createDocument { id ->
                                        navigateTo({ activity -> SketchScreen(activity, id) })
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    doc: SketchDocumentMeta,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (confirmingDelete) confirmingDelete = false else onOpen() },
                onLongClick = onRename,
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = doc.name, variant = LightTextVariant.Subheading)
        }

        if (confirmingDelete) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .lightClickable(onClick = { confirmingDelete = false; }),
            ) {
                LightText(text = "Cancel", variant = LightTextVariant.Detail, lighten = true)
            }
            Box(modifier = Modifier.lightClickable(onClick = onDelete)) {
                LightIcon(icon = LightIcons.TRASH)
            }
        } else {
            Box(modifier = Modifier.lightClickable(onClick = { confirmingDelete = true })) {
                LightIcon(icon = LightIcons.TRASH, contentDescription = "Delete ${doc.name}")
            }
        }
    }
}
