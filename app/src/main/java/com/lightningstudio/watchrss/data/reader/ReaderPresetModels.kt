package com.lightningstudio.watchrss.data.reader

import org.json.JSONObject
import java.util.UUID

enum class ReaderRenderMode { SYSTEM, READABILITY, LINEAR_SMOOTH }
enum class ReaderFontSynthesis { ENABLED, DISABLED }
enum class ReaderTextAlignment { START, CENTER, JUSTIFY }
enum class ReaderLineBreakMode { SYSTEM, SIMPLE, PARAGRAPH }
enum class ReaderHyphenation { NONE, AUTO }
enum class ReaderBackgroundType { SOLID, IMAGE, VIDEO }
enum class ReaderBackgroundFit { CROP, FIT, FILL }
enum class ReaderTypographyRole { TITLE, SUBTITLE, QUOTE, CODE, LINK }

data class ReaderTextStyle(
    val fontAssetId: String? = null,
    val fontFaceIndex: Int = 0,
    val variationSettings: String = "",
    val fontSizeSp: Float = 14f,
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val colorArgb: Long = 0xFFF2F2F2,
    val lineHeightEm: Float = 1.35f,
    val letterSpacingEm: Float = 0f,
    val paragraphSpacingDp: Float = 6f,
    val firstLineIndentEm: Float = 0f,
    val horizontalPaddingDp: Float = 14f,
    val alignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val lineBreakMode: ReaderLineBreakMode = ReaderLineBreakMode.PARAGRAPH,
    val hyphenation: ReaderHyphenation = ReaderHyphenation.NONE,
    val renderMode: ReaderRenderMode = ReaderRenderMode.READABILITY,
    val fontSynthesis: ReaderFontSynthesis = ReaderFontSynthesis.ENABLED
)

data class ReaderTextStyleOverride(
    val fontAssetId: String? = null,
    val useOwnFont: Boolean = false,
    val fontFaceIndex: Int? = null,
    val variationSettings: String? = null,
    val fontSizeSp: Float? = null,
    val fontScale: Float? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val colorArgb: Long? = null,
    val lineHeightEm: Float? = null,
    val letterSpacingEm: Float? = null,
    val alignment: ReaderTextAlignment? = null
) {
    fun resolve(base: ReaderTextStyle): ReaderTextStyle = base.copy(
        fontAssetId = if (useOwnFont) fontAssetId else base.fontAssetId,
        fontFaceIndex = fontFaceIndex ?: base.fontFaceIndex,
        variationSettings = variationSettings ?: base.variationSettings,
        fontSizeSp = fontSizeSp ?: fontScale?.let { base.fontSizeSp * it } ?: base.fontSizeSp,
        fontWeight = fontWeight ?: base.fontWeight,
        italic = italic ?: base.italic,
        underline = underline ?: base.underline,
        strikethrough = strikethrough ?: base.strikethrough,
        colorArgb = colorArgb ?: base.colorArgb,
        lineHeightEm = lineHeightEm ?: base.lineHeightEm,
        letterSpacingEm = letterSpacingEm ?: base.letterSpacingEm,
        alignment = alignment ?: base.alignment
    )

    fun resolve(
        base: ReaderTextStyle,
        categoryDefault: ReaderTextStyleOverride
    ): ReaderTextStyle {
        val categoryStyle = categoryDefault.resolve(base)
        return categoryStyle.copy(
            fontAssetId = if (useOwnFont) fontAssetId else categoryStyle.fontAssetId,
            fontFaceIndex = fontFaceIndex ?: categoryStyle.fontFaceIndex,
            variationSettings = variationSettings ?: categoryStyle.variationSettings,
            fontSizeSp = fontSizeSp
                ?: fontScale?.let { base.fontSizeSp * it }
                ?: categoryStyle.fontSizeSp,
            fontWeight = fontWeight ?: categoryStyle.fontWeight,
            italic = italic ?: categoryStyle.italic,
            underline = underline ?: categoryStyle.underline,
            strikethrough = strikethrough ?: categoryStyle.strikethrough,
            colorArgb = colorArgb ?: categoryStyle.colorArgb,
            lineHeightEm = lineHeightEm ?: categoryStyle.lineHeightEm,
            letterSpacingEm = letterSpacingEm ?: categoryStyle.letterSpacingEm,
            alignment = alignment ?: categoryStyle.alignment
        )
    }

    fun normalized(): ReaderTextStyleOverride = copy(
        fontFaceIndex = fontFaceIndex?.coerceAtLeast(0),
        fontSizeSp = fontSizeSp?.coerceIn(10f, 64f),
        fontScale = fontScale?.coerceIn(0.5f, 2.5f),
        fontWeight = fontWeight?.coerceIn(100, 900),
        lineHeightEm = lineHeightEm?.coerceIn(0.8f, 3f),
        letterSpacingEm = letterSpacingEm?.coerceIn(-0.1f, 0.5f)
    )
}

