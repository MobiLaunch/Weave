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

/**
 * Feelings are rarely just one thing. A blend is a main mood, optionally tinted by a second one;
 * [mix] (0..0.5) is how much of the second shows through.
 */
data class MoodBlend(val primary: Mood, val secondary: Mood? = null, val mix: Float = 0f) {
    val emoji: String get() = primary.emoji + (secondary?.emoji ?: "")

    val label: String get() = secondary?.let { mixName(primary, it) } ?: primary.label

    companion object {
        /** Names for every pairing of two moods. */
        fun mixName(a: Mood, b: Mood): String {
            val pair = setOf(a, b)
            fun has(x: Mood, y: Mood) = pair == setOf(x, y)
            return when {
                has(Mood.Joyful, Mood.Blue) -> "Bittersweet"
                has(Mood.Fired, Mood.Blue) -> "Hurt"
                has(Mood.Joyful, Mood.Inspired) -> "Elated"
                has(Mood.Calm, Mood.Blue) -> "Wistful"
                has(Mood.Joyful, Mood.Calm) -> "Content"
                has(Mood.Fired, Mood.Inspired) -> "Driven"
                has(Mood.Curious, Mood.Inspired) -> "Fascinated"
                has(Mood.Curious, Mood.Blue) -> "Uneasy"
                has(Mood.Fired, Mood.Curious) -> "Restless"
                has(Mood.Calm, Mood.Curious) -> "Contemplative"
                has(Mood.Calm, Mood.Inspired) -> "Dreamy"
                has(Mood.Joyful, Mood.Fired) -> "Exhilarated"
                has(Mood.Joyful, Mood.Curious) -> "Playful"
                has(Mood.Calm, Mood.Fired) -> "Simmering"
                has(Mood.Inspired, Mood.Blue) -> "Yearning"
                else -> "${a.label} & ${b.label.lowercase()}"
            }
        }
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
