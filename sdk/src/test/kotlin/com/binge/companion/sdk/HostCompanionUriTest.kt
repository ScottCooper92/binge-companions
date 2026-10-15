package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The "turn me on in Binge" link a companion builds for its own page in the host. */
class HostCompanionUriTest {
    @Test
    fun `a package name gets Binge's companion link`() {
        assertEquals(
            "binge://companions/com.cooper.binge.companion.seerr",
            CompanionManifest.hostCompanionUri("com.cooper.binge.companion.seerr"),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "seerr", "com.example/../other", "com.example?x=1", "1com.example", "com..example"])
    fun `anything but a package name is refused rather than linked`(packageName: String) {
        assertThrows<IllegalArgumentException> { CompanionManifest.hostCompanionUri(packageName) }
    }

    @Test
    fun `the Play listing names release Binge`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.cooper.binge.app",
            CompanionManifest.HOST_PLAY_LISTING_URL,
        )
    }
}
