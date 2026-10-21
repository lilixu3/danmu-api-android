// WS8 首批功能模块提取试点：`:feature:records`。
// 请求记录功能（列表、筛选、统计洞察、详情展开与复制）。
// 只允许依赖 `:core:*` 与 `:ui:designsystem`，不得依赖 app 内部包
// （见 `scripts/refactoring/check_architecture.py` 的 MODULE_RULES）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.danmuapiapp.feature.records"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":ui:designsystem"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // `RequestRecordsViewModel` 是 `@HiltViewModel`，需要 Hilt 注解与编译器。
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // `hiltViewModel()` 来自 androidx.hilt（navigation 包会带上 lifecycle-viewmodel-compose）。
    implementation(libs.hilt.navigation.compose)

    // 功能模块自带 `RequestRecordInsights` 单测。
    testImplementation("junit:junit:4.13.2")
}
