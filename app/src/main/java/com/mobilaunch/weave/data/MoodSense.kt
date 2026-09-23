package com.mobilaunch.weave.data

/**
 * Reads the feelings in what's being typed using an on-device word list – no network, no model.
 *
 * - Stems match word prefixes ("frustrat" catches frustrated / frustrating).
 * - Intensifiers ("so", "really", "super") boost the next feeling word; later words count a little more.
 * - A preceding "not"/"never" flips happy words to sad and cancels the rest.
 * - The two strongest feelings are returned as a [MoodBlend], so mixed feelings make mixed worlds.
 */
object MoodSense {

    private val lexicon: Map<Mood, List<String>> = mapOf(
        Mood.Fired to listOf(
            "angry", "anger", "mad", "furious", "fury", "hate", "hating", "rage", "annoy", "frustrat",
            "pissed", "irritat", "livid", "ugh", "argh", "damn", "stupid", "worst", "unfair", "fed up",
            "sick of", "yell", "scream", "fight", "burn", "fire", "explod", "outrage", "resent", "grr",
            "bitter", "betray", "disgust", "offend", "insult", "jealous", "envy", "envious", "hostile",
            "cranky", "grumpy", "bothered", "agitat", "infuriat", "enrag", "seething", "fuming", "boiling",
            "heated", "snapped", "rude", "ridiculous", "unacceptable", "done with", "can't stand",
            "stress", "overwhelm", "pressure", "deadline", "chaos",
        ),
        Mood.Blue to listOf(
            "sad", "cry", "cried", "tears", "lonely", "alone", "=miss", "missing", "missed", "=lost", "hurt", "depress", "feeling down", "let down",
            "tired", "exhaust", "sorry", "grief", "griev", "heartbr", "broken", "gloom", "rain", "empty",
            "anxious", "anxiety", "worri", "worry", "afraid", "scared", "regret", "hopeless", "numb",
            "blue", "storm", "sigh", "awful", "hard day", "unhappy", "miserable", "upset", "disappoint",
            "gutted", "ache", "aching", "mourn", "sorrow", "melanchol", "homesick", "nostalg", "abandon",
            "rejected", "reject", "ashamed", "shame", "guilt", "insecure", "fragile", "drained", "burnt out",
            "burned out", "defeat", "failure", "failed", "fail", "cloudy", "dark", "heavy", "hollow",
            "goodbye", "farewell", "funeral", "=sick", "ill", "=pain", "painful", "sadness", "saddest", "crying", "wish i", "if only", "nervous", "panic",
            "dread", "fear", "terrified", "helpless", "worthless",
        ),
        Mood.Joyful to listOf(
            "happy", "happi", "joy", "love", "great", "awesome", "amazing", "excit", "yay", "fun",
            "laugh", "smil", "glad", "joyful", "joyous", "funny", "grateful", "thank", "celebrat", "party", "win", "won", "best",
            "wonderful", "delight", "sunshine", "sunny", "beautiful", "lovely", "cheer", "hooray", "woo",
            "perfect", "fantastic", "blessed", "proud", "thrill", "ecstat", "elat", "overjoy", "bliss",
            "giggl", "adore", "sweet", "cute", "friends", "family", "hug", "kiss", "birthday", "holiday",
            "vacation", "weekend", "success", "nailed", "finally", "good news", "promot", "engaged",
            "wedding", "baby", "puppy", "kitten", "best day", "good day", "enjoy",
            "pleased", "stoked", "psyched", "jolly", "merry", "playful", "silly",
        ),
        Mood.Calm to listOf(
            "calm", "peace", "relax", "quiet", "=rest", "rested", "resting", "breath", "slow", "gentle", "still", "serene",
            "cozy", "cosy", "tea", "meditat", "sleep", "ocean", "beach", "easy", "content", "soft",
            "chill", "mellow", "lazy", "sunday", "walk", "breeze", "warm", "nap", "unwind", "tranquil",
            "soothing", "sooth", "balanced", "grounded", "centered", "centred", "mindful", "present",
            "yoga", "bath", "candle", "blanket", "rain sounds", "lake", "garden", "sunset",
            "sunrise", "morning", "coffee", "book", "reading", "stretch", "safe", "comfort", "okay",
            "fine", "settled", "at ease", "letting go", "accept", "patient", "restful", "hush",
        ),
        Mood.Curious to listOf(
            "why", "how come", "what if", "wonder", "curious", "maybe", "question", "explor", "learn",
            "strange", "weird", "mystery", "myster", "research", "figure out", "puzzl", "discover",
            "hmm", "unknown", "investigat", "study", "notice", "interest", "intrigu", "fascinat",
            "odd", "unusual", "confus", "unsure", "uncertain", "not sure", "perhaps", "might", "could it",
            "experiment", "test", "try", "trying", "google", "read about", "rabbit hole", "theory",
            "hypothes", "clue", "secret", "hidden", "explain", "understand", "compare", "decide", "choice",
        ),
        Mood.Inspired to listOf(
            "idea", "inspir", "creat", "build", "dream", "vision", "imagin", "plan", "design", "invent",
            "spark", "brilliant", "future", "project", "write", "art", "music", "goal", "launch",
            "make", "craft", "possib", "breakthrough", "eureka", "motivat", "ambit", "startup", "business",
            "app", "novel", "story", "poem", "song", "paint", "draw", "sketch", "prototype", "ship",
            "manifest", "aspir", "purpose", "passion", "determin", "focus", "grow",
            "improve", "new chapter", "begin", "start", "fresh", "energ", "unstoppable",
            "can do", "let's go", "lets go", "level up", "potential", "innovat", "opportunit",
        ),
    )

