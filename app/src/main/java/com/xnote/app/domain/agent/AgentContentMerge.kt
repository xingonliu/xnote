package com.xnote.app.domain.agent

import com.xnote.app.domain.document.*
import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
data class AgentEditableContent(val title: String, val document: NoteDocument)

sealed interface AgentContentMerge {
    data class Merged(val content: AgentEditableContent) : AgentContentMerge
    data class Conflict(val location: String) : AgentContentMerge
}

internal data class AgentSequenceEdit<T>(val start: Int, val end: Int, val replacement: List<T>)
private data class StyledCharacter(val codePoint: Int, val marks: InlineRun)
private class MergeConflict(val location: String) : Exception()

// -- Constants

// Bounds the Myers trace to about 1 MiB of integer storage for concurrent, heavily divergent edits.
private const val MaxDiffTraceCells = 262_144

// -- Functions

fun mergeAgentContent(base: AgentEditableContent, current: AgentEditableContent, proposed: AgentEditableContent): AgentContentMerge = try {
    val title = mergeSequence(base.title.codePoints().toArray().toList(), current.title.codePoints().toArray().toList(),
        proposed.title.codePoints().toArray().toList(), "标题").toCodePointString()
    val old = base.document.blocks.associateBy { it.id }
    val locations = base.document.blocks.mapIndexed { index, block -> block.id to "第 ${index + 1} 段" }.toMap()
    val latest = current.document.blocks.associateBy { it.id }
    val next = proposed.document.blocks.associateBy { it.id }
    for (id in old.keys) {
        if (id !in next && id in latest && latest[id] != old[id]) throw MergeConflict("${locations[id]}：删除与编辑重叠")
        if (id !in latest && id in next && next[id] != old[id]) throw MergeConflict("${locations[id]}：编辑与删除重叠")
    }
    val order = mergeSequence(base.document.blocks.map { it.id }, current.document.blocks.map { it.id },
        proposed.document.blocks.map { it.id }, "块顺序")
    if (order.distinct().size != order.size) throw MergeConflict("块顺序")
    val blocks = order.map { id ->
        val before = old[id]; val user = latest[id]; val agent = next[id]
        when {
            user == agent -> requireNotNull(user)
            user == before -> requireNotNull(agent)
            agent == before -> requireNotNull(user)
            before is TextBlock && user is TextBlock && agent is TextBlock -> mergeTextBlock(before, user, agent, locations.getValue(id))
            before is TableBlock && user is TableBlock && agent is TableBlock -> mergeTable(before, user, agent, locations.getValue(id))
            else -> throw MergeConflict(locations[id] ?: "新增段落")
        }
    }
    AgentContentMerge.Merged(AgentEditableContent(title, NoteDocument(
        mergeValue(base.document.schemaVersion, current.document.schemaVersion, proposed.document.schemaVersion, "文档结构"), blocks)))
} catch (conflict: MergeConflict) { AgentContentMerge.Conflict(conflict.location) }

private fun mergeTextBlock(base: TextBlock, current: TextBlock, proposed: TextBlock, location: String): TextBlock = current.copy(
    paragraphStyle = mergeValue(base.paragraphStyle, current.paragraphStyle, proposed.paragraphStyle, "$location 段落样式"),
    alignment = mergeValue(base.alignment, current.alignment, proposed.alignment, "$location 对齐"),
    listMarker = mergeValue(base.listMarker, current.listMarker, proposed.listMarker, "$location 清单类型"),
    indent = mergeValue(base.indent, current.indent, proposed.indent, "$location 缩进"),
    quoted = mergeValue(base.quoted, current.quoted, proposed.quoted, "$location 引用"),
    collapsed = mergeValue(base.collapsed, current.collapsed, proposed.collapsed, "$location 折叠"),
    checked = mergeValue(base.checked, current.checked, proposed.checked, "$location 勾选"),
    inlines = mergeInlines(base.inlines, current.inlines, proposed.inlines, "$location 文字或格式"),
)

private fun mergeTable(base: TableBlock, current: TableBlock, proposed: TableBlock, location: String): TableBlock {
    val shape = base.rows.map { it.cells.size }
    // Cells have no stable IDs. Concurrent structural edits cannot safely infer their identity.
    if (shape != current.rows.map { it.cells.size } || shape != proposed.rows.map { it.cells.size }) throw MergeConflict("$location 表格结构")
    return current.copy(rows = base.rows.mapIndexed { rowIndex, row -> TableRow(row.cells.mapIndexed { columnIndex, cell ->
        TableCell(mergeInlines(cell.inlines, current.rows[rowIndex].cells[columnIndex].inlines,
            proposed.rows[rowIndex].cells[columnIndex].inlines, "$location 表格 ${rowIndex + 1}:${columnIndex + 1}"))
    }) })
}

