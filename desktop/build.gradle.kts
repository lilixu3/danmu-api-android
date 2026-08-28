import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.desktop)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

group = "com.example"
version = "0.1.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.example.danmuapiapp.desktop.DesktopMainKt"

        nativeDistributions {
            // 按用户决策交付双形态：直装版（jpackage EXE 安装器）+ 免安装版
            // （packagePortableZip，由应用镜像压缩而成，解压即用）。
            // 仅支持 x64：Node.js 官方自 19 起不再提供 32 位 Windows 二进制。
            targetFormats(TargetFormat.Exe)
            packageName = "DanmuApiDesktop"
            packageVersion = "0.1.0"
            description = "弹幕 API Windows 桌面端"
            vendor = "danmu-api-android"
            modules(
                "java.desktop",
                "java.logging",
                "java.management",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "java.net.http",
            )

            windows {
                // 与 Android 启动图标同源（由 gen-icon 脚本从矢量 drawable 渲染生成）
                iconFile.set(rootProject.file("desktop/icons/danmuapi.ico"))
            }

        // W-0004：随包运行资源走 JVM classpath 资源（见下方 prepareDesktopAppResources），
        // 由 jpackage --input 的应用 jar 自动带入所有产物；首启由宿主解压到可写数据目录。
        // 不使用 compose 的 appResourcesRootDir（1.12 实测 createDistributable 与
        // jpackage --resource-dir 均不落地）。
        }
    }
}

// W-0004：汇集随包分发的运行资源，作为 JVM classpath 资源打进应用 jar。
// node.exe 路径通过 -PdanmuNodeExe 显式传入，不在仓库中硬编码个人路径。
val configuredNodeExe = providers.gradleProperty("danmuNodeExe").orNull?.let { file(it) }
val desktopRuntimeResources = layout.buildDirectory.dir("generated/desktop-runtime-resources")

val prepareDesktopAppResources = tasks.register<Sync>("prepareDesktopAppResources") {
    into(desktopRuntimeResources)
    from(rootProject.file("app/src/main/assets/nodejs-project")) {
        into("runtime/nodejs-project")
    }
    if (configuredNodeExe != null) {
        from(configuredNodeExe) { into("runtime") }
        val nodeLicense = configuredNodeExe.parentFile.resolve("LICENSE")
        if (nodeLicense.isFile) {
            from(nodeLicense) { into("runtime") }
        }
    } else {
        doLast {
            logger.warn(
                "⚠ 未提供 -PdanmuNodeExe=<node.exe 绝对路径>，随包资源缺少 node.exe；" +
                    "该产物仅适合 UI 开发，不能启动 Node 运行时。"
            )
        }
    }
    doLast {
        // 生成随包资源清单，供运行期 ClasspathRuntimeExtractor 首启解压
        val dest = desktopRuntimeResources.get().asFile
        val manifest = dest.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dest).invariantSeparatorsPath.replace('\\', '/') }
            .filterNot { it == "runtime-manifest.txt" }
            .sorted()
            .joinToString("\n")
        File(dest, "runtime-manifest.txt").writeText(manifest + "\n")
    }
}

sourceSets.named("main") {
    resources.srcDir(desktopRuntimeResources)
}

tasks.matching { it.name == "processResources" }.configureEach {
    dependsOn(prepareDesktopAppResources)
}

// 免安装版：压缩应用镜像目录，解压后直接运行 DanmuApiDesktop.exe。
val packagePortableZip = tasks.register<Zip>("packagePortableZip") {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveBaseName.set("DanmuApiDesktop")
    archiveClassifier.set("portable-x64")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
}

tasks.test {
    // W-0003 集成测试需要真实 node.exe；通过属性或环境变量提供，缺失时相关用例自动跳过。
    systemProperty(
        "danmu.desktop.nodeExe",
        providers.gradleProperty("danmuNodeExe")
            .orElse(providers.environmentVariable("DANMU_DESKTOP_NODE_EXE"))
            .getOrElse("")
    )
    systemProperty(
        "danmu.desktop.runtimeSource",
        rootProject.file("app/src/main/assets/nodejs-project").absolutePath
    )
    systemProperty(
        "danmu.desktop.longSmoke",
        providers.gradleProperty("desktopLongSmoke").getOrElse("false")
    )
    testLogging {
        events("failed", "skipped")
    }
}
