package com.binge.companion.sdk

import com.binge.companion.contracts.v1.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val PLAY = CompanionManifest.ACTION_PLAY
private const val MOVIE = MediaType.MEDIA_TYPE_MOVIE_VALUE
private const val TV = MediaType.MEDIA_TYPE_TV_VALUE

class PlayRequestTest {
    @Test
    fun `a movie hand-off carries its type and id`() {
        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_MOVIE, 603, null, null), playRequestOf(PLAY, MOVIE, 603, null, null))
    }

    @Test
    fun `a series hand-off may name no episode, one season, or an episode`() {
        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_TV, 1399, null, null), playRequestOf(PLAY, TV, 1399, null, null))
        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_TV, 1399, 2, null), playRequestOf(PLAY, TV, 1399, 2, null))
        assertEquals(PlayRequest(MediaType.MEDIA_TYPE_TV, 1399, 2, 5), playRequestOf(PLAY, TV, 1399, 2, 5))
    }

    @Test
    fun `specials, season 0, are a season`() {
        assertEquals(0, playRequestOf(PLAY, TV, 1399, 0, 1)?.seasonNumber)
    }

    @Test
    fun `another action, or none, is refused`() {
        assertNull(playRequestOf("com.binge.companion.ADVANCED_REQUEST", MOVIE, 603, null, null))
        assertNull(playRequestOf(null, MOVIE, 603, null, null))
    }

    @Test
    fun `an unknown or unspecified media type is refused`() {
        assertNull(playRequestOf(PLAY, MediaType.MEDIA_TYPE_UNSPECIFIED_VALUE, 603, null, null))
        assertNull(playRequestOf(PLAY, 99, 603, null, null))
    }

    @Test
    fun `a missing or negative TMDB id is refused`() {
        assertNull(playRequestOf(PLAY, MOVIE, 0, null, null))
        assertNull(playRequestOf(PLAY, MOVIE, -5, null, null))
    }

    @Test
    fun `a negative season, or an episode below 1, is refused`() {
        assertNull(playRequestOf(PLAY, TV, 1399, -1, null))
        assertNull(playRequestOf(PLAY, TV, 1399, 1, 0))
        assertNull(playRequestOf(PLAY, TV, 1399, 1, -3))
    }

    @Test
    fun `an episode without its season is refused`() {
        assertNull(playRequestOf(PLAY, TV, 1399, null, 4))
    }

    @Test
    fun `a movie that names a season or an episode is refused`() {
        assertNull(playRequestOf(PLAY, MOVIE, 603, 1, null))
        assertNull(playRequestOf(PLAY, MOVIE, 603, 1, 1))
    }
}
