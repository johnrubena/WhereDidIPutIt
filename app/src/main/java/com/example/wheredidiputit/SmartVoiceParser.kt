
package com.example.wheredidiputit

data class ParsedVoiceEntry(
    val itemName: String,
    val location: String = "",
    val specificPlace: String = "",
    val container: String = "",
    val notes: String = ""
)

object SmartVoiceParser {

    fun parse(spokenText: String): ParsedVoiceEntry {

        val original = spokenText.trim()
            .replace(Regex("\\s+"), " ")

        if (original.isBlank()) {
            return ParsedVoiceEntry(itemName = "")
        }

        val text = original
            .replace(Regex("[.!?]+$"), "")
            .trim()

        val patterns = listOf(
            Regex(
                """(?i)^(?:i\s+)?(?:put|placed|left|kept|stored)\s+(?:my\s+|the\s+)?(.+?)\s+(?:in|inside|on|under|beside|behind|near)\s+(?:the\s+)?(.+)$"""
            ),
            Regex(
                """(?i)^(.+?)\s+(?:is|are)\s+(?:in|inside|on|under|beside|behind|near)\s+(?:the\s+)?(.+)$"""
            )
        )

        for (pattern in patterns) {
            val match = pattern.matchEntire(text) ?: continue

            val itemName = match.groupValues[1]
                .trim()
                .trimEnd('.', ',', ' ')

            val place = match.groupValues[2]
                .trim()
                .trimEnd('.', ',', ' ')

            if (itemName.isNotBlank() && place.isNotBlank()) {
                return ParsedVoiceEntry(
                    itemName = itemName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase()
                        else it.toString()
                    },
                    location = place
                )
            }
        }

        return ParsedVoiceEntry(
            itemName = text.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase()
                else it.toString()
            }
        )
    }
}


fun testSmartVoiceParser() {
    val result = SmartVoiceParser.parse(
        "I put my passport in the bedroom drawer"
    )

    println("Item name: ${result.itemName}")
    println("Location: ${result.location}")
    println("Specific place: ${result.specificPlace}")
}
