package com.swimvpn.desktop.system

/** Build version, written into resources at build time (gradle writeVersion task). */
object BuildInfo {
    val version: String by lazy {
        runCatching {
            BuildInfo::class.java.classLoader.getResourceAsStream("version.txt")
                ?.bufferedReader()?.use { it.readText().trim() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "dev"
    }
}
