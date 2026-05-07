plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "dev.oskaras"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2024.3")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("243.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    matching { it.name == "instrumentCode" || it.name == "instrumentedJar" }.configureEach {
        enabled = false
    }
}
