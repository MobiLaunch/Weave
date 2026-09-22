package com.mobilaunch.weave.data

import com.mobilaunch.weave.web.Vec3

data class Note(
    val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val parentId: String?,
    val x: Float,
    val y: Float,
    val z: Float,
) {
    val pos: Vec3 get() = Vec3(x, y, z)

    val snippet: String
        get() {
            val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "Untitled thought"
            return if (line.length > 40) line.take(38).trimEnd() + "…" else line
        }

    fun movedBy(d: Vec3) = copy(x = x + d.x, y = y + d.y, z = z + d.z)
}
