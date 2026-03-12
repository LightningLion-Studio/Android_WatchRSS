package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lightningstudio.watchrss.ui.screen.bili.BiliSearchResultScreen
import com.lightningstudio.watchrss.ui.screen.bili.BiliSearchScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliSearchActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                val navController = rememberNavController()
                val context = LocalContext.current
                val factory = remember(repository, rssRepository) {
                    BiliViewModelFactory(repository, rssRepository)
                }

                NavHost(
                    navController = navController,
                    startDestination = "search"
                ) {
                    composable("search") {
                        BiliSearchScreen(
                            factory = factory,
                            onNavigateBack = { finish() },
                            onSearch = { keyword ->
                                navController.navigate("search_result/$keyword")
                            }
                        )
                    }

                    composable("search_result/{keyword}") { backStackEntry ->
                        val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
                        BiliSearchResultScreen(
                            keyword = keyword,
                            factory = factory,
                            onNavigateBack = { navController.popBackStack() },
                            onVideoClick = { aid, bvid ->
                                context.startActivity(
                                    BiliDetailActivity.createIntent(context, aid, bvid, null)
                                )
                            },
                            onSearch = { newKeyword ->
                                navController.navigate("search_result/$newKeyword") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, BiliSearchActivity::class.java)
        }
    }
}
