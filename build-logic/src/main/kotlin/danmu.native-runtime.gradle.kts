// WS7：由 app/build.gradle.kts 迁出的原生运行时准备、校验与内网穿透内核门禁。
import danmu.danmuAbiFilters
import danmu.danmuIsTermuxHost
import danmu.danmuLocalProperties
import danmu.danmuNativeRuntimeArchiveOverride
import danmu.danmuNativeRuntimeArchiveUrlOverride
import danmu.danmuNativeRuntimeChecksumFile
import danmu.danmuNativeRuntimeSourcesFile
import danmu.danmuPreparedNativeRuntimeDir
import danmu.defaultReleaseAbis
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.GradleException

val configuredAbiFilters = danmuAbiFilters()
val isTermuxHost = danmuIsTermuxHost()
val localProps = danmuLocalProperties()
val nativeRuntimeArchiveOverride = danmuNativeRuntimeArchiveOverride()
val nativeRuntimeArchiveUrlOverride = danmuNativeRuntimeArchiveUrlOverride()
val nativeRuntimeChecksumFile = danmuNativeRuntimeChecksumFile()
val nativeRuntimeSourcesFile = danmuNativeRuntimeSourcesFile()
val preparedNativeRuntimeDir = danmuPreparedNativeRuntimeDir()


fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

/**
 * 16KB 页对齐门禁：Android 15+ 的 16KB 内核设备（仅 64 位 ABI）要求
 * 可执行段按 16KB 对齐加载；armeabi-v7a 不存在 16KB 内核，仅需 ≥4KB。
 * 解析 ELF program header 校验 PT_LOAD 对齐/偏移与 GNU_RELRO 末尾，覆盖上游
 * libnode.so / libc++_shared.so 与本地编译的 libnative-lib.so，
 * 防止任一环节的对齐回归静默进入 APK。
 */
private val ELF_16K_REQUIRED_ABIS = setOf("arm64-v8a", "x86_64")

fun assertElfLoadAlignment(file: File, relativePath: String = file.relativeTo(preparedNativeRuntimeDir).invariantSeparatorsPath) {
    RandomAccessFile(file, "r").use { raf ->
        fun u8(): Int = raf.read().also { if (it < 0) throw GradleException("ELF 文件被截断：${file.name}") }
        fun u16At(offset: Long): Int {
            raf.seek(offset)
            return u8() or (u8() shl 8)
        }
        fun u32At(offset: Long): Long {
            raf.seek(offset)
            // ELF 字段为小端编码，低字节在前，读取顺序必须与 u16At 一致。
            var value = 0L
            repeat(4) { value = value or (u8().toLong() shl (8 * it)) }
            return value
        }
        fun u64At(offset: Long): Long {
            raf.seek(offset)
            var value = 0L
            repeat(8) { value = value or (u8().toLong() shl (8 * it)) }
            return value
        }

        val ident = ByteArray(16)
        raf.readFully(ident)
        val isElf = ident[0] == 0x7F.toByte() && ident[1] == 0x45.toByte() &&
            ident[2] == 0x4C.toByte() && ident[3] == 0x46.toByte()
        if (!isElf) throw GradleException("内嵌运行时出现非 ELF 的 .so：${file.name}")
        if (ident[5].toInt() != 1) throw GradleException("不支持大端 ELF .so：${file.name}")
        val is64 = ident[4].toInt() == 2

        val phoff = if (is64) u64At(0x20) else u32At(0x1C)
        val phentsize = u16At(if (is64) 0x36 else 0x2A).toLong()
        val phnum = u16At(if (is64) 0x38 else 0x2C)
        if (phnum <= 0 || phentsize <= 0L || phoff + phnum * phentsize > raf.length()) {
            throw GradleException(
                "ELF program header 非法或越界：${file.name} [path=${file.absolutePath} size=${raf.length()} " +
                    "phoff=$phoff phentsize=$phentsize phnum=$phnum]"
            )
        }

        val minRequired = if (ELF_16K_REQUIRED_ABIS.any { "/$it/" in "/$relativePath" }) 16384L else 4096L
        var sawLoad = false
        for (i in 0 until phnum) {
            val base = phoff + i * phentsize
            val kind = u32At(base)
            if (kind == 0x6474e552L) {
                val address = if (is64) u64At(base + 16) else u32At(base + 8)
                val size = if (is64) u64At(base + 40) else u32At(base + 20)
                if ((address + size) % minRequired != 0L) {
                    throw GradleException("$relativePath 的 GNU_RELRO 末尾未按 ${minRequired}B 页对齐")
                }
            }
            if (kind != 1L) continue
            sawLoad = true
            val align = if (is64) u64At(base + 48) else u32At(base + 28)
            val offset = if (is64) u64At(base + 8) else u32At(base + 4)
            val address = if (is64) u64At(base + 16) else u32At(base + 8)
            if (align < minRequired || offset % minRequired != address % minRequired) {
                throw GradleException(
                    "$relativePath 的 PT_LOAD 对齐为 ${align}B，低于 ${minRequired}B 页要求" +
                        "（${if (minRequired == 16384L) "16KB 内核设备无法加载" else "文件异常"}）"
                )
            }
        }
        if (!sawLoad) throw GradleException("ELF 缺少 PT_LOAD 段：${file.name}")

    }
}

