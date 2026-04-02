package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionFeature
import com.lightningstudio.watchrss.ui.screen.ProfileScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ProfileActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                ProfileScreen(
                    showPhoneConnectionEntry = PhoneConnectionFeature.isDebugBuild,
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
                        Toast.makeText(
                            this,
                            "您可以在App的以下各处使用手机互联功能：添加RSS源、我的收藏、稍后再看",
                            Toast.LENGTH_LONG
                        ).show()
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
                    },
                    onBeianClick = {
                        if (!allowNavigation()) return@ProfileScreen
                        startActivity(BeianActivity.createIntent(this))
                    }
                )
            }
        }
    }
}
