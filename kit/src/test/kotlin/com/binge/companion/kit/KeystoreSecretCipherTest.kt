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
import java.security.Security

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