data class ReaderBackground(
    val type: ReaderBackgroundType = ReaderBackgroundType.SOLID,
    val colorArgb: Long = 0xFF000000,
    val assetId: String? = null,
    val fit: ReaderBackgroundFit = ReaderBackgroundFit.CROP,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
    val rotationDegrees: Float = 0f,
    val blurDp: Float = 0f,
    val brightness: Float = 1f,
    val saturation: Float = 1f,
    val overlayColorArgb: Long = 0xFF000000,
    val overlayOpacity: Float = 0f,
    val videoTrimStartMs: Long = 0L,
    val videoTrimEndMs: Long = 60_000L,
    val videoSpeed: Float = 1f,
    val loop: Boolean = true,
    val posterAssetId: String? = null
)

data class ReaderPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val body: ReaderTextStyle = ReaderTextStyle(),
    val categoryTypographyEnabled: Boolean = false,
    val title: ReaderTextStyleOverride = ReaderTextStyleOverride(),
    val subtitle: ReaderTextStyleOverride = ReaderTextStyleOverride(),
    val quote: ReaderTextStyleOverride = ReaderTextStyleOverride(),
    val code: ReaderTextStyleOverride = ReaderTextStyleOverride(),
    val link: ReaderTextStyleOverride = ReaderTextStyleOverride(),
    val background: ReaderBackground = ReaderBackground(),
    val codeBackgroundColorArgb: Long = defaultReaderCodeBackgroundColorArgb(0xFF000000),
    val accentColorArgb: Long = 0xFFFF5A36,
    val updatedAt: Long = System.currentTimeMillis(),
    val modifiedBy: String = "",
    val deleted: Boolean = false
) {
    fun normalized(): ReaderPreset = copy(
        name = name.trim().take(MAX_PRESET_NAME_LENGTH),
        body = body.copy(
            fontSizeSp = body.fontSizeSp.coerceIn(10f, 64f),
            fontWeight = body.fontWeight.coerceIn(100, 900),
            lineHeightEm = body.lineHeightEm.coerceIn(0.8f, 3f),
            letterSpacingEm = body.letterSpacingEm.coerceIn(-0.1f, 0.5f),
            paragraphSpacingDp = body.paragraphSpacingDp.coerceIn(0f, 64f),
            firstLineIndentEm = body.firstLineIndentEm.coerceIn(0f, 4f),
            horizontalPaddingDp = body.horizontalPaddingDp.coerceIn(0f, 64f)
        ),
        title = title.normalized(),
        subtitle = subtitle.normalized(),
        quote = quote.normalized(),
        code = code.normalized(),
        link = link.normalized(),
        background = background.copy(
            focusX = background.focusX.coerceIn(0f, 1f),
            focusY = background.focusY.coerceIn(0f, 1f),
            zoom = background.zoom.coerceIn(0.25f, 8f),
            rotationDegrees = background.rotationDegrees.coerceIn(-180f, 180f),
            blurDp = background.blurDp.coerceIn(0f, 64f),
            brightness = background.brightness.coerceIn(0f, 2f),
            saturation = background.saturation.coerceIn(0f, 2f),
            overlayOpacity = background.overlayOpacity.coerceIn(0f, 1f),
            videoTrimStartMs = background.videoTrimStartMs.coerceAtLeast(0L),
            videoTrimEndMs = background.videoTrimEndMs
                .coerceAtLeast(background.videoTrimStartMs)
                .coerceAtMost(background.videoTrimStartMs + MAX_VIDEO_SEGMENT_MS),
            videoSpeed = background.videoSpeed.coerceIn(0.25f, 4f)
        )
    )

    fun categoryDefault(role: ReaderTypographyRole): ReaderTextStyleOverride = when (role) {
        ReaderTypographyRole.TITLE -> ReaderTextStyleOverride(fontScale = 1.55f, fontWeight = 700)
        ReaderTypographyRole.SUBTITLE -> ReaderTextStyleOverride(
            fontScale = 1.12f,
            colorArgb = body.colorArgb.withAlpha(0xCB)
        )
        ReaderTypographyRole.QUOTE -> ReaderTextStyleOverride(
            fontScale = 1f,
            colorArgb = body.colorArgb.withAlpha(0xD0)
        )
        ReaderTypographyRole.CODE -> ReaderTextStyleOverride(fontScale = 0.9f)
        ReaderTypographyRole.LINK -> ReaderTextStyleOverride(
            fontScale = 1f,
            colorArgb = accentColorArgb
        )
    }

    fun resolvedStyle(role: ReaderTypographyRole): ReaderTextStyle {
        val defaults = categoryDefault(role)
        val custom = when (role) {
            ReaderTypographyRole.TITLE -> title
            ReaderTypographyRole.SUBTITLE -> subtitle
            ReaderTypographyRole.QUOTE -> quote
            ReaderTypographyRole.CODE -> code
            ReaderTypographyRole.LINK -> link
        }
        return if (categoryTypographyEnabled) {
            custom.resolve(body, defaults)
        } else {
            defaults.resolve(body)
        }
    }

    companion object {
        const val MAX_PRESET_NAME_LENGTH = 40
        const val MAX_VIDEO_SEGMENT_MS = 60_000L
        const val FALLBACK_ID = "reader-system-fallback"

        fun darkDefault(
            id: String = UUID.randomUUID().toString(),
            name: String = "默认深色",
            fontSizeSp: Float = 14f
        ) = ReaderPreset(
            id = id,
            name = name,
            body = ReaderTextStyle(fontSizeSp = fontSizeSp),
            background = ReaderBackground(colorArgb = 0xFF000000)
        )

        fun lightDefault(
            id: String = UUID.randomUUID().toString(),
            name: String = "默认浅色",
            fontSizeSp: Float = 14f
        ) = ReaderPreset(
            id = id,
            name = name,
            body = ReaderTextStyle(fontSizeSp = fontSizeSp, colorArgb = 0xFF221F1B),
            background = ReaderBackground(colorArgb = 0xFFF8F3EC),
            codeBackgroundColorArgb = defaultReaderCodeBackgroundColorArgb(0xFFF8F3EC),
            accentColorArgb = 0xFFD94720
        )

        val fallback = darkDefault(id = FALLBACK_ID, name = "安全默认")
    }
}

