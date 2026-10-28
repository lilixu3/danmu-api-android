package com.example.danmuapiapp.data.repository

import java.net.URLEncoder

internal object CoreRemoteUrlPolicy {
    fun downloadCandidates(zipUrl: String, proxyCandidates: List<String>): List<String> {
        val preferDirectFirst = zipUrl.contains("://api.github.com/")
        return if (preferDirectFirst) {
            listOf(zipUrl).plus(proxyCandidates).distinct()
        } else {
            proxyCandidates
        }
    }

    fun commitHistoryEndpoints(
        repo: String,
        branchCandidates: List<String>,
        apiUrlCandidates: (String) -> List<String>
    ): List<String> {
        return buildList {
            branchCandidates.forEach { branch ->
                addAll(apiUrlCandidates("repos/$repo/commits?sha=${encodeUrlPart(branch)}&per_page=20"))
            }
            addAll(apiUrlCandidates("repos/$repo/commits?per_page=20"))
        }.distinct()
    }

    fun encodeUrlPart(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
