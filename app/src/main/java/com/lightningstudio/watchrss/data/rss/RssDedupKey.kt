package com.lightningstudio.watchrss.data.rss

object RssDedupKey {
    fun compute(guid: String?, link: String?, title: String): String = when {
        !guid.isNullOrBlank() -> "guid:$guid"
        !link.isNullOrBlank() -> "link:$link"
        else -> "title:$title"
    }
}
