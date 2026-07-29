package com.lightningstudio.watchrss

import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object AppResumeStateStore {
    private const val PREFS_NAME = "app_resume_state"
    private const val KEY_INTENT_URI = "intent_uri"

    fun save(context: Context, intent: Intent) {
        val normalized = normalizeIntent(context, intent) ?: return
        prefs(context).edit {
                putString(KEY_INTENT_URI, normalized.toUri(Intent.URI_INTENT_SCHEME))
            }
    }

    fun load(context: Context): Intent? {
        val stored = prefs(context).getString(KEY_INTENT_URI, null) ?: return null
        val parsed = runCatching {
            Intent.parseUri(stored, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: run {
            clear(context)
            return null
        }
        return normalizeIntent(context, parsed) ?: run {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit {remove(KEY_INTENT_URI)}
    }

    fun clearIfMatches(context: Context, intent: Intent) {
        val normalized = normalizeIntent(context, intent) ?: return
        val stored = prefs(context).getString(KEY_INTENT_URI, null)
        if (stored == normalized.toUri(Intent.URI_INTENT_SCHEME)) {
            clear(context)
        }
    }

    private fun normalizeIntent(context: Context, intent: Intent): Intent? {
        val component = intent.component ?: resolveInternalComponent(context, intent) ?: return null
        if (component.packageName != context.packageName) return null
        return Intent().setComponent(component).replaceExtras(intent)
    }

    private fun resolveInternalComponent(context: Context, intent: Intent): ComponentName? {
        val resolved = intent.resolveActivity(context.packageManager) ?: return null
        return resolved.takeIf { it.packageName == context.packageName }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
