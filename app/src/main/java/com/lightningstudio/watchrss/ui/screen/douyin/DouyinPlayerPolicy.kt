@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.media3.common.Player
import com.lightningstudio.watchrss.data.douyin.DouyinOfflineReadiness

/** Input is normalized and already multiplied by DIGITAL_CROWN_VOLUME_STEP (0.01). */
internal fun douyinCrownVolumeDelta(scaledDelta: Float): Float =
    if (scaledDelta.isFinite()) scaledDelta / 3f else 0f

internal fun shouldBlockDouyinReverseSwipe(
    showTitlePage: Boolean,
    pagerPage: Int,
    pageOffset: Float,
    deltaY: Float
): Boolean = !showTitlePage && pagerPage <= 1 && pageOffset <= 0f && deltaY > 0f

/** Silence and pause synchronously before publishing the incoming slot. */
internal fun commitDouyinForegroundAudio(outgoing: Player, commit: () -> Unit) {
    outgoing.volume = 0f
    outgoing.playWhenReady = false
    outgoing.pause()
    commit()
}

internal fun douyinPlaybackNeedsInternet(
    uri: String?,
    readiness: DouyinOfflineReadiness
): Boolean = readiness == DouyinOfflineReadiness.NONE &&
    (uri?.startsWith("http://", ignoreCase = true) == true ||
        uri?.startsWith("https://", ignoreCase = true) == true)

internal fun shouldEndDouyinOfflinePrefix(
    unavailable: Boolean,
    readiness: DouyinOfflineReadiness,
    hasPlayed: Boolean
): Boolean = unavailable && readiness == DouyinOfflineReadiness.PREFIX_READY && hasPlayed

internal fun douyinFeedLoadNotice(isSlow: Boolean, deadlineExceeded: Boolean): String? = when {
    deadlineExceeded -> "内容加载较慢，请稍候"
    isSlow -> "内容加载较慢，请稍候"
    else -> null
}
