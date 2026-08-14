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

# JGit includes optional desktop-only JMX, process inspection, and SPNEGO paths.
# The PR lab uses only anonymous HTTPS clone/fetch and local repository APIs.
-keep class org.eclipse.jgit.** extends org.eclipse.jgit.nls.TranslationBundle {
    public <init>();
    public java.lang.String *;
}
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# LSPosed / libxposed 入口由 META-INF/xposed/java_init.list 反射加载，R8 不能移除或改名。
-keep class com.example.danmuapiapp.xposed.DanmuXposedModule { *; }
-dontwarn io.github.libxposed.**
