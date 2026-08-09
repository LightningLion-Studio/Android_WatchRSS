package com.lightningstudio.watchrss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNoteRichTextTest {
    @Test
    fun longMarkdown_isSplitIntoLazyLayoutSizedBlocks() {
        val paragraph = "A paragraph with **formatting** and enough text to exercise chunking."
        val markdown = List(80) { "$paragraph $it" }.joinToString("\n\n")

        val chunks = chunkWatchNotePreviewBlocks(
            blocks = listOf(WatchNotePreviewBlock.RichText(markdown)),
            maxRichTextChars = 500
        ).filterIsInstance<WatchNotePreviewBlock.RichText>()

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.markup.length <= 500 })
        assertEquals(
            markdown.replace("\n\n", ""),
            chunks.joinToString("") { it.markup }.replace("\n\n", "")
        )
    }

    @Test
    fun fencedCode_isNeverSplitIntoInvalidMarkdownFragments() {
        val fenced = "```text\n" + "long code line\n".repeat(80) + "```"

        val chunks = chunkWatchNotePreviewBlocks(
            blocks = listOf(WatchNotePreviewBlock.RichText("before\n\n$fenced\n\nafter")),
            maxRichTextChars = 120
        ).filterIsInstance<WatchNotePreviewBlock.RichText>()

        assertTrue(chunks.any { it.markup.contains(fenced) })
    }

    @Test
    fun pipeTable_keepsPhoneAlignmentAndWideColumns() {
        val markdown = """
            Intro

            | Lua function | Legacy dispatcher | Arguments | Behavior | Why it exists |
            | :--- | :---: | ---: | --- | --- |
            | `foo()` | `bar` | none | opens | compatibility |

            Outro
        """.trimIndent()

        val table = parseWatchNotePreviewBlocks(markdown)
            .filterIsInstance<WatchNotePreviewBlock.Table>()
            .single()
            .table

        assertEquals(5, table.columnCount)
        assertEquals(WatchNoteTableAlignment.Start, table.alignments[0])
        assertEquals(WatchNoteTableAlignment.Center, table.alignments[1])
        assertEquals(WatchNoteTableAlignment.End, table.alignments[2])
        assertEquals("`foo()`", table.rows[1][0])
    }

    @Test
    fun tabSeparatedText_matchesPhoneTableBehavior() {
        val tsv = "Name\tCommand\tDescription\nMission\thymission:toggle\tOpen overview"

        val table = parseWatchNotePreviewBlocks(tsv)
            .filterIsInstance<WatchNotePreviewBlock.Table>()
            .single()
            .table

        assertEquals(3, table.columnCount)
        assertEquals(2, table.rows.size)
        assertEquals("hymission:toggle", table.rows[1][1])
    }

    @Test
    fun flattenedRichEditorTable_isRecoveredAsRealTableBlock() {
        val flattened = """
            #### Workspace scope, ordering, and transitions

            Option  Type  Default  Description

            `multi_workspace_sort_recent_first`  bool  `1`  Sort recent workspaces first.
            `only_active_workspace`  bool  `0`  Restrict the default scope.

            Following prose.
        """.trimIndent()

        val blocks = parseWatchNotePreviewBlocks(flattened)
        val table = blocks.filterIsInstance<WatchNotePreviewBlock.Table>().single().table

        assertEquals(4, table.columnCount)
        assertEquals(3, table.rows.size)
        assertEquals("Option", table.rows[0][0])
        assertEquals("`multi_workspace_sort_recent_first`", table.rows[1][0])
        assertEquals("Restrict the default scope.", table.rows[2][3])
    }

    @Test
    fun singleSpaceFlattenedConfigTable_isRecoveredFromStructuredHeader() {
        val flattened = """
            Option  Type  Default  Description

            `outer_padding` int `32` Legacy fallback for all four edge paddings.
            `layout_engine_forceall` string  empty  Engine override for the forceall scope.
            `max_preview_scale` float `0.95` Maximum preview scale for all-workspace overview.

            Following prose.
        """.trimIndent()

        val table = parseWatchNotePreviewBlocks(flattened)
            .filterIsInstance<WatchNotePreviewBlock.Table>()
            .single()
            .table

        assertEquals(4, table.columnCount)
        assertEquals(4, table.rows.size)
        assertEquals("`outer_padding`", table.rows[1][0])
        assertEquals("int", table.rows[1][1])
        assertEquals("`32`", table.rows[1][2])
        assertEquals("Legacy fallback for all four edge paddings.", table.rows[1][3])
        assertEquals("string", table.rows[2][1])
        assertEquals("empty", table.rows[2][2])
        assertEquals("Engine override for the forceall scope.", table.rows[2][3])
    }

    @Test
    fun singleSpaceFlattenedDispatcherTable_stillGetsARealGrid() {
        val flattened = """
            Lua function  Legacy dispatcher  Arguments  Behavior  Why it exists

            `hl.plugin.hymission.open(args?)` `hymission:open` Optional scope Ensures overview is open. Gives integrations a deterministic operation.
            `hl.plugin.hymission.close()` `hymission:close` None Ensures overview is closed. Gives scripts a deterministic operation.
        """.trimIndent()

        val table = parseWatchNotePreviewBlocks(flattened)
            .filterIsInstance<WatchNotePreviewBlock.Table>()
            .single()
            .table

        assertEquals(5, table.columnCount)
        assertEquals(3, table.rows.size)
        assertEquals("`hl.plugin.hymission.open(args?)`", table.rows[1][0])
        assertEquals("`hymission:open`", table.rows[1][1])
        assertTrue(table.rows[1][2].contains("Ensures overview is open."))
    }

    @Test
    fun isolatedDoubleSpacesInProse_doNotBecomeTable() {
        val blocks = parseWatchNotePreviewBlocks(
            "First sentence.  Second sentence.\n\nAnother paragraph.  Still prose."
        )

        assertTrue(blocks.none { it is WatchNotePreviewBlock.Table })
    }

    @Test
    fun tableSyntaxInsideCodeFence_staysRichText() {
        val markdown = """
            ```text
            A\tB\tC
            | A | B |
            | --- | --- |
            ```
        """.trimIndent()

        val blocks = parseWatchNotePreviewBlocks(markdown)

        assertTrue(blocks.none { it is WatchNotePreviewBlock.Table })
    }

    @Test
    fun richHtmlDetection_matchesPhoneStorageFallback() {
        assertTrue("<span style=\"color:#fff\">text</span>".isWatchNoteRichHtml())
    }

    @Test
    fun markdownImage_isIndependentClickablePreviewBlock() {
        val blocks = parseWatchNotePreviewBlocks(
            "Before\n![photo](assets/example.jpg)\nAfter line one\nAfter line two"
        )

        val image = blocks.filterIsInstance<WatchNotePreviewBlock.Image>().single()
        assertEquals("assets/example.jpg", image.path)
        assertEquals("photo", image.description)
        assertTrue(blocks.filterIsInstance<WatchNotePreviewBlock.RichText>()
            .joinToString("\n") { it.markup }
            .contains("After line one\nAfter line two"))
    }

    @Test
    fun richHtmlImage_isIndependentClickablePreviewBlock() {
        val blocks = parseWatchNotePreviewBlocks(
            "<p>Before</p><img src=\"assets/example.jpg\" alt=\"photo\" width=\"320\"><p>After</p>"
        )

        val image = blocks.filterIsInstance<WatchNotePreviewBlock.Image>().single()
        assertEquals("assets/example.jpg", image.path)
        assertEquals("photo", image.description)
        assertTrue(blocks.filterIsInstance<WatchNotePreviewBlock.RichText>().all { it.html })
    }

    @Test
    fun markdownImageSyntaxInsideCodeFence_staysRichText() {
        val blocks = parseWatchNotePreviewBlocks(
            "```markdown\n![not an image](assets/example.jpg)\n```"
        )

        assertTrue(blocks.none { it is WatchNotePreviewBlock.Image })
    }

    @Test
    fun readEditAnchor_findsSameTextAcrossMarkdownFormatting() {
        val plain = "标题\n前文\n这里是刚才读到的位置\n后文"
        val markdown = "# 标题\n\n前文\n\n**这里是刚才读到的位置**\n\n后文"
        val plainOffset = plain.indexOf("刚才")

        val markdownOffset = mapNoteTextAnchorOffset(plain, markdown, plainOffset)

        assertEquals(markdown.indexOf("刚才"), markdownOffset)
    }
}
