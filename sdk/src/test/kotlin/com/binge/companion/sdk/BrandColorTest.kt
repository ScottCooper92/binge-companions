package com.binge.companion.sdk

import android.content.res.Resources
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The fallback chain and the validation of [brandColor] on the JVM, and the Bundle half of [readBrandColor], which reads an
 * Int resource id and nothing else (aapt types an `android:resource` as an Int, and a stray literal must not be taken for one).
 */
@RunWith(RobolectricTestRunner::class)
class BrandColorTest {
    private val plainId = 11
    private val darkId = 22
    private val colours = mapOf(plainId to 0xFF6366F1.toInt(), darkId to 0xFFA5B4FC.toInt())

    private fun colour(
        dark: Boolean,
        ids: Map<String, Int?>,
        resolve: (Int) -> Int? = { colours[it] },
    ): Int? = brandColor(dark, readId = { ids[it] }, resolve = resolve)

    private val both = mapOf(CompanionManifest.META_COLOR to plainId, CompanionManifest.META_COLOR_DARK to darkId)

    @Test
    fun `a dark theme reads the dark key, then the plain one`() {
        assertEquals(0xFFA5B4FC.toInt(), colour(dark = true, ids = both))
        assertEquals(0xFF6366F1.toInt(), colour(dark = true, ids = mapOf(CompanionManifest.META_COLOR to plainId)))
    }

    @Test
    fun `a light theme reads only the plain key`() {
        assertEquals(0xFF6366F1.toInt(), colour(dark = false, ids = both))
        assertNull(colour(dark = false, ids = mapOf(CompanionManifest.META_COLOR_DARK to darkId)))
    }

    @Test
    fun `no key declares nothing`() {
        assertNull(colour(dark = true, ids = emptyMap()))
        assertNull(colour(dark = false, ids = emptyMap()))
    }

    @Test
    fun `an id of 0 is absent, and the next key is tried`() {
        val ids = mapOf(CompanionManifest.META_COLOR_DARK to 0, CompanionManifest.META_COLOR to plainId)

        assertEquals(0xFF6366F1.toInt(), colour(dark = true, ids = ids))
    }

    @Test
    fun `an id that does not resolve is absent, whether it answers null or throws`() {
        val ids = mapOf(CompanionManifest.META_COLOR_DARK to darkId, CompanionManifest.META_COLOR to plainId)

        assertEquals(0xFF6366F1.toInt(), colour(dark = true, ids = ids, resolve = { if (it == darkId) null else colours[it] }))
        assertEquals(
            0xFF6366F1.toInt(),
            colour(dark = true, ids = ids, resolve = { if (it == darkId) throw Resources.NotFoundException() else colours[it] }),
        )
        assertNull(colour(dark = false, ids = ids, resolve = { null }))
    }

    @Test
    fun `alpha is dropped and the colour comes back opaque`() {
        val translucent = colour(dark = false, ids = mapOf(CompanionManifest.META_COLOR to plainId), resolve = { 0x336366F1 })

        assertEquals(0xFF6366F1.toInt(), translucent)
    }

    @Test
    fun `the bundle is read as an Int id, and a string or another type is absent`() {
        val bundle = Bundle().apply { putInt(CompanionManifest.META_COLOR, plainId) }

        assertEquals(0xFF6366F1.toInt(), bundle.readBrandColor(dark = false, resolve = { colours[it] }))
        val literal = Bundle().apply { putString(CompanionManifest.META_COLOR, "#6366F1") }
        assertNull(literal.readBrandColor(dark = false, resolve = { colours[it] }))
        val other = Bundle().apply { putBoolean(CompanionManifest.META_COLOR, true) }
        assertNull(other.readBrandColor(dark = false, resolve = { colours[it] }))
        assertNull(Bundle().readBrandColor(dark = true, resolve = { colours[it] }))
    }
}
