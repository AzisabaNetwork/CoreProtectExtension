import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.azisaba"
version = "1.0.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))

    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
    maven {
        name = "azisaba-repo"
        url = uri("https://repo.azisaba.net/repository/maven-public/")
    }
    maven { url = uri("https://maven.playpro.com/") }
}

dependencies {
    implementation("com.charleskorn.kaml:kaml:0.55.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("xyz.acrylicstyle.java-util:common:2.0.0-SNAPSHOT")
    implementation("xyz.acrylicstyle.java-util:reflector:2.0.0-SNAPSHOT")
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("com.destroystokyo.paper:paper-api:1.15.2-R0.1-SNAPSHOT")
    compileOnly("net.coreprotect:coreprotect:22.2")
    testImplementation(kotlin("test"))
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        from(sourceSets.main.get().resources.srcDirs) {
            filter(ReplaceTokens::class, mapOf("tokens" to mapOf("version" to project.version.toString())))
            filteringCharset = "UTF-8"
        }
    }

    shadowJar {
        exclude("org/slf4j/**")
        relocate("kotlin", "net.azisaba.coreprotectextension.lib.kotlin")
        relocate("org.jetbrains", "net.azisaba.coreprotectextension.lib.org.jetbrains")
        relocate("com.charleskorn.kaml", "net.azisaba.coreprotectextension.lib.com.charleskorn.kaml")
        relocate("org.yaml", "net.azisaba.coreprotectextension.lib.org.yaml")
        relocate("org.snakeyaml", "net.azisaba.coreprotectextension.lib.org.snakeyaml")
        relocate("org.intellij", "net.azisaba.coreprotectextension.lib.org.intellij")
        relocate("xyz.acrylicstyle.util", "net.azisaba.coreprotectextension.lib.xyz.acrylicstyle.util")
    }
}

kotlin {
    jvmToolchain(8)
}
