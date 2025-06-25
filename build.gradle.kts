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
    maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.azisaba.net/repository/maven-public/")
    maven("https://maven.playpro.com/")
}

dependencies {
    implementation("com.charleskorn.kaml:kaml:0.55.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("xyz.acrylicstyle.java-util:common:2.0.0-SNAPSHOT")
    implementation("xyz.acrylicstyle.java-util:reflector:2.0.0-SNAPSHOT")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
//    implementation("com.zaxxer:HikariCP:4.0.3")
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("org.spigotmc:spigot-api:1.15.2-R0.1-SNAPSHOT")
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
        relocate("org.mariadb", "net.azisaba.coreprotectextension.lib.org.mariadb")
//        relocate("com.zaxxer", "net.azisaba.coreprotectextension.lib.com.zaxxer")
    }
}

kotlin {
    jvmToolchain(8)
}
