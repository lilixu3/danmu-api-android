package com.example.danmuapiapp.domain.model

/**
 * Removes logs generated only by the Android app polling core management endpoints itself.
 *
 * The core logs every HTTP request. If Android screens poll /api/logs, /api/reqrecords,
 * or /api/cache/animes, those app-internal requests can dominate the log screen and evict
 * real diagnostics from the core buffer.
 *
 * Keep this filter narrow: it only removes the exact server-access group for known
 * app-internal management endpoints and the harmless one-time local-cache init info that
 * can be triggered by those reads. Normal danmu API requests and /api/logs/clear remain visible.
 */
object CoreLogNoiseFilter {

    fun removeLogEndpointPollingBlocks(blocks: List<String>): List<String> {
        if (blocks.isEmpty()) return blocks
        val result = ArrayList<String>(blocks.size)
        var index = 0
        while (index < blocks.size) {
            val groupSize = internalEndpointPollingGroupSize(blocks, index)
            if (groupSize > 0) {
                index += groupSize
                while (index < blocks.size && isLocalCacheInitInfoBlock(blocks[index])) {
                    index++
                }
                continue
            }

            // Defensive fallback for partially captured groups: remove only the unambiguous
            // internal endpoint lines. Do not remove standalone client-ip lines here because
            // those are useful for real API requests when the surrounding request path is unknown.
            if (isInternalEndpointRequestUrlBlock(blocks[index]) ||
                isInternalEndpointRequestPathBlock(blocks[index]) ||
                isInternalEndpointSummaryBlock(blocks[index])
            ) {
                index++
                continue
            }

            result += blocks[index]
            index++
        }
        return result
    }

    private fun internalEndpointPollingGroupSize(blocks: List<String>, start: Int): Int {
        if (start + MIN_ENDPOINT_GROUP_SIZE > blocks.size) return 0
        if (!isInternalEndpointRequestUrlBlock(blocks[start]) ||
            !isInternalEndpointRequestPathBlock(blocks[start + 1]) ||
            !isServerClientIpBlock(blocks[start + 2])
        ) {
            return 0
        }
        return if (start + MIN_ENDPOINT_GROUP_SIZE < blocks.size &&
            isInternalEndpointSummaryBlock(blocks[start + 3])
        ) {
            MIN_ENDPOINT_GROUP_SIZE + 1
        } else {
            MIN_ENDPOINT_GROUP_SIZE
        }
    }

    private fun isInternalEndpointRequestUrlBlock(block: String): Boolean {
        val message = block.messagePayload()
        return message.contains("[Server] request url:", ignoreCase = true) &&
            message.referencesAppInternalEndpoint()
    }

    private fun isInternalEndpointRequestPathBlock(block: String): Boolean {
        val message = block.messagePayload()
        return message.contains("[Server] request path:", ignoreCase = true) &&
            message.referencesAppInternalEndpoint()
    }

    private fun isServerClientIpBlock(block: String): Boolean {
        val message = block.messagePayload()
        return message.contains("[Server] client ip:", ignoreCase = true)
    }

    private fun isInternalEndpointSummaryBlock(block: String): Boolean {
        val message = block.messagePayload().trim()
        if (!message.contains("[Server]", ignoreCase = true)) return false
        if (!message.referencesAppInternalEndpoint()) return false
        return !message.contains("request url:", ignoreCase = true) &&
            !message.contains("request path:", ignoreCase = true)
    }

    private fun isLocalCacheInitInfoBlock(block: String): Boolean {
        val message = block.messagePayload()
        return message.contains("[Cache] getLocalCaches start.", ignoreCase = true) ||
            message.contains("[Cache] Restored lastSelectMap from local cache", ignoreCase = true) ||
            message.contains("[Cache] getLocalCaches completed successfully.", ignoreCase = true)
    }

    private fun String.messagePayload(): String {
        return trim()
    }

    private fun String.referencesAppInternalEndpoint(): Boolean {
        return APP_INTERNAL_ENDPOINT_REGEX.containsMatchIn(this)
    }

    private const val MIN_ENDPOINT_GROUP_SIZE = 3
    private val APP_INTERNAL_ENDPOINT_REGEX = Regex(
        """(^|/)api/(?:logs|reqrecords|cache/animes)(?=([\"'\s?#]|$))""",
        RegexOption.IGNORE_CASE
    )
}
