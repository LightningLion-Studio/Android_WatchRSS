package com.lightningstudio.watchrss.data.telemetry

interface UsageTelemetry {
    fun recordAppLaunch()
    fun recordScreenOpen(screen: String)
    fun recordScreenDuration(screen: String, durationMs: Long)
    fun recordSyncReceived(kind: String, itemCount: Int = 0)
    fun recordFeedRefreshed(channelId: String?, channelTitle: String?, success: Boolean)
    fun recordArticleReadStarted(itemId: Long, title: String?, channelId: String?, channelTitle: String?)
    fun recordArticleReadFinished(
        itemId: Long,
        title: String?,
        channelId: String?,
        channelTitle: String?,
        reachedBottom: Boolean,
        durationMs: Long
    )
    fun recordVideoPlayed(source: String, id: String, title: String?)
    fun recordSyncAccount()
    fun backlogCount(): Int
}

object NoOpUsageTelemetry : UsageTelemetry {
    override fun recordAppLaunch() {}
    override fun recordScreenOpen(screen: String) {}
    override fun recordScreenDuration(screen: String, durationMs: Long) {}
    override fun recordSyncReceived(kind: String, itemCount: Int) {}
    override fun recordFeedRefreshed(channelId: String?, channelTitle: String?, success: Boolean) {}
    override fun recordArticleReadStarted(itemId: Long, title: String?, channelId: String?, channelTitle: String?) {}
    override fun recordArticleReadFinished(
        itemId: Long,
        title: String?,
        channelId: String?,
        channelTitle: String?,
        reachedBottom: Boolean,
        durationMs: Long
    ) {}
    override fun recordVideoPlayed(source: String, id: String, title: String?) {}
    override fun recordSyncAccount() {}
    override fun backlogCount(): Int = 0
}
