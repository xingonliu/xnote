package com.xnote.app.domain.text

// -- Functions

object MarkdownVisibleText {
    fun extract(markdown: String): String {
        var text = markdown.replace("\r\n", "\n")
        text = stripLeadingTitleHeading(text)
        text = text.replace(Regex("```[^\\n]*\\n"), "")
        text = text.replace("```", "")
        text = text.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("(?m)^#+\\s*"), "")
        text = text.replace(Regex("(?m)^>\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*-\\s\\[[ xX]\\]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$"), "")
        text = text.replace("|", " ")
        text = text.replace("**", "")
        text = text.replace("__", "")
        text = text.replace("~~", "")
        text = text.replace("==", "")
        text = text.replace(Regex("<u>|</u>", RegexOption.IGNORE_CASE), "")
        text = text.replace("`", "")
        text = text.replace("*", "")
        text = text.replace("_", "")
        return text
    }

    private fun stripLeadingTitleHeading(markdown: String): String {
        val firstLineEnd = markdown.indexOf('\n')
        val firstLine = if (firstLineEnd == -1) markdown else markdown.substring(0, firstLineEnd)
        if (!firstLine.startsWith("# ") && !firstLine.startsWith("#\t")) {
            return markdown
        }
        return if (firstLineEnd == -1) {
            ""
        } else {
            markdown.substring(firstLineEnd + 1)
        }
    }
}
