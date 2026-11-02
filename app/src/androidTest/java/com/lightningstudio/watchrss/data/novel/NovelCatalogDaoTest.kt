package com.lightningstudio.watchrss.data.novel

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NovelCatalogDaoTest {
    @Test fun longDirectoryReturnsOnlyMetadataWithoutHydratingBodiesOrMedia() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "novel-metadata-${UUID.randomUUID()}"
        val db = Room.databaseBuilder(context, WatchRssDatabase::class.java, name).build()
        try {
            val source = "https://watchrss.local/import-epub/test"
            db.rssChannelDao().insertChannel(RssChannelEntity(1L, source, "目录", null, null, null, 0L, 0L, false))
            db.rssItemDao().insertItems((1L..450L).map { id ->
                RssItemEntity(id, 1L, "章节$id", "description", "body".repeat(300), "original".repeat(300),
                    "$source/chapter/${id.toString().padStart(4, '0')}-x", null, null,
                    "https://example.invalid/image.png", null, null, "summary", null,
                    false, false, 0f, "chapter-$id", id, 3_300)
            })
            val metadata = DatabaseNovelCatalogSource(db.rssItemDao()).observe(1L).first()
            assertEquals(450, metadata.size)
            assertTrue(metadata.all { it.content == null && it.originalContent == null && it.description == null })
            assertTrue(metadata.all { it.imageUrl == null && it.previewImageUrl == null })
            assertTrue(metadata.any { it.title == "章节450" })
            assertTrue(db.rssItemDao().getItem(1L)!!.content!!.isNotBlank())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
