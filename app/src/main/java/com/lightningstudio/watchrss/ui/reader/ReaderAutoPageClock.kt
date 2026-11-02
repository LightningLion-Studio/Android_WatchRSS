package com.lightningstudio.watchrss.ui.reader

import kotlinx.coroutines.delay

/** Cancellation discards elapsed time; every new foreground page gets a full interval. */
internal suspend fun awaitReaderAutoPage(seconds: Int) {
    delay(seconds.coerceIn(5, 120) * 1000L)
}
