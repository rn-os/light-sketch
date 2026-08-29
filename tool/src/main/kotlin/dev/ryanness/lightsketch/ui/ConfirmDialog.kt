package dev.ryanness.lightsketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** A blocking two-choice confirmation, for destructive actions the SDK has no built-in dialog for. */
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
) {
    val colors = LightThemeTokens.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 2f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(
            text = message,
            variant = LightTextVariant.Heading,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LightText(
                text = cancelLabel,
                variant = LightTextVariant.Button,
                lighten = true,
                modifier = Modifier
                    .lightClickable(onClick = onCancel)
                    .padding(1f.gridUnitsAsDp()),
            )
            LightText(
                text = confirmLabel,
                variant = LightTextVariant.Button,
                modifier = Modifier
                    .lightClickable(onClick = onConfirm)
                    .padding(1f.gridUnitsAsDp()),
            )
        }
    }
}
