package com.lightningstudio.watchrss.data.reader

import android.content.Context
import androidx.room.withTransaction
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class ReaderThemeMode { LIGHT, DARK, SYSTEM }

data class ReaderPresetSelection(
    val mode: ReaderThemeMode,
    val lightPresetId: String?,
    val darkPresetId: String?,
    val darkFollowsLight: Boolean
)

class ReaderPresetRepository(
    context: Context,
    private val database: WatchRssDatabase,
    private val dao: ReaderPresetDao,
    private val deviceId: String,
    scope: CoroutineScope,
    val resourceStore: ReaderResourceStore = ReaderResourceStore(context)
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ACTIVE_PRESET_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val legacyActivePresetId = preferences.getString(ACTIVE_PRESET_KEY, null)
    private val selectionState = MutableStateFlow(
        ReaderPresetSelection(
            mode = preferences.getString(THEME_MODE_KEY, null)
                ?.let { runCatching { ReaderThemeMode.valueOf(it) }.getOrNull() }
                ?: ReaderThemeMode.DARK,
            lightPresetId = preferences.getString(LIGHT_PRESET_KEY, legacyActivePresetId),
            darkPresetId = preferences.getString(DARK_PRESET_KEY, null),
            darkFollowsLight = preferences.getBoolean(
                DARK_FOLLOWS_LIGHT_KEY,
                !preferences.contains(DARK_FOLLOWS_LIGHT_KEY)
            )
        )
    )
    private val scheduleState = MutableStateFlow(
        WatchReaderThemeSchedule(
            mode = preferences.getString(SCHEDULE_MODE_KEY, null)
                ?.let { runCatching { WatchThemeScheduleMode.valueOf(it) }.getOrNull() }
                ?: WatchThemeScheduleMode.FIXED_TIME,
            lightStartMinutes = preferences.getInt(LIGHT_START_MINUTES_KEY, 7 * 60),
            darkStartMinutes = preferences.getInt(DARK_START_MINUTES_KEY, 19 * 60),
            latitude = preferences.getString(LATITUDE_KEY, null)?.toDoubleOrNull(),
            longitude = preferences.getString(LONGITUDE_KEY, null)?.toDoubleOrNull(),
            locationUpdatedAt = preferences.getLong(LOCATION_UPDATED_AT_KEY, 0L)
        )
    )
    private val scheduledDark = MutableStateFlow(false)
    val selection: StateFlow<ReaderPresetSelection> = selectionState
    val schedule: StateFlow<WatchReaderThemeSchedule> = scheduleState

    val presetRecords: StateFlow<List<ReaderPresetEntity>> = dao.observeAllPresets()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val presets: StateFlow<List<ReaderPreset>> = presetRecords
        .map { records ->
            records.asSequence()
                .filterNot(ReaderPresetEntity::deleted)
                .mapNotNull { runCatching { ReaderPresetCodec.decode(it.payloadJson) }.getOrNull() }
                .sortedBy { it.name.lowercase() }
                .toList()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val fonts: StateFlow<List<ReaderFontAssetEntity>> = dao.observeAllFonts()
        .map { it.filterNot(ReaderFontAssetEntity::deleted) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val backgrounds: StateFlow<List<ReaderBackgroundAssetEntity>> = dao.observeAllBackgrounds()
        .map { it.filterNot(ReaderBackgroundAssetEntity::deleted) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activePreset: StateFlow<ReaderPreset> = combine(
        presets,
        selectionState,
        scheduledDark
    ) { all, selection, isScheduledDark ->
        val useDark = when (selection.mode) {
            ReaderThemeMode.LIGHT -> false
            ReaderThemeMode.DARK -> true
            ReaderThemeMode.SYSTEM -> isScheduledDark
        }
        val id = if (useDark && !selection.darkFollowsLight) {
            selection.darkPresetId
        } else {
            selection.lightPresetId
        }
        if (id == ReaderPreset.FALLBACK_ID) ReaderPreset.fallback
        else all.firstOrNull { it.id == id } ?: all.firstOrNull() ?: ReaderPreset.fallback
    }.stateIn(scope, SharingStarted.Eagerly, ReaderPreset.fallback)

    init {
        refreshScheduledDark()
        scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L + 50L)
                refreshScheduledDark()
            }
        }
    }

    suspend fun ensureSeeded(legacyDark: Boolean, legacyFontSizeSp: Int) {
        if (dao.allPresetRecords().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val migrated = if (legacyDark) {
            ReaderPreset.darkDefault(
                name = "迁移设置",
                fontSizeSp = legacyFontSizeSp.toFloat()
            )
        } else {
            ReaderPreset.lightDefault(
                name = "迁移设置",
                fontSizeSp = legacyFontSizeSp.toFloat()
            )
        }.copy(updatedAt = now, modifiedBy = deviceId)
        val opposite = if (legacyDark) {
            ReaderPreset.lightDefault()
        } else {
            ReaderPreset.darkDefault()
        }.copy(updatedAt = now + 1, modifiedBy = deviceId)
        dao.upsertPresets(listOf(migrated.toEntity(), opposite.toEntity()))
        updateSelection(
            ReaderPresetSelection(
                mode = legacyReaderThemeMode(legacyDark),
                lightPresetId = if (legacyDark) opposite.id else migrated.id,
                darkPresetId = if (legacyDark) migrated.id else opposite.id,
                darkFollowsLight = false
            )
        )
    }

    suspend fun savePreset(draft: ReaderPreset, applyAfterSave: Boolean = false): ReaderPreset {
        val normalized = draft.normalized()
        require(normalized.name.isNotBlank()) { "预设名称不能为空" }
        require(
            presets.value.none {
                it.id != normalized.id &&
                    canonicalPresetName(it.name) == canonicalPresetName(normalized.name)
            } &&
                dao.countNameConflicts(normalized.name, normalized.id) == 0
        ) {
            "预设名称已存在"
        }
        val saved = normalized.copy(
            updatedAt = maxOf(System.currentTimeMillis(), normalized.updatedAt + 1L),
            modifiedBy = deviceId,
            deleted = false
        )
        dao.upsertPreset(saved.toEntity())
        if (applyAfterSave) setActivePreset(saved.id)
        return saved
    }

    suspend fun saveAsNew(draft: ReaderPreset, name: String): ReaderPreset =
        savePreset(
            draft.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueName(name),
                updatedAt = 0L,
                modifiedBy = deviceId,
                deleted = false
            )
        )

    suspend fun duplicate(id: String): ReaderPreset {
        val original = requireNotNull(preset(id)) { "预设不存在" }
        return saveAsNew(original, uniqueName("${original.name} 副本"))
    }

    suspend fun rename(id: String, name: String): ReaderPreset {
        val original = requireNotNull(preset(id)) { "预设不存在" }
        return savePreset(original.copy(name = name))
    }

    suspend fun deletePreset(id: String) {
        val original = dao.presetById(id) ?: return
        val timestamp = maxOf(System.currentTimeMillis(), original.updatedAt + 1)
        database.withTransaction {
            val tombstone = runCatching {
                ReaderPresetCodec.decode(original.payloadJson)
            }.getOrElse {
                ReaderPreset(id = id, name = original.name)
            }.copy(updatedAt = timestamp, modifiedBy = deviceId, deleted = true)
            dao.upsertPreset(tombstone.toEntity())
            dao.upsertDeletion(
                ReaderDeletionEntity(
                    kind = DELETION_KIND_PRESET,
                    entityId = id,
                    deletedAt = timestamp,
                    deletedBy = deviceId
                )
            )
        }
        val current = selectionState.value
        updateSelection(
            current.copy(
                lightPresetId = current.lightPresetId
                    .takeUnless { it == id }
                    ?: ReaderPreset.FALLBACK_ID,
                darkPresetId = current.darkPresetId.takeUnless { it == id },
                darkFollowsLight = current.darkFollowsLight || current.darkPresetId == id
            )
        )
    }

    fun setActivePreset(id: String?) {
        updateSelection(
            selectionState.value.copy(
                lightPresetId = validPresetId(id),
                darkPresetId = null,
                darkFollowsLight = true
            )
        )
    }

    fun setThemeMode(mode: ReaderThemeMode) {
        updateSelection(selectionState.value.copy(mode = mode))
    }

    fun setLightPreset(id: String?) {
        updateSelection(selectionState.value.copy(lightPresetId = validPresetId(id)))
    }

    fun setDarkPreset(id: String?) {
        updateSelection(
            selectionState.value.copy(
                darkPresetId = validPresetId(id),
                darkFollowsLight = id == null
            )
        )
    }

    fun setScheduleMode(mode: WatchThemeScheduleMode) {
        updateSchedule(scheduleState.value.copy(mode = mode))
    }

    fun setFixedLightStart(minutes: Int) {
        updateSchedule(scheduleState.value.copy(lightStartMinutes = minutes.coerceIn(0, 1439)))
    }

    fun setFixedDarkStart(minutes: Int) {
        updateSchedule(scheduleState.value.copy(darkStartMinutes = minutes.coerceIn(0, 1439)))
    }

    fun setSunLocation(latitude: Double, longitude: Double) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "位置坐标无效"
        }
        updateSchedule(
            scheduleState.value.copy(
                mode = WatchThemeScheduleMode.SUNRISE_SUNSET,
                latitude = latitude,
                longitude = longitude,
                locationUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    fun todaySunTimes(): WatchSunTimes? {
        val current = scheduleState.value
        val latitude = current.latitude ?: return null
        val longitude = current.longitude ?: return null
        return WatchSunCalculator.calculate(
            LocalDate.now(),
            latitude,
            longitude,
            ZoneId.systemDefault()
        )
    }

    private fun updateSchedule(schedule: WatchReaderThemeSchedule) {
        preferences.edit()
            .putString(SCHEDULE_MODE_KEY, schedule.mode.name)
            .putInt(LIGHT_START_MINUTES_KEY, schedule.lightStartMinutes)
            .putInt(DARK_START_MINUTES_KEY, schedule.darkStartMinutes)
            .putString(LATITUDE_KEY, schedule.latitude?.toString())
            .putString(LONGITUDE_KEY, schedule.longitude?.toString())
            .putLong(LOCATION_UPDATED_AT_KEY, schedule.locationUpdatedAt)
            .apply()
        scheduleState.value = schedule
        refreshScheduledDark()
    }

    private fun refreshScheduledDark() {
        val now = LocalTime.now()
        scheduledDark.value = scheduleState.value.isDarkAt(
            date = LocalDate.now(),
            time = now,
            zoneId = ZoneId.systemDefault()
        )
    }

    private fun validPresetId(id: String?): String? =
        id?.takeIf { candidate ->
            candidate == ReaderPreset.FALLBACK_ID || presets.value.any { it.id == candidate }
        }

    private fun updateSelection(selection: ReaderPresetSelection) {
        preferences.edit()
            .putString(THEME_MODE_KEY, selection.mode.name)
            .putString(LIGHT_PRESET_KEY, selection.lightPresetId)
            .putString(DARK_PRESET_KEY, selection.darkPresetId)
            .putBoolean(DARK_FOLLOWS_LIGHT_KEY, selection.darkFollowsLight)
            .remove(ACTIVE_PRESET_KEY)
            .apply()
        selectionState.value = selection
    }

    fun fontFile(assetId: String?): File? {
        val record = fonts.value.firstOrNull { it.id == assetId } ?: return null
        return resourceStore.fontFile(record.fileName)
    }

    fun backgroundFile(assetId: String?): File? {
        val record = backgrounds.value.firstOrNull { it.id == assetId } ?: return null
        val variant = if (record.kind == ReaderBackgroundType.VIDEO.name) {
            record.watchPosterFileName()
        } else {
            record.watchVariantFileName()
        }
        return variant?.let(resourceStore::variantFile)
            ?: resourceStore.backgroundFile(record.masterFileName)
    }

    suspend fun preset(id: String): ReaderPreset? =
        dao.presetById(id)
            ?.takeUnless(ReaderPresetEntity::deleted)
            ?.let { runCatching { ReaderPresetCodec.decode(it.payloadJson) }.getOrNull() }

    suspend fun mergeRemote(
        presets: List<ReaderPresetEntity>,
        fonts: List<ReaderFontAssetEntity>,
        backgrounds: List<ReaderBackgroundAssetEntity>,
        deletions: List<ReaderDeletionEntity>
    ) {
        database.withTransaction {
            presets.forEach { incoming ->
                val local = dao.presetById(incoming.id)
                if (local == null || incoming.winsOver(local)) dao.upsertPreset(incoming)
            }
            fonts.forEach { incoming ->
                val local = dao.fontById(incoming.id)
                if (local == null || incoming.winsOver(local)) dao.upsertFont(incoming)
            }
            backgrounds.forEach { incoming ->
                val local = dao.backgroundById(incoming.id)
                if (local == null || incoming.winsOver(local)) dao.upsertBackground(incoming)
            }
            dao.upsertDeletions(deletions)
        }
    }

    suspend fun exportSnapshot(): ReaderPresetSnapshot = ReaderPresetSnapshot(
        presets = dao.allPresetRecords(),
        fonts = dao.allFontRecords(),
        backgrounds = dao.allBackgroundRecords(),
        deletions = dao.allDeletions()
    )

    private suspend fun uniqueName(candidate: String): String {
        val base = candidate.trim().take(ReaderPreset.MAX_PRESET_NAME_LENGTH).ifBlank { "未命名预设" }
        if (dao.countNameConflicts(base, "") == 0) return base
        var suffix = 2
        while (true) {
            val suffixText = " $suffix"
            val value = base.take(ReaderPreset.MAX_PRESET_NAME_LENGTH - suffixText.length) + suffixText
            if (dao.countNameConflicts(value, "") == 0) return value
            suffix += 1
        }
    }

    private fun canonicalPresetName(value: String): String =
        value.filterNot(Char::isWhitespace).lowercase()

    companion object {
        private const val ACTIVE_PRESET_PREFERENCES = "reader_preset_state"
        private const val ACTIVE_PRESET_KEY = "active_preset_id"
        private const val THEME_MODE_KEY = "reader_theme_mode"
        private const val LIGHT_PRESET_KEY = "light_preset_id"
        private const val DARK_PRESET_KEY = "dark_preset_id"
        private const val DARK_FOLLOWS_LIGHT_KEY = "dark_follows_light"
        private const val SCHEDULE_MODE_KEY = "watch_theme_schedule_mode"
        private const val LIGHT_START_MINUTES_KEY = "watch_light_start_minutes"
        private const val DARK_START_MINUTES_KEY = "watch_dark_start_minutes"
        private const val LATITUDE_KEY = "watch_sun_latitude"
        private const val LONGITUDE_KEY = "watch_sun_longitude"
        private const val LOCATION_UPDATED_AT_KEY = "watch_sun_location_updated_at"
        const val DELETION_KIND_PRESET = "preset"
    }
}

internal fun legacyReaderThemeMode(legacyDark: Boolean): ReaderThemeMode =
    if (legacyDark) ReaderThemeMode.DARK else ReaderThemeMode.LIGHT

data class ReaderPresetSnapshot(
    val presets: List<ReaderPresetEntity>,
    val fonts: List<ReaderFontAssetEntity>,
    val backgrounds: List<ReaderBackgroundAssetEntity>,
    val deletions: List<ReaderDeletionEntity>
)

fun ReaderPreset.toEntity(): ReaderPresetEntity {
    val safe = normalized()
    return ReaderPresetEntity(
        id = safe.id,
        name = safe.name,
        payloadJson = ReaderPresetCodec.encode(safe),
        updatedAt = safe.updatedAt,
        modifiedBy = safe.modifiedBy,
        deleted = safe.deleted
    )
}

private fun ReaderPresetEntity.winsOver(local: ReaderPresetEntity): Boolean =
    updatedAt > local.updatedAt ||
        (updatedAt == local.updatedAt && deleted && !local.deleted) ||
        (updatedAt == local.updatedAt && deleted == local.deleted && modifiedBy > local.modifiedBy)

private fun ReaderFontAssetEntity.winsOver(local: ReaderFontAssetEntity): Boolean =
    updatedAt > local.updatedAt ||
        (updatedAt == local.updatedAt && deleted && !local.deleted) ||
        (updatedAt == local.updatedAt && deleted == local.deleted && modifiedBy > local.modifiedBy)

private fun ReaderBackgroundAssetEntity.winsOver(local: ReaderBackgroundAssetEntity): Boolean =
    updatedAt > local.updatedAt ||
        (updatedAt == local.updatedAt && deleted && !local.deleted) ||
        (updatedAt == local.updatedAt && deleted == local.deleted && modifiedBy > local.modifiedBy)

private fun ReaderBackgroundAssetEntity.watchVariantFileName(): String? {
    return runCatching {
        org.json.JSONObject(variantsJson).optJSONObject("watch")?.optString("fileName")
    }.getOrNull()?.takeIf(String::isNotBlank)
}

private fun ReaderBackgroundAssetEntity.watchPosterFileName(): String? {
    return runCatching {
        org.json.JSONObject(variantsJson).optJSONObject("watchPoster")?.optString("fileName")
    }.getOrNull()?.takeIf(String::isNotBlank)
}