fun defaultReaderCodeBackgroundColorArgb(backgroundColorArgb: Long): Long {
    val warmYellow = 0xFFD8A52BL
    fun blendChannel(shift: Int): Long {
        val background = (backgroundColorArgb shr shift) and 0xFF
        val warm = (warmYellow shr shift) and 0xFF
        return (background * 82 + warm * 18 + 50) / 100
    }
    return 0xFF000000L or
        (blendChannel(16) shl 16) or
        (blendChannel(8) shl 8) or
        blendChannel(0)
}

object ReaderPresetCodec {
    const val SCHEMA_VERSION = 2

    fun encode(preset: ReaderPreset): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", preset.id)
        put("name", preset.name)
        put("body", preset.body.toJson())
        put("categoryTypographyEnabled", preset.categoryTypographyEnabled)
        put("title", preset.title.toJson())
        put("subtitle", preset.subtitle.toJson())
        put("quote", preset.quote.toJson())
        put("code", preset.code.toJson())
        put("link", preset.link.toJson())
        put("background", preset.background.toJson())
        put("codeBackgroundColorArgb", preset.codeBackgroundColorArgb)
        put("accentColorArgb", preset.accentColorArgb)
        put("updatedAt", preset.updatedAt)
        put("modifiedBy", preset.modifiedBy)
        put("deleted", preset.deleted)
    }.toString()

    fun decode(raw: String): ReaderPreset {
        val json = JSONObject(raw)
        val schemaVersion = json.optInt("schemaVersion", 1)
        require(schemaVersion <= SCHEMA_VERSION) {
            "不支持的阅读器预设版本"
        }
        val background = json.optJSONObject("background").toBackground()
        return ReaderPreset(
            id = json.getString("id"),
            name = json.getString("name"),
            body = json.optJSONObject("body").toTextStyle(),
            categoryTypographyEnabled = if (schemaVersion < 2) {
                true
            } else {
                json.optBoolean("categoryTypographyEnabled")
            },
            title = json.optJSONObject("title").toOverride(),
            subtitle = json.optJSONObject("subtitle").toOverride(),
            quote = json.optJSONObject("quote").toOverride(),
            code = json.optJSONObject("code").toOverride(),
            link = json.optJSONObject("link").toOverride(),
            background = background,
            codeBackgroundColorArgb = json.optLong(
                "codeBackgroundColorArgb",
                defaultReaderCodeBackgroundColorArgb(background.colorArgb)
            ),
            accentColorArgb = json.optLong("accentColorArgb", 0xFFFF5A36),
            updatedAt = json.optLong("updatedAt"),
            modifiedBy = json.optString("modifiedBy"),
            deleted = json.optBoolean("deleted")
        ).normalized()
    }

    fun applyChanges(base: ReaderPreset, changes: JSONObject): ReaderPreset {
        var merged = base
        changes.keys().forEach { key ->
            merged = when (key) {
                "schemaVersion" -> {
                    require(changes.getInt(key) <= SCHEMA_VERSION) {
                        "不支持的阅读器预设版本"
                    }
                    merged
                }
                "id" -> merged.copy(id = changes.getString(key))
                "name" -> merged.copy(name = changes.getString(key))
                "body" -> merged.copy(body = changes.getJSONObject(key).toTextStyle())
                "categoryTypographyEnabled" -> merged.copy(
                    categoryTypographyEnabled = changes.getBoolean(key)
                )
                "title" -> merged.copy(title = changes.getJSONObject(key).toOverride())
                "subtitle" -> merged.copy(subtitle = changes.getJSONObject(key).toOverride())
                "quote" -> merged.copy(quote = changes.getJSONObject(key).toOverride())
                "code" -> merged.copy(code = changes.getJSONObject(key).toOverride())
                "link" -> merged.copy(link = changes.getJSONObject(key).toOverride())
                "background" -> merged.copy(
                    background = changes.getJSONObject(key).toBackground()
                )
                "codeBackgroundColorArgb" -> merged.copy(
                    codeBackgroundColorArgb = changes.getLong(key)
                )
                "accentColorArgb" -> merged.copy(accentColorArgb = changes.getLong(key))
                "updatedAt" -> merged.copy(updatedAt = changes.getLong(key))
                "modifiedBy" -> merged.copy(modifiedBy = changes.getString(key))
                "deleted" -> merged.copy(deleted = changes.getBoolean(key))
                else -> merged
            }
        }
        return merged.normalized()
    }
}

