package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.screen.rss.DetailTextBlock
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle

@Composable
fun PrivacyPolicyConsentScreen(
    policyContent: String,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    val density = LocalDensity.current
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val pagePadding = watchDimensionResource(R.dimen.detail_page_horizontal_padding)
    val blockSpacing = watchDimensionResource(R.dimen.detail_block_spacing)
    val titlePadding = watchDimensionResource(R.dimen.detail_title_safe_padding)
    val backgroundColor = Color.Black
    val textColor = Color.White
    val bodyFontSize = with(density) { 12.dp.toSp() }
    val textBlocks = remember(policyContent) { buildInfoBlocks(policyContent) }
    val contentGap = blockSpacing + 4.dp
    val listState = rememberLazyListState()

    InstallDigitalCrownLazyListHandler(listState)

    Box(modifier = Modifier.fillMaxSize()) {
        ReaderBackgroundSurface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = pagePadding)
            ) {
                item(key = "topSpacer") {
                    Spacer(modifier = Modifier.height(safePadding))
                }
                item(key = "titleGap") {
                    Spacer(modifier = Modifier.height(watchDimensionResource(R.dimen.hey_distance_4dp)))
                }
                item(key = "title") {
                    DetailTitle(
                        title = "隐私政策更新",
                        titlePadding = titlePadding,
                        textColor = textColor
                    )
                }
                item(key = "notice") {
                    Spacer(modifier = Modifier.height(contentGap))
                    Text(
                        text = "我们更新了隐私政策，请阅读并确认是否同意。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "contentGap") {
                    Spacer(modifier = Modifier.height(contentGap))
                }
                if (textBlocks.isEmpty()) {
                    item(key = "emptyContent") {
                        DetailTextBlock(
                            text = "暂无正文",
                            style = ContentTextStyle.BODY,
                            textColor = textColor,
                            fontSizeSp = bodyFontSize,
                            topPadding = 0.dp,
                            isScrolling = false
                        )
                    }
                } else {
                    itemsIndexed(
                        items = textBlocks,
                        key = { index, block -> "txt:${block.style}:${block.text.hashCode()}:$index" }
                    ) { index, block ->
                        DetailTextBlock(
                            text = block.text,
                            style = block.style,
                            textColor = textColor,
                            fontSizeSp = bodyFontSize,
                            topPadding = if (index == 0) 0.dp else blockSpacing,
                            isScrolling = false
                        )
                    }
                }
                item(key = "bottomSpacer") {
                    Spacer(modifier = Modifier.height(safePadding + 64.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(backgroundColor.copy(alpha = 0.92f))
                .padding(horizontal = pagePadding, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConsentButton(
                    text = "不同意",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onDisagree
                )
                Spacer(modifier = Modifier.width(8.dp))
                ConsentButton(
                    text = "同意",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onAgree
                )
            }
        }
    }
}

@Composable
private fun ConsentButton(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
