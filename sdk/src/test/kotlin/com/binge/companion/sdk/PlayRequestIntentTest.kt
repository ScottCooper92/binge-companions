package com.binge.companion.sdk

import android.content.Intent
import com.binge.companion.contracts.v1.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Intent half of [toPlayRequest], which [PlayRequestTest] cannot reach: real extras in a real Bundle,
 * including the ones whose type is wrong (#280). The decision itself is [playRequestOf], tested apart.
 */
@RunWith(RobolectricTestRunner::class)
class PlayRequestIntentTest {
    private fun play(configure: Intent.() -> Unit = {}): Intent =
        Intent(CompanionManifest.ACTION_PLAY)
            .putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_TV_VALUE)
            .putExtra(CompanionManifest.EXTRA_TMDB_ID, 1399)
            .apply(configure)

    @Test
    fun `a series with a season and an episode is read`() {
        val request =
            play {
                putExtra(CompanionManifest.EXTRA_SEASON, 2)
                putExtra(CompanionManifest.EXTRA_EPISODE, 3)
            }.toPlayRequest()

        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_TV, 1399, 2, 3), request)
    }

    @Test
    fun `a series with no season is the whole series`() {
        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_TV, 1399, null, null), play().toPlayRequest())
    }

    @Test
    fun `a movie is read from its type and id alone`() {
        val intent =
            Intent(CompanionManifest.ACTION_PLAY)
                .putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_MOVIE_VALUE)
                .putExtra(CompanionManifest.EXTRA_TMDB_ID, 603)

        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_MOVIE, 603, null, null), intent.toPlayRequest())
    }

    @Test
    fun `another action is refused`() {
        assertNull(play().setAction(CompanionManifest.ACTION_ADVANCED_REQUEST).toPlayRequest())
        assertNull(play().setAction(null).toPlayRequest())
    }

    @Test
    fun `a missing media type or id is refused`() {
        assertNull(Intent(CompanionManifest.ACTION_PLAY).putExtra(CompanionManifest.EXTRA_TMDB_ID, 1399).toPlayRequest())
        assertNull(
            Intent(
                CompanionManifest.ACTION_PLAY,
            ).putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_TV_VALUE).toPlayRequest(),
        )
    }

    @Test
    fun `a season of the wrong type is refused, not read as specials`() {
        assertNull(play { putExtra(CompanionManifest.EXTRA_SEASON, 2L) }.toPlayRequest())
        assertNull(play { putExtra(CompanionManifest.EXTRA_SEASON, "2") }.toPlayRequest())
    }

    @Test
    fun `an episode of the wrong type is refused`() {
        val intent =
            play {
                putExtra(CompanionManifest.EXTRA_SEASON, 2)
                putExtra(CompanionManifest.EXTRA_EPISODE, "3")
            }

        assertNull(intent.toPlayRequest())
    }

    @Test
    fun `an episode with no season is refused`() {
        assertNull(play { putExtra(CompanionManifest.EXTRA_EPISODE, 3) }.toPlayRequest())
    }

    @Test
    fun `a media type or id of the wrong type is refused`() {
        assertNull(play { putExtra(CompanionManifest.EXTRA_MEDIA_TYPE, "2") }.toPlayRequest())
        assertNull(play { putExtra(CompanionManifest.EXTRA_TMDB_ID, "1399") }.toPlayRequest())
    }
}
