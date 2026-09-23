package com.mobilaunch.weave.data

/**
 * Reads the mood of what's being typed with a small on-device word list – no network, no model.
 * Stems match word prefixes ("frustrat" catches frustrated / frustrating), later words count a
 * little more, and a preceding "not"/"never" flips happy words to sad.
 */
object MoodSense {

    private val lexicon: Map<Mood, List<String>> = mapOf(
        Mood.Fired to listOf(
            "angry", "anger", "mad", "furious", "fury", "hate", "hating", "rage", "annoy", "frustrat",
            "pissed", "irritat", "livid", "ugh", "argh", "damn", "stupid", "worst", "unfair", "fed up",
            "sick of", "yell", "scream", "fight", "burn", "fire", "explod", "outrage", "resent", "grr",
        ),
        Mood.Blue to listOf(
            "sad", "cry", "cried", "tears", "lonely", "alone", "miss", "lost", "hurt", "depress", "down",
            "tired", "exhaust", "sorry", "grief", "griev", "heartbr", "broken", "gloom", "rain", "empty",
            "anxious", "anxiety", "worri", "worry", "afraid", "scared", "regret", "hopeless", "numb",
            "blue", "storm", "sigh", "awful", "hard day",
        ),
        Mood.Joyful to listOf(
            "happy", "happi", "joy", "love", "great", "awesome", "amazing", "excit", "yay", "fun",
            "laugh", "smil", "glad", "grateful", "thank", "celebrat", "party", "win", "won", "best",
            "wonderful", "delight", "sunshine", "sunny", "beautiful", "lovely", "cheer", "hooray", "woo",
            "perfect", "fantastic", "blessed", "proud",
        ),
        Mood.Calm to listOf(
            "calm", "peace", "relax", "quiet", "rest", "breath", "slow", "gentle", "still", "serene",
            "cozy", "cosy", "tea", "meditat", "sleep", "ocean", "beach", "easy", "content", "soft",
            "chill", "mellow", "lazy", "sunday", "walk", "breeze", "warm", "nap", "unwind",
        ),
        Mood.Curious to listOf(
            "why", "how come", "what if", "wonder", "curious", "maybe", "question", "explor", "learn",
            "strange", "weird", "mystery", "myster", "research", "figure out", "puzzl", "discover",
            "hmm", "unknown", "investigat", "study", "notice",
        ),
        Mood.Inspired to listOf(
            "idea", "inspir", "creat", "build", "dream", "vision", "imagin", "plan", "design", "invent",
            "spark", "brilliant", "future", "project", "write", "art", "music", "goal", "launch",
            "make", "craft", "possib", "breakthrough", "eureka", "motivat", "ambit",
        ),
    )

    private val emoji: Map<Mood, List<String>> = mapOf(
        Mood.Fired to listOf("😡", "🤬", "😤", "🔥", "💢"),
        Mood.Blue to listOf("😢", "😭", "😞", "💔", "🌧", "😔"),
        Mood.Joyful to listOf("😊", "😄", "😁", "🥳", "❤", "😍", "🎉", ":)", ":D"),
        Mood.Calm to listOf("😌", "🌊", "🍵", "🌙", "🧘"),
        Mood.Curious to listOf("🤔", "🧐", "❓"),
        Mood.Inspired to listOf("✨", "💡", "🚀", "🎨", "🌟"),
    )

    private val negators = setOf("not", "no", "never", "dont", "don't", "isnt", "isn't", "wasnt", "wasn't", "cant", "can't", "hardly")

    fun detect(text: String): Mood? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val scores = HashMap<Mood, Float>()
        fun add(m: Mood, w: Float) {
            scores[m] = (scores[m] ?: 0f) + w
        }

        val words = lower.split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
        for ((i, word) in words.withIndex()) {
            val recency = 1f + 0.5f * i / words.size.coerceAtLeast(1)
            val negated = i > 0 && words[i - 1] in negators
            for ((mood, stems) in lexicon) {
                if (stems.any { !it.contains(' ') && matches(word, it) }) {
                    val target = if (negated && mood == Mood.Joyful) Mood.Blue else mood
                    if (!(negated && mood != Mood.Joyful)) add(target, recency)
                }
            }
        }
        // Multi-word phrases.
        for ((mood, stems) in lexicon) for (stem in stems) if (stem.contains(' ') && lower.contains(stem)) add(mood, 1.2f)
        for ((mood, marks) in emoji) for (mark in marks) if (lower.contains(mark)) add(mood, 1.5f)

        val bangs = lower.count { it == '!' }
        if (bangs > 0) {
            val loud = if ((scores[Mood.Fired] ?: 0f) >= (scores[Mood.Joyful] ?: 0f) && (scores[Mood.Fired] ?: 0f) > 0f) Mood.Fired else Mood.Joyful
            add(loud, 0.35f * bangs.coerceAtMost(4))
        }
        val questions = lower.count { it == '?' }
        if (questions > 0) add(Mood.Curious, 0.6f * questions.coerceAtMost(3))

        val best = scores.maxByOrNull { it.value } ?: return null
        return if (best.value >= 0.9f) best.key else null
    }

    private fun matches(word: String, stem: String): Boolean =
        if (stem.length <= 3) word == stem || word == stem + "s" else word.startsWith(stem)
}
