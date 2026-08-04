package com.lightningstudio.watchrss.data.tts

data class ReadAloudSegment(
    val text: String,
    val importedChunkIndex: Int? = null,
    val importedCharOffset: Int = 0,
    val contentBlockIndex: Int? = null,
    val contentCharOffset: Int = 0,
    val isTitle: Boolean = false,
    val sourceOffsets: IntArray? = null
) {
    fun sourceOffsetForTextOffset(textOffset: Int): Int {
        val offsets = sourceOffsets ?: return textOffset
        if (offsets.isEmpty()) return 0
        val index = textOffset.coerceIn(0, text.length)
        if (index >= offsets.size) {
            return offsets.last() + 1
        }
        return offsets[index]
    }

    fun sourceEndOffsetForTextOffset(textOffset: Int): Int {
        val offsets = sourceOffsets ?: return textOffset
        if (offsets.isEmpty()) return 0
        val index = textOffset.coerceIn(0, text.length)
        if (index >= offsets.size) {
            return offsets.last() + 1
        }
        return offsets[index].coerceAtLeast(sourceOffsetForTextOffset(textOffset))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadAloudSegment

        if (text != other.text) return false
        if (importedChunkIndex != other.importedChunkIndex) return false
        if (importedCharOffset != other.importedCharOffset) return false
        if (contentBlockIndex != other.contentBlockIndex) return false
        if (contentCharOffset != other.contentCharOffset) return false
        if (isTitle != other.isTitle) return false
        if (sourceOffsets != null) {
            if (other.sourceOffsets == null) return false
            if (!sourceOffsets.contentEquals(other.sourceOffsets)) return false
        } else if (other.sourceOffsets != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (importedChunkIndex ?: 0)
        result = 31 * result + importedCharOffset
        result = 31 * result + (contentBlockIndex ?: 0)
        result = 31 * result + contentCharOffset
        result = 31 * result + isTitle.hashCode()
        result = 31 * result + (sourceOffsets?.contentHashCode() ?: 0)
        return result
    }
}
