package com.lightningstudio.watchrss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import com.mohamedrejeb.richeditor.model.LocalImageLoader
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import java.io.File

@Composable
internal fun WatchNoteRichText(
    markup: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    onImageClick: (WatchNotePreviewBlock.Image) -> Unit = {},
    onTextBlockLayout: (WatchNoteRenderedTextLayout) -> Unit = {}
) {
    val bodyStyle = readerTextStyle(ReaderTextRole.BODY).copy(textAlign = TextAlign.Start)
    val codeStyle = readerTextStyle(ReaderTextRole.CODE)
    val linkStyle = readerTextStyle(ReaderTextRole.LINK)
    val preset = LocalReaderPresetRuntime.current.preset
    val codeBackground = Color(preset.codeBackgroundColorArgb)
    val blocks = remember(markup) { parseWatchNotePreviewBlocks(markup) }
    val context = LocalContext.current
    val imageLoader = remember(context) { WatchNoteImageLoader(context.filesDir) }

    if (markup.isBlank()) {
        Text(
            text = emptyText,
            modifier = modifier,
            style = bodyStyle,
            color = bodyStyle.color.copy(alpha = 0.62f)
        )
        return
    }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            key(index, block) {
                when (block) {
                    is WatchNotePreviewBlock.RichText -> WatchNoteRichTextBlock(
                        markup = block.markup,
                        html = block.html,
                        bodyStyle = bodyStyle,
                        codeTextColor = codeStyle.color,
                        codeBackgroundColor = codeBackground,
                        linkColor = linkStyle.color,
                        imageLoader = imageLoader,
                        layoutKey = "rich-$index",
                        onTextBlockLayout = onTextBlockLayout,
                        onTextBlockDisposed = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )

                    is WatchNotePreviewBlock.Image -> WatchNoteImageBlock(
                        image = block,
                        filesDir = context.filesDir,
                        onClick = { onImageClick(block) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )

                    is WatchNotePreviewBlock.Table -> WatchNoteTable(
                        table = block.table,
                        bodyStyle = bodyStyle,
                        codeTextColor = codeStyle.color,
                        codeBackgroundColor = codeBackground,
                        blockIndex = index,
                        onTextBlockLayout = onTextBlockLayout,
                        onTextBlockDisposed = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Long notes must not be measured as one giant text layout on the watch UI thread. Each rich-text
 * item is kept small and [LazyColumn] only composes the items near the viewport.
 */
@Composable
internal fun WatchNoteRichTextLazyColumn(
    markup: String,
    blocks: List<WatchNotePreviewBlock>,
    emptyText: String,
    title: String,
    metadata: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onImageClick: (WatchNotePreviewBlock.Image) -> Unit = {},
    onTextBlockLayout: (WatchNoteRenderedTextLayout) -> Unit = {},
    onTextBlockDisposed: (String) -> Unit = {}
) {
    val bodyStyle = readerTextStyle(ReaderTextRole.BODY).copy(textAlign = TextAlign.Start)
    val codeStyle = readerTextStyle(ReaderTextRole.CODE)
    val linkStyle = readerTextStyle(ReaderTextRole.LINK)
    val titleStyle = readerTextStyle(ReaderTextRole.TITLE)
    val subtitleStyle = readerTextStyle(ReaderTextRole.SUBTITLE)
    val preset = LocalReaderPresetRuntime.current.preset
    val codeBackground = Color(preset.codeBackgroundColorArgb)
    val context = LocalContext.current
    val imageLoader = remember(context) { WatchNoteImageLoader(context.filesDir) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ReaderPageLayout.horizontalPadding,
            top = ReaderPageLayout.titleTopPadding,
            end = ReaderPageLayout.horizontalPadding,
            bottom = 64.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "note-header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailTitle(
                    title = title,
                    titlePadding = ReaderPageLayout.titleHorizontalPadding,
                    textColor = titleStyle.color
                )
                if (metadata != null) {
                    Text(
                        text = metadata,
                        style = subtitleStyle,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        if (markup.isBlank()) {
            item(key = "note-empty") {
                Text(
                    text = emptyText,
                    modifier = Modifier.fillMaxWidth(),
                    style = bodyStyle,
                    color = bodyStyle.color.copy(alpha = 0.62f)
                )
            }
        } else {
            itemsIndexed(
                items = blocks,
                key = { index, _ -> "note-block-$index" }
            ) { index, block ->
                when (block) {
                    is WatchNotePreviewBlock.RichText -> WatchNoteRichTextBlock(
                        markup = block.markup,
                        html = block.html,
                        bodyStyle = bodyStyle,
                        codeTextColor = codeStyle.color,
                        codeBackgroundColor = codeBackground,
                        linkColor = linkStyle.color,
                        imageLoader = imageLoader,
                        layoutKey = "rich-$index",
                        onTextBlockLayout = onTextBlockLayout,
                        onTextBlockDisposed = onTextBlockDisposed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )

                    is WatchNotePreviewBlock.Image -> WatchNoteImageBlock(
                        image = block,
                        filesDir = context.filesDir,
                        onClick = { onImageClick(block) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )

                    is WatchNotePreviewBlock.Table -> WatchNoteTable(
                        table = block.table,
                        bodyStyle = bodyStyle,
                        codeTextColor = codeStyle.color,
                        codeBackgroundColor = codeBackground,
                        blockIndex = index,
                        onTextBlockLayout = onTextBlockLayout,
                        onTextBlockDisposed = onTextBlockDisposed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchNoteRichTextBlock(
    markup: String,
    html: Boolean,
    bodyStyle: TextStyle,
    codeTextColor: Color,
    codeBackgroundColor: Color,
    linkColor: Color,
    imageLoader: ImageLoader,
    layoutKey: String,
    onTextBlockLayout: (WatchNoteRenderedTextLayout) -> Unit,
    onTextBlockDisposed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(markup, codeTextColor, codeBackgroundColor, linkColor) {
        RichTextState().also {
            it.applyWatchNoteStyle(codeTextColor, codeBackgroundColor, linkColor)
            if (html) it.setHtml(markup) else it.setMarkdown(markup)
        }
    }
    DisposableEffect(layoutKey) {
        onDispose { onTextBlockDisposed(layoutKey) }
    }
    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        SelectionContainer {
            var latestLayout by remember(markup) { mutableStateOf<TextLayoutResult?>(null) }
            var latestPositionInRoot by remember(markup) {
                mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
            }
            val renderedText = state.annotatedString.text

            fun dispatchLayout() {
                val layout = latestLayout ?: return
                val position = latestPositionInRoot ?: return
                onTextBlockLayout(
                    WatchNoteRenderedTextLayout(
                        layoutKey,
                        renderedText,
                        layout,
                        position.x,
                        position.y
                    )
                )
            }

            BasicRichText(
                state = state,
                style = bodyStyle,
                modifier = modifier.onGloballyPositioned { coordinates ->
                    latestPositionInRoot = coordinates.positionInRoot()
                    dispatchLayout()
                },
                onTextLayout = { layout ->
                    latestLayout = layout
                    dispatchLayout()
                }
            )
        }
    }
}

@Composable
private fun WatchNoteImageBlock(
    image: WatchNotePreviewBlock.Image,
    filesDir: File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val painter = rememberAsyncImagePainter(resolveWatchNoteImageSource(filesDir, image.path))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "查看图片", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painter,
                contentDescription = image.description.ifBlank { "备忘录图片" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 260.dp)
            )
            when (painter.state) {
                is AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading -> WatchCircularProgressIndicator(
                    modifier = Modifier.size(28.dp)
                )
                is AsyncImagePainter.State.Error -> Text(
                    text = "图片未同步\n请在手机端重新同步",
                    style = readerTextStyle(ReaderTextRole.SUBTITLE),
                    color = readerTextStyle(ReaderTextRole.SUBTITLE).color.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center
                )
                is AsyncImagePainter.State.Success -> Unit
            }
        }
        if (image.description.isNotBlank()) {
            Text(
                text = image.description,
                style = readerTextStyle(ReaderTextRole.SUBTITLE),
                color = readerTextStyle(ReaderTextRole.SUBTITLE).color.copy(alpha = 0.76f)
            )
        }
    }
}

@Composable
private fun WatchNoteTable(
    table: WatchNotePreviewTable,
    bodyStyle: TextStyle,
    codeTextColor: Color,
    codeBackgroundColor: Color,
    blockIndex: Int,
    onTextBlockLayout: (WatchNoteRenderedTextLayout) -> Unit,
    onTextBlockDisposed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (table.columnCount == 0) return
    DisposableEffect(blockIndex, table) {
        onDispose {
            table.rows.forEachIndexed { rowIndex, row ->
                row.padWatchNoteTableRow(table.columnCount).indices.forEach { column ->
                    onTextBlockDisposed("table-$blockIndex-$rowIndex-$column")
                }
            }
        }
    }
    val bodyColor = bodyStyle.color
    val horizontalScrollState = rememberScrollState()
    val cellWidth = 128.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, bodyColor.copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            Column(modifier = Modifier.width(cellWidth * table.columnCount)) {
                table.rows.forEachIndexed { rowIndex, row ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.padWatchNoteTableRow(table.columnCount).forEachIndexed { column, cell ->
                            val alignment = table.alignments.getOrNull(column)
                                ?: WatchNoteTableAlignment.Start
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .fillMaxHeight()
                                    .background(
                                        if (rowIndex < table.headerRows) {
                                            bodyColor.copy(alpha = 0.1f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .border(0.5.dp, bodyColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                            ) {
                                val renderedText = remember(
                                    cell,
                                    codeTextColor,
                                    codeBackgroundColor
                                ) {
                                    watchNoteTableCellText(
                                        markdown = cell,
                                        codeTextColor = codeTextColor,
                                        codeBackgroundColor = codeBackgroundColor
                                    )
                                }
                                var latestLayout by remember(cell) {
                                    mutableStateOf<TextLayoutResult?>(null)
                                }
                                var latestPositionInRoot by remember(cell) {
                                    mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
                                }
                                val layoutKey = "table-$blockIndex-$rowIndex-$column"

                                fun dispatchLayout() {
                                    val layout = latestLayout ?: return
                                    val position = latestPositionInRoot ?: return
                                    onTextBlockLayout(
                                        WatchNoteRenderedTextLayout(
                                            layoutKey,
                                            renderedText.text,
                                            layout,
                                            position.x,
                                            position.y
                                        )
                                    )
                                }

                                Text(
                                    text = renderedText,
                                    style = bodyStyle.copy(
                                        textAlign = when (alignment) {
                                            WatchNoteTableAlignment.Start -> TextAlign.Start
                                            WatchNoteTableAlignment.Center -> TextAlign.Center
                                            WatchNoteTableAlignment.End -> TextAlign.End
                                        },
                                        fontWeight = if (rowIndex < table.headerRows) {
                                            FontWeight.SemiBold
                                        } else {
                                            bodyStyle.fontWeight
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            latestPositionInRoot = coordinates.positionInRoot()
                                            dispatchLayout()
                                        },
                                    onTextLayout = { layout ->
                                        latestLayout = layout
                                        dispatchLayout()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun watchNoteTableCellText(
    markdown: String,
    codeTextColor: Color,
    codeBackgroundColor: Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    WATCH_NOTE_INLINE_CODE_REGEX.findAll(markdown).forEach { match ->
        append(markdown.substring(cursor, match.range.first))
        withStyle(
            SpanStyle(
                color = codeTextColor,
                background = codeBackgroundColor
            )
        ) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(markdown.substring(cursor))
}

private val WATCH_NOTE_INLINE_CODE_REGEX = Regex("`([^`\\n]+)`")

internal data class WatchNoteRenderedTextLayout(
    val key: String,
    val renderedText: String,
    val layout: TextLayoutResult,
    val leftInRoot: Float,
    val topInRoot: Float
)

private class WatchNoteImageLoader(private val filesDir: File) : ImageLoader {
    @Composable
    override fun load(model: Any): ImageData {
        val resolved = (model as? String)?.let { resolveWatchNoteImageSource(filesDir, it) } ?: model
        return ImageData(
            painter = rememberAsyncImagePainter(resolved),
            contentDescription = null
        )
    }
}

internal fun resolveWatchNoteImageSource(filesDir: File, path: String): String =
    if (path.startsWith("assets/")) File(filesDir, "notes/$path").absolutePath else path

private fun RichTextState.applyWatchNoteStyle(
    codeTextColor: Color,
    codeBackgroundColor: Color,
    linkColor: Color
) {
    config.codeSpanColor = codeTextColor
    config.codeSpanBackgroundColor = codeBackgroundColor
    config.codeSpanStrokeColor = Color.Transparent
    config.linkColor = linkColor
}

internal sealed interface WatchNotePreviewBlock {
    data class RichText(val markup: String, val html: Boolean = false) : WatchNotePreviewBlock
    data class Image(val path: String, val description: String) : WatchNotePreviewBlock
    data class Table(val table: WatchNotePreviewTable) : WatchNotePreviewBlock
}

internal data class WatchNotePreviewTable(
    val rows: List<List<String>>,
    val headerRows: Int,
    val alignments: List<WatchNoteTableAlignment>
) {
    val columnCount: Int = rows.maxOfOrNull { it.size } ?: 0
}

internal enum class WatchNoteTableAlignment { Start, Center, End }

internal fun parseWatchNotePreviewBlocks(markup: String): List<WatchNotePreviewBlock> {
    if (markup.isWatchNoteRichHtml()) return parseWatchNoteHtmlPreviewBlocks(markup)
    return parseWatchNoteMarkdownImageBlocks(markup)
}

internal fun prepareWatchNotePreviewBlocks(markup: String): List<WatchNotePreviewBlock> =
    chunkWatchNotePreviewBlocks(parseWatchNotePreviewBlocks(markup))

internal fun chunkWatchNotePreviewBlocks(
    blocks: List<WatchNotePreviewBlock>,
    maxRichTextChars: Int = WATCH_NOTE_RICH_TEXT_CHUNK_CHARS
): List<WatchNotePreviewBlock> {
    require(maxRichTextChars > 0)
    return blocks.flatMap { block ->
        if (block !is WatchNotePreviewBlock.RichText ||
            block.html ||
            block.markup.length <= maxRichTextChars
        ) {
            listOf(block)
        } else {
            chunkWatchNoteMarkdown(block.markup, maxRichTextChars).map {
                WatchNotePreviewBlock.RichText(markup = it)
            }
        }
    }
}

private fun chunkWatchNoteMarkdown(markdown: String, maxChars: Int): List<String> {
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    var fence: WatchNoteMarkdownFence? = null

    fun flush() {
        if (current.isNotEmpty()) {
            chunks += current.toString().trimEnd('\n')
            current.clear()
        }
    }

    markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
        val marker = watchNoteMarkdownFenceMarker(line)
        val insideFence = fence != null
        val lineLength = line.length + if (current.isEmpty()) 0 else 1

        if (!insideFence && current.isNotEmpty() && current.length + lineLength > maxChars) {
            flush()
        }
        if (current.isNotEmpty()) current.append('\n')
        current.append(line)

        if (insideFence) {
            if (marker?.closes(requireNotNull(fence)) == true) fence = null
        } else if (marker != null) {
            fence = marker
        }

        if (fence == null && line.isBlank() && current.length >= maxChars / 2) {
            flush()
        }
    }
    flush()
    return chunks.ifEmpty { listOf(markdown) }
}

private val WatchNoteImageTagRegex = Regex("""(?is)<img\b[^>]*>(?:\s*</img>)?""")
private val WatchNoteMarkdownImageRegex = Regex(
    """!\[([^]]*)]\(([^)\s]+)(?:\s+[\"'][^\"']*[\"'])?\)"""
)

private const val WATCH_NOTE_RICH_TEXT_CHUNK_CHARS = 1_200

private fun parseWatchNoteHtmlPreviewBlocks(markup: String): List<WatchNotePreviewBlock> {
    val result = mutableListOf<WatchNotePreviewBlock>()
    var cursor = 0
    WatchNoteImageTagRegex.findAll(markup).forEach { match ->
        val before = markup.substring(cursor, match.range.first)
        if (before.isNotBlank()) result += WatchNotePreviewBlock.RichText(before, html = true)
        val source = match.value.watchNoteHtmlAttribute("src")
        if (!source.isNullOrBlank()) {
            result += WatchNotePreviewBlock.Image(
                path = source,
                description = match.value.watchNoteHtmlAttribute("alt").orEmpty()
            )
        } else {
            result += WatchNotePreviewBlock.RichText(match.value, html = true)
        }
        cursor = match.range.last + 1
    }
    val after = markup.substring(cursor)
    if (after.isNotBlank()) result += WatchNotePreviewBlock.RichText(after, html = true)
    return result.ifEmpty { listOf(WatchNotePreviewBlock.RichText(markup, html = true)) }
}

private fun String.watchNoteHtmlAttribute(name: String): String? =
    Regex("""(?is)\b${Regex.escape(name)}\s*=\s*[\"']([^\"']*)[\"']""")
        .find(this)
        ?.groupValues
        ?.get(1)

private fun parseWatchNoteMarkdownImageBlocks(markup: String): List<WatchNotePreviewBlock> {
    val result = mutableListOf<WatchNotePreviewBlock>()
    val richText = StringBuilder()
    var fence: WatchNoteMarkdownFence? = null

    fun flushRichText() {
        if (richText.isNotEmpty()) {
            result += parseWatchNoteTableBlocks(richText.toString())
            richText.clear()
        }
    }

    markup.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
        val fenceMarker = watchNoteMarkdownFenceMarker(line)
        if (fence != null) {
            if (richText.isNotEmpty()) richText.append('\n')
            richText.append(line)
            if (fenceMarker?.closes(fence) == true) fence = null
            return@forEach
        }
        if (fenceMarker != null) {
            fence = fenceMarker
            if (richText.isNotEmpty()) richText.append('\n')
            richText.append(line)
            return@forEach
        }

        val images = WatchNoteMarkdownImageRegex.findAll(line).toList()
        if (images.isEmpty()) {
            if (richText.isNotEmpty()) richText.append('\n')
            richText.append(line)
            return@forEach
        }

        var cursor = 0
        images.forEach { image ->
            richText.append(line.substring(cursor, image.range.first))
            flushRichText()
            result += WatchNotePreviewBlock.Image(
                path = image.groupValues[2].replace("%20", " "),
                description = image.groupValues[1]
            )
            cursor = image.range.last + 1
        }
        richText.append(line.substring(cursor))
    }
    flushRichText()
    return result
}

private fun parseWatchNoteTableBlocks(markup: String): List<WatchNotePreviewBlock> {
    val normalized = markup.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val result = mutableListOf<WatchNotePreviewBlock>()
    val richText = StringBuilder()
    var index = 0
    var fence: WatchNoteMarkdownFence? = null

    fun appendRichTextLine(line: String) {
        if (richText.isNotEmpty()) richText.append('\n')
        richText.append(line)
    }

    fun flushRichText() {
        if (richText.isNotEmpty()) {
            result += WatchNotePreviewBlock.RichText(richText.toString())
            richText.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val fenceMarker = watchNoteMarkdownFenceMarker(line)
        if (fence != null) {
            appendRichTextLine(line)
            if (fenceMarker?.closes(fence) == true) fence = null
            index++
            continue
        }
        if (fenceMarker != null) {
            fence = fenceMarker
            appendRichTextLine(line)
            index++
            continue
        }

        val pipeTable = parseWatchNotePipeTable(lines, index)
        if (pipeTable != null) {
            flushRichText()
            result += WatchNotePreviewBlock.Table(pipeTable.table)
            index = pipeTable.nextLine
            continue
        }

        val tabTable = parseWatchNoteTabTable(lines, index)
        if (tabTable != null) {
            flushRichText()
            result += WatchNotePreviewBlock.Table(tabTable.table)
            index = tabTable.nextLine
            continue
        }

        val alignedTextTable = parseWatchNoteAlignedTextTable(lines, index)
        if (alignedTextTable != null) {
            flushRichText()
            result += WatchNotePreviewBlock.Table(alignedTextTable.table)
            index = alignedTextTable.nextLine
            continue
        }

        appendRichTextLine(line)
        index++
    }
    flushRichText()
    return result
}

private data class ParsedWatchNoteTable(
    val table: WatchNotePreviewTable,
    val nextLine: Int
)

private fun parseWatchNotePipeTable(lines: List<String>, start: Int): ParsedWatchNoteTable? {
    if (start + 1 >= lines.size) return null
    val header = splitWatchNotePipeRow(lines[start]) ?: return null
    val separators = splitWatchNotePipeRow(lines[start + 1]) ?: return null
    if (header.size < 2 || separators.size != header.size) return null
    val alignments = separators.map(::parseWatchNoteTableSeparator)
    if (alignments.any { it == null }) return null

    val rows = mutableListOf(header)
    var index = start + 2
    while (index < lines.size) {
        val row = splitWatchNotePipeRow(lines[index]) ?: break
        rows += row.padWatchNoteTableRow(header.size)
        index++
    }
    return ParsedWatchNoteTable(
        table = WatchNotePreviewTable(
            rows = rows,
            headerRows = 1,
            alignments = alignments.filterNotNull()
        ),
        nextLine = index
    )
}

private fun parseWatchNoteTabTable(lines: List<String>, start: Int): ParsedWatchNoteTable? {
    val first = splitWatchNoteTabRow(lines[start]) ?: return null
    val rows = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val row = splitWatchNoteTabRow(lines[index]) ?: break
        rows += row
        index++
    }
    if (rows.size < 2 && first.size < 3) return null
    val columns = rows.maxOf { it.size }
    return ParsedWatchNoteTable(
        table = WatchNotePreviewTable(
            rows = rows.map { it.padWatchNoteTableRow(columns) },
            headerRows = 1,
            alignments = List(columns) { WatchNoteTableAlignment.Start }
        ),
        nextLine = index
    )
}

private fun splitWatchNotePipeRow(line: String): List<String>? {
    val trimmed = line.trim()
    if ('|' !in trimmed || trimmed.startsWith("    ")) return null
    val content = trimmed.removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var escaped = false
    content.forEach { character ->
        when {
            escaped -> {
                cell.append(character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(character)
        }
    }
    if (escaped) cell.append('\\')
    cells += cell.toString().trim()
    return cells.takeIf { it.size >= 2 }
}

private fun splitWatchNoteTabRow(line: String): List<String>? {
    if ('\t' !in line || line.startsWith("    ")) return null
    return line.split('\t').map(String::trim).takeIf { it.size >= 2 }
}

/**
 * Rich-editor Markdown round trips can flatten a pipe/TSV table into columns separated by two
 * spaces and leave the old separator row as one blank line. Recover that durable form before it
 * reaches BasicRichText; otherwise the whole document becomes one flowing TextView on the watch.
 */
private fun parseWatchNoteAlignedTextTable(
    lines: List<String>,
    start: Int
): ParsedWatchNoteTable? {
    val header = splitWatchNoteAlignedTextRow(lines[start]) ?: return null
    if (header.any { it.length > WATCH_NOTE_ALIGNED_HEADER_MAX_LENGTH }) return null

    var index = start + 1
    if (index < lines.size && lines[index].isBlank()) index++
    val rows = mutableListOf(header)
    while (index < lines.size) {
        val alignedRow = splitWatchNoteAlignedTextRow(lines[index])
        val row = alignedRow?.takeIf { it.size == header.size }
            ?: splitWatchNoteDegradedAlignedTextRow(lines[index], header)
            ?: alignedRow
            ?: break
        rows += row
        index++
    }
    if (rows.size < 2) return null
    if (header.size == 2 && rows.size < 3) return null

    val columns = rows.maxOf { it.size }
    return ParsedWatchNoteTable(
        table = WatchNotePreviewTable(
            rows = rows.map { it.padWatchNoteTableRow(columns) },
            headerRows = 1,
            alignments = List(columns) { WatchNoteTableAlignment.Start }
        ),
        nextLine = index
    )
}

private fun splitWatchNoteAlignedTextRow(line: String): List<String>? {
    if (line.startsWith("    ") || '\t' in line) return null
    val trimmed = line.trim()
    if (!trimmed.contains(WATCH_NOTE_ALIGNED_COLUMN_SEPARATOR)) return null
    return trimmed.split(WATCH_NOTE_ALIGNED_COLUMN_REGEX)
        .map(String::trim)
        .takeIf { cells -> cells.size >= 2 && cells.all(String::isNotEmpty) }
}

/**
 * Some rich-text Markdown serializers preserve the aligned header but collapse data-row column
 * gaps to one space. Only recover rows when the header names a known structured table and the
 * leading cells are still unambiguous inline-code tokens; ordinary prose must never become a
 * table merely because it follows a line containing two spaces.
 */
private fun splitWatchNoteDegradedAlignedTextRow(
    line: String,
    header: List<String>
): List<String>? {
    if (line.isBlank() || line.startsWith("    ") || '\t' in line) return null
    val normalizedHeader = header.map { it.trim().lowercase() }
    val trimmed = line.trim()

    if (normalizedHeader.take(3) == listOf("option", "type", "default")) {
        val match = WATCH_NOTE_DEGRADED_CONFIG_ROW.matchEntire(trimmed) ?: return null
        return listOf(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
            match.groupValues[4]
        ).padWatchNoteTableRow(header.size)
    }

    if (
        normalizedHeader.size >= 2 &&
        normalizedHeader[0] == "lua function" &&
        normalizedHeader[1] == "legacy dispatcher"
    ) {
        val match = WATCH_NOTE_DEGRADED_DISPATCHER_ROW.matchEntire(trimmed) ?: return null
        return listOf(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3]
        ).padWatchNoteTableRow(header.size)
    }

    if (normalizedHeader == listOf("argument", "accepted by", "meaning")) {
        val match = WATCH_NOTE_DEGRADED_ARGUMENT_ROW.matchEntire(trimmed) ?: return null
        return listOf(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3]
        )
    }

    return null
}

private const val WATCH_NOTE_ALIGNED_COLUMN_SEPARATOR = "  "
private const val WATCH_NOTE_ALIGNED_HEADER_MAX_LENGTH = 64
private val WATCH_NOTE_ALIGNED_COLUMN_REGEX = Regex(" {2,}")
private val WATCH_NOTE_DEGRADED_CONFIG_ROW = Regex(
    "^(`[^`]+`)\\s+(\\S+)\\s+(`[^`]+`|empty|\\S+)\\s+(.+)$"
)
private val WATCH_NOTE_DEGRADED_DISPATCHER_ROW = Regex(
    "^(`[^`]+`)\\s+(`[^`]+`)\\s+(.+)$"
)
private val WATCH_NOTE_DEGRADED_ARGUMENT_ROW = Regex(
    "^(`[^`]+`|No argument)\\s+((?:`[^`]+`(?:,\\s*)?)+(?:\\s+only)?)\\s+(.+)$"
)

private fun parseWatchNoteTableSeparator(value: String): WatchNoteTableAlignment? {
    val trimmed = value.trim()
    if (!Regex(":?-{3,}:?").matches(trimmed)) return null
    return when {
        trimmed.startsWith(':') && trimmed.endsWith(':') -> WatchNoteTableAlignment.Center
        trimmed.endsWith(':') -> WatchNoteTableAlignment.End
        else -> WatchNoteTableAlignment.Start
    }
}

private fun List<String>.padWatchNoteTableRow(size: Int): List<String> =
    take(size) + List((size - this.size).coerceAtLeast(0)) { "" }

private data class WatchNoteMarkdownFence(val marker: Char, val length: Int) {
    fun closes(open: WatchNoteMarkdownFence): Boolean =
        marker == open.marker && length >= open.length
}

private fun watchNoteMarkdownFenceMarker(line: String): WatchNoteMarkdownFence? {
    val trimmed = line.trimStart()
    if (line.length - trimmed.length > 3) return null
    val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = trimmed.takeWhile { it == marker }.length
    return WatchNoteMarkdownFence(marker, length).takeIf { length >= 3 }
}

internal fun String.isWatchNoteRichHtml(): Boolean = listOf(
    "color:",
    "background:",
    "text-align:",
    "<img",
    "<sub>",
    "<sup>"
).any(::contains)