fun readNativeRuntimeChecksums(): Map<String, String> {
    if (!nativeRuntimeChecksumFile.isFile) {
        throw GradleException("缺少原生运行时校验清单：${nativeRuntimeChecksumFile.absolutePath}")
    }
    val result = linkedMapOf<String, String>()
    nativeRuntimeChecksumFile.readLines(Charsets.UTF_8).forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith('#')) return@forEachIndexed
        val separator = line.indexOf("  ")
        if (separator <= 0) {
            throw GradleException("原生运行时校验清单第 ${index + 1} 行格式错误")
        }
        val expected = line.substring(0, separator).trim().lowercase()
        val relativePath = line.substring(separator + 2).trim()
        if (!expected.matches(Regex("[0-9a-f]{64}")) ||
            relativePath.isBlank() || relativePath.startsWith('/') ||
            ".." in relativePath.split('/') || result.put(relativePath, expected) != null
        ) {
            throw GradleException("原生运行时校验清单第 ${index + 1} 行内容非法")
        }
    }
    return result
}

data class NativeRuntimeReleaseSource(
    val abi: String,
    val assetName: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val githubActionsArtifact: Boolean = false
)

fun readNativeRuntimeReleaseSources(): Pair<String, Map<String, NativeRuntimeReleaseSource>> {
    if (!nativeRuntimeSourcesFile.isFile) {
        throw GradleException("缺少原生运行时来源清单：${nativeRuntimeSourcesFile.absolutePath}")
    }
    val props = Properties().apply {
        nativeRuntimeSourcesFile.inputStream().use { load(it) }
    }
    fun required(key: String): String = props.getProperty(key)?.trim().orEmpty().takeIf { it.isNotBlank() }
        ?: throw GradleException("原生运行时来源清单缺少 $key")

    val releaseVersion = required("release.version")
    val sourceKind = props.getProperty("source.kind", "release").trim()
    if (sourceKind !in listOf("release", "github-actions")) {
        throw GradleException("原生运行时 source.kind 非法：$sourceKind")
    }
    val sources = defaultReleaseAbis.associateWith { abi ->
        val sha = required("$abi.sha256").lowercase()
        if (!sha.matches(Regex("[0-9a-f]{64}"))) {
            throw GradleException("原生运行时来源清单中的 $abi.sha256 非法")
        }
        NativeRuntimeReleaseSource(
            abi = abi,
            assetName = required("$abi.asset"),
            url = nativeRuntimeArchiveUrlOverride ?: required("$abi.url"),
            size = required("$abi.size").toLongOrNull()?.takeIf { it in 1..(128L * 1024L * 1024L) }
                ?: throw GradleException("原生运行时来源清单中的 $abi.size 非法"),
            sha256 = sha,
            githubActionsArtifact = nativeRuntimeArchiveUrlOverride == null && sourceKind == "github-actions"
        )
    }
    if (nativeRuntimeArchiveOverride != null || nativeRuntimeArchiveUrlOverride != null) {
        require(sources.values.map { it.sha256 to it.size }.distinct().size == 1) {
            "nativeRuntimeArchive/nativeRuntimeArchiveUrl 只适用于所有 ABI 共用同一 ZIP 的来源清单"
        }
    }
    return releaseVersion to sources
}

