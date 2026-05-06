plugins {
    kotlin("jvm") version "2.0.21"
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "org.j2keval"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.javaparser:javaparser-core:3.26.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi:1.15.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "eval.MainKt"
    }
    archiveClassifier.set("all")
}