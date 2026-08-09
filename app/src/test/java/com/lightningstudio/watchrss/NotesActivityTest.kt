package com.lightningstudio.watchrss

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesActivityTest {
    @Test
    fun loadingIndicator_rotatesAndWrapsEveryCycle() {
        assertEquals(0f, noteLoadingRotationDegrees(0L))
        assertEquals(90f, noteLoadingRotationDegrees(200_000_000L))
        assertEquals(180f, noteLoadingRotationDegrees(400_000_000L))
        assertEquals(0f, noteLoadingRotationDegrees(800_000_000L))
    }

    @Test
    fun editButton_hidesAndShowsAcrossLazyListItems() {
        assertEquals(
            false,
            noteEditButtonVisibleAfterLazyScroll(
                wasVisible = true,
                previousItemIndex = 1,
                previousItemOffset = 300,
                currentItemIndex = 2,
                currentItemOffset = 10
            )
        )
        assertEquals(
            true,
            noteEditButtonVisibleAfterLazyScroll(
                wasVisible = false,
                previousItemIndex = 2,
                previousItemOffset = 10,
                currentItemIndex = 1,
                currentItemOffset = 300
            )
        )
    }

    @Test
    fun editButton_hidesWhileScrollingDownAndStaysHiddenAfterStop() {
        val hiddenWhileScrolling = noteEditButtonVisibleAfterScroll(
            wasVisible = true,
            previousScrollValue = 20,
            currentScrollValue = 40
        )

        assertEquals(
            false,
            noteEditButtonVisibleAfterScroll(
                wasVisible = hiddenWhileScrolling,
                previousScrollValue = 40,
                currentScrollValue = 40
            )
        )
    }

    @Test
    fun editButton_showsWhenScrollingUpAndStaysVisibleAfterStop() {
        val visibleWhileScrolling = noteEditButtonVisibleAfterScroll(
            wasVisible = false,
            previousScrollValue = 40,
            currentScrollValue = 20
        )

        assertEquals(
            true,
            noteEditButtonVisibleAfterScroll(
                wasVisible = visibleWhileScrolling,
                previousScrollValue = 20,
                currentScrollValue = 20
            )
        )
    }

    @Test
    fun notePreview_doesNotRepeatTitle() {
        assertEquals(
            "第一段 第二段",
            notePreview("会议安排", "会议安排\n\n第一段\n第二段")
        )
    }

    @Test
    fun notePreview_keepsFirstLineWhenDifferentFromTitle() {
        assertEquals(
            "先买牛奶 再取快递",
            notePreview("待办", "先买牛奶\n再取快递")
        )
    }

    @Test
    fun notePreview_collapsesBlankLinesAndLimitsLength() {
        val preview = notePreview("标题", "\n\n" + "内容".repeat(100))

        assertEquals(120, preview.length)
    }

    @Test
    fun crownCursorMovement_collapsesSelectionAndMovesByCodePoint() {
        val selected = TextFieldValue("甲😀乙", selection = TextRange(1, 3))

        assertEquals(4, moveNoteCursor(selected, 1).selection.start)
        assertEquals(0, moveNoteCursor(selected, -1).selection.start)
    }

    @Test
    fun crownCursorMovement_stopsAtDocumentEdges() {
        val atStart = TextFieldValue("正文", selection = TextRange(0))
        val atEnd = TextFieldValue("正文", selection = TextRange(2))

        assertEquals(0, moveNoteCursor(atStart, -1).selection.start)
        assertEquals(2, moveNoteCursor(atEnd, 1).selection.start)
    }

    @Test
    fun initialCursor_roughlyTracksPreservedScrollPosition() {
        assertEquals(0, estimateNoteCursorForScroll(100, 0, 400))
        assertEquals(50, estimateNoteCursorForScroll(100, 200, 400))
        assertEquals(100, estimateNoteCursorForScroll(100, 400, 400))
    }

    @Test
    fun cursorVisibility_scrollsOnlyEnoughToRevealCursorBelowViewport() {
        assertEquals(
            180,
            noteCursorVisibleScrollTarget(
                scrollValue = 100,
                scrollMaxValue = 500,
                bodyTopInRoot = 50f,
                cursorTopInBody = 300f,
                cursorBottomInBody = 330f,
                visibleTopInRoot = 120f,
                visibleBottomInRoot = 300f
            )
        )
    }

    @Test
    fun cursorVisibility_scrollsOnlyEnoughToRevealCursorAboveViewport() {
        assertEquals(
            50,
            noteCursorVisibleScrollTarget(
                scrollValue = 100,
                scrollMaxValue = 500,
                bodyTopInRoot = -50f,
                cursorTopInBody = 120f,
                cursorBottomInBody = 150f,
                visibleTopInRoot = 120f,
                visibleBottomInRoot = 300f
            )
        )
    }

    @Test
    fun cursorVisibility_keepsScrollWhenCursorIsAlreadyVisible() {
        assertEquals(
            100,
            noteCursorVisibleScrollTarget(
                scrollValue = 100,
                scrollMaxValue = 500,
                bodyTopInRoot = 50f,
                cursorTopInBody = 100f,
                cursorBottomInBody = 130f,
                visibleTopInRoot = 120f,
                visibleBottomInRoot = 300f
            )
        )
    }

    @Test
    fun cursorCentering_usesPhysicalViewportCenterInsteadOfScrollFraction() {
        assertEquals(
            300,
            noteCursorCenteredScrollTarget(
                scrollValue = 100,
                scrollMaxValue = 1_000,
                bodyTopInRoot = -50f,
                cursorTopInBody = 450f,
                cursorBottomInBody = 470f,
                viewportCenterInRoot = 210f
            )
        )
    }
}
