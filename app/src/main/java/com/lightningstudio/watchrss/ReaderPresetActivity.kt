package com.lightningstudio.watchrss

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.lightningstudio.watchrss.ui.components.DownloadPhoneAppButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.reader.ReaderPresetSelection
import com.lightningstudio.watchrss.data.reader.ReaderThemeMode
import com.lightningstudio.watchrss.data.reader.WatchReaderThemeSchedule
import com.lightningstudio.watchrss.data.reader.WatchThemeScheduleMode
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import java.time.format.DateTimeFormatter

class ReaderPresetActivity : BaseWatchActivity() {
    private val repository by lazy {
        (application as WatchRssApplication).container.readerPresetRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent {
            WatchRSSTheme {
                WatchReaderPresetSelector(repository)
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ReaderPresetActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchReaderPresetSelector(repository: ReaderPresetRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val presets by repository.presets.collectAsStateWithLifecycle()
    val active by repository.activePreset.collectAsStateWithLifecycle()
    val selection by repository.selection.collectAsStateWithLifecycle()
    val schedule by repository.schedule.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    var message by remember { mutableStateOf<String?>(null) }
    var locationLoading by remember { mutableStateOf(false) }
    InstallDigitalCrownScrollHandler(scroll)

    fun applyLocation(location: Location?) {
        locationLoading = false
        if (location == null) {
            message = "无法获取当前位置，请到空旷处重试"
        } else {
            repository.setSunLocation(location.latitude, location.longitude)
            message = "已更新日出日落位置"
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                locationLoading = true
                requestCurrentWatchLocation(context, ::applyLocation)
            } else {
                message = "需要位置权限才能计算日出日落"
            }
        }

    fun updateSunLocation() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            locationPermissionLauncher.launch(permissions)
        } else {
            locationLoading = true
            requestCurrentWatchLocation(context, ::applyLocation)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 26.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "阅读器预设",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "手表端只选择手机同步的预设，不提供新建或编辑。",
            style = MaterialTheme.typography.bodySmall
        )
        DownloadPhoneAppButton(operation = "同步阅读器预设")
        Text("当前：${active.name}", fontWeight = FontWeight.SemiBold)

        Text("显示方式")
        val themeModes = listOf(
            ReaderThemeMode.DARK,
            ReaderThemeMode.LIGHT,
            ReaderThemeMode.SYSTEM
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeModes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selection.mode == mode,
                    onClick = { repository.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = themeModes.size
                    ),
                    icon = {},
                    label = {
                        Text(
                            when (mode) {
                                ReaderThemeMode.DARK -> "深色"
                                ReaderThemeMode.LIGHT -> "浅色"
                                ReaderThemeMode.SYSTEM -> "自动"
                            }
                        )
                    }
                )
            }
        }

        if (selection.mode == ReaderThemeMode.SYSTEM) {
            WatchScheduleSection(
                schedule = schedule,
                repository = repository,
                locationLoading = locationLoading,
                onUpdateLocation = ::updateSunLocation
            )
        }

        WatchPresetDropdown(
            label = "浅色预设",
            presets = presets,
            selectedId = selection.lightPresetId,
            repository = repository,
            followLight = false,
            onSelected = repository::setLightPreset
        )
        WatchPresetDropdown(
            label = "深色预设",
            presets = presets,
            selectedId = selection.darkPresetId,
            repository = repository,
            followLight = selection.darkFollowsLight,
            allowFollowLight = true,
            onSelected = repository::setDarkPreset
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WatchScheduleSection(
    schedule: WatchReaderThemeSchedule,
    repository: ReaderPresetRepository,
    locationLoading: Boolean,
    onUpdateLocation: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("自动切换规则", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = schedule.mode == WatchThemeScheduleMode.FIXED_TIME,
                onClick = { repository.setScheduleMode(WatchThemeScheduleMode.FIXED_TIME) },
                label = { Text("固定时间") }
            )
            FilterChip(
                selected = schedule.mode == WatchThemeScheduleMode.SUNRISE_SUNSET,
                onClick = {
                    if (schedule.latitude == null || schedule.longitude == null) {
                        onUpdateLocation()
                    } else {
                        repository.setScheduleMode(WatchThemeScheduleMode.SUNRISE_SUNSET)
                    }
                },
                label = { Text("日出日落") }
            )
        }
        if (schedule.mode == WatchThemeScheduleMode.FIXED_TIME) {
            TimeSettingButton(
                label = "浅色开始",
                minutes = schedule.lightStartMinutes
            ) {
                showTimePicker(context, schedule.lightStartMinutes, repository::setFixedLightStart)
            }
            TimeSettingButton(
                label = "深色开始",
                minutes = schedule.darkStartMinutes
            ) {
                showTimePicker(context, schedule.darkStartMinutes, repository::setFixedDarkStart)
            }
        } else {
            val sunTimes = repository.todaySunTimes()
            Text(
                if (sunTimes == null) {
                    "当前位置今天无法计算日出日落，将暂用固定时间。"
                } else {
                    "今天 ${sunTimes.sunrise.format(TIME_FORMAT)} 日出 · " +
                        "${sunTimes.sunset.format(TIME_FORMAT)} 日落"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onUpdateLocation,
                enabled = !locationLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (locationLoading) "正在定位…" else "更新当前位置")
            }
        }
    }
}

@Composable
private fun TimeSettingButton(label: String, minutes: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label  ${formatMinutes(minutes)}")
    }
}

@Composable
private fun WatchPresetDropdown(
    label: String,
    presets: List<ReaderPreset>,
    selectedId: String?,
    repository: ReaderPresetRepository,
    followLight: Boolean,
    allowFollowLight: Boolean = false,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (followLight) {
        "跟随浅色"
    } else {
        presets.firstOrNull { it.id == selectedId }?.name ?: "请选择"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedName, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowFollowLight) {
                DropdownMenuItem(
                    text = { Text("跟随浅色") },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    }
                )
            }
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        WatchPresetSingleLinePreview(preset, repository)
                    },
                    onClick = {
                        expanded = false
                        onSelected(preset.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun WatchPresetSingleLinePreview(
    preset: ReaderPreset,
    repository: ReaderPresetRepository
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = preset,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile
        )
    ) {
        ReaderBackgroundSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                "${preset.name} · 正文预览",
                style = readerTextStyle(ReaderTextRole.BODY),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

private fun showTimePicker(
    context: Context,
    initialMinutes: Int,
    onSelected: (Int) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(hour * 60 + minute) },
        initialMinutes / 60,
        initialMinutes % 60,
        true
    ).show()
}

@SuppressLint("MissingPermission")
private fun requestCurrentWatchLocation(
    context: Context,
    onResult: (Location?) -> Unit
) {
    val manager = context.getSystemService(LocationManager::class.java)
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }
    val recent = providers
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)
    if (recent != null && System.currentTimeMillis() - recent.time < 6 * 60 * 60 * 1000L) {
        onResult(recent)
        return
    }
    val provider = providers.firstOrNull()
    if (provider == null) {
        onResult(recent)
        return
    }
    manager.getCurrentLocation(
        provider,
        null,
        ContextCompat.getMainExecutor(context)
    ) { location ->
        onResult(location ?: recent)
    }
}

private fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
