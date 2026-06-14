buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.9.1")
    }
}

import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.gradleup.shadow") version "9.4.1"
    id("java")
    id("idea")
}

val pluginVersion: String by project

group = "org.lovelycheck"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("projectVersion" to pluginVersion))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.lunarclient.dev")
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.oraxen.com/releases") {
        content { includeGroup("md.thomas.hopper") }
    }
    maven("https://repo.oraxen.com/snapshots") {
        content { includeGroup("md.thomas.hopper") }
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        content {
            includeGroup("io.papermc.paper")
            includeGroup("net.md-5")
            includeGroup("com.mojang")
        }
    }
    maven("https://repo.purpurmc.org/snapshots") {
        content { includeGroup("org.purpurmc.purpur") }
    }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven("https://repo.codemc.org/repository/maven-public") {
        content { includeGroup("org.bstats") }
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        content { includeGroup("com.github.retrooper") }
    }
    maven("https://repo.codemc.io/repository/maven-snapshots/") {
        content { includeGroup("com.github.retrooper") }
    }
    maven("https://jitpack.io") {
        content { includeGroupByRegex("com\\.github\\..*") }
    }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots") }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        content { includeGroup("me.clip") }
    }
}

dependencies {
    // Bukkit / Paper / Purpur / PacketEvents (Compile Only)
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.1")
    compileOnly("io.netty:netty-all:4.1.68.Final")
    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")

    // Dependencies to be shaded into the final jar
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("io.github.xtomlj:xtomlj:1.1.0")
    implementation("org.antlr:antlr4-runtime:4.7.2")
    implementation("com.lunarclient:apollo-protos:0.0.6")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("md.thomas.hopper:hopper-bukkit:1.4.2")
    compileOnly("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("org.yaml:snakeyaml:1.30")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    relocate("org.bstats", "org.lovelycheck.shaded.bstats")
    relocate("org.tomlj", "org.lovelycheck.shaded.tomlj")
    relocate("org.antlr.v4.runtime", "org.lovelycheck.shaded.antlr4.runtime")
    relocate("md.thomas.hopper", "org.lovelycheck.shaded.hopper")
    relocate("com.google.protobuf", "org.lovelycheck.shaded.protobuf")
    relocate("com.lunarclient.apollo", "org.lovelycheck.shaded.apollo")
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Version" to pluginVersion,
            "Build-Timestamp" to SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ").format(Date()),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Build-Jdk" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.version")})",
            "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}",
            "paperweight-mappings-namespace" to "mojang"
        )
    }
    archiveFileName.set("lovelycheck-${pluginVersion}-unobfuscated.jar")
}

val proguard = tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    dependsOn(tasks.shadowJar)
    injars(tasks.shadowJar.flatMap { it.archiveFile })
    outjars(layout.buildDirectory.file("libs/lovelycheck-${pluginVersion}.jar"))

    configuration("proguard-rules.pro")

    val javaHome = System.getProperty("java.home")
    libraryjars("$javaHome/jmods/java.base.jmod")

    // Add compileClasspath to libraryjars
    configurations.compileClasspath.get().files.forEach {
        libraryjars(it)
    }
}

tasks.compileJava {
    dependsOn(tasks.clean)
}

tasks.build {
    dependsOn(proguard)
}

val copyJar: Boolean = project.findProperty("copyJar")?.toString()?.toBoolean() ?: false
val pluginPath: String? = project.findProperty("lovelycheck_plugin_path")?.toString()?.takeIf { it.isNotBlank() }

if (copyJar) {
    val copyJarTask = tasks.register<Copy>("copyJarTask") {
        if (pluginPath != null) {
            from("build/libs/lovelycheck-${pluginVersion}.jar")
            into(pluginPath)
            doLast {
                println("Copied to plugin directory $pluginPath")
            }
        }
    }

    copyJarTask {
        dependsOn(proguard)
    }

    tasks.named("build") {
        dependsOn(copyJarTask)
    }
}
