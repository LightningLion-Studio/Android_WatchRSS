package com.lightningstudio.watchrss.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

@Composable
fun WatchSettingsPillRow(
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WatchDimens.hey_button_default_radius),
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    endPaddingMultiplier: Float = 1f,
    content: @Composable RowScope.() -> Unit = {}
) {
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillHeight = WatchDimens.hey_multiple_item_height
    val startPadding = WatchDimens.hey_distance_10dp
    val endPadding = WatchDimens.hey_distance_10dp * endPaddingMultiplier
    val verticalPadding = WatchDimens.hey_distance_8dp
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_lefticon_height_width)
    val iconSpacing = WatchDimens.hey_distance_8dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = pillHeight)
            .clip(shape)
            .background(pillColor)
            .then(testTag?.let(Modifier::testTag) ?: Modifier)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(iconSpacing))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}
