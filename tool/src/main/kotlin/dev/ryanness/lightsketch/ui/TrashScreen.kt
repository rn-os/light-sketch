package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.ryanness.lightsketch.data.SketchDocumentMeta
import dev.ryanness.lightsketch.data.SketchRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrashScreenViewModel(private val repository: SketchRepository) : LightViewModel<Unit>() {

    private val _trashedDocuments = MutableStateFlow<List<SketchDocumentMeta>>(emptyList())
    val trashedDocuments: StateFlow<List<SketchDocumentMeta>> = _trashedDocuments.asStateFlow()

    private val _autoEmptyEnabled = MutableStateFlow(true)
    val autoEmptyEnabled: StateFlow<Boolean> = _autoEmptyEnabled.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _trashedDocuments.value = repository.listTrash()
            _autoEmptyEnabled.value = repository.isAutoEmptyTrashEnabled()
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
            refresh()
        }
    }

    fun deleteForever(id: String) {
        viewModelScope.launch {
            repository.permanentlyDelete(id)
            refresh()
        }
    }

    fun setAutoEmpty(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoEmptyTrashEnabled(enabled)
            _autoEmptyEnabled.value = enabled
        }
    }
}

class TrashScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, TrashScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<TrashScreenViewModel>
        get() = TrashScreenViewModel::class.java

    override fun createViewModel(): TrashScreenViewModel =
        TrashScreenViewModel(SketchRepository(lightContext.filesDir))

    @Composable
    override fun Content() {
        val trashed by viewModel.trashedDocuments.collectAsState()
        val autoEmpty by viewModel.autoEmptyEnabled.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingDeleteId by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            if (pendingDeleteId != null) {
                ConfirmDialog(
                    message = "Delete this sketch forever? This can't be undone.",
                    confirmLabel = "Delete",
                    onConfirm = {
                        viewModel.deleteForever(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                    onCancel = { pendingDeleteId = null },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                        center = LightTopBarCenter.Text("Trash"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { viewModel.setAutoEmpty(!autoEmpty) })
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Empty automatically after 30 days",
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.weight(1f),
                        )
                        LightIcon(icon = if (autoEmpty) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF)
                    }

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        if (trashed.isEmpty()) {
                            LightText(
                                text = "Trash is empty.",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        } else {
                            trashed.forEach { doc ->
                                TrashRow(
                                    doc = doc,
                                    onRestore = { viewModel.restore(doc.id) },
                                    onDeleteForever = { pendingDeleteId = doc.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashRow(doc: SketchDocumentMeta, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    val deletedAt = doc.deletedAtEpochMs?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = doc.name, variant = LightTextVariant.Subheading)
            if (deletedAt != null) {
                LightText(
                    text = "Deleted $deletedAt",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
        }

        Row {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .lightClickable(onClick = onRestore),
            ) {
                LightIcon(icon = LightIcons.LOOP, contentDescription = "Restore ${doc.name}")
            }
            Box(modifier = Modifier.lightClickable(onClick = onDeleteForever)) {
                LightIcon(icon = LightIcons.TRASH, contentDescription = "Delete ${doc.name} forever")
            }
        }
    }
}
