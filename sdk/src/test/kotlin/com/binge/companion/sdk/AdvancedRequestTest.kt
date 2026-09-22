package com.binge.companion.sdk

import com.binge.companion.contracts.v1.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdvancedRequestTest {
    @Test
    fun `a movie hand-off carries its type, id and 4K flag`() {
        val request = advancedRequestOf(mediaTypeNumber = MediaType.MEDIA_TYPE_MOVIE_VALUE, tmdbId = 603, seasonNumbers = null, is4k = true)

        assertEquals(AdvancedRequest(MediaType.MEDIA_TYPE_MOVIE, 603, emptyList(), is4k = true), request)
    }

    @Test
    fun `a TV hand-off keeps the seasons the host picked`() {
        val request = advancedRequestOf(MediaType.MEDIA_TYPE_TV_VALUE, tmdbId = 1399, seasonNumbers = intArrayOf(1, 2), is4k = false)

        assertEquals(listOf(1, 2), request?.seasonNumbers)
        assertEquals(MediaType.MEDIA_TYPE_TV, request?.mediaType)
    }

    @Test
    fun `an unknown or unspecified media type names no title`() {
        assertNull(advancedRequestOf(MediaType.MEDIA_TYPE_UNSPECIFIED_VALUE, tmdbId = 603, seasonNumbers = null, is4k = false))
        assertNull(advancedRequestOf(mediaTypeNumber = 99, tmdbId = 603, seasonNumbers = null, is4k = false))
    }

    @Test
    fun `a missing TMDB id names no title`() {
        assertNull(advancedRequestOf(MediaType.MEDIA_TYPE_MOVIE_VALUE, tmdbId = 0, seasonNumbers = null, is4k = false))
    }
}
