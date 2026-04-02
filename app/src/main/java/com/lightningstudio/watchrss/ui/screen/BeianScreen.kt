package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.QrCodePanel
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator

@Composable
fun BeianScreen() {
    val density = LocalDensity.current
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val qrSize = with(density) { 120.dp.toPx().toInt() }

    val qrBitmap = remember {
        QrCodeGenerator.create("https://beian.miit.gov.cn/", qrSize)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(safePadding)
            .semantics { contentDescription = "备案信息页面。使用手机扫描二维码访问工业和信息化部备案查询网站。" },
        contentAlignment = Alignment.Center
    ) {
        QrCodePanel(
            qrBitmap = qrBitmap,
            qrSizeDp = 120.dp,
            qrContentDescription = "备案查询二维码，扫描后可在手机浏览器中打开备案查询页面",
            title = "工信部备案",
            subtitle = "beian.miit.gov.cn",
            titleContentDescription = "标题：工信部备案",
            subtitleContentDescription = "备案查询网址：beian.miit.gov.cn",
        )
    }
}
