package com.lightningstudio.watchrss.data.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NotesHomePlacement(
    val sortOrder: Long,
    val isPinned: Boolean
)

/** Persists the notes entry with the same placement semantics used by RSS channels. */
class HomeEntryPlacementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun notesPlacement(): NotesHomePlacement {
        val sortOrder = if (preferences.contains(KEY_NOTES_SORT_ORDER)) {
            preferences.getLong(KEY_NOTES_SORT_ORDER, 0L)
        } else {
            System.currentTimeMillis().also { initialOrder ->
                preferences.edit().putLong(KEY_NOTES_SORT_ORDER, initialOrder).apply()
            }
        }
        return NotesHomePlacement(
            sortOrder = sortOrder,
            isPinned = preferences.getBoolean(KEY_NOTES_PINNED, false)
        )
    }

    fun observeNotesPlacement(): Flow<NotesHomePlacement> = callbackFlow {
        trySend(notesPlacement())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NOTES_SORT_ORDER || key == KEY_NOTES_PINNED) {
                trySend(notesPlacement())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun moveNotesToTop() {
        preferences.edit()
            .putLong(KEY_NOTES_SORT_ORDER, System.currentTimeMillis())
            .apply()
    }

    fun setNotesPinned(pinned: Boolean) {
        preferences.edit().apply {
            putBoolean(KEY_NOTES_PINNED, pinned)
            if (pinned) {
                putLong(KEY_NOTES_SORT_ORDER, System.currentTimeMillis())
            }
        }.apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "home_entry_placement"
        private const val KEY_NOTES_SORT_ORDER = "notes_sort_order"
        private const val KEY_NOTES_PINNED = "notes_pinned"
    }
}
