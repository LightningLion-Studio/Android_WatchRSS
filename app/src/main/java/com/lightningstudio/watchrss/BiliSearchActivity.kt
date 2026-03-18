package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lightningstudio.watchrss.ui.screen.bili.BiliSearchResultScreen
import com.lightningstudio.watchrss.ui.screen.bili.BiliSearchScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliSearchSubmitAction
import com.lightningstudio.watchrss.ui.viewmodel.BiliSearchViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliSearchActivity : BaseWatchActivity() {
    private val resetSearchRequest = mutableIntStateOf(0)

    private object Routes {
        const val SEARCH = "search"
        const val SEARCH_RESULT = "search_result"
    }

    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }
    private val searchViewModel: BiliSearchViewModel by viewModels {
        BiliViewModelFactory(repository, rssRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_RESET_TO_ROOT, false)) {
            resetSearchRequest.intValue++
        }

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                val navController = rememberNavController()
                val context = LocalContext.current
                val resetSearchRequestToken = resetSearchRequest.intValue

                val handleSearchAction: (BiliSearchSubmitAction) -> Unit = { action ->
                    when (action) {
                        BiliSearchSubmitAction.None -> Unit
                        BiliSearchSubmitAction.OpenResults -> {
                            if (navController.currentDestination?.route != Routes.SEARCH_RESULT && allowNavigation()) {
                                navController.navigate(Routes.SEARCH_RESULT)
                            }
                        }
                        is BiliSearchSubmitAction.OpenVideo -> {
                            if (allowNavigation()) {
                                context.startActivity(
                                    BiliDetailActivity.createIntent(
                                        context = context,
                                        aid = action.aid,
                                        bvid = action.bvid,
                                        cid = null
                                    )
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(resetSearchRequestToken) {
                    if (resetSearchRequestToken > 0) {
                        searchViewModel.resetSearchSession()
                        navController.popBackStack(Routes.SEARCH, inclusive = false)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.SEARCH
                ) {
                    composable(Routes.SEARCH) {
                        BiliSearchScreen(
                            viewModel = searchViewModel,
                            onNavigateBack = { finish() },
                            onSearch = handleSearchAction
                        )
                    }

                    composable(Routes.SEARCH_RESULT) {
                        BiliSearchResultScreen(
                            viewModel = searchViewModel,
                            onNavigateBack = {
                                searchViewModel.resetSearchSession()
                                navController.popBackStack()
                            },
                            onVideoClick = { aid, bvid ->
                                if (!allowNavigation()) return@BiliSearchResultScreen
                                context.startActivity(
                                    BiliDetailActivity.createIntent(context, aid, bvid, null)
                                )
                            },
                            onSearch = handleSearchAction
                        )
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESET_TO_ROOT, false)) {
            resetSearchRequest.intValue++
        }
    }

    override fun finish() {
        searchViewModel.resetSearchSession()
        super.finish()
    }

    companion object {
        private const val EXTRA_RESET_TO_ROOT = "resetToRoot"

        fun createIntent(context: Context): Intent {
            return Intent(context, BiliSearchActivity::class.java).apply {
                putExtra(EXTRA_RESET_TO_ROOT, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}
