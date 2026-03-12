package com.lightningstudio.watchrss.ui.screen

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.widget.ProgressRingView

@Composable
fun WebViewScreen(
    onWebViewReady: (WebView) -> Unit,
    onProgressRingReady: (ProgressRingView) -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).also(onWebViewReady)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(start = safePadding, end = safePadding)
        )
        AndroidView(
            factory = { context ->
                ProgressRingView(context).also(onProgressRingReady)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
