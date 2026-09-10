package com.xnote.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.files.LocalStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// -- Tests

class LocalStorageInstrumentedTest {
    @Test fun cacheCleanupKeepsRecentSharesAttachmentsDatabaseAndUnknownCaches() = runTest {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "storage-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir() = File(root, "cache")
            override fun getFilesDir() = File(root, "files")
            override fun getDatabasePath(name: String) = File(root, "databases/$name")
        }
        fun create(path: String, old: Boolean = false) = File(root, path).apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(100))
            if (old) setLastModified(System.currentTimeMillis() - 86_400_001L)
        }
        try {
            val database = create("databases/xnote.db")
            create("databases/xnote.db-wal")
            create("databases/unrelated.db")
            val attachment = create("files/attachments/photo.png")
            val oldExport = create("cache/exports/old/page.png", true)
            val camera = create("cache/camera/old.png", true)
            val recent = create("cache/exports/new/page.png")
            val other = create("cache/other-cache", true)
            val storage = LocalStorage(context)
            val before = storage.usage()
            assertEquals(200L, before.databaseBytes)
            assertEquals(100L, before.attachmentBytes)
            assertEquals(400L, before.cacheBytes)
            assertEquals(200L, before.clearableBytes)
            val after = storage.clearCache()
            assertEquals(200L, after.cacheBytes)
            assertEquals(0L, after.clearableBytes)
            assertFalse(oldExport.exists()); assertFalse(camera.exists())
            assertTrue(database.exists()); assertTrue(attachment.exists()); assertTrue(recent.exists()); assertTrue(other.exists())
        } finally { root.deleteRecursively() }
    }
}
