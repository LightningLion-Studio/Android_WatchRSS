package com.lightningstudio.watchrss.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTelemetrySnapshotTest {
    @Test
    fun record_builds_cumulative_daily_counters_without_article_metadata() {
        val snapshot = DailyTelemetrySnapshot("2026-08-13")
            .record("screen_opened", mapOf("screen" to "reader"))
            .record("screen_duration", mapOf("screen" to "reader", "durationMs" to 2_000L))
            .record("article_read_started", mapOf("title" to "private title"))
            .record("sync_received", mapOf("kind" to "articles"))

        assertEquals(1L, snapshot.eventCounts["article_read_started"])
        assertEquals(mapOf("reader" to 1L), snapshot.screenOpenCounts)
        assertEquals(mapOf("reader" to 2_000L), snapshot.screenDurationMs)
        assertEquals(2_000L, snapshot.appForegroundMs)
        assertEquals(1, snapshot.syncSuccessCount)
    }
}
