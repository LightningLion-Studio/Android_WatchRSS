package com.lightningstudio.watchrss.data.note

/** Null means an unresolved branch conflict; callers must retain both versions. */
internal fun mergeNoteVersions(local: WatchNoteEntity?, remote: WatchNoteEntity, deviceId: String): WatchNoteEntity? {
    fun canonical(note: WatchNoteEntity) = note.copy(contentHash = watchNoteHash(note.markdown), baseContentHash = watchNoteHash(note.baseMarkdown), plainText = watchPlainText(note.markdown))
    val incoming = canonical(remote)
    if (local == null) return incoming
    require(local.noteId == remote.noteId)
    val current = canonical(local)
    val winner = if (compareValuesBy(incoming, current, { it.updatedAt }, { it.deleted }, { it.modifiedBy }, { it.title }, { it.folderId.orEmpty() }, { it.pinned }) > 0) incoming else current
    if (current.deleted != incoming.deleted) {
        val tombstone = if (current.deleted) current else incoming
        val live = if (current.deleted) incoming else current
        // No implicit undelete: unchanged live snapshots cannot reverse a tombstone.
        // A different edited branch is retained for an explicit user decision.
        return if (live.markdown == tombstone.markdown || live.markdown == tombstone.baseMarkdown) tombstone else null
    }
    if (current.deleted) return winner
    if (current.markdown == incoming.markdown) return winner
    val body = when {
        current.markdown == incoming.baseMarkdown -> incoming.markdown // proven descendant
        incoming.markdown == current.baseMarkdown -> current.markdown // proven ancestor
        current.baseMarkdown == incoming.baseMarkdown -> when (val result = MarkdownThreeWayMerge.merge(current.baseMarkdown, current.markdown, incoming.markdown)) {
            is MarkdownMergeResult.Merged -> result.markdown
            MarkdownMergeResult.Conflict -> return null
        }
        else -> return null // different bases cannot safely be fed into diff3
    }
    return when (body) {
        current.markdown -> winner.copy(markdown = body, contentHash = current.contentHash, plainText = current.plainText,
            baseMarkdown = current.baseMarkdown, baseContentHash = current.baseContentHash)
        incoming.markdown -> winner.copy(markdown = body, contentHash = incoming.contentHash, plainText = incoming.plainText,
            baseMarkdown = incoming.baseMarkdown, baseContentHash = incoming.baseContentHash)
        else -> winner.copy(markdown = body, contentHash = watchNoteHash(body), plainText = watchPlainText(body),
            baseMarkdown = incoming.markdown, baseContentHash = incoming.contentHash,
            updatedAt = maxOf(current.updatedAt, incoming.updatedAt).coerceAtMost(Long.MAX_VALUE - 1) + 1, modifiedBy = deviceId)
    }
}

internal fun nextNoteTimestamp(previous: Long?, now: Long): Long {
    require(previous != Long.MAX_VALUE) { "备忘录版本超出范围" }
    return maxOf(now, previous?.plus(1) ?: now)
}
