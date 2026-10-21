// WS7：由 app/build.gradle.kts 迁出的 Node 运行时资产、APK 运行时校验与 Node 测试任务。
import danmu.danmuAbiFilters
import danmu.danmuMissingRuntimeTestAssetsDir
import danmu.danmuNativeRuntimeSourcesFile
import danmu.danmuRequestedTaskNames
import danmu.danmuTestMissingRuntimePackage
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.GradleException

val configuredAbiFilters = danmuAbiFilters()
val nativeRuntimeSourcesFile = danmuNativeRuntimeSourcesFile()
val missingRuntimeTestAssetsDir = danmuMissingRuntimeTestAssetsDir()
val requestedTaskNames = danmuRequestedTaskNames()
val testMissingRuntimePackage = danmuTestMissingRuntimePackage()


if (testMissingRuntimePackage != null) {
    if (testMissingRuntimePackage != "opencc-js") {
        throw GradleException("testMissingRuntimePackage 仅允许 opencc-js")
    }
    if (requestedTaskNames.any { it.contains("release") || it.contains("bundle") }) {
        throw GradleException("缺依赖测试开关只能用于 Debug APK，禁止用于 Release/AAB")
    }
}
val prepareMissingRuntimeTestAssetsTask = testMissingRuntimePackage?.let { missingPackage ->
    tasks.register("prepareMissingRuntimeTestAssets") {
        val sourceAssets = file("src/main/assets")
        inputs.dir(sourceAssets)
        inputs.property("missingRuntimePackage", missingPackage)
        outputs.dir(missingRuntimeTestAssetsDir)
        doLast {
            delete(missingRuntimeTestAssetsDir)
            copy {
                from(sourceAssets)
                exclude("nodejs-project/node_modules/$missingPackage/**")
                into(missingRuntimeTestAssetsDir)
            }
        }
    }
}

// 清理项目内控制字符文件名，避免异常垃圾文件混入仓库
tasks.register("cleanupGarbageFiles") {
    doLast {
        val appRoot = projectDir
        val targets = appRoot.walkBottomUp().filter { file ->
            file.name.any { ch -> ch.code < 32 || ch.code == 127 }
        }.toList()
        targets.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            println("已删除垃圾文件：${file.relativeTo(appRoot).invariantSeparatorsPath}")
        }
    }
}

fun org.gradle.api.file.CopySpec.includeNodeModuleDirs(packages: Iterable<String>) {
    packages.forEach { pkg ->
        include("$pkg/**")
    }
}

fun normalizeNodeDependencyVersion(raw: String): String {
    val value = raw.trim()
    return value.removePrefix("^").removePrefix("~").trim()
}

fun readBundledNodeDependencyNames(): List<String> {
    val packageJsonFile = file("src/main/assets/nodejs-project/package.json")
    if (!packageJsonFile.exists()) {
        throw GradleException("缺少运行时依赖声明文件：${packageJsonFile.absolutePath}")
    }
    val pkg = groovy.json.JsonSlurper().parse(packageJsonFile) as? Map<*, *>
        ?: throw GradleException("无法解析 package.json：${packageJsonFile.absolutePath}")
    val dependencies = (pkg["dependencies"] as? Map<*, *>)?.keys
        ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        .orEmpty()
    if (dependencies.isEmpty()) {
        throw GradleException("package.json 未声明任何运行时依赖：${packageJsonFile.absolutePath}")
    }
    return dependencies
}

// danmu_api imports dan-any's pure in-memory implementation and adapters only.
// The package also publishes a separate PGlite/Drizzle implementation that is not
// part of the Android runtime path.
val androidRuntimeExcludedNodeModules = setOf(
    "@electric-sql/pglite",
    "@electric-sql/pglite-tools",
    "drizzle-orm",
    // Node >= 16.5 内置 node:stream/web；web-streams-polyfill 仅作为
    // fetch-blob 在无原生 ReadableStream 环境的兜底，内嵌 Node 24 永不触达。
    "web-streams-polyfill"
)

