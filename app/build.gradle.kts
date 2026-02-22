import java.util.Properties
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
    ?: "1.0.4.8"
val configuredVersionCode = findProperty("versionCode")
    ?.toString()
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: 79
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.danmuapiapp"
        minSdk = 23
        targetSdk = 36
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

    // Image loading
    implementation(libs.coil.compose)

    // 提供 XML 主题 Theme.Material3.DayNight.NoActionBar
    implementation("com.google.android.material:material:1.13.0")

    // QR Code
    implementation(libs.zxing.core)

    // Java 8+ API（如 java.time）向低版本兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)

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

// 按 node_modules.zip 同步依赖，避免旧项目残留文件长期混入
tasks.register("prepareNodeModules") {
    val zipFile = rootProject.file("node_modules.zip")
    val targetDir = file("src/main/assets/nodejs-project/node_modules")
    if (zipFile.exists()) {
        inputs.file(zipFile)
    }
    outputs.dir(targetDir)
    dependsOn("cleanupGarbageFiles")
    doLast {
        if (!zipFile.exists()) return@doLast
        delete(targetDir)
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
                        path = normalized
                    }
                }
            }
            into(targetDir)
        }
    }
}

val prepareNodeModulesTask = tasks.named("prepareNodeModules")
tasks.named("preBuild").configure {
    dependsOn(prepareNodeModulesTask)
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.startsWith("lintAnalyze") ||
        (it.name.startsWith("generate") && it.name.endsWith("LintReportModel")) ||
        (it.name.startsWith("generate") && it.name.endsWith("LintVitalReportModel")) ||
        it.name.contains("lintVital", ignoreCase = true)
}.configureEach {
    dependsOn(prepareNodeModulesTask)
}

// Termux 下预裁剪 JNI so，避免 AGP strip 工具链与宿主架构不兼容导致体积异常
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

            val stripPathCandidates = listOf(
                System.getenv("LLVM_STRIP")?.trim().orEmpty(),
                (System.getenv("PREFIX")?.trim().orEmpty() + "/bin/llvm-strip").trim(),
                "/data/data/com.termux/files/usr/bin/llvm-strip",
                "llvm-strip"
            ).filter { it.isNotBlank() }

            val stripTool = stripPathCandidates.firstOrNull { candidate ->
                if (candidate.contains('/')) File(candidate).exists() else true
            } ?: "llvm-strip"

            val soFiles = outRoot.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .toList()
            soFiles.forEach { so ->
                runCatching {
                    val process = ProcessBuilder(
                        stripTool,
                        "--strip-unneeded",
                        so.absolutePath
                    ).redirectErrorStream(true).start()
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val exit = process.waitFor()
                    if (exit != 0) {
                        throw GradleException("strip exit=$exit")
                    }
                }.onFailure {
                    println("警告：预裁剪失败 ${so.absolutePath} -> ${it.message}")
                }
            }
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
