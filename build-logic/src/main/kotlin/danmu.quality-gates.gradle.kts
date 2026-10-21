// WS7：由 app/build.gradle.kts 迁出的重构护栏、发布防呆与发布校验入口。
import danmu.danmuDefaultVersionCode
import danmu.danmuDefaultVersionName
import danmu.danmuRequestedTaskNames
import danmu.danmuVersionCodeOverride
import danmu.danmuVersionNameOverride

val requestedTaskNames = danmuRequestedTaskNames()
val defaultVersionName = danmuDefaultVersionName()
val defaultVersionCode = danmuDefaultVersionCode()
val isExplicitVersionName = danmuVersionNameOverride() != null
val isExplicitVersionCode = danmuVersionCodeOverride() != null


tasks.register<Exec>("checkNodeRuntimeScripts") {
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/refactoring/check_runtime_js_syntax.py")
}

tasks.register<Exec>("checkRefactorArchitecture") {
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/refactoring/check_architecture.py")
}

tasks.register<Exec>("checkRuntimeAssetManifest") {
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/refactoring/runtime_assets.py")
}

tasks.register<Exec>("auditRuntimeHardcoding") {
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/refactoring/audit_runtime_hardcoding.py",
        "--fail-on-findings"
    )
}

tasks.register<Exec>("testRefactorGuardrails") {
    workingDir = rootProject.projectDir
    environment("PYTHONPATH", "scripts/refactoring")
    commandLine(
        "python3",
        "-m",
        "unittest",
        "discover",
        "-s",
        "scripts/refactoring",
        "-p",
        "test_*.py"
    )
}

tasks.register("checkRefactorGuardrails") {
    dependsOn(
        "checkRefactorArchitecture",
        "checkRuntimeAssetManifest",
        "auditRuntimeHardcoding",
        "testRefactorGuardrails"
    )
}

// WS9：常规验证一次执行——护栏、各模块单测、Node 回归与应用单测。
tasks.register("verifyAll") {
    dependsOn(
        "checkRefactorGuardrails",
        "checkNodeRuntimeScripts",
        "testAppOutboundBridge",
        ":core:model:testDebugUnitTest",
        ":core:common:assembleDebug",
        ":ui:designsystem:testDebugUnitTest",
        ":feature:records:testDebugUnitTest",
        "testDebugUnitTest"
    )
}

// 发布防呆：release/bundle 任务未显式传 -PversionName/-PversionCode 时给出醒目告警，
// 避免工作流漏传参数后静默产出旧版本号的产物。
gradle.projectsEvaluated {
    val isReleaseLike = requestedTaskNames.any { it.contains("release") || it.contains("bundle") }
    if (isReleaseLike && (!isExplicitVersionName || !isExplicitVersionCode)) {
        logger.warn(
            "⚠️⚠️⚠️ 当前构建包含 release/bundle 任务，但未显式指定版本：\n" +
                "   versionName=${if (isExplicitVersionName) "(显式指定)" else "$defaultVersionName (内置默认值!)"}\n" +
                "   versionCode=${if (isExplicitVersionCode) "(显式指定)" else "$defaultVersionCode (内置默认值!)"}\n" +
                "   如需发布，请通过 -PversionName=... -PversionCode=... 显式传入。"
        )
    }
}

// Enhanced outbound uses Node TLS/QUIC and Android DoH; no executable helper.
val testAppOutboundTask = tasks.register<Exec>("testAppOutboundBridge") {
    workingDir(project.rootDir)
    commandLine("node", "--test", "node-tests/app-proxy-routing.test.cjs", "node-tests/app-proxy-host.test.cjs", "node-tests/app-source-probe.test.cjs", "node-tests/app-outbound-native.test.cjs", "node-tests/app-outbound-dns.test.cjs", "node-tests/app-outbound-bridge.test.cjs", "node-tests/app-outbound-runtime.test.cjs", "node-tests/app-outbound-host.test.cjs", "node-tests/app-management.test.cjs", "node-tests/app-management-host.test.cjs", "node-tests/security-discovery-host.test.cjs", "node-tests/app-series-refresh.test.cjs", "node-tests/app-url-metadata.test.cjs", "node-tests/runtime-data-context.test.cjs", "node-tests/server-logging.test.cjs", "node-tests/access-control.test.cjs", "node-tests/worker-runtime.test.cjs", "node-tests/config-runtime.test.cjs", "node-tests/danmaku-cache.test.cjs", "node-tests/proxy-helper.test.cjs", "node-tests/http-routes.test.cjs")
}


tasks.register("releaseCheck") {
    dependsOn("assembleRelease")
    dependsOn("verifyEmbeddedNodeCompatibility")
    dependsOn("verifyPackagedNodeModulesRelease")
}



// 构建前置门禁：出站桥接的 Node 回归测试必须先通过。
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("testAppOutboundBridge")
}

