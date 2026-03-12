package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lightningstudio.watchrss.ui.theme.WatchBackground
import com.lightningstudio.watchrss.ui.theme.WatchBackgroundDeep
import com.lightningstudio.watchrss.ui.theme.WatchSurface
import com.lightningstudio.watchrss.BuildConfig

@Composable
fun WatchSurface(
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (pureBlack) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val outerGradient = Brush.linearGradient(
            colors = listOf(WatchBackground, WatchBackgroundDeep)
        )
        val innerGradient = Brush.radialGradient(
            colors = listOf(WatchSurface, Color.Black)
        )

        if (BuildConfig.DEBUG) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(outerGradient),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(innerGradient)
                ) {
                    content()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
