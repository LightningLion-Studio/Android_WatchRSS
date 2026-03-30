package com.lightningstudio.watchrss.ui.screen.douyin

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
    fun resolveDouyinItemIndexForPagerPage_resolvesVisibleWindowToBackedItemIndex() {
        assertNull(resolveDouyinItemIndexForPagerPage(pagerPage = 0, entryStartIndex = 2))
        assertEquals(2, resolveDouyinItemIndexForPagerPage(pagerPage = 1, entryStartIndex = 2))
        assertEquals(4, resolveDouyinItemIndexForPagerPage(pagerPage = 3, entryStartIndex = 2))
    }
}
