package com.lightningstudio.watchrss

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionFeature

class DebugAcousticConnectionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PhoneConnectionFeature.isDebugBuild) {
            finish()
            return
        }

        startActivity(
            Intent(this, AcousticConnectionActivity::class.java).apply {
                putExtras(intent)
            }
        )
        finish()
    }
}
