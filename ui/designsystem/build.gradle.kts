// WS8 模块骨架：`:ui:designsystem`。
// 主题、颜色、排版、形状与共用 Compose 基础组件；只允许依赖 `:core:*`，不得依赖 `:app` 或 data 层。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.danmuapiapp.ui.designsystem"
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
    // `AppDialog` 通过 `data.util.DeviceCompatMode` 判断兼容界面设备。
    implementation(project(":core:common"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.kyant.shapes)
    implementation(libs.backdrop)
    implementation(libs.compose.ui.tooling.preview)

    // 组件与主题用到的 AndroidX / 图片 / Markdown 运行时依赖。
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil2)
    implementation(libs.markdown.renderer.code)

    // 主题与共用组件的单测随模块迁移，模块自带测试依赖。
    testImplementation("junit:junit:4.13.2")
}
