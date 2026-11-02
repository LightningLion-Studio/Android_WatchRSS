package com.lightningstudio.watchrss.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.lightningstudio.watchrss.FeedActivity
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Debug-only adb entry point: imports a ten-page novel fixture and opens its chapter list. */
class DebugNovelImportActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_IMPORT_TEN_PAGE_NOVEL) {
            Log.w(TAG, "Ignoring unexpected action=${intent.action}")
            finish()
            return
        }

        activityScope.launch {
            runCatching {
                val container = (application as WatchRssApplication).container as? DefaultAppContainer
                    ?: error("当前应用容器不支持调试小说导入")
                container.debugImportTenPageNovel()
            }.onSuccess { channelId ->
                Log.i(TAG, "Imported ten-page novel channelId=$channelId")
                Toast.makeText(this@DebugNovelImportActivity, "已导入 10 页调试小说", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@DebugNovelImportActivity, FeedActivity::class.java)
                    .putExtra(FeedActivity.EXTRA_CHANNEL_ID, channelId))
            }.onFailure { error ->
                Log.e(TAG, "Ten-page novel import failed", error)
                Toast.makeText(this@DebugNovelImportActivity, "调试小说导入失败：${error.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_IMPORT_TEN_PAGE_NOVEL = "com.lightningstudio.watchrss.action.DEBUG_IMPORT_TEN_PAGE_NOVEL"
        private const val TAG = "DebugNovelImport"
    }
}
