package com.xnote.app.data.files

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.xnote.app.data.agent.AgentFileStore
import com.xnote.app.data.db.AgentFileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// -- Functions

suspend fun saveAgentFile(context: Context, store: AgentFileStore, card: AgentFileCard, target: Uri) = withContext(Dispatchers.IO) {
    requireNotNull(context.contentResolver.openOutputStream(target)).use { output -> store.file(card.id).inputStream().use { it.copyTo(output) } }
}

suspend fun agentFileShareIntent(context: Context, store: AgentFileStore, card: AgentFileCard): Intent = withContext(Dispatchers.IO) {
    val exported = File(context.cacheDir, "exports/${card.id}/${card.name}")
    exported.parentFile?.mkdirs()
    store.file(card.id).copyTo(exported, overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", exported)
    Intent(Intent.ACTION_SEND).apply {
        type = card.mimeType; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, card.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
