pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/central") {
            content { includeGroup("org.eclipse.jgit") }
        }
        mavenCentral()
        maven("https://maven.aliyun.com/repository/jcenter")
    }
}

rootProject.name = "DanmuApiApp"
include(":app")
