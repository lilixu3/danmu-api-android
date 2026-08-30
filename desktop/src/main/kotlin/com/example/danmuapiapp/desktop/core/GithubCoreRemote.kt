package com.example.danmuapiapp.desktop.core

import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import com.example.danmuapiapp.desktop.node.GithubRouteFailureException
import com.example.danmuapiapp.desktop.node.isGithubRouteUnavailable
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Strict, desktop-only GitHub metadata client for the Core workbench.
 * The caller supplies the already-confirmed route; this class never changes it silently.
 */
class GithubCoreRemote(
    private val repository: String,
    private val proxyId: String,
    private val token: String = "",
    private val client: HttpClient = defaultClient(),
) {
    private val repo: String = validateRepository(repository)

    fun repository(): CoreRepositoryMetadata {
        val root = getObject("repos/$repo")
        return CoreRepositoryMetadata(
            repository = repo,
            defaultBranch = requiredString(root, "default_branch"),
            description = optionalString(root, "description"),
            isPrivate = optionalBoolean(root, "private") ?: false,
        )
    }

    fun branches(page: Int = 1, perPage: Int = 100): List<CoreBranch> {
        require(page > 0) { "页码必须为正数" }
        require(perPage in 1..100) { "每页数量必须在 1..100" }
        return getArray("repos/$repo/branches?per_page=$perPage&page=$page").map { value ->
            val branch = value.asObject("branch")
            val commit = requiredObject(branch, "commit")
            CoreBranch(
                name = requiredString(branch, "name"),
                commitSha = requiredString(commit, "sha"),
                protected = optionalBoolean(branch, "protected") ?: false,
            )
        }
    }

    fun branchHead(branch: String): CoreRemoteSnapshot {
        val normalizedBranch = branch.trim().takeIf { it.isNotBlank() }
            ?: throw IOException("核心分支不能为空")
        val root = getObject("repos/$repo/commits/${encodePath(normalizedBranch)}")
        return parseCommit(root, normalizedBranch)
    }

    fun releases(page: Int = 1, perPage: Int = 30): List<CoreRelease> {
        require(page > 0) { "页码必须为正数" }
        require(perPage in 1..100) { "每页数量必须在 1..100" }
        return getArray("repos/$repo/releases?per_page=$perPage&page=$page").map { value ->
            val release = value.asObject("release")
            CoreRelease(
                tagName = requiredString(release, "tag_name"),
                name = optionalString(release, "name") ?: requiredString(release, "tag_name"),
                body = optionalString(release, "body").orEmpty(),
                publishedAt = optionalString(release, "published_at"),
                htmlUrl = optionalString(release, "html_url"),
                zipballUrl = optionalString(release, "zipball_url"),
                prerelease = optionalBoolean(release, "prerelease") ?: false,
                draft = optionalBoolean(release, "draft") ?: false,
            )
        }
    }

    fun commits(branch: String, page: Int = 1, perPage: Int = 30): CoreRevisionPage {
        require(page > 0) { "页码必须为正数" }
        require(perPage in 1..100) { "每页数量必须在 1..100" }
        val values = getArray(
            "repos/$repo/commits?sha=${encodeQuery(branch)}&per_page=$perPage&page=$page",
        )
        return CoreRevisionPage(
            revisions = values.map { value -> parseRevision(value.asObject("commit")) },
            page = page,
            hasNextPage = values.size == perPage,
        )
    }

    fun commitDetails(sha: String): CoreRevisionDetails {
        val normalizedSha = sha.trim().takeIf { it.isNotBlank() }
            ?: throw IOException("提交 SHA 不能为空")
        val root = getObject("repos/$repo/commits/${encodePath(normalizedSha)}")
        val revision = parseRevision(root)
        val files = requiredArray(root, "files").map { value -> parseFileChange(value.asObject("file")) }
        val stats = root["stats"]?.asObject("stats")
        return CoreRevisionDetails(
            revision = revision,
            files = files,
            additions = optionalInt(stats, "additions") ?: files.sumOf { it.additions },
            deletions = optionalInt(stats, "deletions") ?: files.sumOf { it.deletions },
            changedFiles = optionalInt(stats, "total") ?: files.size,
        )
    }

    fun compare(base: String, head: String): CoreUpdateComparison {
        val baseRef = base.trim().takeIf { it.isNotBlank() } ?: throw IOException("比较基线不能为空")
        val headRef = head.trim().takeIf { it.isNotBlank() } ?: throw IOException("比较目标不能为空")
        val root = getObject("repos/$repo/compare/${encodePath(baseRef)}...${encodePath(headRef)}")
        val commits = requiredArray(root, "commits").map { parseRevision(it.asObject("commit")) }
        val files = requiredArray(root, "files").map { parseFileChange(it.asObject("file")) }
        val baseCommit = requiredObject(root, "base_commit")
        val headCommit = requiredObject(root, "head_commit")
        val stats = requiredArray(root, "files")
        return CoreUpdateComparison(
            localSha = requiredString(baseCommit, "sha"),
            remoteSha = requiredString(headCommit, "sha"),
            status = requiredString(root, "status"),
            aheadBy = requiredInt(root, "ahead_by"),
            behindBy = requiredInt(root, "behind_by"),
            commits = commits,
            files = files,
            additions = stats.sumOf { optionalInt(it.asObject("file"), "additions") ?: 0 },
            deletions = stats.sumOf { optionalInt(it.asObject("file"), "deletions") ?: 0 },
            changedFiles = files.size,
        )
    }

    fun pullRequests(
        state: String = "open",
        page: Int = 1,
        perPage: Int = 30,
    ): List<CorePullRequest> {
        require(state in setOf("open", "closed", "all")) { "PR 状态无效：$state" }
        require(page > 0) { "页码必须为正数" }
        require(perPage in 1..100) { "每页数量必须在 1..100" }
        return getArray("repos/$repo/pulls?state=$state&per_page=$perPage&page=$page").map { value ->
            val pull = value.asObject("pull request")
            val base = requiredObject(pull, "base")
            val head = requiredObject(pull, "head")
            CorePullRequest(
                number = requiredInt(pull, "number"),
                title = requiredString(pull, "title"),
                body = optionalString(pull, "body").orEmpty(),
                state = requiredString(pull, "state"),
                author = pull["user"]?.asObject("pull.user")?.let { optionalString(it, "login") },
                baseBranch = requiredString(base, "ref"),
                headBranch = requiredString(head, "ref"),
                merged = false,
                additions = optionalInt(pull, "additions"),
                deletions = optionalInt(pull, "deletions"),
                changedFiles = optionalInt(pull, "changed_files"),
                htmlUrl = optionalString(pull, "html_url"),
            )
        }
    }

    fun pullRequestFiles(number: Int, page: Int = 1, perPage: Int = 100): List<CoreRevisionFileChange> {
        require(number > 0) { "PR 编号无效" }
        require(page > 0) { "页码必须为正数" }
        require(perPage in 1..100) { "每页数量必须在 1..100" }
        return getArray("repos/$repo/pulls/$number/files?per_page=$perPage&page=$page")
            .map { parseFileChange(it.asObject("pull request file")) }
    }

    private fun getObject(path: String): JsonObject {
        val body = request(path)
        return JsonParser.parse(body).asObject("GitHub response")
    }

    private fun getArray(path: String): List<JsonValue> {
        val body = request(path)
        return JsonParser.parse(body).asArray("GitHub response")
    }

    private fun request(path: String): String {
        val original = "https://api.github.com/$path"
        val urls = GithubProxyCatalog.downloadCandidates(proxyId, original)
        var lastError: Throwable? = null
        urls.forEach { url ->
            try {
                val builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "DanmuApiDesktop")
                if (token.isNotBlank() && URI.create(url).host.equals("api.github.com", ignoreCase = true)) {
                    builder.header("Authorization", if (token.startsWith("Bearer ") || token.startsWith("token ")) token else "Bearer $token")
                }
                val response = client.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
                    .orTimeout(25, TimeUnit.SECONDS)
                    .join()
                if (response.statusCode() !in 200..299) throw IOException("HTTP ${response.statusCode()}：$url")
                if (response.body().isBlank()) throw IOException("GitHub 返回空响应：$url")
                return response.body()
            } catch (error: Throwable) {
                lastError = error
            }
        }
        val failure = lastError ?: IOException("未知网络错误")
        if (isGithubRouteUnavailable(failure)) {
            throw GithubRouteFailureException(
                "GitHub 线路不可达（已选线路：${GithubProxyCatalog.optionById(proxyId).label}）：${failure.message}",
                failure,
            )
        }
        throw IOException(
            "GitHub 请求失败（已选线路：${GithubProxyCatalog.optionById(proxyId).label}）：${failure.message}",
            failure,
        )
    }

    private fun parseCommit(root: JsonObject, branch: String): CoreRemoteSnapshot {
        val commit = requiredObject(root, "commit")
        val author = commit["author"]?.asObject("commit.author")
        val message = requiredString(commit, "message")
        return CoreRemoteSnapshot(
            repository = repo,
            branch = branch,
            commitSha = requiredString(root, "sha"),
            shortSha = requiredString(root, "sha").take(7),
            version = null,
            title = message.lineSequence().firstOrNull().orEmpty(),
            message = message,
            author = author?.let { optionalString(it, "name") },
            committedAt = author?.let { optionalString(it, "date") },
        )
    }

    private fun parseRevision(root: JsonObject): CoreRevision {
        val commit = requiredObject(root, "commit")
        val author = commit["author"]?.asObject("commit.author")
        val message = requiredString(commit, "message")
        return CoreRevision(
            commitSha = requiredString(root, "sha"),
            title = message.lineSequence().firstOrNull().orEmpty(),
            message = message,
            author = author?.let { optionalString(it, "name") },
            committedAt = author?.let { optionalString(it, "date") },
            version = null,
        )
    }

    private fun parseFileChange(root: JsonObject): CoreRevisionFileChange {
        val patch = optionalString(root, "patch")
        val parsedPatch = patch?.let { CorePatchParser.parse(it) }
        val status = optionalString(root, "status") ?: "modified"
        return CoreRevisionFileChange(
            path = requiredString(root, "filename"),
            previousPath = optionalString(root, "previous_filename"),
            status = status,
            additions = optionalInt(root, "additions") ?: 0,
            deletions = optionalInt(root, "deletions") ?: 0,
            changes = optionalInt(root, "changes") ?: 0,
            lines = parsedPatch ?: emptyList(),
            patchUnavailableReason = when {
                patch != null && parsedPatch != null -> null
                patch == null -> "GitHub 未提供 patch（可能是二进制文件或变更过大）"
                else -> "无法解析 GitHub unified diff"
            },
        )
    }

    private companion object {
        fun requiredObject(obj: JsonObject, key: String): JsonObject = obj[key]?.asObject(key)
            ?: throw IOException("GitHub 字段缺失或为空：$key")

        fun requiredArray(obj: JsonObject, key: String): List<JsonValue> = obj[key]?.asArray(key)
            ?: throw IOException("GitHub 字段缺失或为空：$key")

        private val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        fun validateRepository(value: String): String {
            val normalized = value.trim().removeSuffix(".git").trim('/')
            require(REPOSITORY_PATTERN.matches(normalized)) { "GitHub 仓库格式必须为 owner/repo：$value" }
            return normalized
        }

        fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
        fun encodePath(value: String): String = value.split('/').joinToString("/") { encodeQuery(it) }

        fun requiredString(obj: JsonObject, key: String): String =
            optionalString(obj, key)?.takeIf { it.isNotBlank() } ?: throw IOException("GitHub 字段缺失或为空：$key")

        fun optionalString(obj: JsonObject, key: String): String? = when (val value = obj[key]) {
            null, JsonNull -> null
            is JsonString -> value.value
            else -> throw IOException("GitHub 字段类型错误：$key，应为字符串或 null")
        }

        fun optionalBoolean(obj: JsonObject, key: String): Boolean? = when (val value = obj[key]) {
            null, JsonNull -> null
            is JsonBoolean -> value.value
            else -> throw IOException("GitHub 字段类型错误：$key，应为布尔值或 null")
        }

        fun requiredInt(obj: JsonObject, key: String): Int = optionalInt(obj, key)
            ?: throw IOException("GitHub 字段缺失或为空：$key")

        fun optionalInt(obj: JsonObject?, key: String): Int? {
            if (obj == null) return null
            return when (val value = obj[key]) {
                null, JsonNull -> null
                is JsonNumber -> value.raw.toIntOrNull()
                    ?: throw IOException("GitHub 字段类型错误：$key，应为整数或 null")
                else -> throw IOException("GitHub 字段类型错误：$key，应为整数或 null")
            }
        }
    }
}

