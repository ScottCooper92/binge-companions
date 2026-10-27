package com.binge.companion.sdk

import com.binge.companion.contracts.stream.v1.ForgetProgressRequest
import com.binge.companion.contracts.stream.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.stream.v1.PlaybackProgress
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.PlaybackEvent
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.Base64

class StreamPlaybackProgressTest {
    private fun media(type: MediaType, tmdbId: Int = TMDB_ID): MediaId =
        MediaId
            .newBuilder()
            .setMediaType(type)
            .setTmdbId(tmdbId)
            .build()

    private val thirdEpisode: EpisodeRef =
        EpisodeRef
            .newBuilder()
            .setSeasonNumber(1)
            .setEpisodeNumber(3)
            .build()

    private fun report(
        event: PlaybackEvent,
        type: MediaType = MediaType.MEDIA_TYPE_TV,
        position: Long = 600_000,
        runtime: Long = RUNTIME,
        withEpisode: Boolean = type == MediaType.MEDIA_TYPE_TV,
    ): ReportPlaybackRequest =
        ReportPlaybackRequest
            .newBuilder()
            .setMedia(media(type))
            .apply { if (withEpisode) setEpisode(thirdEpisode) }
            .setEvent(event)
            .setPositionMillis(position)
            .setRuntimeMillis(runtime)
            .build()

    private fun held(
        position: Long = 300_000,
        runtime: Long = RUNTIME,
        completed: Boolean = false,
    ): PlaybackProgress =
        PlaybackProgress
            .newBuilder()
            .setPositionMillis(position)
            .setRuntimeMillis(runtime)
            .setCompleted(completed)
            .build()

    private fun assertInvalidArgument(block: () -> Unit) {
        val error = assertThrows<StatusException> { block() }
        assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }

    @ParameterizedTest
    @EnumSource(
        PlaybackEvent::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["PLAYBACK_EVENT_UNSPECIFIED", "PLAYBACK_EVENT_FINISHED", "UNRECOGNIZED"],
    )
    fun `an event other than FINISHED records the position, stamps now and clears completed`(event: PlaybackEvent) {
        val applied = checkNotNull(applyReport(held(completed = true), report(event), NOW))

        assertEquals(600_000, applied.positionMillis)
        assertEquals(RUNTIME, applied.runtimeMillis)
        assertEquals(NOW.epochSecond, applied.lastPlayed.seconds)
        assertEquals(NOW.nano, applied.lastPlayed.nanos)
        assertFalse(applied.completed)
    }

    @Test
    fun `a report with no runtime keeps the runtime held before`() {
        val applied = checkNotNull(applyReport(held(runtime = 1_000_000), report(PlaybackEvent.PLAYBACK_EVENT_PROGRESS, runtime = 0), NOW))

        assertEquals(1_000_000, applied.runtimeMillis)
    }

    @Test
    fun `a first report with no runtime has none`() {
        val applied = checkNotNull(applyReport(null, report(PlaybackEvent.PLAYBACK_EVENT_STARTED, runtime = 0), NOW))

        assertEquals(0, applied.runtimeMillis)
    }

    @Test
    fun `a position of zero leaves nothing to resume`() {
        assertNull(applyReport(held(), report(PlaybackEvent.PLAYBACK_EVENT_STARTED, position = 0), NOW))
    }

    @Test
    fun `FINISHED on a movie clears its progress`() {
        assertNull(applyReport(held(), report(PlaybackEvent.PLAYBACK_EVENT_FINISHED, MediaType.MEDIA_TYPE_MOVIE), NOW))
    }

    @Test
    fun `FINISHED on an episode records it completed at its runtime`() {
        val applied = checkNotNull(applyReport(held(), report(PlaybackEvent.PLAYBACK_EVENT_FINISHED, position = 2_500_000), NOW))

        assertTrue(applied.completed)
        assertEquals(RUNTIME, applied.positionMillis)
        assertEquals(RUNTIME, applied.runtimeMillis)
        assertEquals(NOW.epochSecond, applied.lastPlayed.seconds)
    }

    @Test
    fun `FINISHED on an episode with no runtime known records the position it finished at`() {
        val applied = checkNotNull(applyReport(null, report(PlaybackEvent.PLAYBACK_EVENT_FINISHED, position = 2_500_000, runtime = 0), NOW))

        assertTrue(applied.completed)
        assertEquals(2_500_000, applied.positionMillis)
    }

    @Test
    fun `applyReport refuses a bad report`() {
        assertInvalidArgument { applyReport(null, report(PlaybackEvent.PLAYBACK_EVENT_UNSPECIFIED), NOW) }
    }

    @Test
    fun `a completed episode is never a resume point`() {
        assertNull(held(completed = true).asResume())
        assertNull(null.asResume())
        assertEquals(held(), held().asResume())
    }

    @Test
    fun `a valid report passes`() {
        assertDoesNotThrow { report(PlaybackEvent.PLAYBACK_EVENT_STOPPED).requireValid() }
        assertDoesNotThrow { report(PlaybackEvent.PLAYBACK_EVENT_STOPPED, MediaType.MEDIA_TYPE_MOVIE).requireValid() }
    }

