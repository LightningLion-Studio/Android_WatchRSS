package com.lightningstudio.watchrss.debug

import android.content.Context
import android.content.Intent

/**
 * Performance testing entry point
 */
object PerfEntryActivity {
    const val TARGET_LARGE_LIST = "large_list"
    const val TARGET_LARGE_ARTICLE = "large_article"

    fun createIntent(context: Context, target: String): Intent {
        return when (target) {
            TARGET_LARGE_LIST -> Intent(context, PerfLargeListActivity::class.java)
            TARGET_LARGE_ARTICLE -> Intent(context, PerfLargeArticleActivity::class.java)
            else -> throw IllegalArgumentException("Unknown target: $target")
        }
    }
}
