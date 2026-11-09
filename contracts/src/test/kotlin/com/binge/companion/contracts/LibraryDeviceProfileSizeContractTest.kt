package com.binge.companion.contracts

import com.binge.companion.contracts.library.v1.CodecLimit
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `codec_limits` is capped at 32 entries in `library.proto`. A device profile at the cap, with every field
 * set large, stays a few kilobytes, far under the Binder limit, and the new field reads as empty on a
 * profile that never set it.
 */
class LibraryDeviceProfileSizeContractTest {
    @Test
    fun `a profile at the cap of 32 limits is a few kilobytes`() {
        val limits =
            (0 until 32).map { index ->
                CodecLimit
                    .newBuilder()
                    .setMimeType("video/hevc")
                    .setProfile("main10-$index")
                    .setMaxWidth(Int.MAX_VALUE)
                    .setMaxHeight(Int.MAX_VALUE)
                    .build()
            }
        val profile = PlaybackDeviceProfile.newBuilder().addAllCodecLimits(limits).build()

        assertEquals(32, profile.codecLimitsCount)
        assertTrue(profile.serializedSize < 4 * 1024, "32 limits serialised to ${profile.serializedSize} bytes")
    }

    @Test
    fun `a profile that sets none reads as having no limits`() {
        val profile = PlaybackDeviceProfile
            .newBuilder()
            .setMaxWidth(1920)
            .setMaxHeight(1080)
            .build()

        assertEquals(0, profile.codecLimitsCount)
        assertEquals(1920, profile.maxWidth)
    }
}