private val retainedOpenccRuntimeFiles = setOf(
    "opencc-js/package.json",
    "opencc-js/LICENSE",
    "opencc-js/LICENSES/Apache-2.0.txt",
    "opencc-js/THIRD_PARTY_LICENSES.md",
    "opencc-js/dist/esm-lib/core.js",
    "opencc-js/dist/esm-lib/dict/STCharacters.js",
    "opencc-js/dist/esm-lib/dict/TSCharacters.js",
    "opencc-js/dist/esm-lib/dict/TSPhrases.js",
    "opencc-js/dist/esm-lib/dict/TWVariants.js",
    "opencc-js/dist/esm-lib/dict/TWVariantsPhrases.js",
    "opencc-js/dist/esm-lib/to/cn.js",
    "opencc-js/dist/esm-lib/to/tw.js"
)

fun readBundledNodeDependencyClosure(): List<String> {
    val lockFile = file("src/main/assets/nodejs-project/package-lock.json")
    if (!lockFile.exists()) {
        throw GradleException("缺少运行时依赖锁文件：${lockFile.absolutePath}")
    }
    val lock = groovy.json.JsonSlurper().parse(lockFile) as? Map<*, *>
        ?: throw GradleException("无法解析 package-lock.json：${lockFile.absolutePath}")
    val packages = lock["packages"] as? Map<*, *>
        ?: throw GradleException("package-lock.json 缺少 packages：${lockFile.absolutePath}")
    val roots = packages.keys.mapNotNull { rawPath ->
        val path = rawPath?.toString().orEmpty()
        if (!path.startsWith("node_modules/")) return@mapNotNull null
        val relative = path.removePrefix("node_modules/")
        val segments = relative.split('/')
        when {
            segments.firstOrNull()?.startsWith('@') == true && segments.size >= 2 ->
                "${segments[0]}/${segments[1]}"
            segments.firstOrNull()?.isNotBlank() == true -> segments[0]
            else -> null
        }
    }.distinct().sorted()
    if (roots.isEmpty()) {
        throw GradleException("package-lock.json 未收录任何运行时依赖：${lockFile.absolutePath}")
    }
    return roots.filterNot { it in androidRuntimeExcludedNodeModules }
}

fun pruneNodeModuleRuntimeNoise(rootDir: java.io.File) {
    if (!rootDir.exists() || !rootDir.isDirectory) return

    val redundantDocName = Regex("""(?i)^(readme|changelog|history)([.-].*)?$""")
    val redundantPakoDistFiles = setOf(
        "pako/dist/pako.es5.js",
        "pako/dist/pako.es5.min.js",
        "pako/dist/pako.js",
        "pako/dist/pako.min.js",
        "pako/dist/pako_deflate.es5.js",
        "pako/dist/pako_deflate.es5.min.js",
        "pako/dist/pako_deflate.js",
        "pako/dist/pako_deflate.min.js",
        "pako/dist/pako_inflate.es5.js",
        "pako/dist/pako_inflate.es5.min.js",
        "pako/dist/pako_inflate.js",
        "pako/dist/pako_inflate.min.js",
        // brotli 仅在弹幕链路使用 decompress；encode 半边零引用。
        // index.js 保留为包根兼容入口（仅导出 decompress），不在裁剪之列。
        "brotli/build/encode.js",
        "brotli/build/mem.js",
        "brotli/compress.js",
        "brotli/enc/pre.js"
    )

    rootDir.walkTopDown()
        .filter { it.isFile }
        .forEach { file ->
            val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
            val shouldDelete =
                relativePath.endsWith(".map") ||
                    // 普通 TS 源码副本仅供编辑器跳转/调试映射使用，
                    // runtime-pack 生产链早已全局剥离且验证安全。
                    relativePath.endsWith(".ts") ||
                    relativePath.endsWith(".mts") ||
                    relativePath.endsWith(".cts") ||
                    relativePath.endsWith(".d.ts") ||
                    relativePath.endsWith(".d.cts") ||
                    relativePath.endsWith(".d.mts") ||
                    redundantDocName.matches(file.name) ||
                    relativePath in redundantPakoDistFiles ||
                    (relativePath.startsWith("opencc-js/") &&
                        relativePath !in retainedOpenccRuntimeFiles)

            if (shouldDelete) {
                file.delete()
            }
        }

    // npm 重新安装后同步裁剪后的 brotli 根入口。
    File(rootDir, "brotli/index.js").takeIf { it.isFile }?.writeText(
        "exports.decompress = require('./decompress.js');\n", Charsets.UTF_8
    )
    rootDir.walkTopDown().filter { it.isDirectory && it.name == ".history" }
        .toList().forEach { it.deleteRecursively() }

    // Node 24 require(esm) 返回 ESM namespace，dan-any 打包产物对
    // fast-xml-builder 的 interop 会把整个 namespace 塞进 .default，
    // 使 `new o.default()` 失效。仅 CJS 理论路径受影响（生产全走 ESM），
    // 此处做幂等防护补丁，保证该调用点在两种模块体系下均可构造。
    val fxbInteropPatched = "new (typeof o.default===\"function\"?o.default:o.default.default)("
    rootDir.walkTopDown()
        .filter { it.isFile && it.name.startsWith("adapters-") && it.name.endsWith(".cjs") }
        .forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            val patched = text.replace("new o.default(", "$fxbInteropPatched")
            if (patched != text) {
                file.writeText(patched, Charsets.UTF_8)
                println("已修补 dan-any fast-xml-builder interop：${file.name}")
            }
        }

    rootDir.walkBottomUp()
        .filter { it.isDirectory && it != rootDir && it.list()?.isEmpty() == true }
        .forEach { it.delete() }
}