    @Test
    fun `a report is INVALID_ARGUMENT for each bad request the contract names`() {
        val unusable = report(PlaybackEvent.PLAYBACK_EVENT_STOPPED)
        listOf(
            unusable.toBuilder().setMedia(media(MediaType.MEDIA_TYPE_UNSPECIFIED)).build(),
            unusable.toBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV, tmdbId = 0)).build(),
            unusable.toBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV).toBuilder().setMediaTypeValue(UNKNOWN_MEDIA_TYPE)).build(),
            report(PlaybackEvent.PLAYBACK_EVENT_STOPPED, MediaType.MEDIA_TYPE_MOVIE, withEpisode = true),
            report(PlaybackEvent.PLAYBACK_EVENT_STOPPED, MediaType.MEDIA_TYPE_TV, withEpisode = false),
            report(PlaybackEvent.PLAYBACK_EVENT_UNSPECIFIED),
            unusable.toBuilder().setEventValue(UNKNOWN_EVENT).build(),
            report(PlaybackEvent.PLAYBACK_EVENT_PROGRESS, position = -1),
            report(PlaybackEvent.PLAYBACK_EVENT_PROGRESS, runtime = -1),
        ).forEach { request -> assertInvalidArgument { request.requireValid() } }
    }

    @Test
    fun `forgetting a movie, a series or one episode is valid`() {
        listOf(
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE)),
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV)),
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV)).setEpisode(thirdEpisode),
        ).forEach { assertDoesNotThrow { it.build().requireValid() } }
    }

    @Test
    fun `forgetting an unusable title or an episode of a movie is INVALID_ARGUMENT`() {
        listOf(
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_UNSPECIFIED)),
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE, tmdbId = -4)),
            ForgetProgressRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE)).setEpisode(thirdEpisode),
        ).forEach { assertInvalidArgument { it.build().requireValid() } }
    }

    private fun episodeRef(season: Int, number: Int): EpisodeRef =
        EpisodeRef
            .newBuilder()
            .setSeasonNumber(season)
            .setEpisodeNumber(number)
            .build()

    private fun reportFor(episode: EpisodeRef): ReportPlaybackRequest =
        report(PlaybackEvent.PLAYBACK_EVENT_STOPPED).toBuilder().setEpisode(episode).build()

    private fun forgetFor(episode: EpisodeRef): ForgetProgressRequest =
        ForgetProgressRequest
            .newBuilder()
            .setMedia(media(MediaType.MEDIA_TYPE_TV))
            .setEpisode(episode)
            .build()

    @Test
    fun `an episode with a negative season or an episode number below 1 is INVALID_ARGUMENT`() {
        listOf(episodeRef(season = -1, number = 1), episodeRef(season = 1, number = 0)).forEach { bad ->
            assertInvalidArgument { reportFor(bad).requireValid() }
            assertInvalidArgument { forgetFor(bad).requireValid() }
        }
    }

    @Test
    fun `the first special of season 0 is a valid episode`() {
        val special = episodeRef(season = 0, number = 1)
        assertDoesNotThrow { reportFor(special).requireValid() }
        assertDoesNotThrow { forgetFor(special).requireValid() }
    }

    private fun listing(pageSize: Int = 20, pageToken: String = ""): ListContinueWatchingRequest =
        ListContinueWatchingRequest
            .newBuilder()
            .setPageSize(pageSize)
            .setPageToken(pageToken)
            .build()

    private fun encode(raw: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())

    @Test
    fun `a continue watching token round-trips, and the first page starts at zero`() {
        val first = listing(pageSize = 10)
        val token = ContinueWatchingPageToken.issue(first, skip = 30)

        assertEquals(0, ContinueWatchingPageToken.skipOf(first))
        assertEquals(30, ContinueWatchingPageToken.skipOf(first.toBuilder().setPageToken(token).build()))
        assertEquals(encode("cw1:10:30"), token)
    }

    @Test
    fun `a continue watching token that is garbled, from ListRequests, or for another page size is INVALID_ARGUMENT`() {
        val token = ContinueWatchingPageToken.issue(listing(pageSize = 20), skip = 20)
        listOf(
            "not base64 !",
            encode("v1:2:20:40"),
            encode("cw1:20"),
            encode("cw1:x:20"),
            encode("cw1:20:-1"),
        ).forEach { assertInvalidArgument { ContinueWatchingPageToken.skipOf(listing(pageToken = it)) } }
        assertInvalidArgument { ContinueWatchingPageToken.skipOf(listing(pageSize = 25, pageToken = token)) }
        assertThrows<IllegalArgumentException> { ContinueWatchingPageToken.issue(listing(), skip = -1) }
    }

    @Test
    fun `the continue watching page size follows the list rule`() {
        assertEquals(DEFAULT_LIST_PAGE_SIZE, listing(pageSize = 0).effectivePageSize())
        assertEquals(7, listing(pageSize = 7).effectivePageSize())
        assertEquals(MAX_LIST_PAGE_SIZE, listing(pageSize = 1_000).effectivePageSize())
        assertInvalidArgument { listing(pageSize = -1).effectivePageSize() }
    }

    private companion object {
        const val TMDB_ID = 1399
        const val RUNTIME = 3_000_000L
        const val UNKNOWN_EVENT = 99
        const val UNKNOWN_MEDIA_TYPE = 99
        val NOW: Instant = Instant.parse("2026-10-26T20:15:30.123456789Z")
    }
}