private fun Long.withAlpha(alpha: Int): Long =
    (this and 0x00FFFFFFL) or ((alpha.coerceIn(0, 255).toLong()) shl 24)

private fun ReaderTextStyle.toJson() = JSONObject().apply {
    nullable("fontAssetId", fontAssetId)
    put("fontFaceIndex", fontFaceIndex)
    put("variationSettings", variationSettings)
    put("fontSizeSp", fontSizeSp.toDouble())
    put("fontWeight", fontWeight)
    put("italic", italic)
    put("underline", underline)
    put("strikethrough", strikethrough)
    put("colorArgb", colorArgb)
    put("lineHeightEm", lineHeightEm.toDouble())
    put("letterSpacingEm", letterSpacingEm.toDouble())
    put("paragraphSpacingDp", paragraphSpacingDp.toDouble())
    put("firstLineIndentEm", firstLineIndentEm.toDouble())
    put("horizontalPaddingDp", horizontalPaddingDp.toDouble())
    put("alignment", alignment.name)
    put("lineBreakMode", lineBreakMode.name)
    put("hyphenation", hyphenation.name)
    put("renderMode", renderMode.name)
    put("fontSynthesis", fontSynthesis.name)
}

private fun JSONObject?.toTextStyle(): ReaderTextStyle {
    val j = this ?: return ReaderTextStyle()
    return ReaderTextStyle(
        fontAssetId = j.stringOrNull("fontAssetId"),
        fontFaceIndex = j.optInt("fontFaceIndex"),
        variationSettings = j.optString("variationSettings"),
        fontSizeSp = j.optDouble("fontSizeSp", 14.0).toFloat(),
        fontWeight = j.optInt("fontWeight", 400),
        italic = j.optBoolean("italic"),
        underline = j.optBoolean("underline"),
        strikethrough = j.optBoolean("strikethrough"),
        colorArgb = j.optLong("colorArgb", 0xFFF2F2F2),
        lineHeightEm = j.optDouble("lineHeightEm", 1.35).toFloat(),
        letterSpacingEm = j.optDouble("letterSpacingEm").toFloat(),
        paragraphSpacingDp = j.optDouble("paragraphSpacingDp", 6.0).toFloat(),
        firstLineIndentEm = j.optDouble("firstLineIndentEm").toFloat(),
        horizontalPaddingDp = j.optDouble("horizontalPaddingDp", 14.0).toFloat(),
        alignment = j.enum("alignment", ReaderTextAlignment.START),
        lineBreakMode = j.enum("lineBreakMode", ReaderLineBreakMode.PARAGRAPH),
        hyphenation = j.enum("hyphenation", ReaderHyphenation.NONE),
        renderMode = j.enum("renderMode", ReaderRenderMode.READABILITY),
        fontSynthesis = j.enum("fontSynthesis", ReaderFontSynthesis.ENABLED)
    )
}

