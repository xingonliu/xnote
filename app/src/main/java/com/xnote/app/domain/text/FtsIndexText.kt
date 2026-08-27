package com.xnote.app.domain.text

// -- Functions

object FtsIndexText {
    fun prepare(text: String): String {
        if (text.isEmpty()) return text
        val output = StringBuilder(text.length * 2)
        var index = 0
        var previousCodePoint = -1
        var previousIsCjk = false
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val isCjk = isCjkCodePoint(codePoint)
            val isWhitespace = Character.isWhitespace(codePoint)
            if (output.isNotEmpty() && !isWhitespace && previousCodePoint != -1 && !Character.isWhitespace(previousCodePoint)) {
                val needsSeparator = (previousIsCjk && isCjk) ||
                    (previousIsCjk && !isCjk) ||
                    (!previousIsCjk && isCjk)
                if (needsSeparator) {
                    output.append(' ')
                }
            }
            output.appendCodePoint(codePoint)
            previousCodePoint = codePoint
            previousIsCjk = isCjk
            index += Character.charCount(codePoint)
        }
        return output.toString().trim().replace(Regex("\\s+"), " ")
    }

    fun matchQuery(rawQuery: String): String? {
        val prepared = prepare(rawQuery.trim())
        if (prepared.isEmpty()) return null
        val sanitized = prepared
            .replace("\"", " ")
            .replace(Regex("[{}()*?:^]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (sanitized.isEmpty()) return null
        return "\"$sanitized\""
    }

    private fun isCjkCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2FA1F ||
            codePoint in 0x3040..0x30FF ||
            codePoint in 0xAC00..0xD7AF
    }
}
