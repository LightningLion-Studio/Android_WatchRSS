package com.lightningstudio.watchrss.ui.screen.douyin

import com.lightningstudio.watchrss.data.douyin.resolveDouyinLookaheadItemIndices
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DouyinImmersiveScreenPagingTest {

    @Test
    fun resolveDouyinEntryStartIndex_usesResumeTargetAsFirstVideoAfterCover() {
        assertEquals(2, resolveDouyinEntryStartIndex(currentPage = 3, itemCount = 6))
    }

    @Test
    fun resolveDouyinPageCount_keepsOnlyResumeWindowPlusCover() {
        assertEquals(4, resolveDouyinPageCount(itemCount = 6, entryStartIndex = 3))
    }

    @Test
    fun resolveDouyinPagerPage_mapsResumeTargetToFirstVideoPage() {
        assertEquals(
            1,
            resolveDouyinPagerPage(
                currentPage = 4,
                entryStartIndex = 3,
                pageCount = 4
            )
        )
    }

    @Test
    fun resolveDouyinAbsolutePage_restoresOriginalFeedIndexFromPagerPage() {
        assertEquals(4, resolveDouyinAbsolutePage(pagerPage = 1, entryStartIndex = 3))
        assertEquals(6, resolveDouyinAbsolutePage(pagerPage = 3, entryStartIndex = 3))
    }

    @Test
    fun resolveDouyinSettledPage_keepsResumeTargetWhenLeavingCover() {
        assertEquals(3, resolveDouyinSettledPage(pagerPage = 1, entryStartIndex = 2))
    }

    @Test
    fun resolveDouyinSettledPageOrNull_ignoresIntermediatePagesWhileScrollIsActive() {
        assertNull(
            resolveDouyinSettledPageOrNull(
                isScrollInProgress = true,
                pagerPage = 2,
                entryStartIndex = 3
            )
        )
    }

    @Test
    fun resolveDouyinSettledPageOrNull_returnsAbsolutePageAfterScrollStops() {
        assertEquals(
            5,
            resolveDouyinSettledPageOrNull(
                isScrollInProgress = false,
                pagerPage = 2,
                entryStartIndex = 3
            )
        )
    }

    @Test
    fun resolveDouyinItemIndexForPagerPage_resolvesVisibleWindowToBackedItemIndex() {
        assertNull(resolveDouyinItemIndexForPagerPage(pagerPage = 0, entryStartIndex = 2))
        assertEquals(2, resolveDouyinItemIndexForPagerPage(pagerPage = 1, entryStartIndex = 2))
        assertEquals(4, resolveDouyinItemIndexForPagerPage(pagerPage = 3, entryStartIndex = 2))
    }

    @Test
    fun resolveDouyinPlaybackAnchorPagerPage_keepsPreviousPageUntilScrollSettles() {
        assertEquals(
            1,
            resolveDouyinPlaybackAnchorPagerPage(
                isScrollInProgress = true,
                pagerPage = 2,
                settledPagerPage = 1
            )
        )
        assertEquals(
            2,
            resolveDouyinPlaybackAnchorPagerPage(
                isScrollInProgress = false,
                pagerPage = 2,
                settledPagerPage = 1
            )
        )
    }

    @Test
    fun resolveDouyinStandbyItemIndex_prefersImmediateNextItemOnly() {
        assertEquals(3, resolveDouyinStandbyItemIndex(activeItemIndex = 2, itemCount = 6))
        assertNull(resolveDouyinStandbyItemIndex(activeItemIndex = 5, itemCount = 6))
        assertNull(resolveDouyinStandbyItemIndex(activeItemIndex = null, itemCount = 6))
    }

    @Test
    fun resolveDouyinLookaheadItemIndices_returnsNextTwoPlayableItems() {
        assertEquals(
            listOf(3, 4),
            resolveDouyinLookaheadItemIndices(activeItemIndex = 2, itemCount = 6, extraCount = 2)
        )
        assertEquals(
            listOf(5),
            resolveDouyinLookaheadItemIndices(activeItemIndex = 4, itemCount = 6, extraCount = 2)
        )
        assertEquals(
            emptyList<Int>(),
            resolveDouyinLookaheadItemIndices(activeItemIndex = null, itemCount = 6, extraCount = 2)
        )
    }

    @Test
    fun resolveDouyinPreparedBackgroundItemIndices_onlyUsesForwardItems() {
        assertEquals(
            listOf(3, 4, 5),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 2,
                itemCount = 6,
                backgroundCount = 3
            )
        )
        assertEquals(
            listOf(5),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 4,
                itemCount = 6,
                backgroundCount = 3
            )
        )
    }

    @Test
    fun resolveDouyinPreparedBackgroundItemIndices_ignoresRecentHistoryAndBackwardFallbacks() {
        assertEquals(
            listOf(4, 5),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 3,
                itemCount = 6,
                backgroundCount = 3,
            )
        )
        assertEquals(
            listOf(2, 3, 4),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 1,
                itemCount = 6,
                backgroundCount = 3,
            )
        )
    }

    @Test
    fun resolveDouyinPreparedBackgroundItemIndices_withSingleSlotLeavesStandbyEmptyWithoutNextItem() {
        assertEquals(
            listOf(3),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 2,
                itemCount = 6,
                backgroundCount = 1
            )
        )
        assertEquals(
            emptyList<Int>(),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 2,
                itemCount = 3,
                backgroundCount = 1
            )
        )
    }

    @Test
    fun resolveDouyinPreparedBackgroundItemIndices_titlePageOnlyPrimesForwardItems() {
        assertEquals(
            listOf(3, 4, 5),
            resolveDouyinPreparedBackgroundItemIndices(
                activeItemIndex = 2,
                itemCount = 6,
                backgroundCount = 3
            )
        )
    }

    @Test
    fun resolveDouyinPlaybackDebugContext_usesSettledCurrentPageAndKeepsNextItemVisible() {
        val items = listOf(
            sampleDouyinStreamItem(awemeId = "aweme-a"),
            sampleDouyinStreamItem(awemeId = "aweme-b"),
            sampleDouyinStreamItem(awemeId = "aweme-c")
        )

        assertEquals(
            "aweme-b" to "aweme-c",
            resolveDouyinPlaybackDebugContext(
                items = items,
                currentPage = 2,
                showTitlePage = false,
                entryStartIndex = 0
            )
        )
    }

    @Test
    fun resolveDouyinPlaybackDebugContext_titlePageReportsResumeTargetAsNextItem() {
        val items = listOf(
            sampleDouyinStreamItem(awemeId = "aweme-a"),
            sampleDouyinStreamItem(awemeId = "aweme-b"),
            sampleDouyinStreamItem(awemeId = "aweme-c")
        )

        assertEquals(
            "aweme-b" to "aweme-c",
            resolveDouyinPlaybackDebugContext(
                items = items,
                currentPage = 2,
                showTitlePage = true,
                entryStartIndex = 1
            )
        )
    }

    @Test
    fun shouldPromoteDouyinStandbySlot_requiresTargetMatchAndPreparedFrame() {
        assertEquals(
            false,
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = "aweme-next",
                targetAwemeId = "aweme-next",
                standbyPrepareKey = "uri#1",
                targetPrepareKey = "uri#1",
                isReady = false,
                hasRenderedFirstFrame = false,
                hasError = false
            )
        )
        assertEquals(
            true,
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = "aweme-next",
                targetAwemeId = "aweme-next",
                standbyPrepareKey = "uri#1",
                targetPrepareKey = "uri#1",
                isReady = true,
                hasRenderedFirstFrame = false,
                hasError = false
            )
        )
        assertEquals(
            true,
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = "aweme-next",
                targetAwemeId = "aweme-next",
                standbyPrepareKey = "uri#1",
                targetPrepareKey = "uri#1",
                isReady = false,
                hasRenderedFirstFrame = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = "aweme-next",
                targetAwemeId = "aweme-next",
                standbyPrepareKey = "uri#1",
                targetPrepareKey = "uri#1",
                isReady = false,
                hasRenderedFirstFrame = false,
                hasError = true
            )
        )
        assertEquals(
            false,
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = "aweme-next",
                targetAwemeId = "aweme-next",
                standbyPrepareKey = "uri#1",
                targetPrepareKey = "uri#2",
                isReady = true,
                hasRenderedFirstFrame = true,
                hasError = false
            )
        )
    }

    @Test
    fun shouldShowDouyinLoadingIndicator_hidesSpinnerWhilePlaybackContinues() {
        assertEquals(
            false,
            shouldShowDouyinLoadingIndicator(
                isActive = true,
                isBuffering = true,
                isPlaying = true,
                hasError = false
            )
        )
        assertEquals(
            true,
            shouldShowDouyinLoadingIndicator(
                isActive = true,
                isBuffering = true,
                isPlaying = false,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinLoadingIndicator(
                isActive = true,
                isBuffering = true,
                isPlaying = false,
                hasError = true
            )
        )
        assertEquals(
            false,
            shouldShowDouyinLoadingIndicator(
                isActive = true,
                isBuffering = true,
                isPlaying = false,
                hasError = false,
                isScrollInProgress = true
            )
        )
    }

    @Test
    fun shouldAutoPlayDouyinActiveSlot_onlyResumesLifecyclePauseAutomatically() {
        assertEquals(
            true,
            shouldAutoPlayDouyinActiveSlot(
                showTitlePage = false,
                pausedByGesture = false,
                pausedByLifecycle = false,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldAutoPlayDouyinActiveSlot(
                showTitlePage = false,
                pausedByGesture = true,
                pausedByLifecycle = false,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldAutoPlayDouyinActiveSlot(
                showTitlePage = false,
                pausedByGesture = false,
                pausedByLifecycle = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldAutoPlayDouyinActiveSlot(
                showTitlePage = true,
                pausedByGesture = false,
                pausedByLifecycle = false,
                hasError = false
            )
        )
    }

    @Test
    fun shouldPlayBoundDouyinForegroundSlot_keepsActiveVideoPlayingWhileLookaheadRebinds() {
        assertEquals(
            true,
            shouldPlayBoundDouyinForegroundSlot(
                showTitlePage = false,
                autoplayEnabled = true,
                pausedByGesture = false,
                pausedByLifecycle = false,
                hasError = false,
                isScrollInProgress = false
            )
        )
    }

    @Test
    fun shouldPlayBoundDouyinForegroundSlot_stopsPlaybackWhenAutoplayGateCloses() {
        assertEquals(
            false,
            shouldPlayBoundDouyinForegroundSlot(
                showTitlePage = false,
                autoplayEnabled = false,
                pausedByGesture = false,
                pausedByLifecycle = false,
                hasError = false,
                isScrollInProgress = false
            )
        )
        assertEquals(
            false,
            shouldPlayBoundDouyinForegroundSlot(
                showTitlePage = false,
                autoplayEnabled = true,
                pausedByGesture = false,
                pausedByLifecycle = false,
                hasError = false,
                isScrollInProgress = true
            )
        )
    }

    @Test
    fun shouldUseDouyinImmediateEntryPlayback_onlyTargetsFirstVideoAfterCover() {
        assertEquals(
            true,
            shouldUseDouyinImmediateEntryPlayback(
                activePage = 1,
                activeItemIndex = 3,
                entryStartIndex = 3
            )
        )
        assertEquals(
            false,
            shouldUseDouyinImmediateEntryPlayback(
                activePage = 2,
                activeItemIndex = 4,
                entryStartIndex = 3
            )
        )
    }

    @Test
    fun shouldUpdateDouyinPlaybackWindow_keepsDeferredTitlePageResumeWarm() {
        assertEquals(
            true,
            shouldUpdateDouyinPlaybackWindow(
                showTitlePage = true,
                currentPage = 3
            )
        )
        assertEquals(
            true,
            shouldUpdateDouyinPlaybackWindow(
                showTitlePage = true,
                currentPage = 0
            )
        )
        assertEquals(
            true,
            shouldUpdateDouyinPlaybackWindow(
                showTitlePage = false,
                currentPage = 3
            )
        )
    }

    @Test
    fun shouldShowDouyinPosterFallback_showsForTargetPageAndNearbyScrollPagesWithoutFrame() {
        assertEquals(
            true,
            shouldShowDouyinPosterFallback(
                pagerPage = 1,
                currentPagerPage = 1,
                playbackAnchorPagerPage = 1,
                isScrollInProgress = false,
                isVideoVisible = false,
                hasFirstFramePoster = true,
                hasError = false
            )
        )
        assertEquals(
            true,
            shouldShowDouyinPosterFallback(
                pagerPage = 2,
                currentPagerPage = 2,
                playbackAnchorPagerPage = 1,
                isScrollInProgress = true,
                isVideoVisible = false,
                hasFirstFramePoster = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinPosterFallback(
                pagerPage = 1,
                currentPagerPage = 1,
                playbackAnchorPagerPage = 1,
                isScrollInProgress = false,
                isVideoVisible = true,
                hasFirstFramePoster = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinPosterFallback(
                pagerPage = 3,
                currentPagerPage = 1,
                playbackAnchorPagerPage = 1,
                isScrollInProgress = true,
                isVideoVisible = false,
                hasFirstFramePoster = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinPosterFallback(
                pagerPage = 2,
                currentPagerPage = 2,
                playbackAnchorPagerPage = 1,
                isScrollInProgress = true,
                isVideoVisible = false,
                hasFirstFramePoster = false,
                hasError = false
            )
        )
    }

    @Test
    fun shouldShowDouyinFirstVideoStartupLoadingIndicator_onlyShowsBeforeFirstVideoStarts() {
        assertEquals(
            true,
            shouldShowDouyinFirstVideoStartupLoadingIndicator(
                pagerPage = 1,
                isActive = true,
                isVideoVisible = false,
                isPlaying = false,
                hasError = false,
                isScrollInProgress = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinFirstVideoStartupLoadingIndicator(
                pagerPage = 2,
                isActive = true,
                isVideoVisible = false,
                isPlaying = false,
                hasError = false,
                isScrollInProgress = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinFirstVideoStartupLoadingIndicator(
                pagerPage = 1,
                isActive = true,
                isVideoVisible = true,
                isPlaying = false,
                hasError = false,
                isScrollInProgress = false
            )
        )
        assertEquals(
            false,
            shouldShowDouyinFirstVideoStartupLoadingIndicator(
                pagerPage = 1,
                isActive = true,
                isVideoVisible = false,
                isPlaying = false,
                hasError = false,
                isScrollInProgress = true
            )
        )
    }

    @Test
    fun resolveDouyinSettleAutoplayDelayFrames_staysWithinThreeFrames() {
        assertEquals(
            0,
            resolveDouyinSettleAutoplayDelayFrames(
                isReady = false,
                hasRenderedFirstFrame = true
            )
        )
        assertEquals(
            3,
            resolveDouyinSettleAutoplayDelayFrames(
                isReady = true,
                hasRenderedFirstFrame = false
            )
        )
        assertNull(
            resolveDouyinSettleAutoplayDelayFrames(
                isReady = false,
                hasRenderedFirstFrame = false
            )
        )
    }

    @Test
    fun shouldKeepDouyinPrewarmPlayingAfterTimeout_onlyKeepsSilentCurrentTarget() {
        assertEquals(
            true,
            shouldKeepDouyinPrewarmPlayingAfterTimeout(
                finishedBeforeTimeout = false,
                hasRenderedFirstFrame = false,
                hasError = false,
                isStillTarget = true
            )
        )
        assertEquals(
            false,
            shouldKeepDouyinPrewarmPlayingAfterTimeout(
                finishedBeforeTimeout = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                isStillTarget = true
            )
        )
        assertEquals(
            false,
            shouldKeepDouyinPrewarmPlayingAfterTimeout(
                finishedBeforeTimeout = false,
                hasRenderedFirstFrame = true,
                hasError = false,
                isStillTarget = true
            )
        )
        assertEquals(
            false,
            shouldKeepDouyinPrewarmPlayingAfterTimeout(
                finishedBeforeTimeout = false,
                hasRenderedFirstFrame = false,
                hasError = false,
                isStillTarget = false
            )
        )
    }

    @Test
    fun shouldAttachDouyinVisibleSurface_onlyAttachesCurrentHealthyPage() {
        assertEquals(
            true,
            shouldAttachDouyinVisibleSurface(
                isActivePage = true,
                hasSlot = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldAttachDouyinVisibleSurface(
                isActivePage = false,
                hasSlot = true,
                hasError = false
            )
        )
        assertEquals(
            false,
            shouldAttachDouyinVisibleSurface(
                isActivePage = true,
                hasSlot = true,
                hasError = true
            )
        )
    }

    @Test
    fun isDouyinPageVideoVisible_requiresVisibleSurfaceFirstFrameForActivePage() {
        assertEquals(
            true,
            isDouyinPageVideoVisible(
                isActivePage = true,
                pageAwemeId = "aweme-next",
                slotAwemeId = "aweme-next",
                slotPrepareKey = "uri#1",
                visibleRenderedPrepareKey = "uri#1",
                hasError = false
            )
        )
        assertEquals(
            false,
            isDouyinPageVideoVisible(
                isActivePage = true,
                pageAwemeId = "aweme-next",
                slotAwemeId = "aweme-next",
                slotPrepareKey = "uri#1",
                visibleRenderedPrepareKey = null,
                hasError = false
            )
        )
        assertEquals(
            false,
            isDouyinPageVideoVisible(
                isActivePage = false,
                pageAwemeId = "aweme-next",
                slotAwemeId = "aweme-next",
                slotPrepareKey = "uri#1",
                visibleRenderedPrepareKey = "uri#1",
                hasError = false
            )
        )
    }

    @Test
    fun shouldHostDouyinHiddenPrewarmSurface_usesTitleTargetOrLookaheadSlot() {
        assertEquals(
            true,
            shouldHostDouyinHiddenPrewarmSurface(
                showTitlePage = true,
                isForegroundSlot = false,
                hasPreparedTitleTarget = false,
                hasMatchingLookaheadTarget = true
            )
        )
        assertEquals(
            true,
            shouldHostDouyinHiddenPrewarmSurface(
                showTitlePage = true,
                isForegroundSlot = true,
                hasPreparedTitleTarget = true,
                hasMatchingLookaheadTarget = false
            )
        )
        assertEquals(
            true,
            shouldHostDouyinHiddenPrewarmSurface(
                showTitlePage = false,
                isForegroundSlot = false,
                hasPreparedTitleTarget = false,
                hasMatchingLookaheadTarget = true
            )
        )
        assertEquals(
            false,
            shouldHostDouyinHiddenPrewarmSurface(
                showTitlePage = false,
                isForegroundSlot = true,
                hasPreparedTitleTarget = false,
                hasMatchingLookaheadTarget = true
            )
        )
    }

    @Test
    fun lookaheadPrewarm_onlyActivatesForRealTransitionTargets() {
        assertEquals(
            true,
            shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = false,
                isForegroundSlot = false,
                hasMatchingLookaheadTarget = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                hasPrewarmSurface = true,
                isPrewarming = false
            )
        )
        assertEquals(
            true,
            shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = true,
                isForegroundSlot = false,
                hasMatchingLookaheadTarget = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                hasPrewarmSurface = true,
                isPrewarming = false
            )
        )
        assertEquals(
            false,
            shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = false,
                isForegroundSlot = true,
                hasMatchingLookaheadTarget = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                hasPrewarmSurface = true,
                isPrewarming = false
            )
        )
        assertEquals(
            false,
            shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = false,
                isForegroundSlot = false,
                hasMatchingLookaheadTarget = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                hasPrewarmSurface = true,
                isPrewarming = true
            )
        )
        assertEquals(
            false,
            shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = false,
                isForegroundSlot = false,
                hasMatchingLookaheadTarget = true,
                hasRenderedFirstFrame = false,
                hasError = false,
                hasPrewarmSurface = false,
                isPrewarming = false
            )
        )
    }

    @Test
    fun shouldEnterDouyinVideoFlowImmediately_requiresOnlyPreparedItem() {
        assertEquals(
            true,
            shouldEnterDouyinVideoFlowImmediately(
                hasPreparedItem = true
            )
        )
        assertEquals(
            false,
            shouldEnterDouyinVideoFlowImmediately(
                hasPreparedItem = false
            )
        )
    }
}
