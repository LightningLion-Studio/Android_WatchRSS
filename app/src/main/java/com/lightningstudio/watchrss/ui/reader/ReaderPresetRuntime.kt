package com.lightningstudio.watchrss.ui.reader

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.data.reader.ReaderBackgroundType
import com.lightningstudio.watchrss.data.reader.ReaderFontSynthesis
import com.lightningstudio.watchrss.data.reader.ReaderHyphenation
import com.lightningstudio.watchrss.data.reader.ReaderLineBreakMode
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.reader.ReaderRenderMode
import com.lightningstudio.watchrss.data.reader.ReaderTextAlignment
import com.lightningstudio.watchrss.data.reader.ReaderTextStyle
import com.lightningstudio.watchrss.data.reader.ReaderTypographyRole
import java.io.File
import java.util.LinkedHashMap

enum class ReaderTextRole {
    BODY,
    TITLE,
    SUBTITLE,
    QUOTE,
    CODE,
    LINK
}

data class ReaderPresetRuntime(
    val preset: ReaderPreset,
    val fontFile: (String?) -> File? = { null },
    val backgroundFile: (String?) -> File? = { null },
    val backgroundVideoFile: (String?) -> File? = { null }
)

data class ReaderChromeStyle(
    val backgroundColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val isDark: Boolean
)

fun ReaderPreset.readerChromeStyle(): ReaderChromeStyle {
    val backgroundColor = Color(background.colorArgb)
    return ReaderChromeStyle(
        backgroundColor = backgroundColor,
        contentColor = Color(body.colorArgb),
        accentColor = Color(accentColorArgb),
        isDark = backgroundColor.luminance() < 0.5f
    )
}

val LocalReaderPresetRuntime = staticCompositionLocalOf {
    ReaderPresetRuntime(ReaderPreset.fallback)
}

@Composable
fun ProvideReaderPreset(
    repository: ReaderPresetRepository,
    content: @Composable () -> Unit
) {
    val preset by repository.activePreset.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = preset,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile,
            backgroundVideoFile = repository::backgroundVideoFile
        ),
        content = content
    )
}

@Composable
fun readerTextStyle(role: ReaderTextRole): TextStyle {
    val runtime = LocalReaderPresetRuntime.current
    val spec = runtime.preset.styleFor(role)
    val file = runtime.fontFile(spec.fontAssetId)
    val filePath = file?.absolutePath
    val fileLength = file?.length() ?: 0L
    val fileModifiedAt = file?.lastModified() ?: 0L
    return remember(spec, filePath, fileLength, fileModifiedAt) {
        spec.toComposeTextStyle(file)
    }
}

@Composable
fun readerTextWidthMeasurer(role: ReaderTextRole): (String) -> Float {
    val runtime = LocalReaderPresetRuntime.current
    val spec = runtime.preset.styleFor(role)
    val file = runtime.fontFile(spec.fontAssetId)
    val filePath = file?.absolutePath
    val fileLength = file?.length() ?: 0L
    val fileModifiedAt = file?.lastModified() ?: 0L
    val density = LocalDensity.current
    val textSizePx = with(density) { spec.fontSizeSp.sp.toPx() }
    return remember(spec, filePath, fileLength, fileModifiedAt, textSizePx) {
        val typeface = file?.let { ReaderFontFamilyCache.getTypeface(it, spec) }
            ?: Typeface.create(
                Typeface.DEFAULT,
                spec.fontWeight.coerceIn(1, 1000),
                spec.italic
            )
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            letterSpacing = spec.letterSpacingEm
        }
        paint::measureText
    }
}

fun readerTypefaceFor(file: File?, style: ReaderTextStyle): Typeface =
    file?.let { ReaderFontFamilyCache.getTypeface(it, style) }
        ?: Typeface.create(
            Typeface.DEFAULT,
            style.fontWeight.coerceIn(1, 1000),
            style.italic
        )

private fun ReaderPreset.styleFor(role: ReaderTextRole): ReaderTextStyle = when (role) {
    ReaderTextRole.BODY -> body
    ReaderTextRole.TITLE -> resolvedStyle(ReaderTypographyRole.TITLE)
    ReaderTextRole.SUBTITLE -> resolvedStyle(ReaderTypographyRole.SUBTITLE)
    ReaderTextRole.QUOTE -> resolvedStyle(ReaderTypographyRole.QUOTE)
    ReaderTextRole.CODE -> resolvedStyle(ReaderTypographyRole.CODE)
    ReaderTextRole.LINK -> resolvedStyle(ReaderTypographyRole.LINK)
}

