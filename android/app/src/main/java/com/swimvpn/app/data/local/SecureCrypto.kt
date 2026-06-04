package com.swimvpn.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * At-rest encryption for sensitive values stored in DataStore / SharedPreferences.
 *
 * Uses an AES-256-GCM key held in the **Android Keystore** (hardware-backed where available). The
 * key is generated on first use, never leaves the Keystore, and requires **no user authentication**
 * so encryption/decryption works headless (boot / sticky reconnect / background service).
 *
 * Encrypted output is `ENC_PREFIX + base64(iv || ciphertext+tag)`. [decrypt] is migration-aware: a
 * value WITHOUT the prefix is treated as legacy plaintext and returned as-is, so existing unencrypted
 * data keeps working and is transparently re-encrypted the next time the caller writes it.
 */
object SecureCrypto {
    private const val KEY_ALIAS = "swimvpn_secure_store_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENC_PREFIX = "enc1:"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    /** True if [value] is one we wrote (encrypted); false for legacy plaintext / empty. */
    fun isEncrypted(value: String?): Boolean = value != null && value.startsWith(ENC_PREFIX)

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(ct, 0, packed, iv.size, ct.size)
        return ENC_PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encrypt]. A value without [ENC_PREFIX] is legacy plaintext and is
     * returned unchanged (transparent migration). Throws if a prefixed value cannot be decrypted
     * (corrupt blob / lost key) — callers already treat unreadable stores as corrupt.
     */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(ENC_PREFIX)) return stored
        val packed = Base64.decode(stored.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
        require(packed.size > IV_LENGTH) { "ciphertext too short" }
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val ct = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
