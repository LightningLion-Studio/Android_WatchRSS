package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Bili-only latest-message feedback. No system Toast queue and no touch interceptor. */
@Composable
internal fun BiliFeedbackHost(
    message: String?,
    onMessageConsumed: () -> Unit,
    content: @Composable () -> Unit
) {
    var visibleMessage by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableStateOf(0L) }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            visibleMessage = message
            generation++
            onMessageConsumed()
        }
    }
    LaunchedEffect(generation) {
        if (visibleMessage != null) {
            delay(2_000)
            visibleMessage = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        visibleMessage?.let { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .widthIn(max = 156.dp)
                    .background(Color(0xFF303030), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
