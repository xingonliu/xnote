package com.xnote.app.data

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.document.encodeToJson
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

// -- Tests

class NotebookMigrationTest {
    @Test
    fun versionTwoUpgradePreservesNotebooksAndNoteContents() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "notebook-migration-${System.nanoTime()}.db"
        val documentJson = emptyNoteDocument().encodeToJson()
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val schema = JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.xnote.app.data.db.XNoteDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val queries = schema.getJSONArray("setupQueries")
                for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
                connection.execSQL("INSERT INTO notebooks VALUES ('book', '升级前笔记本', 7, 100, 200)")
                connection.prepare("INSERT INTO notes VALUES ('note', 'book', '升级前标题', ?, NULL, 1, 0, 0, '', 100, 200, NULL, NULL)").use {
                    it.bindText(1, documentJson)
                    it.step()
                }
                connection.execSQL("PRAGMA user_version = 2")
            }
            val migrated = XNoteDatabase.create(context, name)
            try {
                val book = migrated.notebooks().get("book")
                assertNotNull(book)
                assertEquals("升级前笔记本", book?.name)
                assertEquals(7L, book?.sortIndex)
                assertEquals("gold", book?.color)
                assertEquals("notebook", book?.icon)
                val note = migrated.notes().get("note")
                assertEquals("升级前标题", note?.title)
                assertEquals("book", note?.notebookId)
                assertEquals(documentJson, note?.documentJson)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
