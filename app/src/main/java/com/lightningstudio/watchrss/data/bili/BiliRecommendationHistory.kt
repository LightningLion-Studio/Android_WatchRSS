package com.lightningstudio.watchrss.data.bili

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

internal const val BILI_RECOMMENDATION_HISTORY_LIMIT = 512
private val accountIdPattern = Regex("[0-9]+")
private val recommendationIdPattern = Regex("aid:[1-9][0-9]*|bvid:\\S+")

/** UID survives Cookie rotation. Incomplete sessions never share anonymous history. */
internal fun biliRecommendationScope(account: BiliAccount?): String? {
    if (account == null) return null
    val session = account.cookies["SESSDATA"]?.takeIf { it.isNotBlank() } ?: return null
    val uid = account.cookies["DedeUserID"]?.trim()?.takeIf { it.matches(accountIdPattern) }
        ?.trimStart('0')?.takeIf { it.isNotEmpty() }
    if (uid != null) return "account:$uid"
    val digest = MessageDigest.getInstance("SHA-256").digest(session.toByteArray(Charsets.UTF_8))
    return "session:" + digest.joinToString("") { "%02x".format(it) }
}

internal fun biliRecommendationId(item: BiliItem): String? {
    val aid = item.aid?.takeIf { it > 0 }
    return (if (aid != null) "aid:$aid" else {
        item.bvid?.trim()?.takeIf { it.isNotEmpty() }?.let { "bvid:$it" }
    })?.takeIf(::validRecommendationId)
}

private fun validRecommendationId(id: String): Boolean =
    id.length <= 256 && id.matches(recommendationIdPattern)

internal fun recentBiliRecommendations(ids: Iterable<String>): List<String> =
    ids.filter(::validRecommendationId).distinct().takeLast(BILI_RECOMMENDATION_HISTORY_LIMIT)

internal fun rememberBiliRecommendations(previous: List<String>, items: List<BiliItem>): List<String> {
    val recent = recentBiliRecommendations(previous).toMutableSet()
    items.mapNotNull(::biliRecommendationId).forEach { id ->
        recent.remove(id)
        recent.add(id)
    }
    return recent.toList().takeLast(BILI_RECOMMENDATION_HISTORY_LIMIT)
}

/** Only visible rows call remember; fetching, caching and filtering never advance recency. */
internal class BiliRecommendationHistory(private val dataStore: DataStore<Preferences>) {
    private fun key(scope: String) = stringPreferencesKey("bili_feed_shown_v1:$scope")

    suspend fun read(scope: String?): List<String> {
        if (scope == null) return emptyList()
        return recentBiliRecommendations(dataStore.data.first()[key(scope)].orEmpty().split('\n'))
    }

    suspend fun remember(scope: String?, items: List<BiliItem>) {
        if (scope == null || items.none { biliRecommendationId(it) != null }) return
        dataStore.edit { preferences ->
            val previous = preferences[key(scope)].orEmpty().split('\n')
            preferences[key(scope)] = rememberBiliRecommendations(previous, items).joinToString("\n")
        }
    }
}
