package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.AppTransparencyStore
import com.lightningstudio.watchrss.ui.components.InitialAppTransparencyDialog
import com.lightningstudio.watchrss.ui.components.ThirdPartyPlatformConfirmationDialog
import com.lightningstudio.watchrss.ui.components.ThirdPartyPlatformNotice
import com.lightningstudio.watchrss.ui.screen.bili.BiliEntryNavGraph
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class BiliEntryActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val repository = (application as WatchRssApplication).container.biliRepository
                    val rssRepository = (application as WatchRssApplication).container.rssRepository
                    var showInitialTransparency by remember {
                        mutableStateOf(!AppTransparencyStore.isInitialAppDisclosureAcknowledged(this@BiliEntryActivity))
                    }
                    var showPlatformConfirmation by remember { mutableStateOf(!showInitialTransparency) }
                    if (showInitialTransparency) {
                        InitialAppTransparencyDialog(
                            onAcknowledge = {
                                AppTransparencyStore.acknowledgeInitialAppDisclosure(this@BiliEntryActivity)
                                showInitialTransparency = false
                                showPlatformConfirmation = true
                            }
                        )
                    } else if (showPlatformConfirmation) {
                        ThirdPartyPlatformConfirmationDialog(
                            platform = "哔哩哔哩",
                            onConfirm = { showPlatformConfirmation = false }
                        )
                    }
                    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        ThirdPartyPlatformNotice(
                            platform = "哔哩哔哩",
                            compact = true,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Box(modifier = androidx.compose.ui.Modifier.weight(1f).fillMaxSize()) {
                            BiliEntryNavGraph(repository = repository, rssRepository = rssRepository)
                        }
                    }
                }
            }
        }
    }

}
