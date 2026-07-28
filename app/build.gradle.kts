import java.util.Properties
import java.util.zip.ZipFile
import java.security.MessageDigest
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val enableNativeBuild = (findProperty("enableNativeBuild") as? String)?.toBoolean() ?: false
val isTermuxHost = System.getenv("TERMUX_VERSION") != null ||
    (System.getenv("PREFIX")?.contains("com.termux") == true)
// 支持工作流通过 -PversionName/-PversionCode 覆盖版本
val configuredVersionName = findProperty("versionName")
    ?.toString()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "1.0.5.68"
val configuredVersionCode = findProperty("versionCode")
    ?.toString()
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: 154
val defaultReleaseAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val rawAbiFilters = (findProperty("abiFilters") as? String)
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    .orEmpty()
val unsupportedAbiFilters = rawAbiFilters.filterNot { it in defaultReleaseAbis }
if (unsupportedAbiFilters.isNotEmpty()) {
    throw GradleException(
        "不支持的 abiFilters: ${unsupportedAbiFilters.joinToString(",")}，仅支持: ${defaultReleaseAbis.joinToString(",")}"
    )
}
val configuredAbiFilters = if (rawAbiFilters.isEmpty()) defaultReleaseAbis else rawAbiFilters
val requestedTaskNames = gradle.startParameter.taskNames.map { it.lowercase() }
val isBundleTaskRequested = requestedTaskNames.any { it.contains("bundle") }

fun parseBooleanProperty(name: String): Boolean? {
    val rawValue = (findProperty(name) as? String)?.trim().orEmpty()
    if (rawValue.isBlank()) return null
    return when (rawValue.lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("$name 仅支持 true/false，当前值: $rawValue")
    }
}

val configuredEnableProguard = parseBooleanProperty("enableProguard")
val configuredShrinkResources = parseBooleanProperty("shrinkResources")
val defaultShrinkResources = true

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

