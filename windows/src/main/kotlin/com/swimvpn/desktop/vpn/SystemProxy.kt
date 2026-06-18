package com.swimvpn.desktop.vpn

/**
 * Toggles the Windows per-user system proxy (WinINet) to route HTTP/HTTPS traffic through the
 * local xray HTTP inbound. v1 (unstable) uses system-proxy mode — no TUN driver, no admin. Apps
 * that honour WinINet settings (browsers, most desktop apps) are proxied; a full-TUN mode that
 * captures ALL traffic (wintun) is a later milestone.
 *
 * Implemented via PowerShell: it writes the registry keys AND calls InternetSetOption so running
 * apps pick up the change immediately (a plain `reg add` would not refresh live sessions).
 */
object SystemProxy {
    private const val PROXY = "127.0.0.1:${LocalPorts.HTTP}"

    private fun runPowerShell(script: String): Boolean = try {
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectErrorStream(true)
            .start()
        p.inputStream.readBytes() // drain
        p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (e: Exception) {
        false
    }

    private val refreshSnippet = """
        ${'$'}sig = '[DllImport("wininet.dll", SetLastError=true)] public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);';
        ${'$'}t = Add-Type -MemberDefinition ${'$'}sig -Name Win -Namespace WinInet -PassThru;
        ${'$'}t::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0) | Out-Null;  # SETTINGS_CHANGED
        ${'$'}t::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0) | Out-Null;  # REFRESH
    """.trimIndent()

    fun enable(): Boolean = runPowerShell(
        """
        ${'$'}k = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings';
        Set-ItemProperty -Path ${'$'}k -Name ProxyServer -Value '$PROXY';
        Set-ItemProperty -Path ${'$'}k -Name ProxyEnable -Value 1;
        Set-ItemProperty -Path ${'$'}k -Name ProxyOverride -Value '<local>';
        $refreshSnippet
        """.trimIndent()
    )

    fun disable(): Boolean = runPowerShell(
        """
        ${'$'}k = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings';
        Set-ItemProperty -Path ${'$'}k -Name ProxyEnable -Value 0;
        $refreshSnippet
        """.trimIndent()
    )
}
