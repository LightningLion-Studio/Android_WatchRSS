package com.lightningstudio.watchrss.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ReaderPresetModelsTest {
    @Test
    fun legacyMigrationStyle_roundTrips() {
        val source = ReaderPreset.lightDefault(
            id = "migrated",
            name = "迁移设置",
            fontSizeSp = 18f
        ).copy(categoryTypographyEnabled = true)
        val decoded = ReaderPresetCodec.decode(ReaderPresetCodec.encode(source))

        assertEquals("migrated", decoded.id)
        assertEquals(18f, decoded.body.fontSizeSp)
        assertTrue(decoded.categoryTypographyEnabled)
        assertEquals(ReaderBackgroundType.SOLID, decoded.background.type)
        assertEquals(0xFFF2E5C9, decoded.codeBackgroundColorArgb)
    }

    @Test
    fun phoneCodeBackgroundField_roundTripsAndLegacyPresetGetsDerivedDefault() {
        val source = ReaderPreset.lightDefault(name = "手机同步预设").copy(
            codeBackgroundColorArgb = 0xFFF1E1B8
        )
        val encoded = JSONObject(ReaderPresetCodec.encode(source))

        assertEquals(
            0xFFF1E1B8,
            ReaderPresetCodec.decode(encoded.toString()).codeBackgroundColorArgb
        )

        encoded.remove("codeBackgroundColorArgb")
        assertEquals(
            0xFFF2E5C9,
            ReaderPresetCodec.decode(encoded.toString()).codeBackgroundColorArgb
        )
    }

    @Test
    fun livePhonePreview_canApplyCodeBackgroundChange() {
        val changed = ReaderPresetCodec.applyChanges(
            ReaderPreset.lightDefault(name = "实时预览"),
            JSONObject().put("codeBackgroundColorArgb", 0xFFEFD89A)
        )

        assertEquals(0xFFEFD89A, changed.codeBackgroundColorArgb)
    }

    @Test
    fun categoryTypographyLayersDefaultsAndCustomOverrides() {
        val preset = ReaderPreset.darkDefault(name = "测试").copy(
            body = ReaderTextStyle(fontSizeSp = 20f),
            title = ReaderTextStyleOverride(fontScale = 2f, fontWeight = 900)
        )

        assertEquals(31f, preset.resolvedStyle(ReaderTypographyRole.TITLE).fontSizeSp)
        assertEquals(
            40f,
            preset.copy(categoryTypographyEnabled = true)
                .resolvedStyle(ReaderTypographyRole.TITLE)
                .fontSizeSp
        )
    }

    @Test
    fun versionOnePresetEnablesCategoryTypographyDuringMigration() {
        val json = JSONObject(ReaderPresetCodec.encode(ReaderPreset.darkDefault())).apply {
            put("schemaVersion", 1)
            remove("categoryTypographyEnabled")
        }

        assertTrue(ReaderPresetCodec.decode(json.toString()).categoryTypographyEnabled)
    }

    @Test
    fun videoSegment_isClampedToSixtySeconds() {
        val preset = ReaderPreset.darkDefault().copy(
            background = ReaderBackground(
                type = ReaderBackgroundType.VIDEO,
                videoTrimStartMs = 7_000,
                videoTrimEndMs = 300_000
            )
        ).normalized()

        assertEquals(67_000, preset.background.videoTrimEndMs)
    }

    @Test
    fun activePresetIsNotPartOfProtocolModel() {
        val json = ReaderPresetCodec.encode(ReaderPreset.darkDefault())
        assertFalse(json.contains("activePreset"))
    }
}