@Composable
fun ReaderBackgroundSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val runtime = LocalReaderPresetRuntime.current
    val background = runtime.preset.background
    val assetId = if (background.type == ReaderBackgroundType.VIDEO) {
        background.assetId
    } else {
        background.assetId
    }
    val file = runtime.backgroundFile(assetId)
    Box(
        modifier = modifier
            .background(Color(background.colorArgb))
            .clipToBounds()
    ) {
        if (background.type != ReaderBackgroundType.SOLID) {
            ReaderBackgroundMedia(
                file = file,
                video = if (background.type == ReaderBackgroundType.VIDEO)
                    runtime.backgroundVideoFile(assetId) else null,
                background = background
            )
        }
        if (background.overlayOpacity > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color(background.overlayColorArgb)
                            .copy(alpha = background.overlayOpacity)
                    )
            )
        }
        content()
    }
}

internal fun backgroundColorMatrix(brightness: Float, saturation: Float): ColorMatrix {
    val s = saturation.coerceIn(0f, 2f)
    val b = brightness.coerceIn(0f, 2f)
    val inverse = 1f - s
    val r = 0.213f * inverse
    val g = 0.715f * inverse
    val blue = 0.072f * inverse
    return ColorMatrix(
        floatArrayOf(
            (r + s) * b, g * b, blue * b, 0f, 0f,
            r * b, (g + s) * b, blue * b, 0f, 0f,
            r * b, g * b, (blue + s) * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun ReaderTextStyle.toComposeTextStyle(
    fontFile: File?
): TextStyle {
    val family = fontFile?.let { file -> ReaderFontFamilyCache.get(file, this) }
    return TextStyle(
        color = Color(colorArgb),
        fontFamily = family,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight(fontWeight.coerceIn(1, 1000)),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            underline && strikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            underline -> TextDecoration.Underline
            strikethrough -> TextDecoration.LineThrough
            else -> TextDecoration.None
        },
        fontSynthesis = if (fontSynthesis == ReaderFontSynthesis.DISABLED) {
            FontSynthesis.None
        } else {
            FontSynthesis.All
        },
        lineHeight = lineHeightEm.em,
        letterSpacing = letterSpacingEm.em,
        textAlign = when (alignment) {
            ReaderTextAlignment.START -> TextAlign.Start
            ReaderTextAlignment.CENTER -> TextAlign.Center
            ReaderTextAlignment.JUSTIFY -> TextAlign.Justify
        },
        lineBreak = when (lineBreakMode) {
            ReaderLineBreakMode.SYSTEM -> LineBreak.Unspecified
            ReaderLineBreakMode.SIMPLE -> LineBreak.Simple
            ReaderLineBreakMode.PARAGRAPH -> LineBreak.Paragraph
        },
        hyphens = if (hyphenation == ReaderHyphenation.AUTO) Hyphens.Auto else Hyphens.None,
        textMotion = when (renderMode) {
            ReaderRenderMode.SYSTEM -> null
            ReaderRenderMode.READABILITY -> TextMotion.Static
            ReaderRenderMode.LINEAR_SMOOTH -> TextMotion.Animated
        },
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}

private data class ReaderFontFamilyKey(
    val path: String,
    val length: Long,
    val modifiedAt: Long,
    val faceIndex: Int,
    val weight: Int,
    val italic: Boolean,
    val variationSettings: String
)

private data class ReaderFontResource(
    val typeface: Typeface,
    val family: FontFamily
)

private object ReaderFontFamilyCache {
    private const val MAX_ENTRIES = 32
    private val entries = object : LinkedHashMap<ReaderFontFamilyKey, ReaderFontResource?>(
        MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ReaderFontFamilyKey, ReaderFontResource?>
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(file: File, style: ReaderTextStyle): FontFamily? =
        getResource(file, style)?.family

    @Synchronized
    fun getTypeface(file: File, style: ReaderTextStyle): Typeface? =
        getResource(file, style)?.typeface

    private fun getResource(file: File, style: ReaderTextStyle): ReaderFontResource? {
        val key = ReaderFontFamilyKey(
            path = file.absolutePath,
            length = file.length(),
            modifiedAt = file.lastModified(),
            faceIndex = style.fontFaceIndex.coerceAtLeast(0),
            weight = style.fontWeight.coerceIn(1, 1000),
            italic = style.italic,
            variationSettings = style.variationSettings
        )
        if (entries.containsKey(key)) return entries[key]
        val resource = runCatching {
            val builder = Typeface.Builder(file)
                .setTtcIndex(key.faceIndex)
                .setWeight(key.weight)
                .setItalic(key.italic)
            key.variationSettings.takeIf(String::isNotBlank)
                ?.let(builder::setFontVariationSettings)
            val typeface = builder.build()
            ReaderFontResource(typeface, FontFamily(typeface))
        }.getOrNull()
        entries[key] = resource
        return resource
    }
}
