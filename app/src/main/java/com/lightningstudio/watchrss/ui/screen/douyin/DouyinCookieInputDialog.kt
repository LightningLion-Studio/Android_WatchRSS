package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchButton

@Composable
fun DouyinCookieInputDialog(
    value: String,
    isSaving: Boolean,
    confirmEnabled: Boolean,
    onValueChange: (String) -> Unit,
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
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "手动输入 Cookie",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "仅 Debug 包可见，直接粘贴请求头里的 Cookie 值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                label = {
                    Text(
                        text = "Cookie",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = {
                    Text(
                        text = "sessionid=...; ttwid=...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WatchButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = "取消")
                }
                WatchButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = if (isSaving) "保存中" else "保存")
                }
            }
        }
    }
}
