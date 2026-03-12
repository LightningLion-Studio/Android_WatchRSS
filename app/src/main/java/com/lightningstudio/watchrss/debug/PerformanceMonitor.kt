package com.lightningstudio.watchrss.debug

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.DetailActivity
import com.lightningstudio.watchrss.util.AppLogger
import java.util.Collections
import java.util.WeakHashMap

private const val PERF_TAG = "perf"
private const val FRAME_TAG = "frame"
private const val FRAME_BUDGET_MS = 16f

object PerformanceMonitor {
    private val statsMap = Collections.synchronizedMap(WeakHashMap<ComponentActivity, FrameStats>())

    fun attach(activity: ComponentActivity) {
        if (!BuildConfig.DEBUG) return
        val window = activity.window
        val stateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView).state
        stateHolder?.putState("screen", activity.javaClass.simpleName)

        val stats = FrameStats(activity.javaClass.simpleName)
        statsMap[activity] = stats
        val jankStats = JankStats.createAndTrack(window) { frameData ->
            stats.onFrame(frameData)
        }
        val frameLogger = if (activity is DetailActivity) {
            FrameMetricsLogger(activity)
        } else {
            null
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                jankStats.isTrackingEnabled = true
                frameLogger?.start()
            }

            override fun onPause(owner: LifecycleOwner) {
                jankStats.isTrackingEnabled = false
                frameLogger?.stop()
                logAndReset(stats)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                jankStats.isTrackingEnabled = false
                frameLogger?.stop()
                logAndReset(stats)
            }
        })
    }

    fun setScenario(activity: ComponentActivity, scenario: String) {
        if (!BuildConfig.DEBUG) return
        val stateHolder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView).state
        stateHolder?.putState("scenario", scenario)
        statsMap[activity]?.updateScenario(scenario)
    }

    private fun logAndReset(stats: FrameStats) {
        val snapshot = stats.snapshotAndReset()
        if (snapshot.totalFrames == 0L) return
        val avgMs = snapshot.totalDurationNanos / snapshot.totalFrames / 1_000_000f
        val maxMs = snapshot.maxDurationNanos / 1_000_000f
        val jankPct = snapshot.jankFrames * 100f / snapshot.totalFrames
        val message = buildString {
            append("screen=${snapshot.screenName} ")
            snapshot.scenario?.let { append("scenario=$it ") }
            append("frames=${snapshot.totalFrames} ")
            append("jank=${snapshot.jankFrames} ")
            append("jankPct=${"%.2f".format(jankPct)} ")
            append("avgMs=${"%.2f".format(avgMs)} ")
            append("maxMs=${"%.2f".format(maxMs)} ")
        }
        DebugLogBuffer.log(PERF_TAG, message)
        AppLogger.d(PERF_TAG, message)
    }
}

private class FrameMetricsLogger(private val activity: ComponentActivity) {
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private var frameIndex = 0L
    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, dropCount ->
        frameIndex += 1
        val totalMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION))
        if (totalMs < FRAME_BUDGET_MS) return@OnFrameMetricsAvailableListener
        val layoutMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
        val drawMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.DRAW_DURATION))
        val syncMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.SYNC_DURATION))
        val commandMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))
        val swapMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
        val animMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION))
        val inputMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
        val gpuMs = nanosToMs(frameMetrics.getMetric(FrameMetrics.GPU_DURATION))
        val message = buildString {
            append("frame=").append(frameIndex).append(' ')
            append("total=").append("%.2f".format(totalMs)).append("ms ")
            append("layout=").append("%.2f".format(layoutMs)).append("ms ")
            append("draw=").append("%.2f".format(drawMs)).append("ms ")
            append("sync=").append("%.2f".format(syncMs)).append("ms ")
            append("cmd=").append("%.2f".format(commandMs)).append("ms ")
            append("swap=").append("%.2f".format(swapMs)).append("ms ")
            append("anim=").append("%.2f".format(animMs)).append("ms ")
            append("input=").append("%.2f".format(inputMs)).append("ms ")
            append("gpu=").append("%.2f".format(gpuMs)).append("ms ")
            append("drop=").append(dropCount)
        }
        DebugLogBuffer.log(FRAME_TAG, message)
        AppLogger.d(FRAME_TAG, message)
    }

    fun start() {
        if (attached || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        activity.window.addOnFrameMetricsAvailableListener(listener, handler)
        attached = true
    }

    fun stop() {
        if (!attached || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        attached = false
    }

    private fun nanosToMs(value: Long): Float {
        return if (value <= 0L) 0f else value / 1_000_000f
    }
}

private class FrameStats(private val screenName: String) {
    private var totalFrames: Long = 0
    private var jankFrames: Long = 0
    private var totalDurationNanos: Long = 0
    private var maxDurationNanos: Long = 0
    private var scenario: String? = null

    fun onFrame(frameData: FrameData) {
        totalFrames += 1
        if (frameData.isJank) {
            jankFrames += 1
        }
        totalDurationNanos += frameData.frameDurationUiNanos
        if (frameData.frameDurationUiNanos > maxDurationNanos) {
            maxDurationNanos = frameData.frameDurationUiNanos
        }
    }

    fun updateScenario(value: String) {
        scenario = value
    }

    fun snapshotAndReset(): FrameStatsSnapshot {
        val snapshot = FrameStatsSnapshot(
            screenName = screenName,
            scenario = scenario,
            totalFrames = totalFrames,
            jankFrames = jankFrames,
            totalDurationNanos = totalDurationNanos,
            maxDurationNanos = maxDurationNanos
        )
        totalFrames = 0
        jankFrames = 0
        totalDurationNanos = 0
        maxDurationNanos = 0
        scenario = null
        return snapshot
    }
}

private data class FrameStatsSnapshot(
    val screenName: String,
    val scenario: String?,
    val totalFrames: Long,
    val jankFrames: Long,
    val totalDurationNanos: Long,
    val maxDurationNanos: Long
)