val baseNodeModulesPackages = readBundledNodeDependencyClosure()
val optionalRedisNodeModulesPackages = listOf(
    "redis",
    "@redis/bloom",
    "@redis/client",
    "@redis/json",
    "@redis/search",
    "@redis/time-series",
    "cluster-key-slot"
)

// 显式维护任务：从本地归档刷新受版本控制的运行时资产。普通构建不会调用它。
tasks.register("refreshBundledNodeModulesFromArchive") {
    val zipFile = rootProject.file("node_modules.zip")
    val packageLockFile = file("src/main/assets/nodejs-project/package-lock.json")
    val baseTargetDir = file("src/main/assets/nodejs-project/node_modules")
    val optionalRootDir = file("src/main/assets/nodejs-optional")
    val optionalRedisTargetDir = file("src/main/assets/nodejs-optional/redis/node_modules")
    val tempRootDir = layout.buildDirectory.dir("prepared-node-modules").get().asFile
    if (zipFile.exists()) {
        inputs.file(zipFile)
    }
    inputs.file(packageLockFile)
    outputs.dirs(baseTargetDir, optionalRootDir)
    dependsOn("cleanupGarbageFiles")
    doLast {
        if (!zipFile.isFile) {
            throw GradleException("未找到依赖归档：${zipFile.absolutePath}")
        }
        val sourceRoot = File(tempRootDir, "source")
        val sourceNodeModules = File(sourceRoot, "node_modules")

        delete(tempRootDir)
        sourceRoot.mkdirs()

        copy {
            from(zipTree(zipFile)) {
                include("node_modules/**")
                includeEmptyDirs = false
                eachFile {
                    val normalized = path.removePrefix("node_modules/")
                    if (normalized == path || normalized.isBlank()) {
                        exclude()
                    } else {
                        if (normalized.any { ch -> ch.code < 32 || ch.code == 127 }) {
                            throw GradleException("node_modules.zip 含非法文件名：$path")
                        }
                    }
                }
            }
            into(sourceRoot)
        }

        val missingPackages = (baseNodeModulesPackages + optionalRedisNodeModulesPackages)
            .filterNot { name -> File(sourceNodeModules, "$name/package.json").isFile }
        if (missingPackages.isNotEmpty()) {
            throw GradleException("依赖归档闭包不完整：${missingPackages.joinToString(", ")}")
        }

        pruneNodeModuleRuntimeNoise(sourceNodeModules)

        delete(baseTargetDir)
        delete(optionalRootDir)

        copy {
            from(sourceNodeModules) {
                includeNodeModuleDirs(baseNodeModulesPackages)
                includeEmptyDirs = false
            }
            into(baseTargetDir)
        }

        val redisSourceReady =
            File(sourceNodeModules, "redis/package.json").exists() || File(sourceNodeModules, "@redis").exists()
        if (redisSourceReady) {
            copy {
                from(sourceNodeModules) {
                    includeNodeModuleDirs(optionalRedisNodeModulesPackages)
                    includeEmptyDirs = false
                }
                into(optionalRedisTargetDir)
            }
        }
    }
}

