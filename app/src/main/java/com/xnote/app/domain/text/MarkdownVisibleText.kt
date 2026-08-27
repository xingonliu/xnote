package com.xnote.app.domain.text

// -- Functions

object MarkdownVisibleText {
    fun extract(markdown: String): String {
        val escaped = mutableListOf<String>()
        var text = protectEscapedCharacters(markdown.replace("\r\n", "\n"), escaped)
        text = stripLeadingTitleHeading(text)
        text = text.replace(Regex("```[^\\n]*\\n"), "")
        text = text.replace("```", "")
        text = text.replace(Regex("(?m)^#+\\s*"), "")
        text = text.replace(Regex("(?m)^>\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*-\\s\\[[ xX]\\]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$"), "")
        text = text.replace("|", " ")
        return restoreEscapedCharacters(stripInlineSyntax(text), escaped)
    }

    fun extractInline(markdown: String): String {
        val escaped = mutableListOf<String>()
        val text = protectEscapedCharacters(markdown, escaped)
        return restoreEscapedCharacters(stripInlineSyntax(text), escaped)
    }

    private fun stripInlineSyntax(markdown: String): String {
        var text = markdown
        text = text.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
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

    private fun protectEscapedCharacters(markdown: String, escaped: MutableList<String>): String {
        return Regex("\\\\([\\\\`*_{}\\[\\]<>()#+\\-.!|~=])").replace(markdown) { match ->
            val index = escaped.size
            escaped += match.groupValues[1]
            "\uE000$index\uE001"
        }
    }

    private fun restoreEscapedCharacters(markdown: String, escaped: List<String>): String {
        return Regex("\uE000(\\d+)\uE001").replace(markdown) { match ->
            escaped.getOrNull(match.groupValues[1].toInt()).orEmpty()
        }
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
