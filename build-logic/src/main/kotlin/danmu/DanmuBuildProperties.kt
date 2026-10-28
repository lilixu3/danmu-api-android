package danmu

import java.io.File
import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * app/build.gradle.kts 与 build-logic 各域插件共用的构建配置解析（WS7）。
 *
 * 这些值原先集中在 app/build.gradle.kts 顶部，被 `android {}` 与多个构建任务同时消费；
 * 逻辑迁到插件后必须保持单一来源，因此统一放在这里，模块脚本与插件都只调用这里。
 */

val defaultReleaseAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

fun Project.danmuEnableNativeBuild(): Boolean =
    (findProperty("enableNativeBuild") as? String)?.toBoolean() ?: false

fun Project.danmuIsTermuxHost(): Boolean = System.getenv("TERMUX_VERSION") != null ||
    (System.getenv("PREFIX")?.contains("com.termux") == true)

// 支持工作流通过 -PversionName/-PversionCode 覆盖版本
fun Project.danmuDefaultVersionName(): String = "1.0.5.117"

fun Project.danmuDefaultVersionCode(): Int = 214

fun Project.danmuVersionNameOverride(): String? =
    findProperty("versionName")?.toString()?.trim()?.takeIf { it.isNotEmpty() }

fun Project.danmuVersionCodeOverride(): Int? =
    findProperty("versionCode")?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 }

fun Project.danmuVersionName(): String = danmuVersionNameOverride() ?: danmuDefaultVersionName()

fun Project.danmuVersionCode(): Int = danmuVersionCodeOverride() ?: danmuDefaultVersionCode()

fun Project.danmuAbiFilters(): List<String> {
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
    return if (rawAbiFilters.isEmpty()) defaultReleaseAbis else rawAbiFilters
}

fun Project.danmuRequestedTaskNames(): List<String> = gradle.startParameter.taskNames.map { it.lowercase() }

fun Project.danmuIsBundleTaskRequested(): Boolean = danmuRequestedTaskNames().any { it.contains("bundle") }

fun Project.danmuEnableProguard(): Boolean? = danmuBooleanProperty("enableProguard")

fun Project.danmuShrinkResources(): Boolean? = danmuBooleanProperty("shrinkResources")

private fun Project.danmuBooleanProperty(name: String): Boolean? {
    val rawValue = (findProperty(name) as? String)?.trim().orEmpty()
    if (rawValue.isBlank()) return null
    return when (rawValue.lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("$name 仅支持 true/false，当前值: $rawValue")
    }
}

fun Project.danmuLocalProperties(): Properties = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun Project.danmuTestMissingRuntimePackage(): String? =
    (findProperty("testMissingRuntimePackage") as? String)
        ?.trim()
        ?.takeIf(String::isNotBlank)

fun Project.danmuPreparedNativeRuntimeDir(): File =
    layout.buildDirectory.dir("prepared-native-runtime").get().asFile

fun Project.danmuMissingRuntimeTestAssetsDir(): File =
    layout.buildDirectory.dir("test-missing-runtime-assets").get().asFile

fun Project.danmuNativeRuntimeChecksumFile(): File = file("native-runtime.sha256")

fun Project.danmuNativeRuntimeSourcesFile(): File = file("native-runtime-sources.properties")

fun Project.danmuNativeRuntimeArchiveOverride(): File? =
    findProperty("nativeRuntimeArchive")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(rootProject::file)

fun Project.danmuNativeRuntimeArchiveUrlOverride(): String? =
    findProperty("nativeRuntimeArchiveUrl")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.also {
        val uri = runCatching { URI(it) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "nativeRuntimeArchiveUrl 必须是无凭据的 HTTPS 地址；归档大小与 SHA-256 仍按仓库清单校验"
        }
    }

/**
 * 签名解析结果：store 文件、口令、别名与 key 口令只在同时可用时才成立，
 * 因此整体返回，`null` 表示本次构建不使用项目签名（保持原有回退行为）。
 */
data class DanmuSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

fun Project.danmuSigning(): DanmuSigning? {
    val localProps = danmuLocalProperties()
    val legacyProjectDir = listOf(
        System.getenv("DANMU_LEGACY_PROJECT_DIR")?.trim()?.takeIf { it.isNotEmpty() },
        (findProperty("legacyProjectDir") as? String)?.trim()?.takeIf { it.isNotEmpty() },
        localProps.getProperty("legacy.projectDir")?.trim()?.takeIf { it.isNotEmpty() }
    ).firstOrNull()?.let { file(it) }

    // 旧仓库仅作为签名兜底来源；不再硬编码机器特定路径，
    // 需要时通过环境变量 DANMU_LEGACY_PROJECT_DIR、
    // 属性 -PlegacyProjectDir 或 local.properties 的 legacy.projectDir 显式指定。
    val legacyLocalProps = Properties().apply {
        val propsFile = legacyProjectDir?.resolve("local.properties")
        if (propsFile != null && propsFile.exists()) {
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

    fun isUsableKeystore(file: File): Boolean = file.exists() && file.isFile && file.length() > 128L

    val configuredStorePath = resolveSigningValue(
        envKeys = listOf("ANDROID_KEYSTORE_PATH"),
        propKeys = listOf("keystore.path")
    )
    val defaultLegacyKeystore = legacyProjectDir?.resolve("danmuapi-ci.jks")
    val defaultPrimaryKeystore = rootProject.file("danmuapi-ci.jks")
    val fallbackKeystore = rootProject.file("keystore.jks")
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

    val storeFile = resolvedStoreFile ?: return null
    val storePassword = resolvedStorePassword?.takeIf { it.isNotBlank() } ?: return null
    val keyPassword = resolvedKeyPassword?.takeIf { it.isNotBlank() } ?: return null
    if (resolvedKeyAlias.isBlank()) return null
    return DanmuSigning(storeFile, storePassword, resolvedKeyAlias, keyPassword)
}
