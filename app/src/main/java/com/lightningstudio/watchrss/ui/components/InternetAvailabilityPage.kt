package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.ui.theme.watchColorResource

private val InternetAvailabilityPageGreen = Color(0xFF41C96B)
private val InternetAvailabilityPageBluetoothAmber = Color(0xFFFFA726)

@Composable
fun InternetAvailabilityPage(
    status: InternetAvailabilityStatus,
    guidanceMessage: String,
    statusMessage: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = status != InternetAvailabilityStatus.Checking,
    actionModifier: Modifier = Modifier,
    statusIndicatorModifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val textWidth = (maxWidth * 0.92f).coerceAtMost(208.dp)
        val guidanceTextWidth = (maxWidth * 0.98f).coerceAtMost(224.dp)
        val hasLongGuidance = status == InternetAvailabilityStatus.Bluetooth
        val topSpacing = if (hasLongGuidance) 10.dp else 18.dp
        val titleSpacing = if (hasLongGuidance) 8.dp else 10.dp
        val statusBarSpacing = if (hasLongGuidance) 12.dp else 18.dp
        val statusMessageSpacing = if (hasLongGuidance) 8.dp else 10.dp
        val buttonReservedHeight = 72.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = buttonReservedHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(topSpacing))

            Text(
                text = "互联网",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(titleSpacing))

            Text(
                text = guidanceMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = guidanceTextWidth)
            )

            Spacer(modifier = Modifier.height(statusBarSpacing))

            InternetAvailabilityPageStatusBar(
                status = status,
                statusIndicatorModifier = statusIndicatorModifier,
                modifier = Modifier.widthIn(max = textWidth)
            )

            Spacer(modifier = Modifier.height(statusMessageSpacing))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = textWidth)
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        InternetAvailabilityPageActionButton(
            text = actionText,
            enabled = actionEnabled,
            onClick = onAction,
            modifier = actionModifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun InternetAvailabilityPageStatusBar(
    status: InternetAvailabilityStatus,
    modifier: Modifier = Modifier,
    statusIndicatorModifier: Modifier = Modifier
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
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.size(12.dp))
        InternetAvailabilityPageStatusIndicator(
            status = status,
            modifier = statusIndicatorModifier
        )
    }
}

@Composable
private fun InternetAvailabilityPageStatusIndicator(
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
                        .background(InternetAvailabilityPageBluetoothAmber)
                )
            }

            InternetAvailabilityStatus.Available -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(InternetAvailabilityPageGreen)
                )
            }
        }
    }
}

@Composable
private fun InternetAvailabilityPageActionButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold
        )
    }
}
