package com.mobilaunch.weave.data

/** How a thought feels. Moods give orbs their personality and a little emoji badge. */
enum class Mood(val key: String, val emoji: String, val label: String) {
    Joyful("joyful", "😊", "Joyful"),
    Calm("calm", "😌", "Calm"),
    Curious("curious", "🤔", "Curious"),
    Inspired("inspired", "✨", "Inspired"),
    Fired("fired", "🔥", "Fired up"),
    Blue("blue", "🌧️", "Blue"),
    ;

    companion object {
        fun fromKey(key: String?): Mood? = entries.firstOrNull { it.key == key }
    }
}

/** What a thought is about. Categories colour the orb and can filter the web. */
enum class Category(val key: String, val label: String, val argb: Long) {
    Idea("idea", "Ideas", 0xFFFDD663),
    Personal("personal", "Personal", 0xFFF2B8B5),
    Work("work", "Work", 0xFFA8C7FA),
    Dream("dream", "Dreams", 0xFFD7AEFB),
    Todo("todo", "To-do", 0xFF6DD58C),
    Memory("memory", "Memories", 0xFF7FCFFF),
    ;

    companion object {
        fun fromKey(key: String?): Category? = entries.firstOrNull { it.key == key }
    }
}
