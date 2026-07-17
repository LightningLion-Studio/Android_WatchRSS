package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

private val InternetAvailableGreen = Color(0xFF41C96B)
private val InternetBluetoothAmber = Color(0xFFFFA726)

@Composable
fun InternetAvailabilityOverlay(
    status: InternetAvailabilityStatus,
    modifier: Modifier = Modifier,
    message: String = internetAvailabilityGuidanceMessage(status),
    actionText: String = "重试",
    actionEnabled: Boolean = status != InternetAvailabilityStatus.Checking,
    onAction: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val textWidth = (maxWidth * 0.92f).coerceAtMost(208.dp)
            val guidanceTextWidth = (maxWidth * 0.98f).coerceAtMost(224.dp)
            val buttonReservedHeight = 56.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = buttonReservedHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "互联网",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = guidanceTextWidth)
                )

                Spacer(modifier = Modifier.height(9.dp))

                InternetAvailabilityStatusBar(
                    status = status,
                    modifier = Modifier.widthIn(max = textWidth)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = internetAvailabilityStatusMessage(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = textWidth)
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                InternetAvailabilityActionButton(
                    text = actionText,
                    enabled = actionEnabled,
                    onClick = onAction
                )
            }
        }
    }
}

@Composable
private fun InternetAvailabilityStatusBar(
    status: InternetAvailabilityStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "互联网可用状态",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.size(12.dp))
        InternetAvailabilityStatusIndicator(status = status)
    }
}

@Composable
private fun InternetAvailabilityStatusIndicator(
    status: InternetAvailabilityStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            InternetAvailabilityStatus.Checking -> {
                WatchCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            InternetAvailabilityStatus.Unavailable -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(watchColorResource(R.color.danger_red))
                )
            }

            InternetAvailabilityStatus.Bluetooth -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(InternetBluetoothAmber)
                )
            }

            InternetAvailabilityStatus.Available -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(InternetAvailableGreen)
                )
            }
        }
    }
}

@Composable
private fun InternetAvailabilityActionButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val radius = watchDimensionResource(R.dimen.hey_button_default_radius)
    val height = watchDimensionResource(R.dimen.hey_button_height)
    val horizontalPadding = watchDimensionResource(R.dimen.hey_button_mergin_horizontal)
    val verticalPadding = watchDimensionResource(R.dimen.hey_button_padding_vertical)

    Box(
        modifier = modifier
            .widthIn(min = 112.dp)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.primary)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun internetAvailabilityGuidanceMessage(status: InternetAvailabilityStatus): String {
    return when (status) {
        InternetAvailabilityStatus.Checking -> "正在检测网络连接"
        InternetAvailabilityStatus.Unavailable -> "请连接 WiFi 或移动网络"
        InternetAvailabilityStatus.Bluetooth -> "当前使用蓝牙网络，网速较慢，建议连接 WiFi 或移动网络"
        InternetAvailabilityStatus.Available -> "网络连接可用"
    }
}

internal fun internetAvailabilityStatusMessage(status: InternetAvailabilityStatus): String {
    return when (status) {
        InternetAvailabilityStatus.Checking -> "正在检测互联网状态..."
        InternetAvailabilityStatus.Unavailable -> "未检测到可用互联网"
        InternetAvailabilityStatus.Bluetooth -> "已连接蓝牙网络，可能影响加载速度"
        InternetAvailabilityStatus.Available -> "已检测到互联网，可以继续"
    }
}
