package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun PageNavPill(pageNumber: Int, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    val enabledPrevious = pageNumber > 1

    Row(
        modifier = modifier
            .background(colors.background)
            .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.4f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .alpha(if (enabledPrevious) 1f else 0.3f)
                .lightClickable(enabled = enabledPrevious, onClick = onPrevious)
                .padding(0.5f.gridUnitsAsDp()),
        ) {
            LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                contentDescription = "Previous page",
                modifier = Modifier.rotate(180f),
            )
        }

        LightText(
            text = "Page $pageNumber",
            variant = LightTextVariant.Detail,
            modifier = Modifier.padding(horizontal = 0.5f.gridUnitsAsDp()),
        )

        Box(
            modifier = Modifier
                .lightClickable(onClick = onNext)
                .padding(0.5f.gridUnitsAsDp()),
        ) {
            LightIcon(icon = LightIcons.ARROW_RIGHT, contentDescription = "Next page")
        }
    }
}
