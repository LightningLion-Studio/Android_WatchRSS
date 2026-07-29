package com.lightningstudio.watchrss.ui.reader

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageLayoutTest {
    @Test
    fun bottomPaddingMatchesActualReaderFloatingActionStates() {
        assertEquals(15.dp, ReaderPageLayout.bottomPadding(hasFloatingAction = false))
        assertEquals(56.dp, ReaderPageLayout.bottomPadding(hasFloatingAction = true))
    }
}
