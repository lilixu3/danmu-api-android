package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.CoreDiffLine
import com.example.danmuapiapp.domain.model.CoreDiffLineType

internal object CorePatchParser {
    private val hunkHeader = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*$""")

    fun parse(patch: String): List<CoreDiffLine> {
        if (patch.isBlank()) return emptyList()
        var oldLine = 0
        var newLine = 0
        var hasHunk = false

        return patch.lineSequence().map { rawLine ->
            val match = hunkHeader.matchEntire(rawLine)
            if (match != null) {
                oldLine = match.groupValues[1].toIntOrNull() ?: 0
                newLine = match.groupValues[3].toIntOrNull() ?: 0
                hasHunk = true
                return@map CoreDiffLine(CoreDiffLineType.Header, rawLine)
            }

            if (!hasHunk || rawLine.startsWith("\\ No newline at end of file")) {
                return@map CoreDiffLine(CoreDiffLineType.Header, rawLine)
            }

            when {
                rawLine.startsWith("+") -> CoreDiffLine(
                    type = CoreDiffLineType.Added,
                    content = rawLine,
                    newLineNumber = newLine++
                )
                rawLine.startsWith("-") -> CoreDiffLine(
                    type = CoreDiffLineType.Removed,
                    content = rawLine,
                    oldLineNumber = oldLine++
                )
                else -> CoreDiffLine(
                    type = CoreDiffLineType.Context,
                    content = rawLine,
                    oldLineNumber = oldLine++,
                    newLineNumber = newLine++
                )
            }
        }.toList()
    }
}
