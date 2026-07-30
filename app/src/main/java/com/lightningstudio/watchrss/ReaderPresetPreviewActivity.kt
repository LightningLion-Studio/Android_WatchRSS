package com.lightningstudio.watchrss

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.data.reader.ReaderHyphenation
import com.lightningstudio.watchrss.data.reader.ReaderLineBreakMode
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.reader.ReaderTextAlignment
import com.lightningstudio.watchrss.data.reader.ReaderTextStyle
import com.lightningstudio.watchrss.data.reader.ReaderTypographyRole
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.readerTypefaceFor
import com.lightningstudio.watchrss.ui.screen.rss.CircleIconButton
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.rememberWatchTitleLineLimitsPx
import com.lightningstudio.watchrss.ui.util.formatWatchTitleForWidthLimitsWithMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

class ReaderPresetPreviewActivity : BaseWatchActivity() {
    private val app by lazy { application as WatchRssApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WatchRSSTheme {
                WatchReaderPresetLivePreview(
                    repository = app.container.readerPresetRepository,
                    application = app,
                    onExpired = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    companion object {
        @Volatile
        var isVisible: Boolean = false
            private set
    }
}

@Composable
private fun WatchReaderPresetLivePreview(
    repository: ReaderPresetRepository,
    application: WatchRssApplication,
    onExpired: () -> Unit
) {
    val session = application.readerPresetPreviewSession
    var backgroundPreview by remember { mutableStateOf(session.state.value) }
    var resourceTransferInProgress by remember {
        mutableStateOf(session.state.value?.resourceTransferInProgress == true)
    }
    LaunchedEffect(session) {
        session.state.collect { current ->
            if (current == null) {
                onExpired()
            } else {
                val wasTransferring = resourceTransferInProgress
                resourceTransferInProgress = current.resourceTransferInProgress
                val previous = backgroundPreview
                if (
                    !current.resourceTransferInProgress &&
                    (
                        previous == null ||
                            wasTransferring ||
                            previous.preset.background != current.preset.background
                        )
                ) {
                    backgroundPreview = current
                }
            }
        }
    }
    val preview = backgroundPreview ?: return
    val context = LocalContext.current
    val showUnsupportedAction = remember(context) {
        {
            Toast.makeText(context, "预览页面不支持收藏/分享", Toast.LENGTH_SHORT).show()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = preview.preset,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile
        )
    ) {
        ReaderBackgroundSurface(modifier = Modifier.fillMaxSize()) {
            val pagePadding = ReaderPageLayout.horizontalPadding
            val blockSpacing = ReaderPageLayout.blockSpacing
            val titlePadding = ReaderPageLayout.titleHorizontalPadding
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val contentWidthPx = with(density) {
                (configuration.screenWidthDp.dp - pagePadding * 2).toPx()
            }.coerceAtLeast(1f)
            val titleAvailableWidthPx = with(density) {
                (configuration.screenWidthDp.dp - pagePadding * 2 - titlePadding * 2).toPx()
            }.coerceAtLeast(1f)
            val titleLimits = remember(titleAvailableWidthPx, density) {
                rememberWatchTitleLineLimitsPx(titleAvailableWidthPx, density)
            }
            val blockSpacingPx = with(density) { blockSpacing.toPx() }
            val layoutHolder = remember { NativePreviewLayoutHolder() }
            LaunchedEffect(
                session,
                contentWidthPx,
                titleLimits,
                blockSpacingPx,
                density.density,
                density.fontScale
            ) {
                session.state
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { incoming ->
                        if (incoming.resourceTransferInProgress) return@collect
                        val exactLayout = withContext(Dispatchers.Default) {
                            buildNativePreviewLayout(
                                preset = incoming.preset,
                                fontFile = repository::fontFile,
                                contentWidthPx = contentWidthPx,
                                titleFirstLimitPx = titleLimits.first,
                                titleSecondLimitPx = titleLimits.second,
                                blockSpacingPx = blockSpacingPx,
                                density = density.density,
                                fontScale = density.fontScale
                            )
                        }
                        layoutHolder.submit(exactLayout)
                    }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = pagePadding,
                        top = ReaderPageLayout.titleTopPadding,
                        end = pagePadding,
                        bottom = ReaderPageLayout.bottomPadding(hasFloatingAction = false)
                    )
            ) {
                AndroidView(
                    factory = { context ->
                        NativePreviewLayoutView(context).also(layoutHolder::attach)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onRelease = layoutHolder::detach
                )
                Spacer(modifier = Modifier.height(15.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleIconButton(
                            icon = Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = Color.Transparent,
                            size = 32.dp,
                            padding = 6.dp,
                            onClick = showUnsupportedAction
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CircleIconButton(
                            icon = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = Color.Transparent,
                            size = 32.dp,
                            padding = 6.dp,
                            iconOffsetX = (-1).dp,
                            onClick = showUnsupportedAction
                        )
                    }
                }
            }
            Text(
                text = if (resourceTransferInProgress) {
                    "资源文件传输中"
                } else {
                    "预设实时预览"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
}

@Immutable
private data class NativePreviewParagraph(
    val layout: StaticLayout,
    val leftPx: Float,
    val topPx: Float
)

@Immutable
private data class NativePreviewLayout(
    val preset: ReaderPreset,
    val paragraphs: List<NativePreviewParagraph>,
    val heightPx: Float
)

private class NativePreviewLayoutHolder {
    private var view: NativePreviewLayoutView? = null
    private var latest: NativePreviewLayout? = null

    fun attach(view: NativePreviewLayoutView) {
        this.view = view
        latest?.let(view::submit)
    }

    fun detach(view: NativePreviewLayoutView) {
        if (this.view === view) this.view = null
    }

    fun submit(layout: NativePreviewLayout) {
        latest = layout
        view?.submit(layout)
    }
}

private class NativePreviewLayoutView(context: Context) : View(context) {
    private var previewLayout: NativePreviewLayout? = null

    init {
        setWillNotDraw(false)
    }

    fun submit(layout: NativePreviewLayout) {
        previewLayout = layout
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = ceil(previewLayout?.heightPx ?: 0f).toInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val layout = previewLayout ?: return
        val clipBounds = canvas.clipBounds
        layout.paragraphs.forEach { paragraph ->
            val paragraphBottomPx = paragraph.topPx + paragraph.layout.height
            if (
                paragraphBottomPx < clipBounds.top ||
                paragraph.topPx > clipBounds.bottom
            ) {
                return@forEach
            }
            val saveCount = canvas.save()
            canvas.translate(paragraph.leftPx, paragraph.topPx)
            paragraph.layout.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }
}

private data class PreviewParagraphSpec(
    val text: String,
    val style: ReaderTextStyle,
    val forceCenter: Boolean = false,
    val firstLineLimitPx: Float? = null,
    val otherLineLimitPx: Float? = null
)

private fun buildNativePreviewLayout(
    preset: ReaderPreset,
    fontFile: (String?) -> File?,
    contentWidthPx: Float,
    titleFirstLimitPx: Float,
    titleSecondLimitPx: Float,
    blockSpacingPx: Float,
    density: Float,
    fontScale: Float
): NativePreviewLayout {
    val body = preset.body
    val title = preset.resolvedStyle(ReaderTypographyRole.TITLE)
    val specs = listOf(
        PreviewParagraphSpec(
            text = "阅读，让时间慢下来",
            style = title,
            forceCenter = true,
            firstLineLimitPx = titleFirstLimitPx,
            otherLineLimitPx = titleSecondLimitPx
        ),
        PreviewParagraphSpec(
            "标题、副标题与正文会分别使用分类样式",
            preset.resolvedStyle(ReaderTypographyRole.SUBTITLE)
        ),
        PreviewParagraphSpec(
            "这是手表端的实时正文预览。你在手机上调整字体、字号、颜色、排版或背景后，这里会自动更新。",
            body
        ),
        PreviewParagraphSpec(
            "“好的排版让文字安静地抵达读者。”",
            preset.resolvedStyle(ReaderTypographyRole.QUOTE)
        ),
        PreviewParagraphSpec(
            "val preview = ReaderPreset.live",
            preset.resolvedStyle(ReaderTypographyRole.CODE)
        ),
        PreviewParagraphSpec(
            "链接样式预览 · watchrss.app",
            preset.resolvedStyle(ReaderTypographyRole.LINK)
        ),
        PreviewParagraphSpec(
            "继续向下阅读时，可以观察正文在圆形屏幕不同高度上的换行和边缘留白。预览内容会保留足够长度，方便检查滚动过程。",
            body
        ),
        PreviewParagraphSpec(
            "较长文章往往包含多个段落。这里再加入一段常规文字，用来确认段间距、首行缩进和两端对齐在连续内容中的实际效果。",
            body
        ),
        PreviewParagraphSpec(
            "到达文章结尾前，还应留出收藏与分享操作的位置，使最后一段文字能够完整滚动到圆屏中央，而不是停留在底部曲面区域。",
            body
        )
    )
    val paragraphs = ArrayList<NativePreviewParagraph>(specs.size)
    var topPx = 0f
    specs.forEach { spec ->
        val paint = spec.style.toPreviewTextPaint(fontFile, density, fontScale)
        val text = if (spec.firstLineLimitPx != null && spec.otherLineLimitPx != null) {
            formatWatchTitleForWidthLimitsWithMeasurer(
                title = spec.text,
                availableWidthPx = contentWidthPx,
                firstLimitPx = spec.firstLineLimitPx,
                secondLimitPx = spec.otherLineLimitPx,
                measureText = paint::measureText
            )
        } else {
            spec.text
        }
        val widthPx = (spec.otherLineLimitPx ?: contentWidthPx)
            .coerceAtLeast(1f)
            .roundToInt()
        val layout = createPreviewStaticLayout(
            text = text,
            paint = paint,
            style = spec.style,
            widthPx = widthPx,
            forceCenter = spec.forceCenter
        )
        paragraphs += NativePreviewParagraph(
            layout = layout,
            leftPx = if (spec.forceCenter) {
                ((contentWidthPx - widthPx) / 2f).coerceAtLeast(0f)
            } else {
                0f
            },
            topPx = topPx
        )
        topPx += layout.height + blockSpacingPx
    }
    return NativePreviewLayout(
        preset = preset,
        paragraphs = paragraphs,
        heightPx = (topPx - blockSpacingPx).coerceAtLeast(0f)
    )
}

private fun ReaderTextStyle.toPreviewTextPaint(
    fontFile: (String?) -> File?,
    density: Float,
    fontScale: Float
): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    color = colorArgb.toInt()
    textSize = fontSizeSp * density * fontScale
    typeface = readerTypefaceFor(fontFile(fontAssetId), this@toPreviewTextPaint)
    letterSpacing = letterSpacingEm
    isUnderlineText = underline
    isStrikeThruText = strikethrough
}

private fun createPreviewStaticLayout(
    text: String,
    paint: TextPaint,
    style: ReaderTextStyle,
    widthPx: Int,
    forceCenter: Boolean
): StaticLayout {
    val naturalLineHeight = paint.fontMetrics.run { descent - ascent }
    val requestedLineHeight = paint.textSize * style.lineHeightEm
    return StaticLayout.Builder
        .obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(
            if (forceCenter || style.alignment == ReaderTextAlignment.CENTER) {
                Layout.Alignment.ALIGN_CENTER
            } else {
                Layout.Alignment.ALIGN_NORMAL
            }
        )
        .setIncludePad(false)
        .setLineSpacing(requestedLineHeight - naturalLineHeight, 1f)
        .setBreakStrategy(
            when (style.lineBreakMode) {
                ReaderLineBreakMode.SIMPLE -> Layout.BREAK_STRATEGY_SIMPLE
                ReaderLineBreakMode.SYSTEM -> Layout.BREAK_STRATEGY_HIGH_QUALITY
                ReaderLineBreakMode.PARAGRAPH -> Layout.BREAK_STRATEGY_BALANCED
            }
        )
        .setHyphenationFrequency(
            if (style.hyphenation == ReaderHyphenation.AUTO) {
                Layout.HYPHENATION_FREQUENCY_FULL
            } else {
                Layout.HYPHENATION_FREQUENCY_NONE
            }
        )
        .apply {
            if (style.alignment == ReaderTextAlignment.JUSTIFY) {
                setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            }
        }
        .build()
}
