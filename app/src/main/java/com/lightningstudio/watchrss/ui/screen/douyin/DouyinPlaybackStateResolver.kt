package com.lightningstudio.watchrss.ui.screen.douyin

internal data class DouyinResolvedPlaybackState(
    val mediaUri: String?,
    val remoteResolvedAtMs: Long
)

internal enum class DouyinPlaybackFailureAction {
    Retry,
    AutoSkip
}

internal fun resolveDouyinPlaybackState(
    currentUri: String?,
    currentRemoteResolvedAtMs: Long,
    localUri: String?,
    remoteUri: String?,
    remoteResolvedAtMs: Long
): DouyinResolvedPlaybackState {
    val normalizedCurrent = currentUri?.takeIf { it.isNotBlank() }
    val normalizedLocal = localUri?.takeIf { it.isNotBlank() }
    val normalizedRemote = remoteUri?.takeIf { it.isNotBlank() }
    val currentIsLocal = normalizedCurrent?.startsWith("file://") == true

    return when {
        normalizedCurrent.isNullOrBlank() -> DouyinResolvedPlaybackState(
            mediaUri = normalizedLocal ?: normalizedRemote,
            remoteResolvedAtMs = remoteResolvedAtMs
        )

        currentIsLocal && normalizedCurrent != normalizedLocal -> DouyinResolvedPlaybackState(
            mediaUri = normalizedLocal ?: normalizedRemote,
            remoteResolvedAtMs = remoteResolvedAtMs
        )

        !currentIsLocal && !normalizedLocal.isNullOrBlank() -> DouyinResolvedPlaybackState(
            mediaUri = normalizedLocal,
            remoteResolvedAtMs = remoteResolvedAtMs
        )

        !currentIsLocal &&
            (normalizedCurrent != normalizedRemote || currentRemoteResolvedAtMs != remoteResolvedAtMs) -> {
            DouyinResolvedPlaybackState(
                mediaUri = normalizedRemote,
                remoteResolvedAtMs = remoteResolvedAtMs
            )
        }

        else -> DouyinResolvedPlaybackState(
            mediaUri = normalizedCurrent,
            remoteResolvedAtMs = currentRemoteResolvedAtMs
        )
    }
}

internal fun resolveDouyinPlaybackFailureAction(
    retryCount: Int,
    maxAutoRetryCount: Int,
    hasValidatedInternetConnection: Boolean,
    hasNextItem: Boolean
): DouyinPlaybackFailureAction {
    return when {
        retryCount < maxAutoRetryCount -> DouyinPlaybackFailureAction.Retry
        else -> DouyinPlaybackFailureAction.AutoSkip
    }
}

internal fun buildDouyinPlaybackPrepareKey(
    mediaUri: String?,
    remoteResolvedAtMs: Long,
    attemptNonce: Int = 0
): String? {
    val normalizedMediaUri = mediaUri?.takeIf { it.isNotBlank() } ?: return null
    return if (normalizedMediaUri.startsWith("file://")) {
        "$normalizedMediaUri#$attemptNonce"
    } else {
        "$normalizedMediaUri#$remoteResolvedAtMs#$attemptNonce"
    }
}