private fun mergeInlines(base: List<InlineRun>, current: List<InlineRun>, proposed: List<InlineRun>, path: String): List<InlineRun> {
    val merged = mergeSequence(base.characters(), current.characters(), proposed.characters(), path)
    val result = mutableListOf<InlineRun>()
    val text = StringBuilder()
    var marks: InlineRun? = null
    for (character in merged) {
        if (marks != character.marks) {
            marks?.let { result += it.copy(text = text.toString()) }
            text.setLength(0)
            marks = character.marks
        }
        text.appendCodePoint(character.codePoint)
    }
    marks?.let { result += it.copy(text = text.toString()) }
    return result
}

private fun List<InlineRun>.characters(): List<StyledCharacter> = flatMap { run ->
    val marks = run.copy(text = "")
    run.text.codePoints().toArray().map { StyledCharacter(it, marks) }
}

private fun List<Int>.toCodePointString(): String = StringBuilder().apply { this@toCodePointString.forEach { appendCodePoint(it) } }.toString()

private fun <T> mergeValue(base: T, current: T, proposed: T, path: String): T = when {
    current == proposed || proposed == base -> current
    current == base -> proposed
    else -> throw MergeConflict(path)
}

private fun <T> mergeSequence(base: List<T>, current: List<T>, proposed: List<T>, path: String): List<T> {
    if (current == proposed || proposed == base) return current
    if (current == base) return proposed
    val user = agentSequenceEdits(base, current) ?: throw MergeConflict("$path：并行差异超出合并预算")
    val agent = agentSequenceEdits(base, proposed) ?: throw MergeConflict("$path：并行差异超出合并预算")
    for (a in user) for (b in agent) {
        if (a == b) continue
        val overlap = if (a.start == a.end && b.start == b.end) a.start == b.start
            else if (a.start == a.end) a.start > b.start && a.start < b.end
            else if (b.start == b.end) b.start > a.start && b.start < a.end
            else maxOf(a.start, b.start) < minOf(a.end, b.end)
        if (overlap) throw MergeConflict(path)
    }
    val changes = (user + agent).distinct().sortedWith(compareBy<AgentSequenceEdit<T>> { it.start }.thenBy { it.end })
    return buildList {
        var offset = 0
        for (change in changes) {
            if (change.start < offset) throw MergeConflict(path)
            addAll(base.subList(offset, change.start)); addAll(change.replacement)
            offset = change.end
        }
        addAll(base.subList(offset, base.size))
    }
}

/** Exact shortest edit script, with unchanged edges removed before allocating the bounded trace. */
internal fun <T> agentSequenceEdits(before: List<T>, after: List<T>): List<AgentSequenceEdit<T>>? {
    var prefix = 0
    while (prefix < minOf(before.size, after.size) && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(before.size, after.size) - prefix && before[before.lastIndex - suffix] == after[after.lastIndex - suffix]) suffix++
    val a = before.subList(prefix, before.size - suffix)
    val b = after.subList(prefix, after.size - suffix)
    if (a.isEmpty() && b.isEmpty()) return emptyList()
    if (a.isEmpty() || b.isEmpty() || a.toHashSet().intersect(b.toSet()).isEmpty()) return listOf(AgentSequenceEdit(prefix, prefix + a.size, b.toList()))
    val trace = mutableListOf<IntArray>()
    for (distance in 0..(a.size + b.size)) {
        if ((distance + 1L) * (distance + 1L) > MaxDiffTraceCells) return null
        val frontier = IntArray(2 * distance + 1)
        for (diagonal in -distance..distance step 2) {
            val previous = trace.lastOrNull()
            var x = if (previous == null) 0 else {
                val offset = distance - 1
                if (diagonal == -distance || (diagonal != distance && previous[diagonal - 1 + offset] < previous[diagonal + 1 + offset]))
                    previous[diagonal + 1 + offset] else previous[diagonal - 1 + offset] + 1
            }
            var y = x - diagonal
            while (x < a.size && y < b.size && a[x] == b[y]) { x++; y++ }
            frontier[diagonal + distance] = x
            if (x >= a.size && y >= b.size) {
                var atA = a.size; var atB = b.size
                val matches = mutableListOf<Pair<Int, Int>>()
                for (d in distance downTo 1) {
                    val last = trace[d - 1]; val k = atA - atB; val offset = d - 1
                    val previousK = if (k == -d || (k != d && last[k - 1 + offset] < last[k + 1 + offset])) k + 1 else k - 1
                    val previousX = last[previousK + offset]; val previousY = previousX - previousK
                    while (atA > previousX && atB > previousY) { atA--; atB--; matches += atA to atB }
                    atA = previousX; atB = previousY
                }
                while (atA > 0 && atB > 0) { atA--; atB--; matches += atA to atB }
                matches.reverse()
                return buildList {
                    var fromA = 0; var fromB = 0
                    for ((matchA, matchB) in matches + (a.size to b.size)) {
                        if (matchA > fromA || matchB > fromB) add(AgentSequenceEdit(prefix + fromA, prefix + matchA, b.subList(fromB, matchB).toList()))
                        fromA = matchA + 1; fromB = matchB + 1
                    }
                }
            }
        }
        trace += frontier
    }
    return null
}
