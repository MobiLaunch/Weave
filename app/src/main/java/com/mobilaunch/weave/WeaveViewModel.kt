package com.mobilaunch.weave

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mobilaunch.weave.data.Note
import com.mobilaunch.weave.data.NoteRepository
import com.mobilaunch.weave.web.MoveResult
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class WeaveViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = NoteRepository(File(app.filesDir, "weave.json"))

    val notes: StateFlow<List<Note>> = repository.notes

    fun createNote(text: String, parentId: String?): Note = repository.create(text, parentId)

    fun updateNote(id: String, text: String) = repository.update(id, text)

    fun deleteNote(id: String) = repository.delete(id)

    fun moveNote(move: MoveResult) = repository.move(move.id, move.newParentId, move.delta, move.subtree)
}