// 显式维护任务：从核心工作区刷新依赖。普通构建不读取兄弟仓库。
tasks.register("refreshBundledNodeModulesFromWorkspace") {
    val workspaceNodeModules = rootProject.file("../danmu_api/node_modules")
    val packageLockFile = file("src/main/assets/nodejs-project/package-lock.json")
    val baseTargetDir = file("src/main/assets/nodejs-project/node_modules")
    val optionalRedisTargetDir = file("src/main/assets/nodejs-optional/redis/node_modules")
    inputs.dir(workspaceNodeModules)
    inputs.file(packageLockFile)
    outputs.dirs(baseTargetDir, optionalRedisTargetDir)
    doLast {
        if (!workspaceNodeModules.exists()) {
            throw GradleException("未找到工作区依赖目录：${workspaceNodeModules.absolutePath}")
        }
        val missingPackages = (baseNodeModulesPackages + optionalRedisNodeModulesPackages)
            .filterNot { name ->
                File(workspaceNodeModules, "$name/package.json").isFile
            }
        if (missingPackages.isNotEmpty()) {
            throw GradleException("工作区依赖闭包不完整：${missingPackages.joinToString(", ")}")
        }

        delete(baseTargetDir)
        delete(file("src/main/assets/nodejs-optional"))
        copy {
            from(workspaceNodeModules) {
                includeNodeModuleDirs(baseNodeModulesPackages)
                includeEmptyDirs = false
            }
            into(baseTargetDir)
        }
        copy {
            from(workspaceNodeModules) {
                includeNodeModuleDirs(optionalRedisNodeModulesPackages)
                includeEmptyDirs = false
            }
            into(optionalRedisTargetDir)
        }
        pruneNodeModuleRuntimeNoise(baseTargetDir)
        pruneNodeModuleRuntimeNoise(optionalRedisTargetDir)
    }
}

