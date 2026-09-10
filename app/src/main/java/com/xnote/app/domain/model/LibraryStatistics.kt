package com.xnote.app.domain.model

import com.xnote.app.domain.document.*
import com.xnote.app.domain.text.visibleTextStats

// -- Type Definitions

data class LibraryStatistics(
    val noteCount: Int,
    val characterCount: Long,
    val latinWordCount: Long,
    val notebookCharacters: Map<String?, Long>,
    val tableNoteCount: Int,
    val imageNoteCount: Int,
    val stickerNoteCount: Int,
    val drawingNoteCount: Int,
    val imageCount: Int,
    val stickerCount: Int,
    val drawingCount: Int,
    val recentlyCreated: List<Note>,
    val recentlyUpdated: List<Note>,
)

// -- Functions

fun libraryStatistics(notes: List<Note>): LibraryStatistics {
    val active = notes.filterNot { it.isTrashed }
    val text = active.associate { it.id to visibleTextStats(it) }
    val blocks = active.flatMap { it.document.blocks }
    return LibraryStatistics(
        noteCount = active.size,
        characterCount = text.values.sumOf { it.characterCount.toLong() },
        latinWordCount = text.values.sumOf { it.latinWordCount.toLong() },
        notebookCharacters = active.groupBy { it.notebookId }.mapValues { (_, group) ->
            group.sumOf { text.getValue(it.id).characterCount.toLong() }
        },
        tableNoteCount = active.count { it.document.blocks.any { block -> block is TableBlock } },
        imageNoteCount = active.count { it.document.blocks.any { block -> block is ImageBlock } },
        stickerNoteCount = active.count { it.document.blocks.any { block -> block is StickerBlock } },
        drawingNoteCount = active.count { it.document.blocks.any { block -> block is DrawingBlock } },
        imageCount = blocks.count { it is ImageBlock },
        stickerCount = blocks.count { it is StickerBlock },
        drawingCount = blocks.count { it is DrawingBlock },
        recentlyCreated = active.sortedWith(compareByDescending<Note> { it.createdAtEpochMs }.thenBy { it.id }).take(5),
        recentlyUpdated = active.sortedWith(compareByDescending<Note> { it.updatedAtEpochMs }.thenBy { it.id }).take(5),
    )
}
