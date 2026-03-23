package com.lightningstudio.watchrss.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource

@Composable
fun WatchReadingThemeToggle(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val outerSize = WatchDimens.hey_distance_20dp
    val innerInset = WatchDimens.hey_distance_2dp
    val innerSize = outerSize - (innerInset * 2f)
    val orange = watchColorResource(R.color.brand_orange)
    val fillColor = if (isDark) Color.Black else Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = if (isPressed) lerp(orange, Color.White, 0.12f) else orange

    Box(
        modifier = modifier
            .size(outerSize)
            .semantics {
                contentDescription = "阅读主题"
                stateDescription = if (isDark) "深色" else "浅色"
            }
            .clip(CircleShape)
            .background(borderColor)
            .toggleable(
                value = isDark,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}

@Composable
fun WatchStepperValue(
    text: String,
    width: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.width(width),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WatchRoundIconButtonIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val size = WatchDimens.hey_distance_20dp
    val iconSize = size * 0.6f
    val baseColor = watchColorResource(R.color.watch_pill_background)
    val idleColor = lerp(baseColor, Color.White, 0.12f)
    val pressedColor = lerp(baseColor, Color.Black, 0.18f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed && enabled) pressedColor else idleColor

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(iconSize)
        )
    }
}