private sealed interface JsonValue {
    fun asObject(label: String): JsonObject = this as? JsonObject
        ?: throw IOException("$label 必须是对象")

    fun asArray(label: String): List<JsonValue> = (this as? JsonArray)?.values
        ?: throw IOException("$label 必须是数组")
}

private data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]
}

private data class JsonArray(val values: List<JsonValue>) : JsonValue
private data class JsonString(val value: String) : JsonValue
private data class JsonNumber(val raw: String) : JsonValue
private data class JsonBoolean(val value: Boolean) : JsonValue
private data object JsonNull : JsonValue

/** Small strict JSON parser kept local to the desktop module to avoid a new runtime dependency. */
private object JsonParser {
    fun parse(text: String): JsonValue = Parser(text).parse()

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): JsonValue {
            skipWhitespace()
            val value = value()
            skipWhitespace()
            if (index != text.length) throw IOException("JSON 尾部存在未解析内容")
            return value
        }

        private fun value(): JsonValue {
            skipWhitespace()
            if (index >= text.length) throw IOException("JSON 意外结束")
            return when (text[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> JsonString(stringValue())
                't' -> literal("true", JsonBoolean(true))
                'f' -> literal("false", JsonBoolean(false))
                'n' -> literal("null", JsonNull)
                '-', in '0'..'9' -> JsonNumber(numberValue())
                else -> throw IOException("JSON 值无效：位置 $index")
            }
        }

        private fun objectValue(): JsonObject {
            expect('{')
            val fields = linkedMapOf<String, JsonValue>()
            skipWhitespace()
            if (consume('}')) return JsonObject(fields)
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw IOException("JSON 对象键必须是字符串")
                val key = stringValue()
                skipWhitespace()
                expect(':')
                fields[key] = value()
                skipWhitespace()
                if (consume('}')) return JsonObject(fields)
                expect(',')
            }
        }

        private fun arrayValue(): JsonArray {
            expect('[')
            val values = mutableListOf<JsonValue>()
            skipWhitespace()
            if (consume(']')) return JsonArray(values)
            while (true) {
                values += value()
                skipWhitespace()
                if (consume(']')) return JsonArray(values)
                expect(',')
            }
        }

        private fun stringValue(): String {
            expect('"')
            val out = StringBuilder()
            while (index < text.length) {
                when (val char = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= text.length) throw IOException("JSON 字符串转义不完整")
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw IOException("JSON unicode 转义不完整")
                                val hex = text.substring(index, index + 4)
                                out.append(hex.toIntOrNull(16)?.toChar() ?: throw IOException("JSON unicode 转义无效"))
                                index += 4
                            }
                            else -> throw IOException("JSON 字符串转义无效：$escaped")
                        }
                    }
                    else -> out.append(char)
                }
            }
            throw IOException("JSON 字符串未闭合")
        }

        private fun numberValue(): String {
            val start = index
            if (consume('-')) Unit
            if (consume('0')) Unit else {
                requireDigits()
            }
            if (consume('.')) {
                requireDigits()
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                requireDigits()
            }
            return text.substring(start, index)
        }

        private fun requireDigits() {
            val start = index
            while (peek()?.isDigit() == true) index++
            if (index == start) throw IOException("JSON 数字缺少数字")
        }

        private fun <T : JsonValue> literal(expected: String, value: T): T {
            if (!text.regionMatches(index, expected, 0, expected.length)) throw IOException("JSON 字面量无效")
            index += expected.length
            return value
        }

        private fun expect(expected: Char) {
            if (index >= text.length || text[index] != expected) throw IOException("JSON 缺少 '$expected'")
            index++
        }

        private fun consume(expected: Char): Boolean {
            if (peek() == expected) {
                index++
                return true
            }
            return false
        }

        private fun peek(): Char? = text.getOrNull(index)
        private fun skipWhitespace() { while (peek()?.isWhitespace() == true) index++ }
    }
}

