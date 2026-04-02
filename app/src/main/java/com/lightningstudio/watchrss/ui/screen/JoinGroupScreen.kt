package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.QrCodePanel
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun JoinGroupScreen(
    qrCodeUrl: String,
    groupNumber: String
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val topPadding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val scrollState = rememberScrollState()

    var qrCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(qrCodeUrl) {
        withContext(Dispatchers.Default) {
            qrCodeBitmap = QrCodeGenerator.create(qrCodeUrl, 200)
        }
    }

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = safePadding,
                    top = topPadding,
                    end = safePadding,
                    bottom = safePadding
                )
                .semantics { contentDescription = "加群页面。扫描二维码加入QQ群，群号：$groupNumber。" },
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrCodePanel(
                qrBitmap = qrCodeBitmap,
                qrSizeDp = 150.dp,
                qrContentDescription = "QQ群二维码，扫描后可在手机上加入群聊",
                title = "加入 QQ 群",
                subtitle = "群号 $groupNumber",
                titleContentDescription = "标题：加入 QQ 群",
                subtitleContentDescription = "QQ群号：$groupNumber",
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
