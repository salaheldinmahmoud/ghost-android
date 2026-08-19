package com.salaheldin.ghost

enum class ReplyRequirement {
    REPLY_REQUIRED,
    NO_REPLY_REQUIRED,
    POSSIBLY_REQUIRES_REPLY
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW
}

data class ClassificationResult(
    val requirement: ReplyRequirement,
    val priority: Priority
)

object MessageClassifier {

    private val questionMarkers = listOf("?", "؟")

    private val urgentKeywords = listOf(
        // English
        "tomorrow", "asap", "urgent", "important", "deadline", "meeting",
        "please", "can you", "could you", "when", "where", "need you",
        "call me", "reply", "answer", "now", "yesterday", "help",
        // Arabic script
        "امتى", "لو سمحت", "مهم", "بكرة", "ضروري", "اتصل",
        // Franco-Arabic — urgent / attention
        "3agel", "mohem", "muhim", "darory", "daroury", "mosta3gel", "el7a2", "el7a2ny",
        "saree3", "saree3an", "delwa2ty", "delwa2ti", "7alan", "halan", "halann", "fel 7een",
        "matet2a5arsh", "matestannash", "mostanyak", "la7e2ny",
        "ta3ala delwa2ty", "radd 3alaya", "radd 3alaya 3agel",
        "shoof da delwa2ty", "kallemny", "kalemny", "kallimni", "kallemny delwa2ty",
        // Franco-Arabic — getting attention
        "ya shabab", "ya gama3a", "ya boss", "ya bro", "ya man",
        "sme3ny", "esma3", "bas esma3ny", "khalik ma3aya",
        "matro7sh", "mat2afalsh", "mat2ta3sh", "estana", "estana shwaya",
        "moment", "sekka",
        // Franco-Arabic — warning / problems
        "fe moshkela", "fe haga 7asalat", "7asal haga", "el donia et2alabet",
        "keda 5atar", "5od balak", "khaly balak", "mat3melsh keda",
        "e7na fe moshkela", "el mawdo3 5ateer", "el mawdo3 mohem",
        "fe haga 8alat", "el system wa2ef", "el net 2ate3",
        // Franco-Arabic — asking for help
        "sa3edny", "ana me7tagak", "me7tagak delwa2ty", "momken tegy",
        "momken terkaz ma3aya", "momken tkalemny", "kallemny darory",
        "please radd", "radd please", "matseebnish", "ana me7tag mosa3da",
        // Franco-Arabic — time pressure
        "3andena wa2t 2aleel", "mafeesh wa2t", "el wa2t by5les",
        "la7e2", "mesh la7e2", "la7e2ena", "emta", "3ala tool",
        "men delwa2ty", "2abl ma",
        // ("ba3den" / "later" intentionally excluded — signals postponing, not urgency)
        // Franco-Arabic — strong urgent phrases
        "ta3ala saree3", "esma3ny darory", "kallemny lamma teshoof",
        "shoof el msg de delwa2ty"
    )

    private val urgentPattern: Regex by lazy {
        val alternation = urgentKeywords
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        // \b works for latin/franco; the (?<![\p{L}]) guard covers Arabic script.
        Regex("(?<![\\p{L}\\p{N}])(?:$alternation)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    private val lowSignalMarkers = listOf(
        "😂", "😊", "👍", "❤", "💟", "🙄", "sticker", "photo", "reel", "views", "reacted"
    )

    fun classify(text: String): ClassificationResult {
        val trimmed = text.trim()

        if (trimmed.isBlank()) {
            return ClassificationResult(ReplyRequirement.NO_REPLY_REQUIRED, Priority.LOW)
        }

        val lower = trimmed.lowercase()
        val hasQuestion = questionMarkers.any { trimmed.contains(it) }
        val hasUrgentKeyword = urgentPattern.containsMatchIn(lower)
        val isLowSignal = trimmed.length <= 3 || lowSignalMarkers.any { lower.contains(it) }

        return when {
            // Low-signal check runs FIRST: "👍" plus a stray keyword is still noise.
            isLowSignal && !hasQuestion ->
                ClassificationResult(ReplyRequirement.NO_REPLY_REQUIRED, Priority.LOW)
            hasQuestion && hasUrgentKeyword ->
                ClassificationResult(ReplyRequirement.REPLY_REQUIRED, Priority.HIGH)
            hasUrgentKeyword ->
                ClassificationResult(ReplyRequirement.REPLY_REQUIRED, Priority.HIGH)
            hasQuestion ->
                ClassificationResult(ReplyRequirement.REPLY_REQUIRED, Priority.MEDIUM)
            else ->
                ClassificationResult(ReplyRequirement.POSSIBLY_REQUIRES_REPLY, Priority.MEDIUM)
        }
    }
}