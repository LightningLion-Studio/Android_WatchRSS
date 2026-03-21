package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionFeature
import com.lightningstudio.watchrss.ui.screen.ProfileScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ProfileActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val settingsRepository = (application as WatchRssApplication).container.settingsRepository

        setContent {
            WatchRSSTheme {
                val phoneConnectionEnabled by settingsRepository.phoneConnectionEnabled.collectAsState(
                    initial = PhoneConnectionFeature.isDebugBuild
                )
                ProfileScreen(
                    showPhoneConnectionEntry = PhoneConnectionFeature.isEnabled(phoneConnectionEnabled),
                    onFavoritesClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        val intent = Intent(this, SavedItemsActivity::class.java)
                        intent.putExtra(
                            SavedItemsActivity.EXTRA_SAVE_TYPE,
                            com.lightningstudio.watchrss.data.rss.SaveType.FAVORITE.name
                        )
                        startActivity(intent)
                    },
                    onWatchLaterClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        val intent = Intent(this, SavedItemsActivity::class.java)
                        intent.putExtra(
                            SavedItemsActivity.EXTRA_SAVE_TYPE,
                            com.lightningstudio.watchrss.data.rss.SaveType.WATCH_LATER.name
                        )
                        startActivity(intent)
                    },
                    onPhoneConnectionClick = {
                        if (!PhoneConnectionFeature.isEnabled(phoneConnectionEnabled)) return@ProfileScreen
                        if (!allowNavigation()) return@ProfileScreen
                        startActivity(Intent(this, PhoneConnectionActivity::class.java))
                    },
                    onSettingsClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onAboutClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
                    onContactDeveloperClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        startActivity(Intent(this, ContactDeveloperActivity::class.java))
                    }
                )
            }
        }
    }
}
