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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sora-editor / Termux artifacts (Fase 1–2) live on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AndroidKris IDE"

include(":app")
include(":editor")
include(":terminal")
include(":build-engine")
include(":lsp")
