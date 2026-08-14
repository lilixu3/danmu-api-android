package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreDiffLine
import com.example.danmuapiapp.data.service.CorePatchParser

internal object CoreRevisionParser {
    fun parsePatch(patch: String): List<CoreDiffLine> = CorePatchParser.parse(patch)
}
