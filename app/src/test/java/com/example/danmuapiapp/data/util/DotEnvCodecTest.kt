package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DotEnvCodecTest {
    @Test
    fun formatValueDoesNotDoubleEscapeRegexBackslashesAcrossRepeatedSaves() {
        val original = "/\\d+[^\\w\\d\\s]+/,/^.$/,/.{20,}/,/^\\d{1,2}[/.]\\d{1,2}$/"

        val firstLine = "BLOCKED_WORDS=${DotEnvCodec.formatValue(original)}"
        val firstParsed = DotEnvCodec.parse(firstLine).getValue("BLOCKED_WORDS")
        val secondLine = "BLOCKED_WORDS=${DotEnvCodec.formatValue(firstParsed)}"
        val secondParsed = DotEnvCodec.parse(secondLine).getValue("BLOCKED_WORDS")

        assertEquals(original, firstParsed)
        assertEquals(firstLine, secondLine)
        assertEquals(original, secondParsed)
    }

    @Test
    fun parseQuotedValueUnescapesOnlyDotenvQuotedCharacters() {
        val content = """
            BLOCKED_WORDS="/\d+/,/quote=\"ok\"/,/path\\segment/"
        """.trimIndent()

        assertEquals(
            "/\\d+/,/quote=\"ok\"/,/path\\segment/",
            DotEnvCodec.parse(content).getValue("BLOCKED_WORDS")
        )
    }

    @Test
    fun parseLegacyQuotedValueWithEscapedRegexBackslashesNormalizesOneSaveRound() {
        val legacyContent = "BLOCKED_WORDS=\"/\\\\d+[^\\\\w\\\\d\\\\s]+/,/[@#&$%^*+\\\\|/\\\\-_=<>]/\""
        val parsed = DotEnvCodec.parse(legacyContent).getValue("BLOCKED_WORDS")

        assertEquals(
            "/\\d+[^\\w\\d\\s]+/,/[@#&$%^*+\\|/\\-_=<>]/",
            parsed
        )
        assertEquals(
            "\"/\\d+[^\\w\\d\\s]+/,/[@#&$%^*+\\|/\\-_=<>]/\"",
            DotEnvCodec.formatValue(parsed)
        )
    }

    @Test
    fun formatValueQuotesHashButKeepsRegexBackslashesStable() {
        val value = "/[@#&$%^*+\\|/\\-_=<>]/"

        assertEquals(
            "\"/[@#&$%^*+\\|/\\-_=<>]/\"",
            DotEnvCodec.formatValue(value)
        )
    }

    @Test
    fun formatValueRoundTripsBackslashesThatLookLikeDotenvEscapes() {
        val original = "prefix # literal\\n literal\\t literal\\r slash\\\\ quote\\\" done"

        val firstLine = "VALUE=${DotEnvCodec.formatValue(original)}"
        val firstParsed = DotEnvCodec.parse(firstLine).getValue("VALUE")
        val secondLine = "VALUE=${DotEnvCodec.formatValue(firstParsed)}"

        assertEquals(original, firstParsed)
        assertEquals(firstLine, secondLine)
    }

    @Test
    fun formatValueRoundTripsBackslashBeforeRealControlCharacter() {
        val original = "prefix # slash-before-newline\\\nslash-before-tab\\\tdone"

        assertEquals(
            original,
            DotEnvCodec.parse("VALUE=${DotEnvCodec.formatValue(original)}").getValue("VALUE")
        )
    }
}
