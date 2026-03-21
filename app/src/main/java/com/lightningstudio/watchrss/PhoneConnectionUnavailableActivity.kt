package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

class PhoneConnectionUnavailableActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        setContent {
            WatchRSSTheme {
                WatchSurface(pureBlack = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(watchDimensionResource(R.dimen.watch_safe_padding)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂不可用",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
