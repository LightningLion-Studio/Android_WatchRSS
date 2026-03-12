package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.screen.rss.DetailActionButton
import com.lightningstudio.watchrss.ui.screen.rss.DetailTextBlock
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.screen.rss.adjustedTextSizeSp
import com.lightningstudio.watchrss.ui.theme.WatchBackgroundDeep
import com.lightningstudio.watchrss.ui.theme.WatchReadingBackgroundLight
import com.lightningstudio.watchrss.ui.theme.WatchReadingTextLight
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

@Composable
fun InfoScreen(
    title: String,
    content: String,
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    onOpenProjectInfo: () -> Unit,
    onBeianClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val pagePadding = dimensionResource(R.dimen.detail_page_horizontal_padding)
    val blockSpacing = dimensionResource(R.dimen.detail_block_spacing)
    val titlePadding = dimensionResource(R.dimen.detail_title_safe_padding)
    val backgroundColor = androidx.compose.ui.graphics.Color.Black
    val textColor = if (readingThemeDark) WatchTextPrimary else WatchReadingTextLight
    val bodyFontSize = remember(readingFontSizeSp, density, context) {
        adjustedTextSizeSp(
            context = context,
            density = density,
            baseDimenRes = R.dimen.detail_body_text_size,
            currentFontSizeSp = readingFontSizeSp
        )
    }
    val textBlocks = remember(content) { buildInfoBlocks(content) }
    val showProjectInfoAction = title == "开源许可与清单"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(horizontal = pagePadding)
    ) {
        item(key = "topSpacer") {
            Spacer(modifier = Modifier.height(safePadding))
        }
        item(key = "titleGap") {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.hey_distance_4dp)))
        }
        item(key = "title") {
            DetailTitle(
                title = title,
                titlePadding = titlePadding,
                textColor = textColor
            )
        }
        if (showProjectInfoAction) {
            item(key = "projectInfoAction") {
                Spacer(modifier = Modifier.height(blockSpacing))
                DetailActionButton(
                    text = "查看项目清单",
                    fontSize = bodyFontSize,
                    onClick = onOpenProjectInfo
                )
            }
        }
        item(key = "contentGap") {
            Spacer(modifier = Modifier.height(blockSpacing))
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
        item(key = "beian") {
            Spacer(modifier = Modifier.height(blockSpacing))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "浙ICP备2024111886号-5A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onBeianClick)
                )
            }
        }
        item(key = "bottomSpacer") {
            Spacer(modifier = Modifier.height(safePadding))
        }
    }
}

private fun buildInfoBlocks(content: String): List<ContentBlock.Text> {
    return content
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { paragraph ->
            paragraph
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    ContentBlock.Text(
                        text = line,
                        style = when {
                            line.startsWith("https://") || line.startsWith("http://") -> ContentTextStyle.CODE
                            line.startsWith("•") -> ContentTextStyle.QUOTE
                            else -> ContentTextStyle.BODY
                        }
                    )
                }
        }
}
