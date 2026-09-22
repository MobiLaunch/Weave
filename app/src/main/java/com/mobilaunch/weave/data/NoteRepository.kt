package com.mobilaunch.weave.data

import android.util.AtomicFile
import android.util.Log
import com.mobilaunch.weave.web.Vec3
import com.mobilaunch.weave.web.WebLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Holds the web in memory and mirrors it to a JSON file. State changes are applied
 * synchronously so animations can rely on them the same frame; disk writes happen on IO.
 */
class NoteRepository(file: File) {

    // Outlives any single screen so the last edit still reaches disk.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store = AtomicFile(file)
    private val writeLock = Mutex()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        scope.launch {
            val loaded = read()
            _notes.update { current ->
                val known = current.mapTo(HashSet()) { it.id }
                loaded.filter { it.id !in known } + current
            }
        }
    }

    fun create(text: String, parentId: String?): Note {
        val all = _notes.value
        val parent = all.firstOrNull { it.id == parentId }
        val pos = WebLayout.placeChild(parent?.pos, all.map { it.pos })
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = now,
            updatedAt = now,
            parentId = parent?.id,
            x = pos.x, y = pos.y, z = pos.z,
        )
        commit(all + note)
        return note
    }

    fun update(id: String, text: String) {
        val trimmed = text.trim()
        commit(
            _notes.value.map {
                if (it.id == id && it.text != trimmed) it.copy(text = trimmed, updatedAt = System.currentTimeMillis()) else it
            },
        )
    }

    /** Removes a thought; its branches re-attach to the thought it grew from. */
    fun delete(id: String) {
        val all = _notes.value
        val victim = all.firstOrNull { it.id == id } ?: return
        commit(
            all.filter { it.id != id }
                .map { if (it.parentId == id) it.copy(parentId = victim.parentId) else it },
        )
    }

    fun move(id: String, newParentId: String, delta: Vec3, subtree: Set<String>) {
        commit(
            _notes.value.map { n ->
                var m = n
                if (n.id in subtree) m = m.movedBy(delta)
                if (n.id == id) m = m.copy(parentId = newParentId)
                m
            },
        )
    }

    private fun commit(next: List<Note>) {
        _notes.value = next
        scope.launch {
            writeLock.withLock { write(_notes.value) }
        }
    }

    private fun read(): List<Note> = try {
        if (!store.baseFile.exists()) {
            emptyList()
        } else {
            val array = JSONArray(String(store.readFully(), Charsets.UTF_8))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Note(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    parentId = if (o.isNull("parentId")) null else o.getString("parentId"),
                    x = o.getDouble("x").toFloat(),
                    y = o.getDouble("y").toFloat(),
                    z = o.getDouble("z").toFloat(),
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the web", e)
        emptyList()
    }

    private fun write(notes: List<Note>) {
        val array = JSONArray()
        for (n in notes) {
            array.put(
                JSONObject()
                    .put("id", n.id)
                    .put("text", n.text)
                    .put("createdAt", n.createdAt)
                    .put("updatedAt", n.updatedAt)
                    .put("parentId", n.parentId ?: JSONObject.NULL)
                    .put("x", n.x.toDouble())
                    .put("y", n.y.toDouble())
                    .put("z", n.z.toDouble()),
            )
        }
        val out = try {
            store.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the web for writing", e)
            return
        }
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            store.finishWrite(out)
        } catch (e: Exception) {
            store.failWrite(out)
            Log.e(TAG, "Could not save the web", e)
        }
    }

    private companion object {
        const val TAG = "NoteRepository"
    }
}
