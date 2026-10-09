
package com.example.wheredidiputit

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartVoiceParserTest {

    @Test
    fun parsesItemNameAndLocation() {
        val result = SmartVoiceParser.parse(
            "I put my passport in the bedroom drawer"
        )

        assertEquals("Passport", result.itemName)
        assertEquals("bedroom drawer", result.location)
    }

    @Test
    fun keepsItemNameWhenNoLocationIsProvided() {
        val result = SmartVoiceParser.parse("my house keys")

        assertEquals("My house keys", result.itemName)
        assertEquals("", result.location)
    }

    @Test
    fun handlesBlankInput() {
        val result = SmartVoiceParser.parse("")

        assertEquals("", result.itemName)
        assertEquals("", result.location)
    }
}
