package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PhoneConnectionScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class PhoneConnectionActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                PhoneConnectionScreen()
            }
        }
    }

    companion object {
        private const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        private const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"
        private const val EXTRA_LLM_SUMMARY_ITEM_ID = "llm_summary_item_id"

        fun createIntent(
            context: Context,
            preferredAbility: PhoneConnectionAbility? = null,
            returnRemoteUrl: Boolean = false,
            llmSummaryItemId: Long = 0L
        ): Intent {
            return Intent(context, PhoneConnectionActivity::class.java).apply {
                putExtra(EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                putExtra(EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
                putExtra(EXTRA_LLM_SUMMARY_ITEM_ID, llmSummaryItemId)
            }
        }
    }
}
