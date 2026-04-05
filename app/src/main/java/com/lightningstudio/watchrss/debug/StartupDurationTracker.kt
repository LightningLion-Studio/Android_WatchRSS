package com.lightningstudio.watchrss.debug

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import com.lightningstudio.watchrss.util.AppLogger
import java.util.concurrent.atomic.AtomicLong

object StartupDurationTracker {
    private const val STARTUP_TAG = "startup"

    private val sessionCounter = AtomicLong(0)

    @Volatile
    private var applicationCreatedElapsedMs: Long? = null

    @Volatile
    private var currentSession: StartupSession? = null

    fun markApplicationCreated() {
        val now = SystemClock.elapsedRealtime()
        if (applicationCreatedElapsedMs == null) {
            applicationCreatedElapsedMs = now
        }
        logCheckpoint(
            event = "application_created",
            session = currentSession,
            nowElapsedMs = now,
            extras = "appMs=0"
        )
    }

    fun markMainActivityCreated(
        isLauncherEntry: Boolean,
        savedInstanceState: Bundle?
    ) {
        val now = SystemClock.elapsedRealtime()
        val session = StartupSession(
            id = sessionCounter.incrementAndGet(),
            activityCreatedElapsedMs = now,
            isLauncherEntry = isLauncherEntry,
            isFreshCreate = savedInstanceState == null
        )
        currentSession = session
        logCheckpoint(
            event = "main_activity_created",
            session = session,
            nowElapsedMs = now,
            extras = buildString {
                append("launcher=").append(isLauncherEntry).append(' ')
                append("freshCreate=").append(savedInstanceState == null).append(' ')
                append("appMs=").append(applicationCreatedElapsedMs?.let { now - it } ?: -1L)
            }
        )
    }

    fun markStartupReady(destination: String) {
        val now = SystemClock.elapsedRealtime()
        val session = currentSession ?: return
        if (session.completed) return
        synchronized(this) {
            val latest = currentSession ?: return
            if (latest.id != session.id || latest.completed) return
            latest.completed = true
            logCheckpoint(
                event = "startup_ready",
                session = latest,
                nowElapsedMs = now,
                extras = buildString {
                    append("destination=").append(destination).append(' ')
                    append("launcher=").append(latest.isLauncherEntry).append(' ')
                    append("freshCreate=").append(latest.isFreshCreate).append(' ')
                    append("activityMs=").append(now - latest.activityCreatedElapsedMs).append(' ')
                    append("appMs=").append(applicationCreatedElapsedMs?.let { now - it } ?: -1L)
                }
            )
        }
    }

    private fun logCheckpoint(
        event: String,
        session: StartupSession?,
        nowElapsedMs: Long,
        extras: String
    ) {
        val processMs = nowElapsedMs - Process.getStartElapsedRealtime()
        val message = buildString {
            append("event=").append(event).append(' ')
            append("processMs=").append(processMs).append(' ')
            session?.let {
                append("session=").append(it.id).append(' ')
            }
            append(extras)
        }.trim()
        PerfTrace.log(STARTUP_TAG, message)
        AppLogger.log("Startup", message)
    }

    private data class StartupSession(
        val id: Long,
        val activityCreatedElapsedMs: Long,
        val isLauncherEntry: Boolean,
        val isFreshCreate: Boolean,
        @Volatile var completed: Boolean = false
    )
}
