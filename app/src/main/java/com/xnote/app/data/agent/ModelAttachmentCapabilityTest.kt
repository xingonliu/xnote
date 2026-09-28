package com.xnote.app.data.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

// -- Type Definitions

class ModelAttachmentCapabilityTest(private val client: ModelClient, private val store: ModelProfileStore) {
    // -- Functions

    suspend fun test(profile: ModelProfile): ModelCapabilityResult {
        val secret = store.credential(profile)
        val probe = profile.copy(outputTokens = minOf(profile.outputTokens, 1024))
        var capabilities = profile.capabilities.copy(images = false, pdf = false, attachmentsTestedAtEpochMs = System.currentTimeMillis())
        val results = mutableListOf<String>()
        for (mime in listOf("image/png", "application/pdf")) {
            val marker = "XNOTE" + UUID.randomUUID().toString().take(8).replace("-", "").uppercase()
            try {
                val bytes = fixture(mime, marker)
                val file = ModelInputFile(if (mime == "image/png") "test.png" else "test.pdf", mime, Base64.getEncoder().encodeToString(bytes), 4096)
                var answer = ""
                var completed = false
                client.stream(probe, secret, ModelRequest("Read the supplied attachment. Return only the printed code.",
                    listOf(ModelMessage(AgentMessageRole.User, "What code is printed in this attachment?", files = listOf(file))))).collect {
                    if (it is ModelEvent.Text) answer += it.value
                    if (it is ModelEvent.Finished) completed = it.reason == ModelFinish.Complete
                }
                require(completed && answer.contains(marker, ignoreCase = true))
                capabilities = if (mime == "image/png") capabilities.copy(images = true) else capabilities.copy(pdf = true)
                results += "${if (mime == "image/png") "图片" else "PDF"}验证通过"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { results += "${if (mime == "image/png") "图片" else "PDF"}未通过：${safeModelError(error)}" }
            store.recordCapabilities(profile, capabilities)
        }
        return ModelCapabilityResult(capabilities, results.joinToString("；"))
    }

    private fun fixture(mime: String, marker: String): ByteArray {
        val output = ByteArrayOutputStream()
        val paint = Paint().apply { color = Color.BLACK; textSize = 24f; isAntiAlias = true }
        if (mime == "image/png") {
            val bitmap = createBitmap(480, 120)
            try {
                val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE); canvas.drawText(marker, 24f, 72f, paint)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally { bitmap.recycle() }
        } else {
            val document = PdfDocument()
            try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(480, 120, 1).create())
            page.canvas.drawText(marker, 24f, 72f, paint)
            document.finishPage(page); document.writeTo(output)
            } finally { document.close() }
        }
        return output.toByteArray()
    }
}
