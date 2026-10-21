// WS8 模块骨架：`:core:model`。
// 只承载不依赖 Android/UI/data 的领域模型与纯逻辑；本模块不得依赖任何其他项目模块。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.danmuapiapp.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.serialization.json)

    // 与 app 模块一致，模块自带领域模型单测。
    testImplementation("junit:junit:4.13.2")
}
