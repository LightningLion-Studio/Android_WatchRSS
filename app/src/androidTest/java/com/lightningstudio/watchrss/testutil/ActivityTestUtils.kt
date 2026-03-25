package com.lightningstudio.watchrss.testutil

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (SystemClock.elapsedRealtime() < deadline) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        if (condition()) return
        SystemClock.sleep(100)
    }
    throw AssertionError("Condition not met within ${timeoutMillis}ms")
}

fun currentResumedActivity(): Activity? {
    var resumedActivity: Activity? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        resumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull()
    }
    return resumedActivity
}
