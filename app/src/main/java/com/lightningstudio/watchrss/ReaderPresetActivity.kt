package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.data.reader.ReaderBackgroundType
import com.lightningstudio.watchrss.data.reader.ReaderPreset
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.reader.ReaderTextAlignment
import com.lightningstudio.watchrss.data.reader.ReaderTextStyle
import com.lightningstudio.watchrss.data.reader.ReaderTextStyleOverride
import com.lightningstudio.watchrss.data.reader.ReaderTypographyRole
import com.lightningstudio.watchrss.data.reader.ReaderFontAssetEntity
import com.lightningstudio.watchrss.data.reader.ReaderRenderMode
import com.lightningstudio.watchrss.data.reader.ReaderFontSynthesis
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.reader.LocalReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPresetRuntime
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ReaderPresetActivity : BaseWatchActivity() {
    private val repository by lazy {
        (application as WatchRssApplication).container.readerPresetRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent { WatchRSSTheme { WatchReaderPresetManager(repository) } }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ReaderPresetActivity::class.java)
    }
}

@Composable
private fun WatchReaderPresetManager(repository: ReaderPresetRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val presets by repository.presets.collectAsStateWithLifecycle()
    val active by repository.activePreset.collectAsStateWithLifecycle()
    val fonts by repository.fonts.collectAsStateWithLifecycle()
    val backgrounds by repository.backgrounds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var editing by remember { mutableStateOf<ReaderPreset?>(null) }
    var editingCategoryTypography by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<ReaderPreset?>(null) }
    var deleting by remember { mutableStateOf<ReaderPreset?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    InstallDigitalCrownScrollHandler(scroll)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when {
                editing == null -> "阅读器预设"
                editingCategoryTypography -> "分类字体设定"
                else -> "编辑预设"
            },
            style = MaterialTheme.typography.titleLarge
        )
        if (editing == null) {
            Text("应用状态只保存在本表；同步不传正在使用的预设名。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        BluetoothConnectionActivity.createIntent(
                            context,
                            preferredAbility = null,
                            returnRemoteUrl = false
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("立即同步") }
            presets.forEach { preset ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(preset.name)
                    Text(
                        if (preset.id == active.id) "正在使用" else "点此应用",
                        modifier = Modifier.clickable { repository.setActivePreset(preset.id) },
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextButton(onClick = {
                            editingCategoryTypography = false
                            editing = preset
                        }) { Text("编辑") }
                        TextButton(onClick = {
                            scope.launch {
                                message = runCatching {
                                    repository.duplicate(preset.id)
                                    "已复制"
                                }.getOrElse { it.message ?: "复制失败" }
                            }
                        }) { Text("复制") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextButton(onClick = { rename = preset }) { Text("重命名") }
                        TextButton(onClick = { deleting = preset }) { Text("删除") }
                    }
                }
            }
        } else {
            val draft = editing!!
            if (editingCategoryTypography) {
                WatchCategoryTypographyEditor(
                    draft = draft,
                    fonts = fonts,
                    repository = repository,
                    onDraftChange = { editing = it },
                    onBack = { editingCategoryTypography = false }
                )
                Spacer(Modifier.height(30.dp))
                return@Column
            }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalReaderPresetRuntime provides ReaderPresetRuntime(
                    preset = draft,
                    fontFile = repository::fontFile,
                    backgroundFile = repository::backgroundFile
                )
            ) {
                ReaderBackgroundSurface(Modifier.fillMaxWidth().height(150.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("实时预览", style = readerTextStyle(ReaderTextRole.TITLE))
                        Text("文字、排版、纯色和图片参数。", style = readerTextStyle(ReaderTextRole.BODY))
                    }
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { editing = draft.copy(name = it) },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
            WatchSlider("字号", draft.body.fontSizeSp, 10f..40f) {
                editing = draft.copy(body = draft.body.copy(fontSizeSp = it))
            }
            WatchSlider("字重", draft.body.fontWeight.toFloat(), 100f..900f) {
                editing = draft.copy(
                    body = draft.body.copy(fontWeight = it.roundToInt())
                )
            }
            WatchColorField("文字颜色", draft.body.colorArgb) {
                editing = draft.copy(body = draft.body.copy(colorArgb = it))
            }
            WatchTextStyleToolbar(
                style = draft.body,
                onStyle = { editing = draft.copy(body = it) }
            )
            WatchSlider("行高", draft.body.lineHeightEm, 0.8f..3f) {
                editing = draft.copy(body = draft.body.copy(lineHeightEm = it))
            }
            WatchSlider("字距", draft.body.letterSpacingEm, -0.1f..0.5f) {
                editing = draft.copy(body = draft.body.copy(letterSpacingEm = it))
            }
            WatchSlider("段距", draft.body.paragraphSpacingDp, 0f..40f) {
                editing = draft.copy(body = draft.body.copy(paragraphSpacingDp = it))
            }
            WatchSlider("页边距", draft.body.horizontalPaddingDp, 0f..40f) {
                editing = draft.copy(body = draft.body.copy(horizontalPaddingDp = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderTextAlignment.entries.forEach { alignment ->
                    FilterChip(
                        selected = draft.body.alignment == alignment,
                        onClick = { editing = draft.copy(body = draft.body.copy(alignment = alignment)) },
                        label = { Text(if (alignment == ReaderTextAlignment.JUSTIFY) "两端" else alignment.name) }
                    )
                }
            }
            ReaderRenderMode.entries.forEach { mode ->
                FilterChip(
                    selected = draft.body.renderMode == mode,
                    onClick = { editing = draft.copy(body = draft.body.copy(renderMode = mode)) },
                    label = { Text(mode.name) }
                )
            }
            FilterChip(
                selected = draft.body.fontSynthesis == ReaderFontSynthesis.ENABLED,
                onClick = {
                    editing = draft.copy(
                        body = draft.body.copy(
                            fontSynthesis = if (draft.body.fontSynthesis == ReaderFontSynthesis.ENABLED) {
                                ReaderFontSynthesis.DISABLED
                            } else {
                                ReaderFontSynthesis.ENABLED
                            }
                        )
                    )
                },
                label = { Text("字体合成") }
            )
            Text("背景")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ReaderBackgroundType.SOLID, ReaderBackgroundType.IMAGE).forEach { type ->
                    FilterChip(
                        selected = draft.background.type == type,
                        onClick = { editing = draft.copy(background = draft.background.copy(type = type)) },
                        label = { Text(if (type == ReaderBackgroundType.SOLID) "纯色" else "图片") }
                    )
                }
            }
            if (draft.background.type == ReaderBackgroundType.IMAGE) {
                backgrounds.filter { it.kind != ReaderBackgroundType.VIDEO.name }.forEach { asset ->
                    FilterChip(
                        selected = draft.background.assetId == asset.id,
                        onClick = {
                            editing = draft.copy(background = draft.background.copy(assetId = asset.id))
                        },
                        label = { Text(asset.displayName) }
                    )
                }
                WatchSlider("焦点 X", draft.background.focusX, 0f..1f) {
                    editing = draft.copy(background = draft.background.copy(focusX = it))
                }
                WatchSlider("焦点 Y", draft.background.focusY, 0f..1f) {
                    editing = draft.copy(background = draft.background.copy(focusY = it))
                }
                WatchSlider("缩放", draft.background.zoom, 0.25f..4f) {
                    editing = draft.copy(background = draft.background.copy(zoom = it))
                }
                WatchSlider("旋转", draft.background.rotationDegrees, -180f..180f) {
                    editing = draft.copy(background = draft.background.copy(rotationDegrees = it))
                }
                WatchSlider("模糊", draft.background.blurDp, 0f..32f) {
                    editing = draft.copy(background = draft.background.copy(blurDp = it))
                }
            }
            if (draft.background.type == ReaderBackgroundType.SOLID) {
                WatchColorField("背景颜色", draft.background.colorArgb) {
                    editing = draft.copy(background = draft.background.copy(colorArgb = it))
                }
            }
            if (draft.background.type == ReaderBackgroundType.VIDEO) {
                Text("视频配置在手表端只读，息屏、后台或省电时显示同步封面。")
            }
            WatchCategoryTypographyEntry(
                enabled = draft.categoryTypographyEnabled,
                onEnabledChange = {
                    editing = draft.copy(categoryTypographyEnabled = it)
                },
                onOpen = { editingCategoryTypography = true }
            )
            Button(
                onClick = {
                    scope.launch {
                        message = runCatching {
                            val saved = repository.savePreset(draft)
                            editing = saved
                            "已保存"
                        }.getOrElse { it.message ?: "保存失败" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val saved = repository.saveAsNew(draft, draft.name)
                        editing = saved
                        message = "已另存为"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("另存为") }
            OutlinedButton(
                onClick = { repository.setActivePreset(draft.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("应用已保存版本") }
            TextButton(onClick = { editing = null }, modifier = Modifier.fillMaxWidth()) {
                Text("返回预设列表")
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    rename?.let { preset ->
        WatchNameDialog("重命名", preset.name, { rename = null }) { value ->
            rename = null
            scope.launch {
                message = runCatching {
                    repository.rename(preset.id, value)
                    "已重命名"
                }.getOrElse { it.message ?: "重命名失败" }
            }
        }
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text("删除“${preset.name}”？活动预设会回退到安全默认。") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repository.deletePreset(preset.id) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }
        )
    }
}

@Composable
private fun WatchCategoryTypographyEntry(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("分类字体设定")
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text(
            if (enabled) "分别调整标题、副标题、引用、代码和链接" else "当前使用内置分类比例",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("进入调整")
        }
    }
}

@Composable
private fun WatchCategoryTypographyEditor(
    draft: ReaderPreset,
    fonts: List<ReaderFontAssetEntity>,
    repository: ReaderPresetRepository,
    onDraftChange: (ReaderPreset) -> Unit,
    onBack: () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReaderPresetRuntime provides ReaderPresetRuntime(
            preset = draft,
            fontFile = repository::fontFile,
            backgroundFile = repository::backgroundFile
        )
    ) {
        ReaderBackgroundSurface(Modifier.fillMaxWidth().height(180.dp)) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("分类标题", style = readerTextStyle(ReaderTextRole.TITLE))
                Text("副标题样张", style = readerTextStyle(ReaderTextRole.SUBTITLE))
                Text("引用与正文比例", style = readerTextStyle(ReaderTextRole.QUOTE))
                Text("code.sample()", style = readerTextStyle(ReaderTextRole.CODE))
                Text("链接样张", style = readerTextStyle(ReaderTextRole.LINK))
            }
        }
    }
    Text(
        "各分类先按正文生成默认比例，再叠加这里的设置。",
        style = MaterialTheme.typography.bodySmall
    )
    WatchRoleStyleEditor(
        label = "标题",
        role = ReaderTypographyRole.TITLE,
        value = draft.title,
        preset = draft,
        fonts = fonts,
        onChange = { onDraftChange(draft.copy(title = it)) }
    )
    WatchRoleStyleEditor(
        label = "副标题",
        role = ReaderTypographyRole.SUBTITLE,
        value = draft.subtitle,
        preset = draft,
        fonts = fonts,
        onChange = { onDraftChange(draft.copy(subtitle = it)) }
    )
    WatchRoleStyleEditor(
        label = "引用",
        role = ReaderTypographyRole.QUOTE,
        value = draft.quote,
        preset = draft,
        fonts = fonts,
        onChange = { onDraftChange(draft.copy(quote = it)) }
    )
    WatchRoleStyleEditor(
        label = "代码",
        role = ReaderTypographyRole.CODE,
        value = draft.code,
        preset = draft,
        fonts = fonts,
        onChange = { onDraftChange(draft.copy(code = it)) }
    )
    WatchRoleStyleEditor(
        label = "链接",
        role = ReaderTypographyRole.LINK,
        value = draft.link,
        preset = draft,
        fonts = fonts,
        onChange = { onDraftChange(draft.copy(link = it)) }
    )
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("返回预设编辑")
    }
}

