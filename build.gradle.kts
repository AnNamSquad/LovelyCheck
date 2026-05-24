import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.gradleup.shadow") version "9.4.1"
    id("java")
}

val pluginVersion: String by project

allprojects {
    apply(plugin = "idea")
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

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
        // Lunar Client Apollo API
        maven("https://repo.lunarclient.dev")
        // Geyser/Floodgate API
        maven("https://repo.opencollab.dev/main/")
        // Hopper (runtime dependency loader) - must be before other repos
        maven("https://repo.oraxen.com/releases") {
            content { includeGroup("md.thomas.hopper") }
        }
        maven("https://repo.oraxen.com/snapshots") {
            content { includeGroup("md.thomas.hopper") }
        }
        // PaperMC repository for Paper API
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroup("io.papermc.paper")
                includeGroup("net.md-5")
                includeGroup("com.mojang")
            }
        }
        // Purpur API, used to verify Purpur-compatible Bukkit/Paper API usage
        maven("https://repo.purpurmc.org/snapshots") {
            content { includeGroup("org.purpurmc.purpur") }
        }
        // server software
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        // BStats
        maven("https://repo.codemc.org/repository/maven-public") {
            content { includeGroup("org.bstats") }
        }
        // Packet Events
        maven("https://repo.codemc.io/repository/maven-releases/") {
            content { includeGroup("com.github.retrooper") }
        }
        maven("https://repo.codemc.io/repository/maven-snapshots/") {
            content { includeGroup("com.github.retrooper") }
        }
        // ProtocolLib (now published to Maven Central under net.dmulloy2)
        // Legacy repo kept as fallback for older artifacts
        maven("https://repo.dmulloy2.net/repository/public/") {
            content { includeGroup("com.comphenix.protocol") }
        }
        // JitPack
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
        // adventure
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        // commandAPI snapshots
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots") }
        // PlaceholderAPI
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
            content { includeGroup("me.clip") }
        }
    }
}

project(":lovelycheck-core") {
    dependencies {
        implementation("net.kyori:adventure-text-minimessage:4.14.0")
        implementation("io.github.xtomlj:xtomlj:1.1.0")
        // Explicit ANTLR runtime dependency to ensure it gets shaded
        // xtomlj uses 4.7.2 which has ATN v3, conflicts with Arclight's ANTLR 4.13.1 (ATN v4)
        implementation("org.antlr:antlr4-runtime:4.7.2")
        implementation("com.lunarclient:apollo-protos:0.0.6")
        // Geyser/Floodgate APIs for bedrock player detection (compile-only, loaded via class isolation)
        compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")
        compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")
    }
}

project(":lovelycheck-spigot") {
    dependencies {
        // Purpur is a Paper fork; compiling against Purpur also covers Bukkit/Paper API usage.
        compileOnly("org.purpurmc.purpur:purpur-api:1.21.8-R0.1-SNAPSHOT")
        compileOnly("me.clip:placeholderapi:2.11.6")
        // ProtocolLib is now published on Maven Central under net.dmulloy2
        compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
        // PacketEvents as alternative to ProtocolLib (better Arclight/hybrid server compatibility)
        compileOnly("com.github.retrooper:packetevents-spigot:2.11.1")
        compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
        compileOnly("io.netty:netty-all:4.1.68.Final")
        compileOnly(project(path = ":lovelycheck-core", configuration = "shadow"))

        implementation("org.bstats:bstats-bukkit:3.1.0")
        // Hopper - Runtime dependency loader for auto-downloading ProtocolLib or PacketEvents
        implementation("md.thomas.hopper:hopper-bukkit:1.4.2")
        implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    }
}

tasks.shadowJar {
    relocate("org.bstats", "org.lovelycheck.shaded.bstats")
    relocate("org.tomlj", "org.lovelycheck.shaded.tomlj")
    // Relocate ANTLR runtime to avoid conflicts with server's ANTLR version (Arclight uses 4.13.1)
    relocate("org.antlr.v4.runtime", "org.lovelycheck.shaded.antlr4.runtime")
    relocate("md.thomas.hopper", "org.lovelycheck.shaded.hopper")
    // Relocate protobuf to avoid conflicts with server's protobuf version
    relocate("com.google.protobuf", "org.lovelycheck.shaded.protobuf")
    relocate("com.lunarclient.apollo", "org.lovelycheck.shaded.apollo")
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Version" to pluginVersion,
            "Build-Timestamp" to SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ").format(Date()),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Build-Jdk" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})",
            "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}",
            "paperweight-mappings-namespace" to "mojang"
        )
    }
    archiveFileName.set("lovelycheck.jar")
}

dependencies {
    implementation(project(path = "lovelycheck-core", configuration = "shadow"))
    implementation(project(path = "lovelycheck-spigot", configuration = "shadow"))
    implementation("net.kyori:adventure-text-minimessage:4.13.0")
    implementation("org.yaml:snakeyaml:1.30")
}

tasks.compileJava {
    dependsOn(tasks.clean)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val copyJar: Boolean = project.findProperty("copyJar")?.toString()?.toBoolean() ?: false
val pluginPath: String? = project.findProperty("lovelycheck_plugin_path")?.toString()?.takeIf { it.isNotBlank() }

if (copyJar) {
    val copyJarTask = tasks.register<Copy>("copyJarTask") {
        if (pluginPath != null) {
            from("build/libs/lovelycheck.jar")
            into(pluginPath)
            doLast {
                println("Copied to plugin directory $pluginPath")
            }
        }
    }

    copyJarTask {
        dependsOn(tasks.shadowJar)
    }

    tasks.named("build") {
        dependsOn(copyJarTask)
    }
}
