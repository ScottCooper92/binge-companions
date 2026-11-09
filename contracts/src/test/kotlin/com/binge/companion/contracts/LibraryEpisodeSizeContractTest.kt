package com.binge.companion.contracts

import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.EpisodeWatchState
import com.binge.companion.contracts.library.v1.GetWatchStateResponse
import com.binge.companion.contracts.library.v1.ListEpisodeWatchStateResponse
import com.binge.companion.contracts.library.v1.WatchState
import com.google.protobuf.Timestamp
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The caps `library.proto` states for per-episode watch state keep a response far under the Binder
 * transaction limit of about 1 MB. The caps are a rule in prose; these build a response at each cap
 * with every field set to a large value, so a cap raised without thought fails here.
 */
class LibraryEpisodeSizeContractTest {
    private fun episode(index: Int): EpisodeWatchState =
        EpisodeWatchState
            .newBuilder()
            .setEpisode(EpisodeRef.newBuilder().setSeasonNumber(99).setEpisodeNumber(1000 + index))
            .setState(
                WatchState
                    .newBuilder()
                    .setPlayed(true)
                    .setPositionMillis(Long.MAX_VALUE)
                    .setRuntimeMillis(Long.MAX_VALUE)
                    .setLastPlayed(Timestamp.newBuilder().setSeconds(Long.MAX_VALUE / 2).setNanos(999_999_999)),
            ).build()

    @Test
    fun `a window of 100 episodes in a watch state is a few kilobytes`() {
        val state = WatchState.newBuilder().addAllEpisodes((0 until 100).map(::episode)).build()
        val size = GetWatchStateResponse
            .newBuilder()
            .setState(state)
            .build()
            .serializedSize

        assertTrue(size < 16 * 1024, "100 episodes serialised to $size bytes")
    }

    @Test
    fun `a page of 200 episodes is well under the Binder limit`() {
        val page = ListEpisodeWatchStateResponse
            .newBuilder()
            .addAllEpisodes(
                (0 until 200).map(::episode),
            ).setNextPageToken("x".repeat(256))
            .build()

        assertTrue(page.serializedSize < 32 * 1024, "200 episodes serialised to ${page.serializedSize} bytes")
    }
}