val nativeRuntimeLayoutDanmuPack = "danmu-pack"
val nativeRuntimeLayoutNodejsMobile = "nodejs-mobile"

// 运行时资产的布局：
// - danmu-pack：兼容旧来源清单的历史布局；本项目当前固定使用下面的 nodejs-mobile。
// - nodejs-mobile：上游配方产物布局，zip 内只有每个 ABI 的 libnode.so 与头文件目录；
//   这种布局下 JNI 桥由本次构建用本地 NDK 依据同一份头文件现编，
//   libc++ / libc++abi / libunwind 从固定的 NDK r29 源码重新编译。
fun readNativeRuntimeLayout(sourcesFile: File): String {
    if (!sourcesFile.isFile) return nativeRuntimeLayoutDanmuPack
    val props = Properties().apply { sourcesFile.inputStream().use { load(it) } }
    val raw = props.getProperty("layout")?.trim().orEmpty()
    return when {
        raw.isEmpty() -> nativeRuntimeLayoutDanmuPack
        raw == nativeRuntimeLayoutNodejsMobile -> raw
        else -> throw GradleException("原生运行时来源清单里的 layout 非法：$raw")
    }
}

// 选择与当前主机匹配的 NDK 预编译目录名（Termux/arm64 上是 linux-aarch64，PC 上是 linux-x86_64 / darwin-*）。
fun resolveNdkHostTag(ndkDir: File): String? {
    val prebuilt = File(ndkDir, "toolchains/llvm/prebuilt")
    if (!prebuilt.isDirectory) return null
    val available = prebuilt.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()
    if (available.isEmpty()) return null
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val preferred = when {
        os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) -> "darwin-arm64"
        os.contains("mac") -> "darwin-x86_64"
        os.contains("windows") -> "windows-x86_64"
        arch.contains("aarch64") || arch.contains("arm64") -> "linux-aarch64"
        else -> "linux-x86_64"
    }
    // 只接受与当前主机匹配的预编译目录：在 Termux(aarch64) 上误选 linux-x86_64
    // 会得到一个无法执行的编译器，宁可让上层继续找下一个候选 NDK。
    return preferred.takeIf { it in available }
}

fun resolveLocalNdkDir(candidates: List<File>): File {
    val usable = candidates.filter { it.isDirectory && resolveNdkHostTag(it) != null }
    return usable.firstOrNull() ?: throw GradleException(
        "找不到可用于本地编译 JNI 桥的 NDK。请用 -PndkDir=、local.properties 的 ndk.dir 或 ANDROID_NDK_HOME 指定。" +
            "候选：${candidates.joinToString { it.absolutePath }}"
    )
}

fun nativeAbiTriples(abi: String): Pair<String, String> = when (abi) {
    "arm64-v8a" -> "aarch64-linux-android" to "aarch64-linux-android"
    "armeabi-v7a" -> "armv7a-linux-androideabi" to "arm-linux-androideabi"
    "x86_64" -> "x86_64-linux-android" to "x86_64-linux-android"
    else -> throw GradleException("不支持的 ABI：$abi")
}