tasks.register("verifyBundledNodeModules") {
    val packageJsonFile = file("src/main/assets/nodejs-project/package.json")
    val packageLockFile = file("src/main/assets/nodejs-project/package-lock.json")
    val nodeModulesDir = file("src/main/assets/nodejs-project/node_modules")
    val optionalRedisNodeModulesDir = file("src/main/assets/nodejs-optional/redis/node_modules")
    inputs.file(packageJsonFile)
    inputs.file(packageLockFile)
    inputs.dir(nodeModulesDir)
    inputs.dir(optionalRedisNodeModulesDir)
    doLast {
        if (!packageJsonFile.exists()) {
            throw GradleException("缺少运行时依赖声明文件：${packageJsonFile.absolutePath}")
        }

        val pkg = groovy.json.JsonSlurper().parse(packageJsonFile) as? Map<*, *>
            ?: throw GradleException("无法解析 package.json：${packageJsonFile.absolutePath}")
        val dependencies = (pkg["dependencies"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            val name = key?.toString()?.trim().orEmpty()
            val version = value?.toString()?.trim().orEmpty()
            if (name.isBlank() || version.isBlank()) null else name to normalizeNodeDependencyVersion(version)
        }.orEmpty()

        val missing = mutableListOf<String>()
        val mismatched = mutableListOf<String>()
        dependencies.forEach { (name, expectedVersion) ->
            val depPkg = file("src/main/assets/nodejs-project/node_modules/$name/package.json")
            if (!depPkg.exists()) {
                missing += "$name@$expectedVersion"
                return@forEach
            }
            val depJson = groovy.json.JsonSlurper().parse(depPkg) as? Map<*, *>
            val actualVersion = depJson?.get("version")?.toString()?.trim().orEmpty()
            if (actualVersion.isBlank()) {
                missing += "$name@$expectedVersion"
            } else if (actualVersion != expectedVersion) {
                mismatched += "$name 期望 $expectedVersion，实际 $actualVersion"
            }
        }

        baseNodeModulesPackages.forEach { name ->
            val depPkg = file("src/main/assets/nodejs-project/node_modules/$name/package.json")
            if (!depPkg.exists()) {
                missing += "$name（锁文件闭包）"
            }
        }
        optionalRedisNodeModulesPackages.forEach { name ->
            val depPkg = file("src/main/assets/nodejs-optional/redis/node_modules/$name/package.json")
            if (!depPkg.exists()) {
                missing += "$name（可选 Redis 运行时）"
            }
        }

        if (missing.isNotEmpty() || mismatched.isNotEmpty()) {
            val details = buildList {
                if (missing.isNotEmpty()) add("缺少依赖：${missing.joinToString(", ")}")
                if (mismatched.isNotEmpty()) add("版本不匹配：${mismatched.joinToString("；")}")
            }.joinToString("；")
            throw GradleException("运行时 assets 依赖校验失败：$details")
        }
    }
}

tasks.register<Exec>("testNodeRuntimeParsing") {
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/parse-dotenv-regression.js")
}

val testDanmakuPrepareCacheTask = tasks.register<Exec>("testDanmakuPrepareCache") {
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/danmaku-prepare-cache-regression.js")
}

val testFavoriteSchedulerHostTask = tasks.register<Exec>("testFavoriteSchedulerHost") {
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/favorite-scheduler-host-regression.js")
}

tasks.register<Exec>("testBundledBrotliRuntime") {
    dependsOn("verifyBundledNodeModules")
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/brotli-runtime-smoke.mjs")
}

tasks.register<Exec>("testBundledNodeLockClosure") {
    dependsOn("verifyBundledNodeModules")
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/bundled-node-lock-closure-smoke.mjs")
}

tasks.register<Exec>("testBundledCoreRuntimeDependencies") {
    dependsOn("verifyBundledNodeModules")
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/core-runtime-dependencies-smoke.mjs")
}

val embeddedNodeVersion = Properties().apply {
    nativeRuntimeSourcesFile.inputStream().use { load(it) }
}.getProperty("node.version")?.trim()?.takeIf { it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) }
    ?: throw GradleException("原生运行时来源清单缺少合法的 node.version")
val targetNodeExecutable = (findProperty("targetNodeExecutable") as? String)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("DANMU_TARGET_NODE")?.trim()?.takeIf { it.isNotBlank() }
    ?: "node"

tasks.register("verifyEmbeddedNodeCompatibility") {
    val smokeScripts = listOf(
        "node-tests/parse-dotenv-regression.js",
        "node-tests/favorite-scheduler-host-regression.js",
        "node-tests/brotli-runtime-smoke.mjs",
        "node-tests/bundled-node-lock-closure-smoke.mjs",
        "node-tests/core-runtime-dependencies-smoke.mjs",
    )
    dependsOn("verifyBundledNodeModules")
    inputs.files(smokeScripts.map(rootProject::file))
    inputs.file("src/main/assets/nodejs-project/android-server.js")
    inputs.file("src/main/assets/nodejs-project/favorite-scheduler-host.js")
    inputs.property("targetNodeExecutable", targetNodeExecutable)
    inputs.property("embeddedNodeVersion", embeddedNodeVersion)
    doLast {
        fun runTargetNode(arguments: List<String>): String {
            val process = try {
                ProcessBuilder(listOf(targetNodeExecutable) + arguments)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: Exception) {
                throw GradleException(
                    "无法执行目标 Node：$targetNodeExecutable。请通过 -PtargetNodeExecutable 或 DANMU_TARGET_NODE 指定 Node $embeddedNodeVersion",
                    error
                )
            }
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    "目标 Node 命令失败（exit=$exitCode）：${arguments.joinToString(" ")}\n${output.trim()}"
                )
            }
            return output.trim()
        }

        val actualVersion = runTargetNode(listOf("--version")).removePrefix("v").trim()
        val embeddedMajor = embeddedNodeVersion.split(".").first()
        val actualMajor = actualVersion.split(".").first()
        if (actualVersion != embeddedNodeVersion) {
            if (actualMajor == embeddedMajor) {
                println(
                    "WARNING: 目标 Node $actualVersion 与内嵌 $embeddedNodeVersion 版本不一致，" +
                        "smoke 结果仅供参考（major=$embeddedMajor 匹配，允许通过）"
                )
            } else {
                throw GradleException(
                    "Release 必须使用与内嵌运行时同 major 的 Node $embeddedNodeVersion 执行 smoke，当前为 $actualVersion（$targetNodeExecutable）"
                )
            }
        }
        runTargetNode(listOf("--check", "app/src/main/assets/nodejs-project/android-server.js"))
        smokeScripts.forEach { script -> runTargetNode(listOf(script)) }
        println("Embedded Node compatibility smoke: OK ($actualVersion)")
    }
}

