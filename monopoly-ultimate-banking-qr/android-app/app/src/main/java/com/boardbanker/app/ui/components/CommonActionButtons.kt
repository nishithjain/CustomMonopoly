package com.boardbanker.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonUiIcon

@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    testTag: String = "top_bar_back",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .testTag(testTag),
    ) {
        CommonUiIconImage(
            icon = CommonUiIcon.BACK,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun TopBarIconTitle(
    icon: CommonUiIcon,
    title: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommonUiIconImage(icon = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(CommonUiIconLabelSpacing))
        Text(text = title, style = textStyle)
    }
}

@Composable
fun BackActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "BACK",
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        IconLabelRow(
            icon = CommonUiIcon.BACK,
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun CommonOutlinedActionButton(
    icon: CommonUiIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        IconLabelRow(
            icon = icon,
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun CommonFilledActionButton(
    icon: CommonUiIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        IconLabelRow(
            icon = icon,
            label = label,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}
