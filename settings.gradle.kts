pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Alternate official Central hostname; all artifacts still undergo strict SHA-256 verification.
        maven { name = "CentralAlternate"; url = uri("https://repo1.maven.org/maven2/") }
    }
    // These plugins are published on Maven Central. Resolve the already pinned
    // implementation modules directly, not repository-specific marker POMs.
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.kapt" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version ?: "2.1.21"}")
                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version ?: "2.1.21"}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral()
        // Alternate official Central hostname; all artifacts still undergo strict SHA-256 verification.
        maven { name = "CentralAlternate"; url = uri("https://repo1.maven.org/maven2/") } }
}
rootProject.name = "SignetVPN"
include(":app", ":core")
