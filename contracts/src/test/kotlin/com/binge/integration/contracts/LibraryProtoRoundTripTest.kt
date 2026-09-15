package com.binge.integration.contracts

import com.binge.integration.contracts.library.v1.Availability
import com.binge.integration.contracts.library.v1.AvailabilityState
import com.binge.integration.contracts.library.v1.Capability
import com.binge.integration.contracts.library.v1.HandshakeResponse
import com.binge.integration.contracts.library.v1.ListContinueWatchingResponse
import com.binge.integration.contracts.library.v1.PlayTarget
import com.binge.integration.contracts.library.v1.WatchState
import com.binge.integration.contracts.library.v1.androidIntent
import com.binge.integration.contracts.library.v1.availability
import com.binge.integration.contracts.library.v1.episodeRef
import com.binge.integration.contracts.library.v1.episodeWatchState
import com.binge.integration.contracts.library.v1.intentExtra
import com.binge.integration.contracts.library.v1.libraryEntry
import com.binge.integration.contracts.library.v1.listContinueWatchingResponse
import com.binge.integration.contracts.library.v1.playTarget
import com.binge.integration.contracts.library.v1.seasonAvailability
import com.binge.integration.contracts.library.v1.watchState
import com.binge.integration.contracts.library.v1.webUrl
import com.binge.integration.contracts.v1.MediaType
import com.binge.integration.contracts.v1.mediaId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SHOW_TMDB_ID = 1396
private const val MOVIE_TMDB_ID = 550
private const val RUNTIME_MILLIS = 2_820_000L
private const val POSITION_MILLIS = 900_000L
private const val LAST_PLAYED_EPOCH_MS = 1_760_000_000_000L
private const val UNKNOWN_ENUM_NUMBER = 9999

class LibraryProtoRoundTripTest {
    @Test
    fun `an availability with per-season detail round-trips through bytes`() {
        val held =
            availability {
                state = AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY
                itemId = "a1b2c3"
                libraryName = "TV Shows"
                seasons += seasonAvailability {
                    seasonNumber = 1
                    episodeNumbers += listOf(1, 2, 3)
                }
            }

        assertEquals(held, Availability.parseFrom(held.toByteArray()))
    }

    @Test
    fun `an intent play target keeps its extras, and a web one keeps its url`() {
        val intent =
            playTarget {
                androidIntent = androidIntent {
                    packageName = "org.jellyfin.androidtv"
                    action = "android.intent.action.VIEW"
                    dataUri = "jellyfin://item/a1b2c3"
                    extras += intentExtra {
                        key = "itemId"
                        value = "a1b2c3"
                    }
                }
            }
        val web = playTarget { webUrl = webUrl { url = "https://jellyfin.example/web/#/details?id=a1b2c3" } }

        val parsedIntent = PlayTarget.parseFrom(intent.toByteArray())
        val parsedWeb = PlayTarget.parseFrom(web.toByteArray())

        assertEquals(intent, parsedIntent)
        assertEquals(
            "a1b2c3",
            parsedIntent.androidIntent.extrasList
                .single()
                .value,
        )
        assertEquals(PlayTarget.TargetCase.WEB_URL, parsedWeb.targetCase)
    }

    @Test
    fun `a series watch state carries its episodes' own progress`() {
        val series =
            watchState {
                runtimeMillis = RUNTIME_MILLIS
                episodes += episodeWatchState {
                    episode = episodeRef {
                        seasonNumber = 1
                        episodeNumber = 2
                    }
                    played = false
                    positionMillis = POSITION_MILLIS
                    runtimeMillis = RUNTIME_MILLIS
                    lastPlayedEpochMs = LAST_PLAYED_EPOCH_MS
                }
            }

        val parsed = WatchState.parseFrom(series.toByteArray())

        assertEquals(series, parsed)
        assertEquals(POSITION_MILLIS, parsed.episodesList.single().positionMillis)
    }

    @Test
    fun `a continue-watching page carries its cursor and its rows`() {
        val page =
            listContinueWatchingResponse {
                entries += libraryEntry {
                    media = mediaId {
                        mediaType = MediaType.MEDIA_TYPE_TV
                        tmdbId = SHOW_TMDB_ID
                    }
                    episode = episodeRef {
                        seasonNumber = 1
                        episodeNumber = 2
                    }
                    state = watchState { positionMillis = POSITION_MILLIS }
                    artworkUrl = "https://jellyfin.example/Items/a1b2c3/Images/Primary"
                    title = "Breaking Bad"
                }
                entries += libraryEntry {
                    media = mediaId {
                        mediaType = MediaType.MEDIA_TYPE_MOVIE
                        tmdbId = MOVIE_TMDB_ID
                    }
                    title = "Fight Club"
                }
                nextPageToken = "2"
            }

        val parsed = ListContinueWatchingResponse.parseFrom(page.toByteArray())

        assertEquals(page, parsed)
        assertEquals("2", parsed.nextPageToken)
        // A movie row leaves the episode unset rather than sending a zeroed one.
        assertTrue(!parsed.entriesList[1].hasEpisode())
    }

    @Test
    fun `a capability added after this host shipped survives a re-serialise`() {
        val fromNewerPeer =
            HandshakeResponse
                .newBuilder()
                .addCapabilitiesValue(UNKNOWN_ENUM_NUMBER)
                .addCapabilities(Capability.CAPABILITY_PLAY)
                .build()

        val parsed = HandshakeResponse.parseFrom(fromNewerPeer.toByteArray())

        assertEquals(2, parsed.capabilitiesValueList.size)
        assertEquals(Capability.CAPABILITY_PLAY, parsed.capabilitiesList[1])
    }
}