    private val emoji: Map<Mood, List<String>> = mapOf(
        Mood.Fired to listOf("😡", "🤬", "😤", "🔥", "💢", "😠", "👿"),
        Mood.Blue to listOf("😢", "😭", "😞", "💔", "🌧", "😔", "😟", "😰", "😩", "🥺"),
        Mood.Joyful to listOf("😊", "😄", "😁", "🥳", "❤", "😍", "🎉", ":)", ":D", "😂", "🥰", "💕", "🤩"),
        Mood.Calm to listOf("😌", "🌊", "🍵", "🌙", "🧘", "☕", "🌿", "🕯"),
        Mood.Curious to listOf("🤔", "🧐", "❓", "👀", "🔍"),
        Mood.Inspired to listOf("✨", "💡", "🚀", "🎨", "🌟", "💪", "🎯"),
    )

    private val negators = setOf(
        "not", "no", "never", "dont", "don't", "isnt", "isn't", "wasnt", "wasn't", "cant", "can't",
        "hardly", "barely", "aint", "ain't", "without", "nothing",
    )
    private val intensifiers = setOf(
        "so", "very", "really", "super", "extremely", "totally", "absolutely", "incredibly", "truly",
        "deeply", "utterly", "completely", "insanely", "too", "such",
    )

    fun sense(text: String): MoodBlend? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val scores = HashMap<Mood, Float>()
        fun add(m: Mood, w: Float) {
            scores[m] = (scores[m] ?: 0f) + w
        }

        val words = lower.split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
        for ((i, word) in words.withIndex()) {
            val recency = 1f + 0.5f * i / words.size.coerceAtLeast(1)
            val prev = words.getOrNull(i - 1)
            val prev2 = words.getOrNull(i - 2)
            val negated = prev in negators || (prev in intensifiers && prev2 in negators)
            val boost = if (prev in intensifiers) 1.6f else 1f
            for ((mood, stems) in lexicon) {
                if (stems.any { !it.contains(' ') && matches(word, it) }) {
                    when {
                        !negated -> add(mood, recency * boost)
                        mood == Mood.Joyful -> add(Mood.Blue, recency * boost)
                        else -> Unit
                    }
                }
            }
        }
        for ((mood, stems) in lexicon) for (stem in stems) if (stem.contains(' ') && lower.contains(stem)) add(mood, 1.3f)
        for ((mood, marks) in emoji) for (mark in marks) if (lower.contains(mark)) add(mood, 1.5f)

        val bangs = lower.count { it == '!' }
        if (bangs > 0) {
            val fired = scores[Mood.Fired] ?: 0f
            val loud = if (fired > 0f && fired >= (scores[Mood.Joyful] ?: 0f)) Mood.Fired else Mood.Joyful
            add(loud, 0.35f * bangs.coerceAtMost(4))
        }
        val questions = lower.count { it == '?' }
        if (questions > 0) add(Mood.Curious, 0.6f * questions.coerceAtMost(3))

        val ranked = scores.entries.sortedByDescending { it.value }
        val first = ranked.firstOrNull() ?: return null
        if (first.value < 0.9f) return null
        val second = ranked.getOrNull(1)?.takeIf { it.value >= 0.9f && it.value >= first.value * 0.35f }
        return if (second == null) {
            MoodBlend(first.key)
        } else {
            MoodBlend(first.key, second.key, (second.value / (first.value + second.value)).coerceIn(0.2f, 0.5f))
        }
    }

    /** Short stems and ones marked with "=" must match the whole word; the rest match as prefixes. */
    private fun matches(word: String, stem: String): Boolean = when {
        stem.startsWith("=") -> word == stem.substring(1)
        stem.length <= 3 -> word == stem || word == stem + "s"
        else -> word.startsWith(stem)
    }
}
