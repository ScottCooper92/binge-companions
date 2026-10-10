package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The manifest strings, pinned (#167). They are read without binding, so a changed value is silent: the companion
 * still exists and reads as declaring nothing. A host that copies them rather than depending on the SDK diffs
 * against this list.
 */
class CompanionManifestKeysTest {
    @Test
    fun `the manifest strings do not move`() {
        assertEquals("com.binge.companion.REQUEST", CompanionManifest.ACTION_REQUEST)
        assertEquals("com.binge.companion.LIBRARY", CompanionManifest.ACTION_LIBRARY)
        assertEquals("com.binge.companion.STREAM", CompanionManifest.ACTION_STREAM)
        assertEquals("com.binge.companion.name", CompanionManifest.META_NAME)
        assertEquals("com.binge.companion.icon", CompanionManifest.META_ICON)
        assertEquals("com.binge.companion.majors", CompanionManifest.META_MAJORS)
        assertEquals("com.binge.companion.majors.request", CompanionManifest.META_MAJORS_REQUEST)
        assertEquals("com.binge.companion.majors.library", CompanionManifest.META_MAJORS_LIBRARY)
        assertEquals("com.binge.companion.majors.stream", CompanionManifest.META_MAJORS_STREAM)
        assertEquals("com.binge.companion.ADVANCED_REQUEST", CompanionManifest.ACTION_ADVANCED_REQUEST)
        assertEquals("com.binge.companion.SETTINGS", CompanionManifest.ACTION_SETTINGS)
        assertEquals("com.binge.companion.extra.MEDIA_TYPE", CompanionManifest.EXTRA_MEDIA_TYPE)
        assertEquals("com.binge.companion.extra.TMDB_ID", CompanionManifest.EXTRA_TMDB_ID)
        assertEquals("com.binge.companion.extra.SEASON_NUMBERS", CompanionManifest.EXTRA_SEASON_NUMBERS)
        assertEquals("com.binge.companion.extra.IS_4K", CompanionManifest.EXTRA_IS_4K)
        assertEquals("binge", CompanionManifest.HOST_TITLE_SCHEME)
    }
}