private fun ReaderTextStyleOverride.toJson() = JSONObject().apply {
    nullable("fontAssetId", fontAssetId)
    put("useOwnFont", useOwnFont)
    nullable("fontFaceIndex", fontFaceIndex)
    nullable("variationSettings", variationSettings)
    nullable("fontSizeSp", fontSizeSp?.toDouble())
    nullable("fontScale", fontScale?.toDouble())
    nullable("fontWeight", fontWeight)
    nullable("italic", italic)
    nullable("underline", underline)
    nullable("strikethrough", strikethrough)
    nullable("colorArgb", colorArgb)
    nullable("lineHeightEm", lineHeightEm?.toDouble())
    nullable("letterSpacingEm", letterSpacingEm?.toDouble())
    nullable("alignment", alignment?.name)
}

private fun JSONObject?.toOverride(): ReaderTextStyleOverride {
    val j = this ?: return ReaderTextStyleOverride()
    return ReaderTextStyleOverride(
        fontAssetId = j.stringOrNull("fontAssetId"),
        useOwnFont = j.optBoolean("useOwnFont"),
        fontFaceIndex = j.intOrNull("fontFaceIndex"),
        variationSettings = j.stringOrNull("variationSettings"),
        fontSizeSp = j.doubleOrNull("fontSizeSp")?.toFloat(),
        fontScale = j.doubleOrNull("fontScale")?.toFloat(),
        fontWeight = j.intOrNull("fontWeight"),
        italic = j.booleanOrNull("italic"),
        underline = j.booleanOrNull("underline"),
        strikethrough = j.booleanOrNull("strikethrough"),
        colorArgb = j.longOrNull("colorArgb"),
        lineHeightEm = j.doubleOrNull("lineHeightEm")?.toFloat(),
        letterSpacingEm = j.doubleOrNull("letterSpacingEm")?.toFloat(),
        alignment = j.stringOrNull("alignment")?.let {
            runCatching { ReaderTextAlignment.valueOf(it) }.getOrNull()
        }
    )
}

