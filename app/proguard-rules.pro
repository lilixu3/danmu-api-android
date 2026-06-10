-keepclassmembers class com.example.danmuapiapp.NodeBridge {
    native <methods>;
}

-keep class com.example.danmuapiapp.NodeBridge { *; }

# Root 模式入口：由 app_process 直接反射调用，必须保留类名与 main
-keep class com.example.danmuapiapp.data.service.RootNodeEntry { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# LSPosed / libxposed 入口由 META-INF/xposed/java_init.list 反射加载，R8 不能移除或改名。
-keep class com.example.danmuapiapp.xposed.DanmuXposedModule { *; }
-dontwarn io.github.libxposed.**
