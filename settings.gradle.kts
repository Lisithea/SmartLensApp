pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Añadir repositorio de JitPack para OpenCV
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SmartLens"
include(":app")