val legacyProjectDir = file("/data/user/0/com.termux/files/home/danmu-api-android-main")
val legacyLocalProps = Properties().apply {
    val propsFile = legacyProjectDir.resolve("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun resolveSigningValue(envKeys: List<String>, propKeys: List<String>): String? {
    envKeys.forEach { key ->
        val value = System.getenv(key)?.trim().orEmpty()
        if (value.isNotBlank()) return value
    }
    propKeys.forEach { key ->
        val gradleValue = (findProperty(key) as? String)?.trim().orEmpty()
        if (gradleValue.isNotBlank()) return gradleValue
        val localValue = localProps.getProperty(key)?.trim().orEmpty()
        if (localValue.isNotBlank()) return localValue
        val legacyValue = legacyLocalProps.getProperty(key)?.trim().orEmpty()
        if (legacyValue.isNotBlank()) return legacyValue
    }
    return null
}

fun isUsableKeystore(file: java.io.File): Boolean {
    return file.exists() && file.isFile && file.length() > 128L
}

val defaultLegacyKeystore = legacyProjectDir.resolve("danmuapi-ci.jks")
val defaultPrimaryKeystore = rootProject.file("danmuapi-ci.jks")
val fallbackKeystore = rootProject.file("keystore.jks")
val configuredStorePath = resolveSigningValue(
    envKeys = listOf("ANDROID_KEYSTORE_PATH"),
    propKeys = listOf("keystore.path")
)
val resolvedStoreFile = sequenceOf(
    configuredStorePath?.let { file(it) },
    defaultLegacyKeystore,
    defaultPrimaryKeystore,
    fallbackKeystore
).filterNotNull().firstOrNull { isUsableKeystore(it) }
val resolvedStorePassword = resolveSigningValue(
    envKeys = listOf("ANDROID_KEYSTORE_PASSWORD", "KS_PASS"),
    propKeys = listOf("keystore.password", "KS_PASS")
)
val resolvedKeyAlias = resolveSigningValue(
    envKeys = listOf("ANDROID_KEY_ALIAS", "KEY_ALIAS"),
    propKeys = listOf("key.alias", "KEY_ALIAS")
) ?: "danmuapi"
val resolvedKeyPassword = resolveSigningValue(
    envKeys = listOf("ANDROID_KEY_PASSWORD", "KEY_PASS"),
    propKeys = listOf("key.password", "KEY_PASS")
) ?: resolvedStorePassword
val useProjectSigning = resolvedStoreFile != null &&
    !resolvedStorePassword.isNullOrBlank() &&
    resolvedKeyAlias.isNotBlank() &&
    !resolvedKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.danmuapiapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.danmuapiapp"
        minSdk = 23
        targetSdk = 37
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    signingConfigs {
        create("projectSign") {
            if (useProjectSigning) {
                storeFile = resolvedStoreFile
                storePassword = resolvedStorePassword!!
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            if (useProjectSigning) {
                signingConfig = signingConfigs.getByName("projectSign")
            }
        }
        release {
            val minifyEnabled = configuredEnableProguard ?: true
            isMinifyEnabled = minifyEnabled
            // 资源压缩依赖 R8，禁用混淆时自动关闭资源压缩。
            isShrinkResources = (configuredShrinkResources ?: defaultShrinkResources) && minifyEnabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useProjectSigning) {
                signingConfig = signingConfigs.getByName("projectSign")
            }
        }
    }

    splits {
        abi {
            // AAB 不走本地 ABI split，避免 shrinkResources 与 split 的已知冲突。
            isEnable = !isBundleTaskRequested
            reset()
            if (isEnable) {
                include(*configuredAbiFilters.toTypedArray())
            }
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // 维持历史打包方式，减小 APK 体积
            useLegacyPackaging = true
            // Termux 下跳过 AGP 的 strip（其依赖的宿主工具不可用），改由自定义任务预裁剪
            if (isTermuxHost) {
                keepDebugSymbols += "**/*.so"
            }
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // LSPosed API 101+ 现代模块入口文件位于 META-INF/xposed/*，必须随 APK 打包。
            merges += "META-INF/xposed/*"
        }
    }

    // CMake 原生构建可按需开启，默认关闭以兼容 Termux 本机构建
    if (enableNativeBuild) {
        externalNativeBuild {
            cmake {
                path = file("CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    sourceSets {
        getByName("main") {
            if (isTermuxHost) {
                val termuxJniDirs = mutableListOf("${layout.buildDirectory.get().asFile}/termux-jni-libs/libnode/bin")
                if (!enableNativeBuild) {
                    termuxJniDirs += "${layout.buildDirectory.get().asFile}/termux-jni-libs/jni-current"
                }
                jniLibs.directories.clear()
                jniLibs.directories.addAll(termuxJniDirs)
            } else {
                val jniDirs = mutableListOf("libnode/bin")
                if (!enableNativeBuild) {
                    // 使用当前包名重新编译的 JNI 桥接库，避免旧符号导致 UnsatisfiedLinkError
                    jniDirs += "jni-current"
                }
                jniLibs.directories.clear()
                jniLibs.directories.addAll(jniDirs)
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.splashscreen)
    implementation(libs.datastore.prefs)
    implementation(libs.documentfile)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // Network
    implementation(libs.okhttp)

    // LSPosed / libxposed API 102 目标：minApiVersion 仍为 101，运行时通过能力检测兼容 101+。
    // service 用于模块 App 侧按官方 XposedServiceHelper 获取真实 API/作用域/RemotePreferences。
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    // Image loading
    implementation(libs.coil.compose)

    // 提供 XML 主题 Theme.Material3.DayNight.NoActionBar
    implementation(libs.material)

    // QR Code
    implementation(libs.zxing.core)

    // Java 8+ API（如 java.time）向低版本兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

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
    "drizzle-orm"
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
        "pako/dist/pako_inflate.min.js"
    )

    rootDir.walkTopDown()
        .filter { it.isFile }
        .forEach { file ->
            val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
            val shouldDelete =
                relativePath.endsWith(".map") ||
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

tasks.register<Exec>("checkNodeRuntimeScripts") {
    commandLine("node", "--check", "src/main/assets/nodejs-project/android-server.js")
}

tasks.register<Exec>("testNodeRuntimeParsing") {
    workingDir = rootProject.projectDir
    commandLine("node", "node-tests/parse-dotenv-regression.js")
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

val embeddedNodeVersion = "18.20.4"
val targetNodeExecutable = (findProperty("targetNodeExecutable") as? String)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("DANMU_TARGET_NODE")?.trim()?.takeIf { it.isNotBlank() }
    ?: "node"

tasks.register("verifyEmbeddedNodeCompatibility") {
    val smokeScripts = listOf(
        "node-tests/parse-dotenv-regression.js",
        "node-tests/brotli-runtime-smoke.mjs",
        "node-tests/bundled-node-lock-closure-smoke.mjs",
        "node-tests/core-runtime-dependencies-smoke.mjs"
    )
    dependsOn("verifyBundledNodeModules")
    inputs.files(smokeScripts.map(rootProject::file))
    inputs.file("src/main/assets/nodejs-project/android-server.js")
    inputs.property("targetNodeExecutable", targetNodeExecutable)
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

        val actualVersion = runTargetNode(listOf("--version")).removePrefix("v")
        if (actualVersion != embeddedNodeVersion) {
            throw GradleException(
                "Release 必须使用与内嵌运行时一致的 Node $embeddedNodeVersion 执行 smoke，当前为 $actualVersion（$targetNodeExecutable）"
            )
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

tasks.register("verifyPackagedNodeModulesDebug") {
    val apkFile = layout.buildDirectory.file("outputs/apk/debug/app-arm64-v8a-debug.apk")
    dependsOn("assembleDebug")
    inputs.file(apkFile)
    doLast {
        val file = apkFile.get().asFile
        if (!file.exists()) throw GradleException("未找到 debug APK：${file.absolutePath}")
        ZipFile(file).use { zip ->
            val missing = requiredPackagedNodeRuntimeEntries.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) {
                throw GradleException("Debug APK 缺少关键依赖文件：${missing.joinToString(", ")}")
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
                val requiredEntries = requiredPackagedNodeRuntimeEntries + listOf(
                    "lib/$abi/libnode.so",
                    "lib/$abi/libnative-lib.so"
                )
                val missing = requiredEntries.filter { zip.getEntry(it) == null }
                if (missing.isNotEmpty()) {
                    throw GradleException(
                        "Release APK（$abi）缺少关键运行时文件：${missing.joinToString(", ")}"
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
val nativeRuntimeChecksumFile = file("native-runtime.sha256")
val verifyNativeRuntimeInputsTask = tasks.register("verifyNativeRuntimeInputs") {
    inputs.file(nativeRuntimeChecksumFile)
    doLast {
        if (!nativeRuntimeChecksumFile.isFile) {
            throw GradleException("缺少原生运行时校验清单：${nativeRuntimeChecksumFile.absolutePath}")
        }
        val malformed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val mismatched = mutableListOf<String>()
        nativeRuntimeChecksumFile.readLines(Charsets.UTF_8).forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith('#')) return@forEachIndexed
            val separator = line.indexOf("  ")
            if (separator <= 0) {
                malformed += "第 ${index + 1} 行"
                return@forEachIndexed
            }
            val expected = line.substring(0, separator).trim().lowercase()
            val relativePath = line.substring(separator + 2).trim()
            if (!expected.matches(Regex("[0-9a-f]{64}")) ||
                relativePath.isBlank() || relativePath.startsWith('/') || ".." in relativePath.split('/')
            ) {
                malformed += "第 ${index + 1} 行"
                return@forEachIndexed
            }
            val runtimeFile = file(relativePath)
            if (!runtimeFile.isFile) {
                missing += relativePath
                return@forEachIndexed
            }
            val digest = MessageDigest.getInstance("SHA-256")
            runtimeFile.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            if (actual != expected) mismatched += relativePath
        }
        if (malformed.isNotEmpty() || missing.isNotEmpty() || mismatched.isNotEmpty()) {
            throw GradleException(
                buildList {
                    if (malformed.isNotEmpty()) add("格式错误：${malformed.joinToString()}")
                    if (missing.isNotEmpty()) add("缺少文件：${missing.joinToString()}")
                    if (mismatched.isNotEmpty()) add("哈希不匹配：${mismatched.joinToString()}")
                }.joinToString("；", prefix = "原生运行时输入校验失败：")
            )
        }
    }
}
tasks.named("preBuild").configure {
    dependsOn(verifyNativeRuntimeInputsTask)
    dependsOn(verifyBundledNodeModulesTask)
    dependsOn(testBundledBrotliRuntimeTask)
    dependsOn(testBundledNodeLockClosureTask)
    dependsOn(testBundledCoreRuntimeDependenciesTask)
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.startsWith("lintAnalyze") ||
        (it.name.startsWith("generate") && it.name.endsWith("LintReportModel")) ||
        (it.name.startsWith("generate") && it.name.endsWith("LintVitalReportModel")) ||
        it.name.contains("lintVital", ignoreCase = true)
}.configureEach {
    dependsOn(verifyBundledNodeModulesTask)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyEmbeddedNodeCompatibilityTask)
}

val verifyPackagedNodeModulesReleaseTask = tasks.named("verifyPackagedNodeModulesRelease")
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyPackagedNodeModulesReleaseTask)
}
verifyPackagedNodeModulesReleaseTask.configure {
    mustRunAfter("assembleRelease")
}
tasks.register("releaseCheck") {
    dependsOn("assembleRelease")
    dependsOn(verifyEmbeddedNodeCompatibilityTask)
    dependsOn(verifyPackagedNodeModulesReleaseTask)
}

// Termux 下复制已固定哈希的 JNI 输入，绕过宿主架构不兼容的 AGP strip 工具链。
tasks.register("prepareTermuxJniLibs") {
    if (isTermuxHost) {
        val outRoot = layout.buildDirectory.dir("termux-jni-libs").get().asFile
        outputs.dir(outRoot)
        doLast {
            delete(outRoot)

            val outLibnode = File(outRoot, "libnode/bin")
            copy {
                from(file("libnode/bin"))
                into(outLibnode)
            }
            if (!enableNativeBuild) {
                val outJniCurrent = File(outRoot, "jni-current")
                copy {
                    from(file("jni-current"))
                    into(outJniCurrent)
                }
            }

            // 输入库已预裁剪并由 SHA-256 清单固定。这里仅复制，避免宿主 llvm-strip
            // 版本差异改变同一提交的 APK 内容。
        }
    }
}

tasks.matching {
    (it.name.startsWith("merge") && (it.name.endsWith("JniLibFolders") || it.name.endsWith("NativeLibs"))) ||
        (it.name.startsWith("strip") && it.name.endsWith("DebugSymbols"))
}.configureEach {
    if (isTermuxHost) {
        dependsOn("prepareTermuxJniLibs")
    }
}
