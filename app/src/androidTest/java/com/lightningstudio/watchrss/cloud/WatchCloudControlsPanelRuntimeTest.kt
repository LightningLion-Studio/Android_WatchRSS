package com.lightningstudio.watchrss.cloud

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchCloudControlsPanel
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WatchCloudControlsPanelRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cloudOptInRequiresConfirmationAndManualOverrideCanBeCancelledBeforeCredentialRead() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountDataContext(target, "verification_cloud_ui_${UUID.randomUUID()}", false)
        var reads = 0
        val account = object : AccountStore {
            override val state = MutableStateFlow<WatchAccountState?>(null)
            override fun read(): WatchAccountState? { reads++; error("UI must not read credentials before manual confirmation") }
            override fun save(state: WatchAccountState) = Unit
            override fun clear() = Unit
        }
        val service = WatchCloudSyncService(context, account, (target.applicationContext as WatchRssApplication).container.rssRepository, canSync = { false })
        try {
            compose.setContent { WatchRSSTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) { WatchCloudControlsPanel(service) }
            } }
            compose.onNodeWithText("开启自动云同步").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(service.controls.state.value.automatic) }
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertFalse(service.controls.state.value.automatic) }
            compose.onNodeWithText("开启自动云同步").performScrollTo().performClick()
            compose.onNodeWithText("确认").performClick()
            compose.runOnIdle { assertTrue(service.controls.state.value.automatic) }
            compose.onNodeWithText("关闭自动云同步").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(service.controls.state.value.automatic) }
            compose.onNodeWithText("立即云同步").performScrollTo().performClick()
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertEquals(0, reads) }
        } finally {
            context.deleteSharedPreferences("watchrss_cloud_controls")
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }
}
