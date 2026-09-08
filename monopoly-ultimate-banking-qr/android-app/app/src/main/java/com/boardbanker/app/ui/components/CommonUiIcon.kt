package com.boardbanker.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonIconRegistry
import com.boardbanker.app.player.CommonUiIcon

val CommonUiIconDefaultSize: Dp = 22.dp
val CommonUiIconLabelSpacing: Dp = 8.dp

@Composable
fun CommonUiIconImage(
    icon: CommonUiIcon,
    modifier: Modifier = Modifier,
    size: Dp = CommonUiIconDefaultSize,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = painterResource(CommonIconRegistry.iconResId(icon)),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .testTag("common_ui_icon_${icon.name.lowercase()}"),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
    )
}

@Composable
fun IconLabelRow(
    icon: CommonUiIcon?,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = CommonUiIconDefaultSize,
    spacing: Dp = CommonUiIconLabelSpacing,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CommonUiIconImage(
                icon = icon,
                size = iconSize,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(spacing))
        }
        Text(
            text = label,
            style = textStyle,
            textAlign = textAlign,
        )
    }
}

fun cancelIconForLabel(label: String): CommonUiIcon {
    val stripped = label
        .removePrefix(BankingActionLabels.CONFIRM_SYMBOL)
        .removePrefix(BankingActionLabels.MIDDLE_SYMBOL)
        .removePrefix(BankingActionLabels.CANCEL_SYMBOL)
        .trim()
    return if (stripped.equals("BACK", ignoreCase = true)) {
        CommonUiIcon.BACK
    } else {
        CommonUiIcon.CANCEL
    }
}
