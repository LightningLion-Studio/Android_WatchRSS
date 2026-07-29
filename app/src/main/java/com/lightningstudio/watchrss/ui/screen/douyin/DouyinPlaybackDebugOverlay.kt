package com.lightningstudio.watchrss.ui.screen.douyin

import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewDebugSnapshot
import java.util.Locale
import kotlin.math.roundToInt

internal data class DouyinPlaybackQuarantinedDebugEntry(
    val awemeId: String,
    val reason: String,
    val failureCount: Int,
    val lastFailedAtMs: Long
)

internal fun buildDouyinPlaybackDebugOverlayText(
    snapshot: DouyinPlaybackPreviewDebugSnapshot,
    activeAwemeId: String?,
    preparedAwemeId: String?,
    currentPage: Int,
    settledPage: Int,
    isScrollInProgress: Boolean,
    foregroundSlotKey: DouyinPlayerSlotKey,
    primarySlot: DouyinPlayerSlotState,
    secondarySlot: DouyinPlayerSlotState,
    quarantinedItems: List<DouyinPlaybackQuarantinedDebugEntry> = emptyList()
): String {
    val lines = mutableListOf<String>()
    lines += "Douyin debug  page=$currentPage settled=$settledPage scroll=${if (isScrollInProgress) 1 else 0} fg=${foregroundSlotKey.name.first()}"
    lines += "active=${shortAwemeId(activeAwemeId)} prepared=${shortAwemeId(preparedAwemeId)} ram=${formatDebugBytes(snapshot.totalPreviewBytes)} gen=${snapshot.sessionGeneration}"

    lines += "DL:"
    if (snapshot.activePrefetches.isEmpty()) {
        lines += "- none"
    } else {
        snapshot.activePrefetches.forEach { entry ->
            lines += "- #${entry.prefetchOrder + 1} ${shortAwemeId(entry.awemeId)} ${formatDebugProgress(entry.downloadedBytes, entry.budgetBytes)} ${entry.reason} ${shortMediaUri(entry.mediaUri)}"
        }
    }

    lines += "RAM:"
    if (snapshot.memoryEntries.isEmpty()) {
        lines += "- empty"
    } else {
        snapshot.memoryEntries.forEachIndexed { index, entry ->
            lines += "- M$index ${shortAwemeId(entry.awemeId)} ${formatDebugProgress(entry.cachedBytes, entry.budgetBytes)} ${shortMediaUri(entry.mediaUri)}"
        }
    }

    lines += "Slots:"
    lines += buildSlotDebugLine(snapshot, primarySlot, foregroundSlotKey == primarySlot.key)
    lines += buildSlotDebugLine(snapshot, secondarySlot, foregroundSlotKey == secondarySlot.key)

    lines += "Quarantined:"
    if (quarantinedItems.isEmpty()) {
        lines += "- none"
    } else {
        quarantinedItems.forEach { entry ->
            lines += "- ${shortAwemeId(entry.awemeId)} ${entry.reason} x${entry.failureCount}"
        }
    }
    return lines.joinToString("\n")
}

private fun buildSlotDebugLine(
    snapshot: DouyinPlaybackPreviewDebugSnapshot,
    slot: DouyinPlayerSlotState,
    isForeground: Boolean
): String {
    val normalizedMediaUri = slot.mediaUri?.trim().orEmpty()
    val registration = snapshot.registrations.firstOrNull { it.mediaUri == normalizedMediaUri }
    val memoryEntry = snapshot.memoryEntries.firstOrNull { it.mediaUri == normalizedMediaUri }
    val cachedBytes = memoryEntry?.cachedBytes ?: registration?.cachedBytes ?: 0
    val budgetBytes = registration?.budgetBytes ?: memoryEntry?.budgetBytes ?: 0
    val flags = buildList {
        if (slot.isReady) add("rdy")
        if (slot.hasRenderedFirstFrame) add("ff")
        if (slot.isBuffering) add("buf")
        if (slot.isPlaying) add("play")
        if (slot.hasError) add("err")
    }.joinToString(",").ifBlank { "-" }
    return "- ${if (isForeground) "*" else " "} ${slot.key.name.first()} ${shortAwemeId(slot.boundAwemeId)} ${slot.boundCodec.name} ${debugSourceKind(slot.mediaUri)} ${formatDebugProgress(cachedBytes, budgetBytes)} $flags ${shortMediaUri(slot.mediaUri)}"
}

private fun formatDebugProgress(currentBytes: Int, budgetBytes: Int): String {
    if (budgetBytes <= 0) return "${formatDebugBytes(currentBytes.toLong())}/-"
    val pct = ((currentBytes.toDouble() / budgetBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    return "${formatDebugBytes(currentBytes.toLong())}/${formatDebugBytes(budgetBytes.toLong())} ${pct}%"
}

private fun formatDebugBytes(bytes: Long): String {
    val absBytes = bytes.coerceAtLeast(0L)
    return when {
        absBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1fMB", absBytes / (1024f * 1024f))
        absBytes >= 1024L -> String.format(Locale.US, "%.0fKB", absBytes / 1024f)
        else -> "${absBytes}B"
    }
}

private fun shortAwemeId(awemeId: String?): String {
    val normalized = awemeId?.trim().orEmpty()
    if (normalized.isEmpty()) return "-"
    return if (normalized.length <= 8) normalized else normalized.takeLast(8)
}

private fun shortMediaUri(mediaUri: String?): String {
    val normalized = mediaUri?.trim().orEmpty()
    if (normalized.isEmpty()) return "-"
    val candidate = normalized.substringAfterLast('/').substringBefore('?').ifBlank { normalized }
    return if (candidate.length <= 18) candidate else candidate.takeLast(18)
}

private fun debugSourceKind(mediaUri: String?): String {
    return if (mediaUri?.startsWith("file://") == true) "LOCAL" else "REMOTE"
}
