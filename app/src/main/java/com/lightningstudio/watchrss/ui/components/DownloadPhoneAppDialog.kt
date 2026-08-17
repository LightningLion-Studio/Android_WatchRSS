package com.lightningstudio.watchrss.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.screen.rss.openExternalLink
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.testing.DownloadPhoneAppTestTags
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 手机端 App 的 OPPO 应用商店下载页。 */
const val PHONE_APP_DOWNLOAD_URL = "https://app.cdo.oppomobile.com/home/detail?app_id=37262051"

/** 「下载手机端App以完成{operation}」提示文案（纯函数，可测）。 */
fun phoneAppDownloadPrompt(operation: String): String = "下载手机端App以完成$operation"

private const val DOWNLOAD_PHONE_DIALOG_BASE_SIZE_DP = 466f
private const val DOWNLOAD_PHONE_QR_BASE_SIZE_DP = 176f

internal val DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES = DialogProperties(
    dismissOnBackPress = true,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

/**
 * Keeps the QR code square and proportional to the circular dialog.
 *
 * The explicit size is important: an empty weighted child has no intrinsic size when
 * measured with `fill = false`, so deriving the QR size from that child can stay at zero.
 */
internal fun downloadPhoneAppQrSize(containerSize: Dp): Dp {
    val scale = (containerSize.value / DOWNLOAD_PHONE_DIALOG_BASE_SIZE_DP).coerceIn(0f, 1f)
    return (DOWNLOAD_PHONE_QR_BASE_SIZE_DP * scale).dp
}

/**
 * 胶囊入口（设置行样式），自带弹窗状态。
 */
@Composable
fun DownloadPhoneAppButton(
    operation: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    WatchSettingsPillRow(
        label = phoneAppDownloadPrompt(operation),
        modifier = modifier,
        testTag = testTag,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        DownloadPhoneAppDialog(operation = operation, onDismiss = { showDialog = false })
    }
}

/** Full-width phone companion entry used for content editing and configuration. */
@Composable
fun ContentEditingDownloadPhoneAppButton(
    operation: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    DownloadPhoneAppButton(
        operation = operation,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = WatchDimens.hey_distance_8dp),
        testTag = testTag
    )
}

/**
 * 密集屏幕的小字链接入口（带下划线），自带弹窗状态。
 */
@Composable
fun DownloadPhoneAppCompactLink(
    operation: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var showDialog by remember { mutableStateOf(false) }
    Text(
        text = phoneAppDownloadPrompt(operation),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textDecoration = TextDecoration.Underline,
        textAlign = TextAlign.Center,
        modifier = modifier.clickable { showDialog = true }
    )
    if (showDialog) {
        DownloadPhoneAppDialog(operation = operation, onDismiss = { showDialog = false })
    }
}

/**
 * 下载手机端 App 的圆形弹窗：标题 + 副标题 + 自适应二维码 + 浏览器兜底按钮。
 * 壳复制自 WatchConfirmDialog（DeleteConfirmDialog.kt），两处刻意偏差：
 * 上下 padding 收窄到 56/20 给二维码让空间；二维码按圆形容器直径等比缩放。
 */
@Composable
fun DownloadPhoneAppDialog(
    operation: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DOWNLOAD_PHONE_APP_DIALOG_PROPERTIES
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag(DownloadPhoneAppTestTags.SCRIM)
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxSize = minOf(maxWidth, maxHeight)
                val containerSize = minOf(maxSize, DOWNLOAD_PHONE_DIALOG_BASE_SIZE_DP.dp)
                val scale = (containerSize.value / DOWNLOAD_PHONE_DIALOG_BASE_SIZE_DP).coerceAtMost(1f)
                val scaleDp: (Dp) -> Dp = { value -> (value.value * scale).dp }
                val qrSize = downloadPhoneAppQrSize(containerSize)
                val qrSizePx = with(density) { qrSize.roundToPx().coerceAtLeast(1) }
                val fontFamily = FontFamily(Font(R.font.watch_sans))

                LaunchedEffect(qrSizePx) {
                    qrBitmap = withContext(Dispatchers.IO) {
                        QrCodeGenerator.create(PHONE_APP_DOWNLOAD_URL, qrSizePx)
                    }
                }

                Column(
                    modifier = Modifier
                        .size(containerSize)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(top = scaleDp(56.dp), bottom = scaleDp(20.dp))
                        .testTag(DownloadPhoneAppTestTags.DIALOG)
                        .semantics { dialog() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(scaleDp(8.dp))
                ) {
                    Text(
                        text = "下载手机端App",
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = (34f * scale).sp,
                        lineHeight = (46f * scale).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .semantics { heading() }
                    )
                    // 副标题在 360dp 以上容器才显示：248dp 圆屏放不下
                    if (containerSize >= 360.dp) {
                        Text(
                            text = "以完成$operation",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = (28f * scale).sp,
                            lineHeight = (40f * scale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(qrSize),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = qrBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "下载手机端App二维码",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag(DownloadPhoneAppTestTags.QR_IMAGE)
                                    .semantics {
                                        role = Role.Image
                                        contentDescription = "下载手机端App二维码"
                                    }
                            )
                        } else if (qrSizePx > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = PHONE_APP_DOWNLOAD_URL,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Text(
                                    text = "二维码生成失败，请点击下方按钮打开下载页",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(scaleDp(16.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(scaleDp(104.dp))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onDismiss)
                                .testTag(DownloadPhoneAppTestTags.CLOSE_BUTTON),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(scaleDp(48.dp))
                            )
                        }
                        WatchButton(
                            onClick = {
                                runCatching { openExternalLink(context, PHONE_APP_DOWNLOAD_URL) }
                            },
                            contentPadding = PaddingValues(horizontal = scaleDp(8.dp)),
                            modifier = Modifier
                                .width(scaleDp(232.dp))
                                .height(scaleDp(104.dp))
                                .testTag(DownloadPhoneAppTestTags.OPEN_BROWSER_BUTTON)
                        ) {
                            Text(
                                text = "浏览器打开下载页",
                                fontSize = (22f * scale).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                    }
                }
            }
        }
    }
}
