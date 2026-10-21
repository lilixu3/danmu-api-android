import danmu.danmuAbiFilters
import danmu.danmuEnableProguard
import danmu.danmuIsBundleTaskRequested
import danmu.danmuIsTermuxHost
import danmu.danmuMissingRuntimeTestAssetsDir
import danmu.danmuPreparedNativeRuntimeDir
import danmu.danmuShrinkResources
import danmu.danmuSigning
import danmu.danmuTestMissingRuntimePackage
import danmu.danmuVersionCode
import danmu.danmuVersionName

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("danmu.runtime-assets")
    id("danmu.native-runtime")
    id("danmu.quality-gates")
}

// 构建配置解析的唯一来源在 build-logic 的 DanmuBuildProperties.kt；
// 这里只把解析结果接到 android {} 上。
val enableNativeBuild = (findProperty("enableNativeBuild") as? String)?.toBoolean() ?: false
val isTermuxHost = danmuIsTermuxHost()
val configuredVersionName = danmuVersionName()
val configuredVersionCode = danmuVersionCode()
val configuredAbiFilters = danmuAbiFilters()
val configuredEnableProguard = danmuEnableProguard()
val configuredShrinkResources = danmuShrinkResources()
val isBundleTaskRequested = danmuIsBundleTaskRequested()
val preparedNativeRuntimeDir = danmuPreparedNativeRuntimeDir()
val testMissingRuntimePackage = danmuTestMissingRuntimePackage()
val missingRuntimeTestAssetsDir = danmuMissingRuntimeTestAssetsDir()
val signing = danmuSigning()
val useProjectSigning = signing != null


android {
    namespace = "com.example.danmuapiapp"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.example.danmuapiapp"
        minSdk = 24
        targetSdk = 37
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    signingConfigs {
        create("projectSign") {
            signing?.let {
                storeFile = it.storeFile
                storePassword = it.storePassword
                keyAlias = it.keyAlias
                keyPassword = it.keyPassword
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
            isShrinkResources = (configuredShrinkResources ?: true) && minifyEnabled
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
            if (testMissingRuntimePackage == "opencc-js") {
                assets.directories.clear()
                assets.directories.add(missingRuntimeTestAssetsDir.absolutePath)
            }
            val jniDirs = mutableListOf(File(preparedNativeRuntimeDir, "libnode/bin").absolutePath)
            if (!enableNativeBuild) {
                // 使用当前包名重新编译的 JNI 桥接库，避免旧符号导致 UnsatisfiedLinkError。
                jniDirs += File(preparedNativeRuntimeDir, "jni-current").absolutePath
            }
            // 内网穿透内核：普通模式要从 nativeLibraryDir 直接 exec libfrpc.so
            // （useLegacyPackaging=true 会在安装时解压到 lib 目录）。
            jniDirs += File(project.rootDir, "runtime/frp").absolutePath
            jniLibs.directories.clear()
            jniLibs.directories.addAll(jniDirs)
        }
    }

}

kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
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
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
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

    // PR 实验室使用成熟的纯 Java Git 实现执行本地三方合并。
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")
    implementation("org.slf4j:slf4j-nop:1.7.36")

    // LSPosed / libxposed API 102 目标：minApiVersion 仍为 101，运行时通过能力检测兼容 101+。
    // service 用于模块 App 侧按官方 XposedServiceHelper 获取真实 API/作用域/RemotePreferences。
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    // Image loading
    implementation(libs.coil.compose)

    // Compose 原生 GFM：PR 说明、公告与更新日志共用同一渲染器。
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil2)
    implementation(libs.markdown.renderer.code)

    // HTML fallback for GitHub releases: structure and asset links, independent of CSS classes.
    implementation("org.jsoup:jsoup:1.23.2")

    // 提供 XML 主题 Theme.Material3.DayNight.NoActionBar
    implementation(libs.material)

    // QR Code
    implementation(libs.zxing.core)

    // Java 8+ API（如 java.time）向低版本兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

}