private fun ReaderBackground.toJson() = JSONObject().apply {
    put("type", type.name)
    put("colorArgb", colorArgb)
    nullable("assetId", assetId)
    put("fit", fit.name)
    put("focusX", focusX.toDouble())
    put("focusY", focusY.toDouble())
    put("zoom", zoom.toDouble())
    put("rotationDegrees", rotationDegrees.toDouble())
    put("blurDp", blurDp.toDouble())
    put("brightness", brightness.toDouble())
    put("saturation", saturation.toDouble())
    put("overlayColorArgb", overlayColorArgb)
    put("overlayOpacity", overlayOpacity.toDouble())
    put("videoTrimStartMs", videoTrimStartMs)
    put("videoTrimEndMs", videoTrimEndMs)
    put("videoSpeed", videoSpeed.toDouble())
    put("loop", loop)
    nullable("posterAssetId", posterAssetId)
}

private fun JSONObject?.toBackground(): ReaderBackground {
    val j = this ?: return ReaderBackground()
    return ReaderBackground(
        type = j.enum("type", ReaderBackgroundType.SOLID),
        colorArgb = j.optLong("colorArgb", 0xFF000000),
        assetId = j.stringOrNull("assetId"),
        fit = j.enum("fit", ReaderBackgroundFit.CROP),
        focusX = j.optDouble("focusX", 0.5).toFloat(),
        focusY = j.optDouble("focusY", 0.5).toFloat(),
        zoom = j.optDouble("zoom", 1.0).toFloat(),
        rotationDegrees = j.optDouble("rotationDegrees").toFloat(),
        blurDp = j.optDouble("blurDp").toFloat(),
        brightness = j.optDouble("brightness", 1.0).toFloat(),
        saturation = j.optDouble("saturation", 1.0).toFloat(),
        overlayColorArgb = j.optLong("overlayColorArgb", 0xFF000000),
        overlayOpacity = j.optDouble("overlayOpacity").toFloat(),
        videoTrimStartMs = j.optLong("videoTrimStartMs"),
        videoTrimEndMs = j.optLong("videoTrimEndMs", 60_000L),
        videoSpeed = j.optDouble("videoSpeed", 1.0).toFloat(),
        loop = j.optBoolean("loop", true),
        posterAssetId = j.stringOrNull("posterAssetId")
    )
}

private fun JSONObject.nullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.intOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else getInt(name)

private fun JSONObject.longOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else getLong(name)

private fun JSONObject.doubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)

private fun JSONObject.booleanOrNull(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else getBoolean(name)

private inline fun <reified T : Enum<T>> JSONObject.enum(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(optString(name)) }.getOrDefault(fallback)