// 用本地 NDK 依据上游头文件现编 JNI 桥，等价于 CMakeLists.txt 里的 native-lib 目标。
fun compileJniBridge(
    ndkDir: File,
    abi: String,
    apiLevel: Int,
    bridgeSource: File,
    includeDir: File,
    libnodeSo: File,
    outFile: File
) {
    val hostTag = resolveNdkHostTag(ndkDir)
        ?: throw GradleException("NDK 缺少可用预编译工具链：${ndkDir.absolutePath}")
    val (clangTriple, _) = nativeAbiTriples(abi)
    val exeSuffix = if (hostTag.startsWith("windows")) ".cmd" else ""
    val clang = File(
        ndkDir,
        "toolchains/llvm/prebuilt/$hostTag/bin/${clangTriple}${apiLevel}-clang++$exeSuffix"
    )
    if (!clang.isFile) throw GradleException("NDK 缺少编译器：${clang.absolutePath}")
    if (!bridgeSource.isFile) throw GradleException("缺少 JNI 桥源码：${bridgeSource.absolutePath}")
    if (!includeDir.isDirectory) throw GradleException("缺少 Node 头文件目录：${includeDir.absolutePath}")
    outFile.parentFile.mkdirs()
    val command = (if (isTermuxHost) listOf("sh") else emptyList()) + listOf(
        clang.absolutePath,
        "-std=c++20", "-O2", "-fPIC", "-shared",
        "-I", includeDir.absolutePath,
        "-Wl,-z,max-page-size=16384",
        "-Wl,-z,common-page-size=16384",
        "-Wl,-soname,libnative-lib.so",
        "-o", outFile.absolutePath,
        bridgeSource.absolutePath,
        libnodeSo.absolutePath,
        "-nostdlib++", File(outFile.parentFile, "libc++_shared.so").absolutePath
    )
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    val exit = process.waitFor()
    if (exit != 0) {
        throw GradleException("编译 JNI 桥失败（$abi，exit=$exit）：\n${output.trim()}")
    }
    stripNativeRuntimeLibrary(ndkDir, outFile)
    println("JNI 桥现编完成：$abi -> ${outFile.name}（${outFile.length()} 字节）")
}

fun buildNativeDependencies(ndkDir: File, abi: String, target: File) {
    target.parentFile.mkdirs()
    val script = rootProject.file("scripts/build-native-deps.py")
    val command = listOf("python3", script.absolutePath, "--ndk", ndkDir.absolutePath,
        "--abi", abi, "--api", "24", "--output", target.absolutePath)
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (process.waitFor() != 0) throw GradleException("源码构建 C++ 运行库失败（$abi）：\n${output.trim()}")
    println("libc++ / libc++abi / libunwind 源码重建完成：$abi")
}

fun stripNativeRuntimeLibrary(ndkDir: File, library: File) {
    val hostTag = resolveNdkHostTag(ndkDir)
        ?: throw GradleException("NDK 缺少可用预编译工具链")
    val strip = File(ndkDir, "toolchains/llvm/prebuilt/$hostTag/bin/llvm-strip")
    val process = ProcessBuilder(strip.absolutePath, "--strip-unneeded", library.absolutePath)
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (process.waitFor() != 0) throw GradleException("strip ${library.name} 失败：${output.trim()}")
}

