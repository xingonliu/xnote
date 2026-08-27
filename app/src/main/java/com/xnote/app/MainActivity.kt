package com.xnote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xnote.app.design.XNoteTheme

// -- Activities

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val library = (application as XNoteApplication).container.noteLibrary
            XNoteTheme {
                XNoteApp(noteLibrary = library)
            }
        }
    }
}
