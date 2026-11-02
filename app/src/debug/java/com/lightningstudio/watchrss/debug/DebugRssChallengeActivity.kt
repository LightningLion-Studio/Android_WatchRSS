package com.lightningstudio.watchrss.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lightningstudio.watchrss.AddRssActivity
import com.lightningstudio.watchrss.DetailActivity

/** Debug-only Intent entry point for exercising an RSS URL without UI automation taps. */
class DebugRssChallengeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId > 0L) {
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ITEM_ID, itemId)
            )
            finish()
            return
        }
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }
        startActivity(
            Intent(this, AddRssActivity::class.java)
                .putExtra(AddRssActivity.EXTRA_URL, url)
                .putExtra(AddRssActivity.EXTRA_DEBUG_AUTO_SUBMIT, true)
        )
        finish()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