val requiredPackagedNodeRuntimeEntries = listOf(
    "assets/nodejs-project/runtime-polyfills.js",
    "assets/nodejs-project/runtime_asset_layout.txt",
    "assets/nodejs-project/node_modules/node-fetch/package.json",
    "assets/nodejs-project/node_modules/pako/package.json",
    "assets/nodejs-project/node_modules/data-uri-to-buffer/dist/index.js",
    "assets/nodejs-project/node_modules/brotli/package.json",
    "assets/nodejs-project/node_modules/brotli/decompress.js",
    "assets/nodejs-project/node_modules/brotli/dec/decode.js",
    "assets/nodejs-project/node_modules/brotli/dec/dictionary-data.js",
    "assets/nodejs-project/node_modules/base64-js/package.json",
    "assets/nodejs-project/node_modules/@dan-uni/dan-any/package.json",
    "assets/nodejs-project/node_modules/@dan-uni/dan-any/dist/adapters.mjs",
    "assets/nodejs-project/node_modules/@dan-uni/dan-any/dist/core/main/pure.mjs",
    "assets/nodejs-project/node_modules/fast-xml-parser/package.json",
    "assets/nodejs-project/node_modules/opencc-js/package.json",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/core.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/dict/STCharacters.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/dict/TSCharacters.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/dict/TSPhrases.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/dict/TWVariants.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/dict/TWVariantsPhrases.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/to/cn.js",
    "assets/nodejs-project/node_modules/opencc-js/dist/esm-lib/to/tw.js",
    "assets/nodejs-project/node_modules/zod/package.json",
    "assets/nodejs-optional/redis/node_modules/redis/package.json",
    "assets/nodejs-optional/redis/node_modules/@redis/client/package.json"
)

fun requiredPackagedNativeRuntimeEntries(abi: String) = listOf(
    "lib/$abi/libnode.so",
    "lib/$abi/libnative-lib.so",
    "lib/$abi/libc++_shared.so",
    "lib/$abi/libandroidx.graphics.path.so",
    "lib/$abi/libdatastore_shared_counter.so"
)

tasks.register("verifyPackagedNodeModulesDebug") {
    val apkFiles = configuredAbiFilters.map { abi ->
        layout.buildDirectory.file("outputs/apk/debug/app-$abi-debug.apk")
    }
    inputs.files(apkFiles)
    doLast {
        apkFiles.zip(configuredAbiFilters).forEach { (provider, abi) ->
            val file = provider.get().asFile
            if (!file.exists()) throw GradleException("未找到 debug APK：${file.absolutePath}")
            ZipFile(file).use { zip ->
                val requiredNodeEntries = if (testMissingRuntimePackage == "opencc-js") {
                    requiredPackagedNodeRuntimeEntries.filterNot {
                        it.startsWith("assets/nodejs-project/node_modules/opencc-js/")
                    }
                } else {
                    requiredPackagedNodeRuntimeEntries
                }
                val requiredEntries = requiredNodeEntries +
                    requiredPackagedNativeRuntimeEntries(abi)
                val missing = requiredEntries.filter { zip.getEntry(it) == null }
                val forbiddenTestEntries = if (testMissingRuntimePackage == "opencc-js") {
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("assets/nodejs-project/node_modules/opencc-js/") }
                        .take(3)
                        .toList()
                } else {
                    emptyList()
                }
                val invalidNative = requiredPackagedNativeRuntimeEntries(abi).filter { entry ->
                    val size = zip.getEntry(entry)?.size ?: -1L
                    size <= 0L || (entry.endsWith("/libnode.so") && size < 10L * 1024L * 1024L)
                }
                if (missing.isNotEmpty() || invalidNative.isNotEmpty() || forbiddenTestEntries.isNotEmpty()) {
                    throw GradleException(
                        buildList {
                            if (missing.isNotEmpty()) add("缺少：${missing.joinToString(", ")}")
                            if (invalidNative.isNotEmpty()) add("内容异常：${invalidNative.joinToString(", ")}")
                            if (forbiddenTestEntries.isNotEmpty()) {
                                add("缺依赖测试包仍包含：${forbiddenTestEntries.joinToString(", ")}")
                            }
                        }.joinToString("；", prefix = "Debug APK（$abi）运行时校验失败：")
                    )
                }
            }
        }
    }
}

