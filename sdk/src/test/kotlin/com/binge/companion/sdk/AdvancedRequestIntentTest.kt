package com.binge.companion.sdk

import android.content.Intent
import com.binge.companion.contracts.v1.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Intent half of [toAdvancedRequest], which [AdvancedRequestTest] cannot reach (#280). The decision is [advancedRequestOf]. */
@RunWith(RobolectricTestRunner::class)
class AdvancedRequestIntentTest {
    private fun advanced(configure: Intent.() -> Unit = {}): Intent =
        Intent(CompanionManifest.ACTION_ADVANCED_REQUEST)
            .putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_TV_VALUE)
            .putExtra(CompanionManifest.EXTRA_TMDB_ID, 1399)
            .apply(configure)

    @Test
    fun `a series with seasons and a version is read`() {
        val request =
            advanced {
                putExtra(CompanionManifest.EXTRA_SEASON_NUMBERS, intArrayOf(1, 2))
                putExtra(CompanionManifest.EXTRA_VERSION_ID, "uhd")
            }.toAdvancedRequest()

        assertEquals(AdvancedRequest(MediaType.MEDIA_TYPE_TV, 1399, listOf(1, 2), versionId = "uhd"), request)
    }

    @Test
    fun `absent seasons and version read as the defaults`() {
        assertEquals(AdvancedRequest(MediaType.MEDIA_TYPE_TV, 1399, emptyList(), versionId = ""), advanced().toAdvancedRequest())
    }

    @Test
    fun `a missing media type or id names no title`() {
        assertNull(Intent(CompanionManifest.ACTION_ADVANCED_REQUEST).putExtra(CompanionManifest.EXTRA_TMDB_ID, 1399).toAdvancedRequest())
        assertNull(
            Intent(CompanionManifest.ACTION_ADVANCED_REQUEST)
                .putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_TV_VALUE)
                .toAdvancedRequest(),
        )
    }

    @Test
    fun `seasons of the wrong type are dropped, not trusted`() {
        val request = advanced { putExtra(CompanionManifest.EXTRA_SEASON_NUMBERS, "1,2") }.toAdvancedRequest()

        assertEquals(emptyList<Int>(), request?.seasonNumbers)
    }

    @Test
    fun `a media type of the wrong type names no title`() {
        assertNull(advanced { putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, "2") }.toAdvancedRequest())
    }
}
