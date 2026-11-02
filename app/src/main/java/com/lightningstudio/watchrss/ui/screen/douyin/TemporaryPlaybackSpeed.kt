package com.lightningstudio.watchrss.ui.screen.douyin

/** One press owns one restoration value, including cancellation and lifecycle interruption. */
internal class TemporaryPlaybackSpeed(
    private val read: () -> Float,
    private val write: (Float) -> Unit
) {
    private var original: Float? = null
    fun begin() {
        if (original != null) return
        original = read()
        write(2f)
    }
    fun end() {
        val previous = original ?: return
        original = null
        write(previous)
    }
}
