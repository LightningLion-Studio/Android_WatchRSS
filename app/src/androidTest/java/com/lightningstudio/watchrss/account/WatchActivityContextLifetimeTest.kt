package com.lightningstudio.watchrss.account

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.ReaderPresetActivity
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.account.workspace.libraryContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchActivityContextLifetimeTest {
    @Test fun destroyedActivityStillResolvesItsOriginalSpaceForLateCallbacks() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WatchRssApplication
        val original = app.container
        lateinit var activity: Activity
        lateinit var bound: Context
        ActivityScenario.launch(ReaderPresetActivity::class.java).use { scenario ->
            scenario.onActivity {
                activity = it
                bound = libraryContext(it)
            }
        }
        app.setContainerForTesting(object : AppContainer by original {})
        try {
            assertTrue(activity.isDestroyed)
            assertNotSame(bound, app.libraryContext)
            assertSame(bound, libraryContext(activity))
        } finally { app.setContainerForTesting(null) }
    }
}
