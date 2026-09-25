package com.lightningstudio.watchrss.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderViewportBoundaryTest {
    @Test fun consumesOnlyVerticalRemainderAfterChildScroll() {
        assertEquals(Offset(0f, -40f), ReaderViewportBoundary.onPostScroll(
            Offset(0f, -200f), Offset(15f, -40f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, ReaderViewportBoundary.onPostScroll(
            Offset(0f, -200f), Offset(15f, 0f), NestedScrollSource.UserInput))
    }

    @Test fun preservesHorizontalFlingAndConsumesBoundaryVelocityOnly() = runTest {
        assertEquals(Velocity(0f, -120f), ReaderViewportBoundary.onPostFling(
            Velocity(0f, -200f), Velocity(60f, -120f)))
    }
}
