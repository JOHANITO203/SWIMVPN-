package android.util

/** Desktop shim for android.util.Base64 (subset used by the engine), backed by java.util.Base64. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    fun decode(str: String, flags: Int): ByteArray {
        val clean = str.trim()
        return try {
            if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder().decode(clean)
            else java.util.Base64.getMimeDecoder().decode(clean)
        } catch (e: Exception) {
            // Tolerate unpadded standard base64 (common in vmess:// / ss:// links).
            val padded = clean.replace('-', '+').replace('_', '/')
                .let { it + "=".repeat((4 - it.length % 4) % 4) }
            java.util.Base64.getMimeDecoder().decode(padded)
        }
    }

    fun decode(bytes: ByteArray, flags: Int): ByteArray = decode(String(bytes, Charsets.UTF_8), flags)

    fun encodeToString(input: ByteArray, flags: Int): String {
        val enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        var s = enc.encodeToString(input)
        if (flags and NO_WRAP != 0) s = s.replace("\n", "").replace("\r", "")
        if (flags and NO_PADDING != 0) s = s.trimEnd('=')
        return s
    }
}
