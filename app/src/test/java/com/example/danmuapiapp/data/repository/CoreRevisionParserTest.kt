package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreDiffLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreRevisionParserTest {
    @Test
    fun parsePatch_tracksOldAndNewLineNumbers() {
        val lines = CoreRevisionParser.parsePatch(
            """
            @@ -10,3 +10,4 @@
             unchanged
            -removed
            +added
            +second added
             tail
            """.trimIndent()
        )

        assertEquals(CoreDiffLineType.Header, lines[0].type)
        assertEquals(10, lines[1].oldLineNumber)
        assertEquals(10, lines[1].newLineNumber)
        assertEquals(CoreDiffLineType.Removed, lines[2].type)
        assertEquals(11, lines[2].oldLineNumber)
        assertNull(lines[2].newLineNumber)
        assertEquals(CoreDiffLineType.Added, lines[3].type)
        assertEquals(11, lines[3].newLineNumber)
        assertEquals(12, lines[4].newLineNumber)
        assertEquals(12, lines[5].oldLineNumber)
        assertEquals(13, lines[5].newLineNumber)
    }
}