// 解析（必要时下载）原生运行时资产 zip，本地 dist 与 Gradle 缓存都按 SHA-256 校验。
fun resolveNativeRuntimeZip(
    source: NativeRuntimeReleaseSource,
    releaseVersion: String,
    projectDir: File,
    gradleUserHomeDir: File,
    offline: Boolean
): File {
    val localReleaseApk = File(projectDir, "dist/$releaseVersion/${source.assetName}")
    val cacheApk = File(
        gradleUserHomeDir,
        "caches/danmu-native-runtime/$releaseVersion/${source.assetName}"
    )
    fun isValid(candidate: File): Boolean =
        candidate.isFile && candidate.length() == source.size && sha256(candidate) == source.sha256

    nativeRuntimeArchiveOverride?.let {
        if (!isValid(it)) throw GradleException("指定的 nativeRuntimeArchive 缺失或大小/SHA-256 与仓库清单不匹配")
        return it
    }
    if (isValid(localReleaseApk)) return localReleaseApk
    if (isValid(cacheApk)) return cacheApk
    if (offline) throw GradleException("离线构建缺少原生运行时缓存：${source.assetName}")

    cacheApk.parentFile.mkdirs()
    val partFile = File(cacheApk.parentFile, "${cacheApk.name}.part")
    partFile.delete()
    try {
        if (source.githubActionsArtifact) {
            val uri = URI(source.url)
            if (uri.scheme != "https" || uri.host != "api.github.com" ||
                !uri.path.matches(Regex("/repos/fogtape/nodejs-mobile/actions/artifacts/[0-9]+/zip"))
            ) {
                throw GradleException("Actions artifact URL 非法")
            }
            val process = try {
                ProcessBuilder("gh", "api", uri.path.removePrefix("/"))
                    .redirectOutput(partFile).start()
            } catch (error: Exception) {
                throw GradleException(
                    "下载固定 Actions artifact 需要已认证的 gh；也可先把校验通过的 ZIP 放到 dist/$releaseVersion/",
                    error
                )
            }
            val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
            if (process.waitFor() != 0) {
                throw GradleException("下载 ${source.assetName} Actions artifact 失败：${errorOutput.trim()}")
            }
        } else {
            val connection = (URI(source.url).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DanmuApiApp-Gradle")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw GradleException("下载 ${source.assetName} 失败：HTTP $responseCode")
                }
                connection.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 128L * 1024L * 1024L) {
                                throw GradleException("下载的 ${source.assetName} 超过大小上限")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (!isValid(partFile)) {
            throw GradleException("下载的 ${source.assetName} 大小或 SHA-256 不匹配")
        }
        try {
            Files.move(
                partFile.toPath(),
                cacheApk.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(partFile.toPath(), cacheApk.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return cacheApk
    } finally {
        partFile.delete()
    }
}

fun registerNativeRuntimeTasks(): Pair<TaskProvider<*>, TaskProvider<*>> {
    val prepareNativeRuntimeTask = tasks.register("prepareNativeRuntime") {
    inputs.files(nativeRuntimeChecksumFile, nativeRuntimeSourcesFile)
    inputs.file("src/main/cpp/native-lib.cpp")
    inputs.file(rootProject.file("scripts/build-native-deps.py"))
    inputs.property("configuredAbiFilters", configuredAbiFilters.joinToString(","))
    nativeRuntimeArchiveOverride?.let { inputs.file(it) }
    inputs.property("nativeRuntimeArchiveUrl", nativeRuntimeArchiveUrlOverride ?: "")
    outputs.dir(preparedNativeRuntimeDir)
    doLast {
        val checksums = readNativeRuntimeChecksums()
        val (releaseVersion, allSources) = readNativeRuntimeReleaseSources()
        val nativeRuntimeLayout = readNativeRuntimeLayout(nativeRuntimeSourcesFile)
        val localNdkDir = if (nativeRuntimeLayout == nativeRuntimeLayoutNodejsMobile) {
            val sdkDirValue = localProps.getProperty("sdk.dir")?.trim()?.takeIf { it.isNotEmpty() }
                ?: System.getenv("ANDROID_SDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() }
                ?: System.getenv("ANDROID_HOME")?.trim()?.takeIf { it.isNotEmpty() }
            val explicit = sequenceOf(
                (findProperty("ndkDir") as? String)?.trim()?.takeIf { it.isNotEmpty() },
                localProps.getProperty("ndk.dir")?.trim()?.takeIf { it.isNotEmpty() },
                System.getenv("ANDROID_NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() },
                System.getenv("ANDROID_NDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() }
            ).filterNotNull().map { File(it) }.toList()
            val fromSdk = sdkDirValue?.let { File(it, "ndk") }
                ?.takeIf { it.isDirectory }
                ?.listFiles()
                ?.sortedBy { it.name }
                ?.reversed()
                .orEmpty()
            resolveLocalNdkDir((explicit + fromSdk).distinct())
        } else {
            null
        }
        val selectedChecksums = checksums.filterKeys { path ->
            configuredAbiFilters.any { abi -> "/$abi/" in "/$path" }
        }
        val requiredManifestEntries = if (nativeRuntimeLayout == nativeRuntimeLayoutNodejsMobile) {
            // 上游布局只提供 libnode.so；JNI 与 C++ 依赖由本次构建从源码重编，另存构建指纹。
            configuredAbiFilters.map { abi -> "libnode/bin/$abi/libnode.so" }
        } else {
            configuredAbiFilters.flatMap { abi ->
                listOf(
                    "libnode/bin/$abi/libnode.so",
                    "jni-current/$abi/libc++_shared.so",
                    "jni-current/$abi/libnative-lib.so"
                )
            }
        }
        val missingManifestEntries = requiredManifestEntries.filterNot(selectedChecksums::containsKey)
        if (missingManifestEntries.isNotEmpty()) {
            throw GradleException(
                "原生运行时校验清单缺少：${missingManifestEntries.joinToString(", ")}"
            )
        }

        delete(preparedNativeRuntimeDir)
        preparedNativeRuntimeDir.mkdirs()

        configuredAbiFilters.forEach { abi ->
            if (nativeRuntimeLayout == nativeRuntimeLayoutNodejsMobile) {
                val ndkDir = localNdkDir!!
                val source = allSources.getValue(abi)
                val zipFile = resolveNativeRuntimeZip(
                    source = source,
                    releaseVersion = releaseVersion,
                    projectDir = rootProject.projectDir,
                    gradleUserHomeDir = gradle.gradleUserHomeDir,
                    offline = gradle.startParameter.isOffline
                )
                val libnodeTarget = File(preparedNativeRuntimeDir, "libnode/bin/$abi/libnode.so")
                libnodeTarget.parentFile.mkdirs()
                val includeDir = File(preparedNativeRuntimeDir, "include/node")
                ZipFile(zipFile).use { zip ->
                    val libnodeEntry = zip.getEntry("bin/$abi/libnode.so")
                        ?: throw GradleException("${source.assetName} 缺少 bin/$abi/libnode.so")
                    zip.getInputStream(libnodeEntry).use { input ->
                        libnodeTarget.outputStream().use(input::copyTo)
                    }
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.startsWith("include/node/") }
                        .forEach { headerEntry ->
                            val target = File(
                                includeDir,
                                headerEntry.name.removePrefix("include/node/")
                            )
                            target.parentFile.mkdirs()
                            zip.getInputStream(headerEntry).use { input ->
                                target.outputStream().use(input::copyTo)
                            }
                        }
                }
                val expectedLibnode = checksums.getValue("libnode/bin/$abi/libnode.so")
                if (sha256(libnodeTarget) != expectedLibnode) {
                    throw GradleException("提取的 libnode.so SHA-256 不匹配：$abi")
                }
                val jniDir = File(preparedNativeRuntimeDir, "jni-current/$abi").apply { mkdirs() }
                buildNativeDependencies(ndkDir, abi, File(jniDir, "libc++_shared.so"))
                compileJniBridge(
                    ndkDir = ndkDir,
                    abi = abi,
                    apiLevel = 24,
                    bridgeSource = file("src/main/cpp/native-lib.cpp"),
                    includeDir = includeDir,
                    libnodeSo = libnodeTarget,
                    outFile = File(jniDir, "libnative-lib.so")
                )
                return@forEach
            }
            val abiEntries = selectedChecksums.filterKeys { "/$abi/" in "/$it" }
            val canUseLegacyFiles = abiEntries.all { (relativePath, expectedHash) ->
                val legacyFile = file(relativePath)
                legacyFile.isFile && sha256(legacyFile) == expectedHash
            }

            if (canUseLegacyFiles) {
                abiEntries.forEach { (relativePath, _) ->
                    val target = File(preparedNativeRuntimeDir, relativePath)
                    target.parentFile.mkdirs()
                    file(relativePath).copyTo(target, overwrite = true)
                }
                return@forEach
            }

            val source = allSources.getValue(abi)
            val localReleaseApk = rootProject.file("dist/$releaseVersion/${source.assetName}")
            val cacheApk = File(
                gradle.gradleUserHomeDir,
                "caches/danmu-native-runtime/$releaseVersion/${source.assetName}"
            )
            fun isValidApk(candidate: File): Boolean =
                candidate.isFile && candidate.length() == source.size && sha256(candidate) == source.sha256

            val apkFile = when {
                isValidApk(localReleaseApk) -> localReleaseApk
                isValidApk(cacheApk) -> cacheApk
                gradle.startParameter.isOffline -> throw GradleException(
                    "离线构建缺少 $abi 原生运行时缓存。请先联网执行 prepareNativeRuntime"
                )
                else -> {
                    cacheApk.parentFile.mkdirs()
                    val partFile = File(cacheApk.parentFile, "${cacheApk.name}.part")
                    partFile.delete()
                    try {
                        val connection = (URI(source.url).toURL().openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 20_000
                            readTimeout = 60_000
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "DanmuApiApp-Gradle")
                        }
                        try {
                            val responseCode = connection.responseCode
                            if (responseCode !in 200..299) {
                                throw GradleException("下载 $abi 原生运行时失败：HTTP $responseCode")
                            }
                            connection.inputStream.use { input ->
                                partFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > 128L * 1024L * 1024L) {
                                            throw GradleException("下载的 $abi 原生运行时超过大小上限")
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                        if (!isValidApk(partFile)) {
                            throw GradleException("下载的 $abi 原生运行时大小或 SHA-256 不匹配")
                        }
                        try {
                            Files.move(
                                partFile.toPath(),
                                cacheApk.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } catch (_: Exception) {
                            Files.move(
                                partFile.toPath(),
                                cacheApk.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                        cacheApk
                    } finally {
                        partFile.delete()
                    }
                }
            }

            ZipFile(apkFile).use { zip ->
                abiEntries.forEach { (relativePath, expectedHash) ->
                    val apkEntryName = "lib/$abi/${relativePath.substringAfterLast('/')}"
                    val entry = zip.getEntry(apkEntryName)
                        ?: throw GradleException("${source.assetName} 缺少 $apkEntryName")
                    if (entry.isDirectory || entry.size <= 0L || entry.size > 128L * 1024L * 1024L) {
                        throw GradleException("${source.assetName} 中的 $apkEntryName 大小异常")
                    }
                    val target = File(preparedNativeRuntimeDir, relativePath)
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use(input::copyTo)
                    }
                    if (sha256(target) != expectedHash) {
                        throw GradleException("提取的原生运行时文件 SHA-256 不匹配：$relativePath")
                    }
                }
            }
        }

        // 页对齐门禁：对解包后的全部 .so 逐一校验（含 canUseLegacyFiles 本地路径）。
        preparedNativeRuntimeDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".so") }
            .forEach { soFile -> assertElfLoadAlignment(soFile) }
    }
}

val verifyNativeRuntimeInputsTask = tasks.register("verifyNativeRuntimeInputs") {
    dependsOn(prepareNativeRuntimeTask)
    inputs.files(nativeRuntimeChecksumFile, nativeRuntimeSourcesFile)
    inputs.property("configuredAbiFilters", configuredAbiFilters.joinToString(","))
    doLast {
        val checksums = readNativeRuntimeChecksums().filterKeys { path ->
            configuredAbiFilters.any { abi -> "/$abi/" in "/$path" }
        }
        val missing = mutableListOf<String>()
        val mismatched = mutableListOf<String>()
        checksums.forEach { (relativePath, expected) ->
            val runtimeFile = File(preparedNativeRuntimeDir, relativePath)
            if (!runtimeFile.isFile) {
                missing += relativePath
            } else if (sha256(runtimeFile) != expected) {
                mismatched += relativePath
            }
        }
        if (missing.isNotEmpty() || mismatched.isNotEmpty()) {
            throw GradleException(
                buildList {
                    if (missing.isNotEmpty()) add("缺少文件：${missing.joinToString()}")
                    if (mismatched.isNotEmpty()) add("哈希不匹配：${mismatched.joinToString()}")
                }.joinToString("；", prefix = "原生运行时输入校验失败：")
            )
        }
    }
    }
    return prepareNativeRuntimeTask to verifyNativeRuntimeInputsTask
}

val (prepareNativeRuntimeTask, verifyNativeRuntimeInputsTask) = registerNativeRuntimeTasks()

// Check the portable archive without an NDK build or a GitHub login.
tasks.register("verifyNativeRuntimeArchive") {
    inputs.file(nativeRuntimeSourcesFile)
    nativeRuntimeArchiveOverride?.let { inputs.file(it) }
    inputs.property("nativeRuntimeArchiveUrl", nativeRuntimeArchiveUrlOverride ?: "")
    doLast {
        val (version, sources) = readNativeRuntimeReleaseSources()
        val source = sources.getValue(configuredAbiFilters.first())
        val archive = resolveNativeRuntimeZip(source, version, rootProject.projectDir,
            gradle.gradleUserHomeDir, gradle.startParameter.isOffline)
        logger.lifecycle("原生运行时归档校验通过：${archive.name}，SHA-256=${source.sha256}")
    }
}

fun verifyFrpcKernels(required: Boolean) {
    val directory = rootProject.file("runtime/frp")
    val checksums = File(directory, "SHA256SUMS").takeIf { it.isFile }?.readLines().orEmpty()
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) parts[1] to parts[0] else null
        }.toMap()
    val version = File(directory, "VERSION").takeIf { it.isFile }?.readText()?.trim()
    val code = file("src/main/java/com/example/danmuapiapp/data/tunnel/TunnelLogic.kt").readText()
    val codeVersion = Regex("const val FRPC_KERNEL_VERSION = \"([^\"]+)\"").find(code)?.groupValues?.get(1)
    val problems = mutableListOf<String>()
    if (version == null || version != codeVersion) problems += "frpc VERSION 与代码版本不一致"
    for (abi in configuredAbiFilters) {
        val path = "$abi/libfrpc.so"
        val kernel = File(directory, path)
        val expected = checksums[path]
        if (!kernel.isFile || kernel.length() == 0L || expected == null) {
            problems += "$abi 缺少内核或校验值"
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            kernel.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) problems += "$abi 内核 SHA-256 不匹配"
        }
    }
    if (problems.isNotEmpty()) {
        val message = problems.joinToString("；") + "。请运行 ./scripts/prepare_frp_kernel.sh ${configuredAbiFilters.joinToString(" ")}"
        if (required) throw GradleException(message) else logger.warn("内网穿透不可用：$message")
    }
}

val verifyFrpcReleaseTask = tasks.register("verifyFrpcReleaseKernels") {
    doLast { verifyFrpcKernels(required = true) }
}
val verifyFrpcDebugTask = tasks.register("verifyFrpcDebugKernels") {
    doLast { verifyFrpcKernels(required = false) }
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(verifyFrpcDebugTask) }

tasks.matching {
    (it.name.startsWith("merge") && (it.name.endsWith("JniLibFolders") || it.name.endsWith("NativeLibs"))) ||
        (it.name.startsWith("strip") && it.name.endsWith("DebugSymbols")) ||
        it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake")
}.configureEach {
    dependsOn(prepareNativeRuntimeTask)
}


// 构建前置门禁：解包后的原生运行时必须与仓库校验清单逐文件一致。
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyNativeRuntimeInputsTask)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyFrpcReleaseTask)
}

