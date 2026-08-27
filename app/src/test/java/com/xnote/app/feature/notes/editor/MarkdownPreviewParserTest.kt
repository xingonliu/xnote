package com.xnote.app.feature.notes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class MarkdownPreviewParserTest {
    @Test
    fun parserRecognizesTheConvertedMarkdownStructures() {
        val blocks = parseMarkdownPreview(
            """# 标题

- [x] 已完成
1. 第一步
> 引用

| 项目 | 状态 |
| --- | --- |
| A\|B | 完成 |

```kotlin
val answer = 42
```""",
        )

        assertTrue(blocks[0] is MarkdownPreviewBlock.Heading)
        assertEquals(true, (blocks[1] as MarkdownPreviewBlock.Checklist).checked)
        assertEquals("1", (blocks[2] as MarkdownPreviewBlock.Numbered).number)
        assertEquals("引用", (blocks[3] as MarkdownPreviewBlock.Quote).content)
        val table = blocks[4] as MarkdownPreviewBlock.Table
        assertEquals(listOf("项目", "状态"), table.header)
        assertEquals("A\\|B", table.rows.single().first())
        val code = blocks[5] as MarkdownPreviewBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.content)
    }
}
