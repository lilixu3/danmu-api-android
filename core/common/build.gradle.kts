// WS8 模块骨架：`:core:common`。
// 跨功能共享的纯粹工具（格式化、比较、字符串/路径处理）；只允许依赖 `:core:model`。
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
    implementation(libs.coroutines.android)

    // Java 8+ API（如 java.time）向低版本兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
