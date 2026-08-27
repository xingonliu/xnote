package com.xnote.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xnote.app.data.background.NoteBackgroundResolver
import com.xnote.app.data.background.ResolvedNoteBackground
import com.xnote.app.data.background.UserBackgroundImporter
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.domain.model.defaultBackgroundKey
import com.xnote.app.domain.model.encode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// -- Tests

@RunWith(AndroidJUnit4::class)
class UserBackgroundImporterInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-background-import-${System.nanoTime()}")
    private val library = NoteLibrary(
        database = database,
        files = AttachmentFileStore(filesRoot),
        clock = SystemEpochClock,
    )
    private var mediaUri: Uri? = null

    @After
    fun tearDown() {
        mediaUri?.let { context.contentResolver.delete(it, null, null) }
        database.close()
        filesRoot.deleteRecursively()
    }

    @Test
    fun galleryImageIsCopiedToAPrivateAttachmentAndCanBeResolved() = runTest {
        val sourceUri = createMediaStoreImage()
        val importedKey = UserBackgroundImporter(context.contentResolver, library).import(sourceUri)
        val attachment = checkNotNull(library.getAttachment(importedKey.attachmentId))

        assertEquals(AttachmentKind.UserBackground, attachment.kind)
        assertEquals("image/png", attachment.mimeType)
        assertEquals(16, attachment.widthPx)
        assertEquals(10, attachment.heightPx)
        assertTrue(library.attachmentFile(attachment).isFile)
        assertTrue(library.attachmentFile(attachment).length() > 0L)

        val resolution = NoteBackgroundResolver(library).resolve(
            noteBackgroundKey = importedKey.encode(),
            defaultBackgroundKeyRaw = defaultBackgroundKey().encode(),
        )
        assertFalse(resolution.fellBack)
        assertTrue(resolution.background is ResolvedNoteBackground.UserImage)
    }

    private fun createMediaStoreImage(): Uri {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "xnote-test-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XNoteTests")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(collection, values))
        mediaUri = uri
        val bitmap = Bitmap.createBitmap(16, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(222, 159, 62))
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } ?: error("Unable to open MediaStore test image")
        bitmap.recycle()
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }
}
