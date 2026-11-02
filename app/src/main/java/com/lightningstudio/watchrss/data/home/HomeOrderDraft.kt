package com.lightningstudio.watchrss.data.home

/** Zero denotes the local notes entry; real channel IDs are positive. */
const val NOTES_HOME_ORDER_ID = 0L

fun moveHomeOrderItem(ids: List<Long>, id: Long, targetIndex: Int): List<Long> {
    val from = ids.indexOf(id)
    if (from < 0 || ids.isEmpty()) return ids
    val to = targetIndex.coerceIn(0, ids.lastIndex)
    if (from == to) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

fun validateHomeOrder(ids: List<Long>, currentChannelIds: List<Long>) {
    require(ids.size == ids.distinct().size && ids.toSet() == (currentChannelIds + NOTES_HOME_ORDER_ID).toSet()) {
        "列表已变化，请返回后重新打开排序页面"
    }
}
