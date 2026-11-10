package com.binge.companion.kit

import android.security.keystore.KeyGenParameterSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * [KeystoreSecretCipher] against a stand-in `AndroidKeyStore` provider, because Robolectric has none. The cipher's
 * own code runs unchanged: the real [KeyGenParameterSpec] builder, the real lookup, the real key generation call. Only
 * the provider that holds the key is fake.
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreSecretCipherTest {
    @Before
    fun install() {
        FakeAndroidKeyStore.keys.clear()
        Security.removeProvider(PROVIDER)
        Security.addProvider(FakeAndroidKeyStore.provider())
    }

    @After
    fun remove() {
        Security.removeProvider(PROVIDER)
    }

    @Test
    fun `a secret round-trips, with the key made under the app's alias`() {
        val cipher = KeystoreSecretCipher("app_alias")
        assertEquals("secret", cipher.decrypt(cipher.encrypt("secret")))
        assertNotNull(FakeAndroidKeyStore.keys["app_alias"])
    }

    @Test
    fun `a second cipher with the same alias finds the key the first made`() {
        val first = KeystoreSecretCipher("shared")
        val blob = first.encrypt("secret")
        val key = FakeAndroidKeyStore.keys.getValue("shared")
        assertEquals("secret", KeystoreSecretCipher("shared").decrypt(blob))
        assertSame(key, FakeAndroidKeyStore.keys.getValue("shared"))
    }

    @Test
    fun `a different alias is a different key`() {
        val blob = KeystoreSecretCipher("one").encrypt("secret")
        assertNull(KeystoreSecretCipher("two").decrypt(blob))
        assertEquals(setOf("one", "two"), FakeAndroidKeyStore.keys.keys)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}

/** An in-memory `AndroidKeyStore`: a [KeyStoreSpi] over a map, and a [KeyGeneratorSpi] that reads the alias off the spec. */
private object FakeAndroidKeyStore {
    val keys: MutableMap<String, SecretKey> = Collections.synchronizedMap(linkedMapOf())

    fun provider(): Provider =
        object : Provider("AndroidKeyStore", 1.0, "test stand-in") {
            init {
                put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi::class.java.name)
                put("KeyGenerator.AES", FakeKeyGeneratorSpi::class.java.name)
            }
        }

    class FakeKeyStoreSpi : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]

        // The default throws for a key entry asked for with no password, which the real AndroidKeyStore does not.
        override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? =
            keys[alias]?.let { KeyStore.SecretKeyEntry(it) }

        override fun engineContainsAlias(alias: String): Boolean = alias in keys

        override fun engineIsKeyEntry(alias: String): Boolean = alias in keys

        override fun engineIsCertificateEntry(alias: String): Boolean = false

        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit

        override fun engineAliases() = Collections.enumeration(keys.keys.toList())

        override fun engineSize(): Int = keys.size

        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

        override fun engineGetCertificate(alias: String): Certificate? = null

        override fun engineGetCreationDate(alias: String): Date? = null

        override fun engineSetKeyEntry(
            alias: String,
            key: Key?,
            password: CharArray?,
            chain: Array<out Certificate>?,
        ) = Unit

        override fun engineSetKeyEntry(
            alias: String,
            key: ByteArray?,
            chain: Array<out Certificate>?,
        ) = Unit

        override fun engineSetCertificateEntry(alias: String, cert: Certificate?) = Unit

        override fun engineDeleteEntry(alias: String) = Unit

        override fun engineGetCertificateAlias(cert: Certificate?): String? = null

        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    }

    class FakeKeyGeneratorSpi : KeyGeneratorSpi() {
        private var alias = ""
        private var bits = 0

        override fun engineInit(random: SecureRandom?) = error("a Keystore key needs a KeyGenParameterSpec")

        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            val spec = params as KeyGenParameterSpec
            alias = spec.keystoreAlias
            bits = spec.keySize
        }

        override fun engineInit(keysize: Int, random: SecureRandom?) = error("a Keystore key needs a KeyGenParameterSpec")

        override fun engineGenerateKey(): SecretKey {
            val key = SecretKeySpec(ByteArray(bits / Byte.SIZE_BITS).also { SecureRandom().nextBytes(it) }, "AES")
            keys[alias] = key
            return key
        }
    }
}
