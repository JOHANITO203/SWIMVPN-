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
    // Gson — the SAME JSON lib the shared Android engine uses to build Xray configs.
    implementation("com.google.code.gson:gson:2.11.0")
}

// --- Shared engine (GUARANTEED parity with Android) ------------------------------------------
// The Windows app compiles the SAME parsing + Xray-config-building source files as the Android
// app (com.swimvpn.app.config), copied fresh from android/ on every build → no drift, identical
// link/config support (VLESS/VMess/Trojan/Shadowsocks, URL + JSON, all transports/security).
// Files coupled to Android runtime (Context/DataStore/OkHttp/Intent) or app resources (R,
// data.network models) are excluded; android.util.Log/Base64 are shimmed under src/main/kotlin/android.
val androidEngineSrc = "../android/app/src/main/java/com/swimvpn/app/config"
val engineGenRoot = layout.buildDirectory.dir("generated/engine")

val syncEngine by tasks.registering(Copy::class) {
    from(androidEngineSrc) {
        // Excluded: pull Android runtime / app-only deps not needed for parse→outbound.
        exclude(
            "ConfigRepository.kt",
            "SubscriptionFetcher.kt",
            "SubscriptionCookieJar.kt",
            "TunnelRuntimeAdapter.kt",
            "ActiveConfigMetadata.kt",
            "CamouflageProfile.kt",
            "FailingServerAlertPolicy.kt",
            "SubscriptionRefreshPolicy.kt",
        )
    }
    into(engineGenRoot.get().dir("com/swimvpn/app/config"))
}

sourceSets["main"].kotlin.srcDir(engineGenRoot)
tasks.named("compileKotlin") { dependsOn(syncEngine) }

// --- Bundle the TUN stack (full-traffic mode) ------------------------------------------------
// tun2socks (xjasonlyu) bridges a WinTUN virtual adapter to xray's local SOCKS, so ALL traffic
// is captured (like Android's VpnService+tun2socks) — not just proxy-aware apps. wintun.dll is
// the WireGuard userspace TUN driver. Both downloaded at build time, never committed.
val tun2socksVersion = "v2.5.2"
val wintunVersion = "0.14.1"

val fetchTunnel by tasks.registering {
    val binDir = xrayResDir.get().asFile.resolve("bin")
    outputs.dir(xrayResDir)
    doLast {
        binDir.mkdirs()
        val t2s = File(binDir, "tun2socks.exe")
        if (!t2s.exists() || t2s.length() == 0L) {
            val zip = File(binDir, "tun2socks.zip")
            ant.withGroovyBuilder {
                "get"(
                    "src" to "https://github.com/xjasonlyu/tun2socks/releases/download/$tun2socksVersion/tun2socks-windows-amd64.zip",
                    "dest" to zip.absolutePath, "verbose" to true, "usetimestamp" to true,
                )
            }
            copy { from(zipTree(zip)); into(binDir) }
            File(binDir, "tun2socks-windows-amd64.exe").takeIf { it.exists() }?.renameTo(t2s)
            zip.delete()
        }
        val wintun = File(binDir, "wintun.dll")
        if (!wintun.exists() || wintun.length() == 0L) {
            val zip = File(binDir, "wintun.zip")
            ant.withGroovyBuilder {
                "get"(
                    "src" to "https://www.wintun.net/builds/wintun-$wintunVersion.zip",
                    "dest" to zip.absolutePath, "verbose" to true, "usetimestamp" to true,
                )
            }
            copy {
                from(zipTree(zip)) { include("wintun/bin/amd64/wintun.dll") }
                into(binDir); includeEmptyDirs = false
                eachFile { path = name } // flatten into bin/wintun.dll
            }
            zip.delete()
        }
    }
}
tasks.named("processResources") { dependsOn(fetchXray, fetchTunnel) }

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
        zip.delete()
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
                // Installer/executable icon = the Android launcher icon (icon coherence).
                iconFile.set(project.file("src/main/resources/icons/app.ico"))
            }
        }
    }
}
