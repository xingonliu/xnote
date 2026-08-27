package com.xnote.app.domain.markdown

import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TableCell
import com.xnote.app.domain.document.TableRow
import com.xnote.app.domain.document.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class RichNoteMarkdownTest {
    @Test
    fun conversionPreservesBlockOrderAndSupportedFormatting() {
        val document = NoteDocument(
            blocks = listOf(
                TextBlock(
                    id = "heading",
                    paragraphStyle = ParagraphStyle.Heading,
                    inlines = listOf(InlineRun("概览", bold = true)),
                ),
                TextBlock(
                    id = "check",
                    listMarker = ListMarker.Checklist,
                    indent = 1,
                    checked = true,
                    inlines = listOf(InlineRun("完成转换")),
                ),
                TextBlock(
                    id = "quote",
                    quoted = true,
                    inlines = listOf(
                        InlineRun("链接", linkUrl = "https://example.com/a)"),
                        InlineRun("与高亮", highlight = true, underline = true),
                    ),
                ),
                TableBlock(
                    id = "table",
                    rows = listOf(
                        TableRow(
                            cells = listOf(
                                TableCell(listOf(InlineRun("项目"))),
                                TableCell(listOf(InlineRun("状态", italic = true))),
                            ),
                        ),
                        TableRow(
                            cells = listOf(
                                TableCell(listOf(InlineRun("A|B"))),
                                TableCell(listOf(InlineRun("完成", strikethrough = true))),
                            ),
                        ),
                    ),
                ),
                TextBlock(
                    id = "code",
                    paragraphStyle = ParagraphStyle.Monospace,
                    inlines = listOf(InlineRun("val answer = 42\nprintln(answer)")),
                ),
            ),
        )

        assertEquals(
            """# 计划

## **概览**

  - [x] 完成转换

> [链接](https://example.com/a\))==<u>与高亮</u>==

| 项目 | *状态* |
| --- | --- |
| A\|B | ~~完成~~ |

```
val answer = 42
println(answer)
```""",
            richNoteMarkdown("计划", document),
        )
    }

    @Test
    fun conversionDropsAlignmentAndCollapseWhileKeepingText() {
        val document = NoteDocument(
            blocks = listOf(
                TextBlock(
                    id = "subheading",
                    paragraphStyle = ParagraphStyle.Subheading,
                    alignment = com.xnote.app.domain.document.TextAlignment.Right,
                    collapsed = true,
                    inlines = listOf(InlineRun("小节")),
                ),
            ),
        )

        assertEquals("# 标题\n\n### 小节", richNoteMarkdown("标题", document))
    }

    @Test
    fun markdownTitleComesOnlyFromTheLeadingLevelOneHeading() {
        assertEquals("格式标题", markdownDocumentTitle("# **格式**标题 #\n正文"))
        assertEquals("", markdownDocumentTitle("## 小标题\n正文"))
    }

    @Test
    fun emptyTitleDoesNotCreateAVisibleHashHeading() {
        val document = NoteDocument(
            blocks = listOf(TextBlock(id = "body", inlines = listOf(InlineRun("正文")))),
        )

        assertEquals("正文", richNoteMarkdown("", document))
    }
}
