// WS8 模块骨架：`:core:common`。
// 跨功能共享的工具（格式化、比较、字符串/路径处理、设备兼容判定）。
// 允许使用 Android API（`DeviceCompatMode` 需要 Context/PackageManager），但只允许依赖 `:core:model`，
// 且不得导入 app 侧包（见 `scripts/refactoring/check_architecture.py` 的 MODULE_RULES）。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.danmuapiapp.core.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 与 app 一致：`data/util` 里的时间解析用到 java.time，需要脱糖。
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    api(project(":core:model"))
    // `DeviceCompatMode` 使用 androidx.core 的 SharedPreferences `edit {}` 扩展。
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)

    // Java 8+ API（如 java.time）向低版本兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
