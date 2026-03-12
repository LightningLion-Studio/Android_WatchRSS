package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R
import androidx.compose.foundation.shape.CircleShape

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxSize = minOf(maxWidth, maxHeight)
            val containerSize = minOf(maxSize, 466.dp)
            val scale = (containerSize.value / 466f).coerceAtMost(1f)
            val scaleDp: (Dp) -> Dp = { value -> (value.value * scale).dp }

            Column(
                modifier = Modifier
                    .size(containerSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = scaleDp(96.dp), bottom = scaleDp(30.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(scaleDp(32.dp))
            ) {
                DialogContent(
                    title = title,
                    message = message,
                    scale = scale,
                    scaleDp = scaleDp
                )
                DialogButtons(
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                    scaleDp = scaleDp
                )
            }
        }
    }
}

@Composable
private fun DialogContent(
    title: String,
    message: String,
    scale: Float,
    scaleDp: (Dp) -> Dp
) {
    val fontFamily = FontFamily(Font(R.font.watch_sans))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = scaleDp(40.dp))
            .height(scaleDp(204.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scaleDp(8.dp), Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (34f * scale).sp,
            lineHeight = (46f * scale).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = message,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (34f * scale).sp,
            lineHeight = (46f * scale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DialogButtons(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    scaleDp: (Dp) -> Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(scaleDp(32.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            background = MaterialTheme.colorScheme.surfaceVariant,
            iconRes = R.drawable.ic_action_cancel,
            onClick = onCancel,
            scaleDp = scaleDp
        )
        RoundIconButton(
            background = colorResource(R.color.danger_red),
            iconRes = R.drawable.ic_delete_confirm,
            onClick = onConfirm,
            scaleDp = scaleDp
        )
    }
}

@Composable
private fun RoundIconButton(
    background: Color,
    iconRes: Int,
    onClick: () -> Unit,
    scaleDp: (Dp) -> Dp
) {
    val size = scaleDp(104.dp)
    val iconSize = scaleDp(48.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}