/** Unified diff parser with explicit old/new line numbers and no silent fallback. */
object CorePatchParser {
    fun parse(patch: String): List<CoreDiffLine> {
        if (patch.isBlank()) return emptyList()
        val result = mutableListOf<CoreDiffLine>()
        var oldLine = 0
        var newLine = 0
        var sawHunk = false
        patch.lineSequence().forEach { line ->
            when {
                line.startsWith("@@") -> {
                    val match = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@").find(line)
                        ?: throw IOException("unified diff hunk 无效：$line")
                    oldLine = match.groupValues[1].toInt()
                    newLine = match.groupValues[3].toInt()
                    sawHunk = true
                    result += CoreDiffLine(CoreDiffLineType.Header, line, null, null)
                }
                !sawHunk -> result += CoreDiffLine(CoreDiffLineType.Header, line, null, null)
                line.startsWith("+") -> {
                    result += CoreDiffLine(CoreDiffLineType.Added, line, null, newLine++)
                }
                line.startsWith("-") -> {
                    result += CoreDiffLine(CoreDiffLineType.Removed, line, oldLine++, null)
                }
                line.startsWith(" ") -> {
                    result += CoreDiffLine(CoreDiffLineType.Context, line, oldLine++, newLine++)
                }
                line == "\\ No newline at end of file" -> result += CoreDiffLine(CoreDiffLineType.Header, line, null, null)
                else -> throw IOException("unified diff 行无效：$line")
            }
        }
        if (!sawHunk) throw IOException("unified diff 缺少 hunk header")
        return result
    }
}