@Composable
private fun WatchTextStyleToolbar(
    style: ReaderTextStyle,
    onStyle: (ReaderTextStyle) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        WatchStyleIcon(style.italic, "斜体", Icons.Default.FormatItalic) {
            onStyle(style.copy(italic = !style.italic))
        }
        WatchStyleIcon(style.underline, "下划线", Icons.Default.FormatUnderlined) {
            onStyle(style.copy(underline = !style.underline))
        }
        WatchStyleIcon(style.strikethrough, "中轴线（删除线）", Icons.Default.StrikethroughS) {
            onStyle(style.copy(strikethrough = !style.strikethrough))
        }
    }
}

@Composable
private fun WatchStyleIcon(
    selected: Boolean,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .semantics { this.selected = selected }
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun WatchRoleStyleEditor(
    label: String,
    role: ReaderTypographyRole,
    value: ReaderTextStyleOverride,
    preset: ReaderPreset,
    fonts: List<ReaderFontAssetEntity>,
    onChange: (ReaderTextStyleOverride) -> Unit
) {
    val body = preset.body
    val defaults = preset.categoryDefault(role)
    val effective = value.resolve(body, defaults)
    val selectedFontId = value.fontAssetId.takeIf { value.useOwnFont }
    val selectedFont = fonts.firstOrNull { it.id == selectedFontId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label)
        Text("字体", style = MaterialTheme.typography.bodySmall)
        FilterChip(
            selected = selectedFontId == null,
            onClick = {
                onChange(
                    value.copy(
                        useOwnFont = false,
                        fontAssetId = null,
                        fontFaceIndex = null
                    )
                )
            },
            label = { Text("继承正文") }
        )
        fonts.forEach { font ->
            FilterChip(
                selected = selectedFontId == font.id,
                onClick = {
                    onChange(
                        value.copy(
                            useOwnFont = true,
                            fontAssetId = font.id,
                            fontFaceIndex = 0
                        )
                    )
                },
                label = { Text(font.displayName) }
            )
        }
        if (selectedFont != null && selectedFont.faceCount > 1) {
            Text("字体面", style = MaterialTheme.typography.bodySmall)
            repeat(selectedFont.faceCount) { index ->
                FilterChip(
                    selected = (value.fontFaceIndex ?: 0) == index,
                    onClick = { onChange(value.copy(fontFaceIndex = index)) },
                    label = { Text("字体面 ${index + 1}") }
                )
            }
        }
        WatchSlider(
            "相对字号",
            value.fontScale ?: defaults.fontScale ?: 1f,
            0.5f..2.5f
        ) {
            onChange(value.copy(fontScale = it, fontSizeSp = null))
        }
        WatchSlider("字重", effective.fontWeight.toFloat(), 100f..900f) {
            onChange(value.copy(fontWeight = it.roundToInt()))
        }
        WatchTextStyleToolbar(effective) { style ->
            onChange(
                value.copy(
                    italic = if (style.italic != effective.italic) {
                        style.italic
                    } else {
                        value.italic
                    },
                    underline = if (style.underline != effective.underline) {
                        style.underline
                    } else {
                        value.underline
                    },
                    strikethrough = if (style.strikethrough != effective.strikethrough) {
                        style.strikethrough
                    } else {
                        value.strikethrough
                    }
                )
            )
        }
        WatchColorField("颜色", effective.colorArgb) {
            onChange(value.copy(colorArgb = it))
        }
        WatchSlider("行高", effective.lineHeightEm, 0.8f..3f) {
            onChange(value.copy(lineHeightEm = it))
        }
        WatchSlider("字距", effective.letterSpacingEm, -0.1f..0.5f) {
            onChange(value.copy(letterSpacingEm = it))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReaderTextAlignment.entries.forEach { alignment ->
                FilterChip(
                    selected = effective.alignment == alignment,
                    onClick = { onChange(value.copy(alignment = alignment)) },
                    label = {
                        Text(
                            when (alignment) {
                                ReaderTextAlignment.START -> "左"
                                ReaderTextAlignment.CENTER -> "中"
                                ReaderTextAlignment.JUSTIFY -> "两端"
                            }
                        )
                    }
                )
            }
        }
        TextButton(
            onClick = {
                onChange(ReaderTextStyleOverride())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复分类默认")
        }
    }
}

@Composable
private fun WatchColorField(label: String, color: Long, onChange: (Long) -> Unit) {
    var value by remember(color) { mutableStateOf("#%08X".format(color)) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            val hex = it.trim().removePrefix("#").let { raw ->
                if (raw.length == 6) "FF$raw" else raw
            }
            if (hex.length == 8) hex.toLongOrNull(16)?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WatchSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    var raw by remember(label) { mutableStateOf(formatWatchNumber(value)) }
    LaunchedEffect(value) {
        if (raw.toFloatOrNull() != value) raw = formatWatchNumber(value)
    }
    Column {
        OutlinedTextField(
            value = raw,
            onValueChange = { input ->
                if (input.matches(Regex("-?[0-9]*([.][0-9]*)?"))) {
                    raw = input
                    input.toFloatOrNull()
                        ?.takeIf { it in range }
                        ?.let(onChange)
                }
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

private fun formatWatchNumber(value: Float): String =
    if (kotlin.math.abs(value - value.roundToInt()) < 0.0001f) {
        value.roundToInt().toString()
    } else {
        "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }

@Composable
private fun WatchNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.trim().isNotEmpty()) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
