package com.lightningstudio.watchrss.ui.screen.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PageBody
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PageTitle
import com.lightningstudio.watchrss.ui.screen.phoneconnection.RoundPage
import com.lightningstudio.watchrss.ui.testing.CloudSyncTestTags
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource

/** 233dp 圆屏上按钮收窄到 168dp，首屏任意说明长度下圆角都落在圆内。 */
private val ActionMaxWidth = 168.dp

@Composable
fun CloudSyncScreen(
    state: CloudSyncUiState,
    onAction: (CloudSyncAction) -> Unit
) {
    RoundPage(modifier = Modifier.testTag(CloudSyncTestTags.ROOT)) {
        PageTitle("云同步")
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(toneColor(state.tone))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(CloudSyncTestTags.STATUS)
            )
        }
        Spacer(Modifier.height(4.dp))
        PageBody(state.detail)
        state.button?.let { button ->
            Spacer(Modifier.height(14.dp))
            CloudSyncPill(button) { onAction(button.action) }
        }
        state.secondaryButton?.let { button ->
            Spacer(Modifier.height(6.dp))
            Text(button.label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(enabled = button.enabled, role = Role.Button) { onAction(button.action) }
                    .padding(12.dp))
        }
        if (state.lastSynced != null || state.space != null) {
            Spacer(Modifier.height(14.dp))
            CloudSyncFacts(state)
        }
    }
}

@Composable
private fun CloudSyncFacts(state: CloudSyncUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = 150.dp).fillMaxWidth()
    ) {
        state.space?.let { space ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(watchColorResource(R.color.watch_pill_background))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(space.fraction.coerceAtLeast(0.02f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Fact(space.label, CloudSyncTestTags.SPACE)
        }
        state.lastSynced?.let { Fact(it, CloudSyncTestTags.LAST_SYNCED) }
    }
}

@Composable
private fun Fact(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

/** 唯一主操作；进行中时置灰且不可点，重复点击不会排出第二次同步。 */
@Composable
internal fun CloudSyncPill(button: CloudSyncButton, onClick: () -> Unit) {
    val container = if (button.enabled) MaterialTheme.colorScheme.primary else watchColorResource(R.color.watch_pill_background)
    val content = if (button.enabled) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .widthIn(max = ActionMaxWidth)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(WatchDimens.hey_button_default_radius))
            .background(container)
            .testTag(CloudSyncTestTags.ACTION)
            .clickable(enabled = button.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = content,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun toneColor(tone: CloudSyncTone): Color = when (tone) {
    CloudSyncTone.OK, CloudSyncTone.BUSY -> MaterialTheme.colorScheme.primary
    CloudSyncTone.ATTENTION -> Color(0xFFFFC266)
    CloudSyncTone.ERROR -> Color(0xFFFF7777)
    CloudSyncTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
}
