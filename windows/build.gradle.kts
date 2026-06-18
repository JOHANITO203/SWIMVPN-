import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

group = "com.swimvpn.desktop"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

// Use the launching JDK (Android Studio jbr 21) — no toolchain auto-provisioning. Target 17
// bytecode for both Java and Kotlin so the compileJava/compileKotlin targets stay consistent.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// --- Bundle Xray-core (Windows) at build time ------------------------------------------------
// Matches the Android xrayCoreVersion. Downloaded + extracted into a generated resources dir so
// the binary is never committed (build/ is gitignored) — same spirit as the Android pipeline.
val xrayVersion = "v26.3.27"
val xrayResDir = layout.buildDirectory.dir("generated/xray")

val fetchXray by tasks.registering {
    val resDir = xrayResDir.get().asFile
    val binDir = File(resDir, "bin")
    outputs.dir(resDir)
    doLast {
        val exe = File(binDir, "xray.exe")
        if (exe.exists() && exe.length() > 0) return@doLast
        binDir.mkdirs()
        val zip = File(resDir, "xray-windows.zip")
        ant.withGroovyBuilder {
            "get"(
                "src" to "https://github.com/XTLS/Xray-core/releases/download/$xrayVersion/Xray-windows-64.zip",
                "dest" to zip.absolutePath,
                "verbose" to true,
                "usetimestamp" to true,
            )
        }
        copy {
            from(zipTree(zip)) { include("xray.exe", "geoip.dat", "geosite.dat") }
            into(binDir)
        }
    }
}

sourceSets["main"].resources.srcDir(xrayResDir)
tasks.named("processResources") { dependsOn(fetchXray) }

compose.desktop {
    application {
        mainClass = "com.swimvpn.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "SWIMVPN"
            packageVersion = "1.0.0"
            description = "SWIMVPN — Windows (unstable)"
            vendor = "SWIMVPN"
            windows {
                menuGroup = "SWIMVPN"
                // Stable UUID so MSI/EXE upgrades replace prior installs in place.
                upgradeUuid = "8F3A1C2E-5B4D-4E2A-9C7F-1A2B3C4D5E6F"
            }
        }
    }
}
