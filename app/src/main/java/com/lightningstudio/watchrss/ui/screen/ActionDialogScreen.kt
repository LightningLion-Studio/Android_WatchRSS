package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle

data class ActionItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun ActionDialogScreen(items: List<ActionItem>) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val horizontalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val bottomPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val dividerSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val endSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance_6_0
    val buttonWidth = dimensionResource(R.dimen.watch_action_button_width)
    val buttonHeight = dimensionResource(R.dimen.watch_action_button_height)
    val buttonRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val buttonColor = colorResource(R.color.watch_pill_background)

    WatchSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = safePadding,
                    bottom = bottomPadding
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEachIndexed { index, item ->
                ActionButton(
                    label = item.label,
                    enabled = item.enabled,
                    onClick = item.onClick,
                    width = buttonWidth,
                    height = buttonHeight,
                    radius = buttonRadius,
                    backgroundColor = buttonColor
                )
                if (index != items.lastIndex) {
                    Spacer(modifier = Modifier.height(dividerSpacing))
                }
            }
            Spacer(modifier = Modifier.height(endSpacing))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    backgroundColor: androidx.compose.ui.graphics.Color
) {
    val shape = RoundedCornerShape(radius)
    val background = backgroundColor
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f)
    val dangerColor = colorResource(R.color.danger_red).copy(alpha = if (enabled) 1f else 0.6f)
    val textColor = if (label.contains("删除")) dangerColor else baseColor
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = ActionButtonTextStyle,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}
