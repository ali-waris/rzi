package com.hc.rzi.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hc.rzi.ui.theme.Spacing

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val content: @Composable () -> Unit = {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            content = content,
        )
    }
}
