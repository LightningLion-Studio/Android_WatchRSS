package com.lightningstudio.watchrss.ui.util

/** Main-thread-owned slot that dismisses the visible toast before creating its replacement. */
internal class LatestToastSlot<T>(private val dismiss: (T) -> Unit) {
    private var current: T? = null

    fun replace(createAndShow: () -> T) {
        current?.let(dismiss)
        current = createAndShow()
    }
}
