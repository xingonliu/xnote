package com.xnote.app

import android.app.Application
import com.xnote.app.data.XNoteContainer

// -- Type Definitions

class XNoteApplication : Application() {
    lateinit var container: XNoteContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = XNoteContainer(this)
        container.start()
    }
}
