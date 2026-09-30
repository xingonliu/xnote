package com.xnote.app.feature.profile

import androidx.compose.runtime.Composable
import com.xnote.app.data.agent.AgentNoteMemoryStore
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook

// -- Functions

@Composable
fun ProfileDetailScreen(
    page: String,
    notes: List<Note>,
    notebooks: List<Notebook>,
    noteMemory: AgentNoteMemoryStore?,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    when (page) {
        "统计" -> StatisticsScreen(notes, notebooks, onBack, onOpenNote)
        "存储与隐私" -> StorageScreen(noteMemory, onBack)
    }
}
