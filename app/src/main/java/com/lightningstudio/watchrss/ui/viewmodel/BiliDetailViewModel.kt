package com.lightningstudio.watchrss.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BiliDetailUiState(
    val isLoading: Boolean = true,
    val detail: BiliVideoDetail? = null,
    val selectedPageIndex: Int = 0,
    val isLiked: Boolean = false,
    val isCoined: Boolean = false,
    val isFavorited: Boolean = false,
    val isWatchLater: Boolean = false,
    val message: String? = null
)

class BiliDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BiliRepositoryContract,
    private val rssRepository: RssRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliDetailUiState())
    val uiState: StateFlow<BiliDetailUiState> = _uiState

    private val aid: Long? = savedStateHandle.get<String>("aid")?.toLongOrNull()
    private val bvid: String? = savedStateHandle.get<String>("bvid")?.takeIf { it.isNotBlank() }
    private val cidArg: Long? = savedStateHandle.get<String>("cid")?.toLongOrNull()
    private val rssItemId: Long? = savedStateHandle.get<String>("rssItemId")?.toLongOrNull()
    private var warmupJob: Job? = null
    private var warmupTarget: BiliTarget? = null

    init {
        observeLocalItem()
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val result = repository.fetchVideoDetail(aid = aid, bvid = bvid)
            if (result.isSuccess) {
                val detail = result.data
                val selected = resolveInitialPageIndex(detail?.pages, cidArg)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = detail,
                        selectedPageIndex = selected,
                        message = null
                    )
                }
                scheduleWarmupForSelection()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = formatBiliError(result.code)
                    )
                }
            }
        }
    }

    fun selectPage(index: Int) {
        _uiState.update { it.copy(selectedPageIndex = index) }
        scheduleWarmupForSelection()
    }

    fun like() {
        val safeAid = currentAid() ?: return
        val target = currentInteractionTarget()
        if (_uiState.value.isLiked) {
            viewModelScope.launch {
                val result = repository.like(safeAid, like = false)
                if (result.isSuccess) {
                    _uiState.update { it.copy(isLiked = false, message = null) }
                } else {
                    _uiState.update { it.copy(message = formatBiliError(result.code)) }
                }
            }
            return
        }
        _uiState.update { it.copy(isLiked = true, message = null) }
        viewModelScope.launch {
            val ready = repository.ensureInteractionReady(
                aid = target?.aid ?: safeAid,
                bvid = target?.bvid,
                cid = target?.cid
            )
            if (ready.isSuccess) {
                repository.like(safeAid, like = true)
            }
        }
    }

    fun coin() {
        if (_uiState.value.isCoined) return
        val safeAid = currentAid() ?: return
        val target = currentInteractionTarget()
        _uiState.update { it.copy(isCoined = true, message = null) }
        viewModelScope.launch {
            val ready = repository.ensureInteractionReady(
                aid = target?.aid ?: safeAid,
                bvid = target?.bvid,
                cid = target?.cid
            )
            if (ready.isSuccess) {
                val result = repository.coin(safeAid)
                if (result.isSuccess && result.data == true) {
                    _uiState.update { it.copy(isLiked = true) }
                }
            }
        }
    }

    fun favorite() {
        val safeAid = currentAid() ?: return
        viewModelScope.launch {
            val nextFavorited = !_uiState.value.isFavorited
            val result = repository.favorite(safeAid, add = nextFavorited)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isFavorited = nextFavorited,
                        message = if (nextFavorited) "已收藏" else "已取消收藏"
                    )
                }
                syncLocalSaved(SaveType.FAVORITE, nextFavorited)
            } else {
                _uiState.update { it.copy(message = formatBiliError(result.code)) }
            }
        }
    }

    fun addToWatchLater() {
        val target = currentInteractionTarget()
        viewModelScope.launch {
            val result = repository.addToView(aid = target?.aid ?: aid, bvid = target?.bvid ?: bvid)
            if (result.isSuccess) {
                _uiState.update { it.copy(isWatchLater = true, message = "已加入稍后再看") }
                syncLocalSaved(SaveType.WATCH_LATER, true)
            } else {
                _uiState.update { it.copy(message = formatBiliError(result.code)) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun selectedCid(): Long? {
        val detail = _uiState.value.detail ?: return cidArg
        val pages = detail.pages
        if (pages.isEmpty()) return cidArg ?: detail.item.cid
        val index = _uiState.value.selectedPageIndex.coerceIn(0, pages.lastIndex)
        return pages[index].cid ?: cidArg ?: detail.item.cid
    }

    fun selectedPage(): BiliPage? {
        val detail = _uiState.value.detail ?: return null
        val pages = detail.pages
        if (pages.isEmpty()) return null
        val index = _uiState.value.selectedPageIndex.coerceIn(0, pages.lastIndex)
        return pages[index]
    }

    private fun resolveInitialPageIndex(pages: List<BiliPage>?, cid: Long?): Int {
        val safePages = pages ?: return 0
        if (cid == null) return 0
        val index = safePages.indexOfFirst { it.cid == cid }
        return if (index >= 0) index else 0
    }

    private suspend fun syncLocalSaved(saveType: SaveType, saved: Boolean) {
        val external = buildExternalSavedItem() ?: return
        rssRepository.syncExternalSavedItem(external, saveType, saved)
        val target = currentInteractionTarget()
        if (saved) {
            repository.cachePreviewClip(aid = target?.aid ?: aid, bvid = target?.bvid ?: bvid, cid = target?.cid)
        } else {
            repository.clearCachedPreview(aid = target?.aid ?: aid, bvid = target?.bvid ?: bvid, cid = target?.cid)
        }
    }

    private fun buildExternalSavedItem(): ExternalSavedItem? {
        val detail = _uiState.value.detail
        val item = detail?.item
        val safeBvid = item?.bvid ?: bvid
        val safeAid = item?.aid ?: aid
        val title = item?.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: safeBvid?.let { "BV号 $it" }
            ?: safeAid?.let { "av$it" }
            ?: "哔哩哔哩视频"
        val link = repository.savedLink(safeBvid, safeAid, selectedCid())
        val guid = when {
            !safeBvid.isNullOrBlank() -> "bili:$safeBvid"
            safeAid != null -> "bili:av$safeAid"
            !link.isNullOrBlank() -> "bili:$link"
            else -> null
        }
        val owner = item?.owner?.name?.trim().takeUnless { it.isNullOrBlank() }
        val description = detail?.desc?.trim()?.ifBlank { null }
            ?: owner?.let { "UP主：$it" }
        val preview = RssPreviewItem(
            title = title,
            description = description,
            content = description,
            link = link,
            guid = guid,
            pubDate = null,
            imageUrl = item?.cover,
            audioUrl = null,
            videoUrl = null
        )
        return ExternalSavedItem(
            channelUrl = BuiltinChannelType.BILI.url,
            item = preview
        )
    }

    private fun observeLocalItem() {
        val itemId = rssItemId ?: return
        viewModelScope.launch {
            rssRepository.observeItem(itemId).collect { item ->
                if (item == null) return@collect
                val fallback = buildLocalDetail(item)
                var shouldWarmup = false
                _uiState.update { current ->
                    if (current.detail != null) {
                        current
                    } else {
                        shouldWarmup = true
                        current.copy(detail = fallback)
                    }
                }
                if (shouldWarmup) {
                    scheduleWarmupForSelection()
                }
            }
        }
    }

    private fun scheduleWarmupForSelection() {
        val target = currentInteractionTarget() ?: return
        if (warmupTarget == target && warmupJob?.isActive == true) {
            return
        }
        warmupTarget = target
        warmupJob?.cancel()
        warmupJob = viewModelScope.launch {
            repository.warmupDetailPreview(
                aid = target.aid,
                bvid = target.bvid,
                cid = target.cid
            )
        }
    }

    private fun currentAid(): Long? = _uiState.value.detail?.item?.aid ?: aid

    private fun currentBvid(): String? = _uiState.value.detail?.item?.bvid ?: bvid

    private fun currentInteractionTarget(): BiliTarget? {
        val safeAid = currentAid()
        val safeBvid = currentBvid()
        val safeCid = selectedCid()
        if (safeAid == null && safeBvid.isNullOrBlank()) return null
        return BiliTarget(
            aid = safeAid,
            bvid = safeBvid,
            cid = safeCid
        )
    }

    private fun buildLocalDetail(item: RssItem): BiliVideoDetail {
        val target = parseBiliTarget(item.link)
        val safeAid = aid ?: target?.aid
        val safeBvid = bvid ?: target?.bvid
        val safeCid = cidArg ?: target?.cid
        val title = item.title.trim().ifBlank {
            safeBvid?.let { "BV号 $it" }
                ?: safeAid?.let { "av$it" }
                ?: "哔哩哔哩视频"
        }
        val rawDesc = item.description?.trim().takeUnless { it.isNullOrBlank() }
        val contentDesc = item.content?.trim().takeUnless { it.isNullOrBlank() }
        val ownerName = parseOwnerName(rawDesc)
        val desc = contentDesc ?: rawDesc?.takeUnless { it.startsWith("UP主：") }
        val owner = ownerName?.let { BiliOwner(name = it) }
        val previewItem = BiliItem(
            aid = safeAid,
            bvid = safeBvid,
            cid = safeCid,
            title = title,
            cover = item.imageUrl,
            owner = owner
        )
        return BiliVideoDetail(
            item = previewItem,
            desc = desc,
            pages = emptyList()
        )
    }

    private fun parseOwnerName(description: String?): String? {
        val raw = description?.trim().orEmpty()
        if (!raw.startsWith("UP主：")) return null
        return raw.removePrefix("UP主：").trim().ifBlank { null }
    }

    private data class BiliTarget(
        val aid: Long?,
        val bvid: String?,
        val cid: Long?
    )

    private fun parseBiliTarget(link: String?): BiliTarget? {
        if (link.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains("bilibili.com")) return null
        val segments = uri.pathSegments
        val videoIndex = segments.indexOf("video")
        if (videoIndex < 0 || videoIndex >= segments.lastIndex) return null
        val rawId = segments[videoIndex + 1]
        val cid = uri.getQueryParameter("cid")?.toLongOrNull()
        return when {
            rawId.startsWith("BV", ignoreCase = true) -> {
                BiliTarget(aid = null, bvid = rawId, cid = cid)
            }
            rawId.startsWith("av", ignoreCase = true) -> {
                val aid = rawId.drop(2).toLongOrNull()
                BiliTarget(aid = aid, bvid = null, cid = cid)
            }
            else -> null
        }
    }
}