tasks.register("verifyPackagedNodeModulesRelease") {
    val apkFiles = configuredAbiFilters.map { abi ->
        layout.buildDirectory.file("outputs/apk/release/app-$abi-release.apk")
    }
    inputs.files(apkFiles)
    doLast {
        apkFiles.zip(configuredAbiFilters).forEach { (provider, abi) ->
            val file = provider.get().asFile
            if (!file.exists()) throw GradleException("未找到 release APK：${file.absolutePath}")
            ZipFile(file).use { zip ->
                val requiredEntries = requiredPackagedNodeRuntimeEntries +
                    requiredPackagedNativeRuntimeEntries(abi)
                val missing = requiredEntries.filter { zip.getEntry(it) == null }
                val invalidNative = requiredPackagedNativeRuntimeEntries(abi).filter { entry ->
                    val size = zip.getEntry(entry)?.size ?: -1L
                    size <= 0L || (entry.endsWith("/libnode.so") && size < 10L * 1024L * 1024L)
                }
                if (missing.isNotEmpty() || invalidNative.isNotEmpty()) {
                    throw GradleException(
                        buildList {
                            if (missing.isNotEmpty()) add("缺少：${missing.joinToString(", ")}")
                            if (invalidNative.isNotEmpty()) add("内容异常：${invalidNative.joinToString(", ")}")
                        }.joinToString("；", prefix = "Release APK（$abi）运行时校验失败：")
                    )
                }
            }
        }
    }
}

val verifyBundledNodeModulesTask = tasks.named("verifyBundledNodeModules")
val testBundledBrotliRuntimeTask = tasks.named("testBundledBrotliRuntime")
val testBundledNodeLockClosureTask = tasks.named("testBundledNodeLockClosure")
val testBundledCoreRuntimeDependenciesTask = tasks.named("testBundledCoreRuntimeDependencies")
val verifyEmbeddedNodeCompatibilityTask = tasks.named("verifyEmbeddedNodeCompatibility")

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.startsWith("lintAnalyze") ||
        (it.name.startsWith("generate") && it.name.endsWith("LintReportModel")) ||
        (it.name.startsWith("generate") && it.name.endsWith("LintVitalReportModel")) ||
        it.name.contains("lintVital", ignoreCase = true)
}.configureEach {
    dependsOn(verifyBundledNodeModulesTask)
    prepareMissingRuntimeTestAssetsTask?.let { dependsOn(it) }
}

val verifyPackagedNodeModulesReleaseTask = tasks.named("verifyPackagedNodeModulesRelease")
val verifyPackagedNodeModulesDebugTask = tasks.named("verifyPackagedNodeModulesDebug")
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(verifyPackagedNodeModulesDebugTask)
}
verifyPackagedNodeModulesDebugTask.configure {
    mustRunAfter("assembleDebug")
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyPackagedNodeModulesReleaseTask)
}
verifyPackagedNodeModulesReleaseTask.configure {
    mustRunAfter("assembleRelease")
}


// 构建前置门禁：Node 依赖闭包、锁文件闭包与核心运行时 smoke 必须先通过。
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(
        verifyBundledNodeModulesTask,
        testBundledBrotliRuntimeTask,
        testBundledNodeLockClosureTask,
        testBundledCoreRuntimeDependenciesTask,
        testDanmakuPrepareCacheTask,
        testFavoriteSchedulerHostTask,
    )
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyEmbeddedNodeCompatibilityTask)
}

