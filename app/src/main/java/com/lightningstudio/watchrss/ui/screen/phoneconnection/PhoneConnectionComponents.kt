package com.lightningstudio.watchrss.ui.screen.phoneconnection

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource

/** White padding remains part of the bounded QR tile. */
private val PAIRING_QR_QUIET_ZONE = 8.dp

/** Largest rounded square inside the screen circle, including its white quiet zone.
 * Corner arc radius is 16dp; leave 2dp before the physical screen edge. */
internal fun pairingQrTileDp(minDimension: Float): Float =
    kotlin.math.floor(kotlin.math.sqrt(2f) * (minDimension / 2f - 2f - 16f) + 32f)
        .coerceIn(32f, minDimension.coerceAtLeast(32f))

/**
 * Dark modules on white with whole-pixel modules, unlike the inverted watch-style
 * [com.lightningstudio.watchrss.ui.util.QrCodeGenerator]: phone scanners read this reliably.
 * Low error correction keeps the long one-time payload at the fewest, largest modules.
 */
fun pairingQrBitmap(text: String, maxPixels: Int): Bitmap? = runCatching {
    val matrix = Encoder.encode(
        text, ErrorCorrectionLevel.L, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
    ).matrix
    val modules = matrix.width
    val scale = (maxPixels / modules).coerceAtLeast(1)
    val size = modules * scale
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val row = y / scale
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x / scale, row).toInt() == 1) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    createBitmap(size, size).also { it.setPixels(pixels, 0, size, 0, 0, size, size) }
}.getOrNull()

/** Pixels available to modules inside the screen-sized tile. */
fun pairingQrPixels(density: Float, minDimensionDp: Float = 233f): Int =
    ((pairingQrTileDp(minDimensionDp) - PAIRING_QR_QUIET_ZONE.value * 2) * density).toInt()

/** Scrolling, crown-aware page that centres short content and lets long content scroll. */
@Composable
internal fun RoundPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    InstallDigitalCrownScrollHandler(scrollState)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
internal fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().semantics { heading() }
    )
}

@Composable
internal fun PageBody(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/** Full-width pill; the primary one is the single committed action on a page. */
@Composable
internal fun PairingPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    testTag: String? = null
) {
    val container = if (primary) MaterialTheme.colorScheme.primary else watchColorResource(R.color.watch_pill_background)
    val content = if (primary) Color.Black else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(WatchDimens.hey_button_default_radius))
            .background(container)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            color = content,
            textAlign = TextAlign.Center
        )
    }
}

/** Landing-page choice: a title the user acts on plus one line saying when it fits. */
@Composable
internal fun ConnectionOption(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    trailing: String? = null,
    testTag: String? = null
) {
    val container = if (primary) MaterialTheme.colorScheme.primary else watchColorResource(R.color.watch_pill_background)
    val titleColor = if (primary) Color.Black else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (primary) Color.Black.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.bodyLarge, color = subtitleColor)
        }
    }
}

/** White tile with a quiet zone so the phone scanner always finds the code's edges. */
@Composable
internal fun PairingQrTile(qr: Bitmap, tileSize: Dp, modifier: Modifier = Modifier, testTag: String? = null) {
    Box(
        modifier = modifier
            .size(tileSize)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(PAIRING_QR_QUIET_ZONE)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .semantics {
                role = Role.Image
                contentDescription = "配对二维码"
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Live progress: done steps get a check, the current one spins, later ones wait. */
@Composable
internal fun PairingSteps(current: PairingStep, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicStepsWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PairingStep.entries.forEach { step ->
            val done = step.ordinal < current.ordinal
            val active = step == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepMarker(done = done, active = active, size = 20.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private val IntrinsicStepsWidth = 140.dp

@Composable
private fun StepMarker(done: Boolean, active: Boolean, size: Dp) {
    when {
        done -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = "已完成", tint = Color.Black, modifier = Modifier.size(size * 0.7f))
        }
        active -> WatchCircularProgressIndicator(modifier = Modifier.size(size), strokeWidth = 2.5.dp)
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
        )
    }
}

/** Large outcome mark for finished or failed pages. */
@Composable
internal fun OutcomeMark(success: Boolean) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (success) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
        } else {
            Text("!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
