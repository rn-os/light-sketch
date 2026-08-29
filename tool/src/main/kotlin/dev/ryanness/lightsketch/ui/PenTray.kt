package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

private val DOT_SIZES = mapOf(
    PenTool.THIN to 6.dp,
    PenTool.MEDIUM to 11.dp,
    PenTool.THICK to 16.dp,
)

@Composable
fun PenTrayOverlay(
    expanded: Boolean,
    tool: PenTool,
    canUndo: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectTool: (PenTool) -> Unit,
    onUndo: () -> Unit,
    onEraserLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .background(colors.background)
                    .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PenTool.entries.filter { it != PenTool.ERASER }.forEach { entry ->
                    PenSizeButton(
                        entry = entry,
                        selected = tool == entry,
                        onClick = { onSelectTool(entry) },
                    )
                    Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
                }

                ToolIconButton(
                    icon = LightIcons.DELETE,
                    selected = tool == PenTool.ERASER,
                    contentDescription = "Eraser. Hold to clear this page.",
                    onClick = { onSelectTool(PenTool.ERASER) },
                    onLongClick = onEraserLongPress,
                )

                Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))

                Box(modifier = Modifier.alpha(if (canUndo) 1f else 0.3f)) {
                    ToolIconButton(
                        icon = LightIcons.LOOP,
                        selected = false,
                        contentDescription = "Undo",
                        onClick = { if (canUndo) onUndo() },
                    )
                }

                Spacer(modifier = Modifier.width(0.5f.gridUnitsAsDp()))
            }
        }

        Box(
            modifier = Modifier
                .padding(1f.gridUnitsAsDp())
                .clip(CircleShape)
                .background(colors.background)
                .lightClickable(onClick = onToggleExpanded)
                .padding(0.75f.gridUnitsAsDp()),
        ) {
            LightIcon(
                icon = if (expanded) LightIcons.CLOSE else LightIcons.PENCIL,
                contentDescription = if (expanded) "Close tools" else "Show tools",
            )
        }
    }
}

@Composable
private fun PenSizeButton(entry: PenTool, selected: Boolean, onClick: () -> Unit) {
    val colors = LightThemeTokens.colors
    Column(
        modifier = Modifier
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(DOT_SIZES.getValue(entry))
                    .clip(CircleShape)
                    .background(colors.content),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(width = 16.dp, height = 2.dp)
                .background(if (selected) colors.content else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun ToolIconButton(
    icon: com.thelightphone.sdk.ui.LightIconConfiguration,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LightThemeTokens.colors
    val interactionButtonModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.lightClickable(onClick = onClick)
    }
    Column(
        modifier = interactionButtonModifier
            .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightIcon(icon = icon, contentDescription = contentDescription)
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(width = 16.dp, height = 2.dp)
                .background(if (selected) colors.content else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
