package com.xnote.app.domain.markdown

import com.xnote.app.domain.text.MarkdownVisibleText

// -- Constants

private val LeadingTitlePattern = Regex("^#(?:[ \\t]+|$)(.*)$")
private val ClosingHeadingPattern = Regex("[ \\t]+#+[ \\t]*$")

// -- Functions

fun markdownDocumentTitle(markdown: String): String {
    val firstLine = markdown.replace("\r\n", "\n").lineSequence().firstOrNull().orEmpty()
    val rawTitle = LeadingTitlePattern.matchEntire(firstLine)?.groupValues?.get(1) ?: return ""
    return MarkdownVisibleText.extractInline(rawTitle.replace(ClosingHeadingPattern, "")).trim()
}
