package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.rss.SavedItem
import org.json.JSONArray
import org.json.JSONObject

object SavedItemsSyncPayload {
    fun buildLinksOnly(items: List<SavedItem>): JSONArray {
        return JSONArray().apply {
            items.forEach { savedItem ->
                val link = savedItem.item.link?.trim().orEmpty()
                if (link.isNotEmpty()) {
                    put(
                        JSONObject().apply {
                            put("link", link)
                        }
                    )
                }
            }
        }
    }
}
