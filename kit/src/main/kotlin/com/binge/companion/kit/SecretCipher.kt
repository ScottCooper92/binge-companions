package com.binge.companion.kit

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the secrets a companion keeps on disk, such as an API key or a session cookie. */
interface SecretCipher {
    fun encrypt(plaintext: String): String

    /** Null when [ciphertext] cannot be decrypted: a lost key or a corrupt blob reads as "no secret". */
    fun decrypt(ciphertext: String): String?
}

/**
 * AES-256-GCM under a key from [keyProvider]. The per-call 12-byte IV is prepended to the ciphertext and the blob is
 * Base64-encoded. [keyProvider] is called once, on first use, and the key is kept: concurrent first callers cannot each
 * make a key and clobber the other's.
 *
 * This is the whole cipher with the key left out, so a test can give it a software key. [KeystoreSecretCipher] gives it
 * an Android Keystore key.
 */
open class AesGcmSecretCipher(
    keyProvider: () -> SecretKey,
) : SecretCipher {
    private val secretKey: SecretKey by lazy(keyProvider)

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val payload = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + payload)
    }

    override fun decrypt(ciphertext: String): String? =
        try {
            val blob = Base64.getDecoder().decode(ciphertext)
            if (blob.size < IV_LENGTH) {
                Log.e(TAG, "Stored secret is shorter than an IV; treating as absent")
                null
            } else {
                val iv = blob.copyOfRange(0, IV_LENGTH)
                val payload = blob.copyOfRange(IV_LENGTH, blob.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
                String(cipher.doFinal(payload), Charsets.UTF_8)
            }
        } catch (e: GeneralSecurityException) {
            // Only the fact is logged: never the ciphertext, the plaintext or the key.
            Log.e(TAG, "Stored secret failed to decrypt: key lost or blob corrupt; treating as absent", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Stored secret is not a valid blob; treating as absent", e)
            null
        }

    private companion object {
        const val TAG = "SecretCipher"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}

/**
 * [AesGcmSecretCipher] under an Android Keystore key, so the raw key never leaves secure hardware. Each app passes its
 * own [alias]; the key is made under it on first use. The alias is part of the on-disk format: change it and every
 * stored secret reads as absent.
 */
class KeystoreSecretCipher(
    alias: String,
) : AesGcmSecretCipher({ loadOrCreateKeystoreKey(alias) })

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_SIZE_BITS = 256

private fun loadOrCreateKeystoreKey(alias: String): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    return KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
}
