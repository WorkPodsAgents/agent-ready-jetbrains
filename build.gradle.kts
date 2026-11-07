plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "ai.workpods"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Built against the oldest IDE we support, so the plugin uses no newer API.
        intellijIdeaCommunity("2025.1")
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            // Open-ended: the plugin uses only stable platform API.
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }
}

tasks.processResources {
    // The plugin reads its version from this file to name itself to the server, without an
    // internal IDE API.
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("agent-ready.properties") {
        expand("pluginVersion" to pluginVersion)
    }
}
