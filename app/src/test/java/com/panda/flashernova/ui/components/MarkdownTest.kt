package com.panda.flashernova.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Markdown rendering in the update dialog.
 * Samples are taken from how the release notes in this project's docs/releases are actually written, plus nested lists/code blocks common on GitHub.
 */
class MarkdownTest {

    private val sample = """
        ## Highlights

        Fixed the startup crash below Android 13 caused by the **liquid glass component**.

        - Added kernel zip rollback: `Operation history` gains a "Flashed kernel zip" entry
        - Cloud G(O)KI repository improvements
          - Manager version red highlight
          - Latest version star marker

        > It is recommended to do an auto backup before upgrading.

        1. Open settings
        2. Tap check for updates

        ```bash
        adb install -r app-release.apk
        ```

        ---

        See [release page](https://github.com/WajahatNaeem056/FlasherNova/releases/tag/v1.0.0).
    """.trimIndent()

    @Test
    fun parsesEveryBlockKindUsedInReleaseNotes() {
        val blocks = parseMarkdownBlocks(sample)
        assertEquals(1, blocks.count { it is MdBlock.Heading })
        assertEquals(4, blocks.count { it is MdBlock.Bullet })
        assertEquals(1, blocks.count { it is MdBlock.Quote })
        assertEquals(2, blocks.count { it is MdBlock.Numbered })
        assertEquals(1, blocks.count { it is MdBlock.CodeBlock })
        assertEquals(1, blocks.count { it is MdBlock.Rule })

        val heading = blocks.filterIsInstance<MdBlock.Heading>().first()
        assertEquals(2, heading.level)
        assertEquals("Highlights", heading.text)

        val bullets = blocks.filterIsInstance<MdBlock.Bullet>()
        assertEquals("Added kernel zip rollback: `Operation history` gains a \"Flashed kernel zip\" entry", bullets[0].text)
        assertEquals(0, bullets[0].depth)
        // Sub-item indented by two spaces -> depth 1
        assertEquals(1, bullets[2].depth)
        assertEquals("Manager version red highlight", bullets[2].text)

        val numbered = blocks.filterIsInstance<MdBlock.Numbered>()
        assertEquals(listOf("1.", "2."), numbered.map { it.marker })

        val code = blocks.filterIsInstance<MdBlock.CodeBlock>().first()
        assertEquals(listOf("adb install -r app-release.apk"), code.lines)

        val quote = blocks.filterIsInstance<MdBlock.Quote>().first()
        assertEquals("It is recommended to do an auto backup before upgrading.", quote.text)
    }

    @Test
    fun paragraphsAreNotSwallowedByNeighbouringBlocks() {
        val blocks = parseMarkdownBlocks(sample)
        val paragraphs = blocks.filterIsInstance<MdBlock.Paragraph>().map { it.text }
        assertTrue(paragraphs.any { it.contains("liquid glass component") })
        assertTrue(paragraphs.any { it.contains("See") })
        // Blank lines around lists/code blocks must split paragraphs, otherwise the whole text is squeezed into one
        assertEquals(2, paragraphs.size)
    }

    @Test
    fun unclosedFenceStillRendersAsCode() {
        val blocks = parseMarkdownBlocks("Note\n```\nkeep me\n")
        assertEquals(1, blocks.count { it is MdBlock.CodeBlock })
        assertEquals(listOf("keep me"), blocks.filterIsInstance<MdBlock.CodeBlock>().first().lines)
    }

    @Test
    fun emptyNotesProduceNoBlocks() {
        assertTrue(parseMarkdownBlocks("").isEmpty())
        assertTrue(parseMarkdownBlocks("\n\n  \n").isEmpty())
    }

    @Test
    fun inlineFormattingIsStrippedButStyled() {
        val color = Color(0xFF123456)
        val bold = inlineMarkdown("Fixed **Android 13** crash", color)
        assertEquals("Fixed Android 13 crash", bold.text)
        assertTrue(bold.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        val code = inlineMarkdown("Run `dd` backup", color)
        assertEquals("Run dd backup", code.text)

        val link = inlineMarkdown("See [release page](https://example.com)", color)
        assertEquals("See release page", link.text)
        assertTrue(link.spanStyles.any { it.item.color == color })

        val italic = inlineMarkdown("This is *italic*", color)
        assertEquals("This is italic", italic.text)
    }

    @Test
    fun plainTextPassesThroughUntouched() {
        val plain = inlineMarkdown("A line without any markup", Color.Unspecified)
        assertEquals("A line without any markup", plain.text)
        assertTrue(plain.spanStyles.isEmpty())
    }
}
