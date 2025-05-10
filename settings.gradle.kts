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
        // Repositorio para QuickBird OpenCV
        maven { url = uri("https://raw.githubusercontent.com/QuickBirdEng/opencv-android/master") }
        // Repositorio para alternativas de OpenCV
        maven { url = uri("https://maven.pkg.github.com/hadilq/opencv-android") }
        maven { url = uri("https://repo1.maven.org/maven2/") }
        // Opcional: para algunas dependencias
        maven { url = uri("https://jitpack.io") }
    }
}

// Excluir el módulo :opencv y :sdk del proyecto principal
// Esto es importante ya que parece que estás intentando incluirlos
rootProject.name = "SmartLens"
include(":app")