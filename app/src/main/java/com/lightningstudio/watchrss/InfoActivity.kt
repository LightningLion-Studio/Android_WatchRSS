package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.InfoScreen
import com.lightningstudio.watchrss.ui.reader.ProvideReaderPreset
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.lightningstudio.watchrss.data.network.withWatchRssAppVersionHeader

class InfoActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private val readerPresetRepository by lazy { (application as WatchRssApplication).container.readerPresetRepository }
    private var isNavigating by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isNavigating = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val remotePath = intent.getStringExtra(EXTRA_REMOTE_PATH)
        val initialContent = if (remotePath == null) resolveContent() else null
        val legalDocumentRepository = LegalDocumentRepository()

        setContent {
            WatchRSSTheme {
                ProvideReaderPreset(readerPresetRepository) {
                val baseDensity = LocalDensity.current
                val readingThemeDark by settingsRepository.readingThemeDark.collectAsState(initial = true)
                val readingFontSizeSp by settingsRepository.readingFontSizeSp.collectAsState(initial = 14)
                var reloadKey by rememberSaveable { mutableIntStateOf(0) }
                var content by rememberSaveable(remotePath) { mutableStateOf(initialContent) }
                var isLoading by rememberSaveable(remotePath) {
                    mutableStateOf(remotePath != null)
                }

                LaunchedEffect(remotePath, reloadKey) {
                    if (remotePath != null) {
                        isLoading = true
                        content = runCatching {
                            legalDocumentRepository.fetch(remotePath)
                        }.getOrNull()
                        isLoading = false
                    }
                }

                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading -> LegalDocumentLoadingState()
                            content == null -> LegalDocumentErrorState(
                                onRetry = { reloadKey += 1 }
                            )
                            else -> InfoScreen(
                                title = title,
                                content = content.orEmpty(),
                                readingThemeDark = readingThemeDark,
                                readingFontSizeSp = readingFontSizeSp,
                                onBeianClick = {
                                    isNavigating = true
                                    startActivity(BeianActivity.createIntent(this@InfoActivity))
                                }
                            )
                        }

                        if (isNavigating) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                WatchCircularProgressIndicator()
                            }
                        }
                    }
                }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_CONTENT_RAW_RES_ID = "content_raw_res_id"
        const val EXTRA_REMOTE_PATH = "remote_path"
        const val WATCH_USER_AGREEMENT_PATH = "/functions/v1/legal/watch/user-agreement"
        const val WATCH_PRIVACY_POLICY_PATH = "/functions/v1/legal/watch/privacy-policy"

        fun createIntent(context: Context, title: String, content: String): Intent {
            return Intent(context, InfoActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
            }
        }

        fun createIntent(context: Context, title: String, @RawRes contentRawResId: Int): Intent {
            return Intent(context, InfoActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT_RAW_RES_ID, contentRawResId)
            }
        }

        fun createRemoteIntent(context: Context, title: String, path: String): Intent {
            return Intent(context, InfoActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REMOTE_PATH, path)
            }
        }
    }

    private fun resolveContent(): String {
        val contentRawResId = intent.getIntExtra(EXTRA_CONTENT_RAW_RES_ID, 0)
        if (contentRawResId != 0) {
            return resources.openRawResource(contentRawResId).bufferedReader().use { it.readText() }
        }
        return intent.getStringExtra(EXTRA_CONTENT) ?: ""
    }
}

@androidx.compose.runtime.Composable
private fun LegalDocumentLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        WatchCircularProgressIndicator()
    }
}

@androidx.compose.runtime.Composable
private fun LegalDocumentErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "协议正文加载失败，请检查网络后重试。",
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            WatchButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("重新加载")
            }
        }
    }
}

internal class LegalDocumentRepository(
    private val baseUrl: String = BuildConfig.WATCHRSS_BACKEND_URL.trimEnd('/'),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetch(path: String): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw IOException("legal document service unavailable")
        val request = Request.Builder()
            .url(baseUrl + path)
            .withWatchRssAppVersionHeader()
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("legal document request failed: ${response.code}")
            }
            response.body?.string()?.trim().orEmpty()
                .takeIf(String::isNotEmpty)
                ?: throw IOException("legal document response was empty")
        }
    }
}
