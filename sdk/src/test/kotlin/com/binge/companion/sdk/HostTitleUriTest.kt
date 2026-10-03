package com.binge.companion.sdk

import com.binge.companion.contracts.v1.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The "open in Binge" link a companion builds, in the shape Binge answers (binge-seerr#684). */
class HostTitleUriTest {
    @Test
    fun `a movie and a show get Binge's title link`() {
        assertEquals("binge://title/movie/550", CompanionManifest.hostTitleUri(MediaType.MEDIA_TYPE_MOVIE, 550))
        assertEquals("binge://title/tv/1399", CompanionManifest.hostTitleUri(MediaType.MEDIA_TYPE_TV, 1399))
    }

    @Test
    fun `an unusable title is refused rather than linked`() {
        assertThrows<IllegalArgumentException> { CompanionManifest.hostTitleUri(MediaType.MEDIA_TYPE_UNSPECIFIED, 550) }
        assertThrows<IllegalArgumentException> { CompanionManifest.hostTitleUri(MediaType.MEDIA_TYPE_MOVIE, 0) }
    }
}
