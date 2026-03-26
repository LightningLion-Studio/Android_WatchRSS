package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliPlaybackProgress
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class BiliPlaybackSourceKind {
    PREVIEW,
    REMOTE
}

data class BiliPlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val kind: BiliPlaybackSourceKind,
    val cacheKey: String? = null
)

data class BiliPlayerUiState(
    val isLoading: Boolean = true,
    val initialSource: BiliPlaybackSource? = null,
    val upgradeSource: BiliPlaybackSource? = null,
    val isUpgradeLoading: Boolean = false,
    val upgradeErrorMessage: String? = null,
    val resumePositionMs: Int = 0,
    val message: String? = null,
    val title: String? = null,
    val owner: String? = null,
    val pageTitle: String? = null
)

class BiliPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BiliRepositoryContract
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliPlayerUiState())
    val uiState: StateFlow<BiliPlayerUiState> = _uiState

    private val aid: Long? = savedStateHandle.get<String>("aid")?.toLongOrNull()
    private val bvid: String? = savedStateHandle.get<String>("bvid")?.takeIf { it.isNotBlank() }
    private val cid: Long? = savedStateHandle.get<String>("cid")?.toLongOrNull()
    private var resolvedAid: Long? = aid
    private var resolvedBvid: String? = bvid
    private var resolvedCid: Long? = cid
    private val titleArg: String? = savedStateHandle.get<String>("title")?.trim()?.takeIf { it.isNotBlank() }
    private val ownerArg: String? = savedStateHandle.get<String>("owner")?.trim()?.takeIf { it.isNotBlank() }
    private val pageTitleArg: String? = savedStateHandle.get<String>("pageTitle")?.trim()?.takeIf { it.isNotBlank() }
    private var loadJob: Job? = null
    private var loadGeneration: Long = 0L
    private var lastPersistedPositionMs: Long? = null
    private var playbackCompleted = false
    private var playbackErrorRecoveryConsumed = false
    private var playbackErrorRecoveryInFlight = false

    init {
        _uiState.update {
            it.copy(
                title = titleArg,
                owner = ownerArg,
                pageTitle = pageTitleArg
            )
        }
        loadPlayUrl()
    }

    fun loadPlayUrl() {
        loadJob?.cancel()
        playbackErrorRecoveryConsumed = false
        playbackErrorRecoveryInFlight = false
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            setLoadingState()
            val targetResolution = resolvePlaybackTargetFromDetail(
                preferredCid = resolvedCid ?: cid
            )
            if (generation != loadGeneration) return@launch
            val initialTarget = when (targetResolution) {
                is PlaybackTargetResolutionOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = formatBiliError(targetResolution.code, targetResolution.message)
                        )
                    }
                    return@launch
                }
                is PlaybackTargetResolutionOutcome.Success -> targetResolution.resolution.target
            }

            var playbackAttempt = resolvePlaybackAttempt(initialTarget)
            if (generation != loadGeneration) return@launch
            if (playbackAttempt is PlaybackAttemptOutcome.Failure) {
                if (playbackAttempt.code == BiliErrorCodes.REQUEST_FAILED) {
                    delay(REQUEST_FAILURE_RETRY_DELAY_MS)
                    if (generation != loadGeneration) return@launch
                    playbackAttempt = resolvePlaybackAttempt(initialTarget)
                }
            }
            if (generation != loadGeneration) return@launch

            when (playbackAttempt) {
                is PlaybackAttemptOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = formatBiliError(playbackAttempt.code, playbackAttempt.message)
                        )
                    }
                }
                is PlaybackAttemptOutcome.Success -> applyPlaybackAttempt(playbackAttempt)
            }
        }
    }

    fun onPlaybackError(): Boolean {
        val currentTarget = currentPlaybackTarget() ?: return false
        if (playbackErrorRecoveryInFlight) return true
        if (playbackErrorRecoveryConsumed) return false

        loadJob?.cancel()
        playbackErrorRecoveryConsumed = true
        playbackErrorRecoveryInFlight = true
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            try {
                setLoadingState()
                val targetResolution = resolvePlaybackTargetFromDetail(
                    preferredCid = currentTarget.cid
                )
                if (generation != loadGeneration) return@launch
                val recoveredTarget = when (targetResolution) {
                    is PlaybackTargetResolutionOutcome.Failure -> null
                    is PlaybackTargetResolutionOutcome.Success ->
                        targetResolution.resolution.target.takeIf { it.cid != currentTarget.cid }
                }
                if (recoveredTarget == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = PLAYBACK_ERROR_MESSAGE
                        )
                    }
                    return@launch
                }

                when (val playbackAttempt = resolvePlaybackAttempt(recoveredTarget)) {
                    is PlaybackAttemptOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = formatBiliError(playbackAttempt.code, playbackAttempt.message)
                            )
                        }
                    }
                    is PlaybackAttemptOutcome.Success -> applyPlaybackAttempt(playbackAttempt)
                }
            } finally {
                playbackErrorRecoveryInFlight = false
            }
        }
        return true
    }

    private suspend fun resolvePlaybackAttempt(target: PlaybackTarget): PlaybackAttemptOutcome {
        val result = repository.resolvePlaybackSource(
            aid = target.aid,
            bvid = target.bvid,
            cid = target.cid,
            qn = DEFAULT_PLAYBACK_QUALITY
        )
        if (!result.isSuccess) {
            return PlaybackAttemptOutcome.Failure(code = result.code, message = result.message)
        }

        val resolvedSource = result.data
            ?: return PlaybackAttemptOutcome.Failure(code = BiliErrorCodes.PLAY_URL_EMPTY)
        val playbackProgress = repository.readPlaybackProgress(
            aid = target.aid,
            bvid = target.bvid,
            cid = target.cid
        )
        val resumePositionMs = playbackProgress?.positionMs
            ?.coerceAtLeast(0L)
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0
        return PlaybackAttemptOutcome.Success(
            target = target,
            source = BiliPlaybackSource(
                url = resolvedSource.url,
                headers = resolvedSource.headers,
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = resolvedSource.cacheKey
            ),
            resumePositionMs = resumePositionMs
        )
    }

    private fun applyPlaybackAttempt(success: PlaybackAttemptOutcome.Success) {
        resolvedAid = success.target.aid ?: resolvedAid
        resolvedBvid = success.target.bvid ?: resolvedBvid
        resolvedCid = success.target.cid
        lastPersistedPositionMs = success.resumePositionMs.toLong()
        playbackCompleted = false
        _uiState.update {
            it.copy(
                isLoading = false,
                initialSource = success.source,
                upgradeSource = null,
                isUpgradeLoading = false,
                upgradeErrorMessage = null,
                resumePositionMs = success.resumePositionMs,
                message = null
            )
        }
    }

    fun onPlaybackProgress(positionMs: Int, durationMs: Int, force: Boolean = false) {
        if (playbackCompleted) return
        val target = currentPlaybackTarget() ?: return
        val safePositionMs = positionMs.toLong().coerceAtLeast(0L)
        val safeDurationMs = durationMs.toLong().coerceAtLeast(0L)
        if (safePositionMs <= 0L && safeDurationMs <= 0L) return
        val lastPersisted = lastPersistedPositionMs
        if (!force && lastPersisted != null && abs(safePositionMs - lastPersisted) < PLAYBACK_PROGRESS_SAVE_STEP_MS) {
            return
        }
        lastPersistedPositionMs = safePositionMs
        viewModelScope.launch {
            repository.writePlaybackProgress(
                BiliPlaybackProgress(
                    aid = target.aid,
                    bvid = target.bvid,
                    cid = target.cid,
                    positionMs = safePositionMs,
                    durationMs = safeDurationMs
                )
            )
        }
    }

    fun onPlaybackEnded() {
        val target = currentPlaybackTarget() ?: return
        playbackCompleted = true
        lastPersistedPositionMs = 0L
        _uiState.update { it.copy(resumePositionMs = 0) }
        viewModelScope.launch {
            repository.clearPlaybackProgress(
                aid = target.aid,
                bvid = target.bvid,
                cid = target.cid
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun setLoadingState() {
        _uiState.update {
            it.copy(
                isLoading = true,
                initialSource = null,
                upgradeSource = null,
                isUpgradeLoading = false,
                upgradeErrorMessage = null,
                resumePositionMs = 0,
                message = null
            )
        }
    }

    private suspend fun resolvePlaybackTargetFromDetail(preferredCid: Long?): PlaybackTargetResolutionOutcome {
        val safeRequestAid = resolvedAid ?: aid
        val safeRequestBvid = resolvedBvid ?: bvid
        if (safeRequestAid == null && safeRequestBvid.isNullOrBlank()) {
            return PlaybackTargetResolutionOutcome.Failure(BiliErrorCodes.PLAY_PARAM_MISSING)
        }
        val result = repository.fetchVideoDetail(aid = safeRequestAid, bvid = safeRequestBvid)
        if (!result.isSuccess) {
            return PlaybackTargetResolutionOutcome.Failure(result.code, result.message)
        }
        val detail = result.data
            ?: return PlaybackTargetResolutionOutcome.Failure(BiliErrorCodes.REQUEST_FAILED, "empty_detail")
        val safeAid = detail.item.aid ?: resolvedAid ?: aid
        val safeBvid = detail.item.bvid?.trim()?.takeIf { it.isNotBlank() } ?: resolvedBvid ?: bvid
        val latestPlayback = repository.readLatestPlaybackProgress(
            aid = safeAid,
            bvid = safeBvid
        )
        val safeCid = selectPlaybackCid(
            detail = detail,
            preferredCid = preferredCid,
            resumeCid = latestPlayback?.cid
        ) ?: return PlaybackTargetResolutionOutcome.Failure(BiliErrorCodes.PLAY_PARAM_MISSING)
        resolvedAid = safeAid
        resolvedBvid = safeBvid
        resolvedCid = safeCid
        applyDetailMeta(detail, safeCid)
        return PlaybackTargetResolutionOutcome.Success(
            PlaybackTargetResolution(
                target = PlaybackTarget(
                    aid = safeAid,
                    bvid = safeBvid,
                    cid = safeCid
                )
            )
        )
    }

    private fun selectPlaybackCid(
        detail: BiliVideoDetail,
        preferredCid: Long?,
        resumeCid: Long?
    ): Long? {
        val validPageCids = detail.pages.mapNotNull { it.cid }
        return when {
            preferredCid != null && validPageCids.contains(preferredCid) -> preferredCid
            resumeCid != null && validPageCids.contains(resumeCid) -> resumeCid
            validPageCids.isNotEmpty() -> validPageCids.first()
            else -> detail.item.cid
        }
    }

    private fun applyDetailMeta(detail: BiliVideoDetail, cid: Long?) {
        val title = detail.item.title?.trim()?.takeIf { it.isNotBlank() }
        val owner = detail.item.owner?.name?.trim()?.takeIf { it.isNotBlank() }
        val pageTitle = cid?.let { targetCid ->
            detail.pages.firstOrNull { it.cid == targetCid }?.part?.trim()?.takeIf { it.isNotBlank() }
        }
        _uiState.update { current ->
            current.copy(
                title = current.title ?: title,
                owner = current.owner ?: owner,
                pageTitle = pageTitle ?: current.pageTitle
            )
        }
    }

    private fun currentPlaybackTarget(): PlaybackTarget? {
        val safeCid = resolvedCid ?: cid ?: return null
        return PlaybackTarget(
            aid = resolvedAid ?: aid,
            bvid = resolvedBvid ?: bvid,
            cid = safeCid
        )
    }

    companion object {
        private const val DEFAULT_PLAYBACK_QUALITY = 32
        private const val PLAYBACK_PROGRESS_SAVE_STEP_MS = 5_000L
        private const val PLAYBACK_ERROR_MESSAGE = "播放失败"
        private const val REQUEST_FAILURE_RETRY_DELAY_MS = 750L
    }

    private data class PlaybackTarget(
        val aid: Long?,
        val bvid: String?,
        val cid: Long
    )

    private data class PlaybackTargetResolution(
        val target: PlaybackTarget
    )

    private sealed interface PlaybackTargetResolutionOutcome {
        data class Success(val resolution: PlaybackTargetResolution) : PlaybackTargetResolutionOutcome

        data class Failure(
            val code: Int,
            val message: String? = null
        ) : PlaybackTargetResolutionOutcome
    }

    private sealed interface PlaybackAttemptOutcome {
        data class Success(
            val target: PlaybackTarget,
            val source: BiliPlaybackSource,
            val resumePositionMs: Int
        ) : PlaybackAttemptOutcome

        data class Failure(
            val code: Int,
            val message: String? = null
        ) : PlaybackAttemptOutcome
    }
}
