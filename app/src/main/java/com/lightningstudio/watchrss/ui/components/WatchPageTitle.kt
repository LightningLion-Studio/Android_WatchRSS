package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import com.lightningstudio.watchrss.ui.util.roundScreenLineWidthPx
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.rememberIsRoundWatch
import com.lightningstudio.watchrss.ui.theme.rememberWatchTitleLineLimitsPx
import com.lightningstudio.watchrss.ui.theme.watchTextUnitResource
import com.lightningstudio.watchrss.ui.util.formatWatchScreenTitle
import kotlin.math.max

const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

/**
 * Page title. On a round screen, each line break keeps the line inside the
 * circle at the title's position on the page (and within the first/second
 * line width limits); a rectangular screen keeps the full width. The optional
 * ⓘ hint stays attached to the last word.
 */
@Composable
fun WatchPageTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Center,
    showInfoHint: Boolean = false
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isRound = rememberIsRoundWatch()
    val measurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.merge(style)
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val suffix = CHANNEL_TITLE_CLICK_HINT_SYMBOL.takeIf { showInfoHint }
    val anchor = rememberTitleAnchor()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthPx = constraints.maxWidth.toFloat()
        val fitsCircle = isRound && constraints.hasBoundedWidth
        val formatted = remember(text, suffix, availableWidthPx, textStyle, fitsCircle, anchor.value, density, configuration) {
            val measure = { line: String ->
                measurer.measure(line, textStyle, softWrap = false, maxLines = 1).size.width.toFloat()
            }
            var (firstLimitPx, secondLimitPx) = if (fitsCircle) {
                rememberWatchTitleLineLimitsPx(availableWidthPx, density)
            } else {
                availableWidthPx to availableWidthPx
            }
            anchor.value?.takeIf { fitsCircle }?.let { position ->
                val lineHeightPx = measurer.measure("国", textStyle).size.height.toFloat()
                val widthPx = { lineIndex: Int ->
                    roundTitleLineWidthPx(position, lineIndex * lineHeightPx, lineHeightPx, density, configuration)
                }
                firstLimitPx = minOf(firstLimitPx, widthPx(0))
                secondLimitPx = minOf(secondLimitPx, widthPx(1))
            }
            formatWatchScreenTitle(
                title = text,
                isRound = fitsCircle,
                availableWidthPx = availableWidthPx,
                firstLimitPx = firstLimitPx,
                secondLimitPx = secondLimitPx,
                protectedSuffix = suffix,
                measureText = measure
            )
        }
        Text(
            text = if (suffix != null) styledHint(formatted, textStyle.fontSize, hintColor) else AnnotatedString(formatted),
            style = textStyle,
            color = color,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .titleAnchor(anchor, enabled = fitsCircle)
                .semantics { heading() }
        )
    }
}

/**
 * Window position (top, centre x) of a title at its first placement, i.e. with
 * the page at its top. Saveable so a lazy list item keeps it after it scrolls
 * out and back.
 */
@Composable
internal fun rememberTitleAnchor(): MutableState<FloatArray?> = rememberSaveable { mutableStateOf(null) }

/** Records [anchor] once; the title stays hidden for the single frame before that. */
internal fun Modifier.titleAnchor(anchor: MutableState<FloatArray?>, enabled: Boolean): Modifier {
    if (!enabled) return this
    return onGloballyPositioned { coordinates ->
        if (anchor.value == null) {
            val position = coordinates.positionInWindow()
            anchor.value = floatArrayOf(position.y, position.x + coordinates.size.width / 2f)
        }
    }.alpha(if (anchor.value == null) 0f else 1f)
}

/** Widest line inside the round screen, [lineOffsetPx] below the anchored title top. */
internal fun roundTitleLineWidthPx(
    anchor: FloatArray,
    lineOffsetPx: Float,
    lineHeightPx: Float,
    density: Density,
    configuration: Configuration
): Float = with(density) {
    roundScreenLineWidthPx(
        lineTopPx = anchor[0] + lineOffsetPx,
        lineHeightPx = lineHeightPx,
        lineCenterXPx = anchor[1],
        screenCenterXPx = configuration.screenWidthDp.dp.toPx() / 2f,
        screenCenterYPx = configuration.screenHeightDp.dp.toPx() / 2f,
        radiusPx = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() / 2f,
        marginPx = TITLE_CIRCLE_MARGIN.toPx()
    )
}

private val TITLE_CIRCLE_MARGIN = 2.dp

/** Channel page title style: `hey_m_title` with the matching line height. */
@Composable
fun watchChannelTitleStyle(): TextStyle {
    val size = watchTextUnitResource(R.dimen.hey_m_title)
    val lineHeight = max(MaterialTheme.typography.titleMedium.lineHeight.value, size.value * 1.24f).sp
    return TextStyle(fontSize = size, lineHeight = lineHeight)
}

private fun styledHint(text: String, titleSize: TextUnit, hintColor: Color): AnnotatedString {
    val symbolIndex = text.lastIndexOf(CHANNEL_TITLE_CLICK_HINT_SYMBOL)
    if (symbolIndex < 0) return AnnotatedString(text)
    val end = symbolIndex + CHANNEL_TITLE_CLICK_HINT_SYMBOL.length
    return buildAnnotatedString {
        append(text.substring(0, symbolIndex))
        withStyle(
            SpanStyle(
                color = hintColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (titleSize.isSpecified) (titleSize.value - 2f).coerceAtLeast(8f).sp else TextUnit.Unspecified
            )
        ) {
            append(CHANNEL_TITLE_CLICK_HINT_SYMBOL)
        }
        if (end < text.length) append(text.substring(end))
    }
}
