rootProject.name = "NijatvaMusicPlayer"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        // nijatva-core's signature kit, published by publish.yml on a release tag.
        maven {
            name = "NijatvaGitHubPackages"
            url = uri("https://maven.pkg.github.com/nijatva/nijatva-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":composeApp")
