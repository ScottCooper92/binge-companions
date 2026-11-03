package com.binge.companion.contracts

import com.binge.companion.contracts.library.v1.Availability
import com.binge.companion.contracts.library.v1.AvailabilityReason
import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.Capability
import com.binge.companion.contracts.library.v1.GetPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.HandshakeResponse
import com.binge.companion.contracts.library.v1.ListContinueWatchingResponse
import com.binge.companion.contracts.library.v1.WatchState
import com.binge.companion.contracts.library.v1.availability
import com.binge.companion.contracts.library.v1.episodeRef
import com.binge.companion.contracts.library.v1.episodeWatchState
import com.binge.companion.contracts.library.v1.getPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.handshakeResponse
import com.binge.companion.contracts.library.v1.libraryEntry
import com.binge.companion.contracts.library.v1.listContinueWatchingResponse
import com.binge.companion.contracts.library.v1.seasonAvailability
import com.binge.companion.contracts.library.v1.trickplay
import com.binge.companion.contracts.library.v1.watchState
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.mediaId
import com.google.protobuf.timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryProtoRoundTripTest {
    @Test
    fun `a series availability round-trips with its per-season episodes`() {
        val held = availability {
            state = AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY
            itemId = "a1b2c3"
            libraryName = "TV Shows"
            seasons += seasonAvailability {
                seasonNumber = 1
                state = AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY
                episodeNumbers += listOf(1, 2, 3)
            }
            seasons += seasonAvailability {
                seasonNumber = 2
                state = AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY
            }
        }

        val parsed = Availability.parseFrom(held.toByteArray())

        assertEquals(held, parsed)
        assertEquals(listOf(1, 2, 3), parsed.getSeasons(0).episodeNumbersList)
        assertTrue(parsed.getSeasons(1).episodeNumbersList.isEmpty())
    }

    @Test
    fun `a not-in-library answer round-trips the reason it was given`() {
        val missing = availability {
            state = AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY
            reason = AvailabilityReason.AVAILABILITY_REASON_SERVER_LACKS_IDS
        }

        val parsed = Availability.parseFrom(missing.toByteArray())

        assertEquals(missing, parsed)
        assertEquals(AvailabilityReason.AVAILABILITY_REASON_SERVER_LACKS_IDS, parsed.reason)
    }

    @Test
    fun `an answer with no reason reads as no reason, the way an integration that predates the field sends it`() {
        val parsed = Availability.parseFrom(
            availability { state = AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY }.toByteArray(),
        )

        assertEquals(AvailabilityReason.AVAILABILITY_REASON_UNSPECIFIED, parsed.reason)
        assertEquals(0, parsed.reasonValue)
    }

    @Test
    fun `a reason a newer peer adds is kept as a number and read as unrecognised, not as a known one`() {
        val fromNewer = availability {
            state = AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY
            reasonValue = UNKNOWN_REASON
        }

        val parsed = Availability.parseFrom(fromNewer.toByteArray())

        assertEquals(AvailabilityReason.UNRECOGNIZED, parsed.reason)
        assertEquals(UNKNOWN_REASON, parsed.reasonValue)
    }

    @Test
    fun `a watch state round-trips its timestamp and its episodes`() {
        val state = watchState {
            played = false
            positionMillis = 930_000
            runtimeMillis = 2_700_000
            lastPlayed = timestamp {
                seconds = 1_757_000_000
                nanos = 0
            }
            episodes += episodeWatchState {
                episode = episodeRef {
                    seasonNumber = 1
                    episodeNumber = 4
                }
                this.state = watchState {
                    played = true
                    runtimeMillis = 2_700_000
                }
            }
        }

        val parsed = WatchState.parseFrom(state.toByteArray())

        assertEquals(state, parsed)
        assertEquals(1_757_000_000L, parsed.lastPlayed.seconds)
        assertTrue(parsed.getEpisodes(0).state.played)
        assertEquals(4, parsed.getEpisodes(0).episode.episodeNumber)
    }

    @Test
    fun `an absent last_played is distinguishable from the epoch`() {
        // `played` and a resume point are separate facts, and so is "the server never said
        // when". A host that reads a default Timestamp as 1970 would date every unplayed
        // item; hasLastPlayed is the only thing that separates them.
        val neverPlayed = watchState { played = false }

        val parsed = WatchState.parseFrom(neverPlayed.toByteArray())

        assertTrue(!parsed.hasLastPlayed())
        assertEquals(0L, parsed.lastPlayed.seconds)
    }

    @Test
    fun `a continue-watching page carries its entries and its cursor`() {
        val page = listContinueWatchingResponse {
            entries += libraryEntry {
                media = mediaId {
                    mediaType = MediaType.MEDIA_TYPE_TV
                    tmdbId = 1396
                }
                state = watchState { positionMillis = 930_000 }
                artworkUrl = "https://example.test/art/1396.jpg"
                title = "Breaking Bad"
                episode = episodeRef {
                    seasonNumber = 1
                    episodeNumber = 4
                }
            }
            nextPageToken = "cursor-2"
        }

        val parsed = ListContinueWatchingResponse.parseFrom(page.toByteArray())

        assertEquals(page, parsed)
        assertEquals(4, parsed.getEntries(0).episode.episodeNumber)
        assertEquals("cursor-2", parsed.nextPageToken)
    }

    @Test
    fun `an unknown capability survives a parse by an older peer`() {
        // The same guarantee REQUEST's handshake relies on, asserted for this package
        // too: a host that shipped before a capability existed must not lose it on a
        // re-serialise, because feature detection is the capability set and nothing else.
        val fromNewerPeer = HandshakeResponse
            .newBuilder()
            .addCapabilitiesValue(9999)
            .addCapabilities(Capability.CAPABILITY_PLAY)
            .build()

        val parsed = HandshakeResponse.parseFrom(fromNewerPeer.toByteArray())

        assertEquals(2, parsed.capabilitiesValueList.size)
        assertEquals(Capability.CAPABILITY_PLAY, parsed.capabilitiesList[1])
    }

    @Test
    fun `a handshake declaring read but not write is a distinct set`() {
        // The split that makes writing a second consent: a companion whose user may read
        // watch state but not change it declares one capability, not one with a flag.
        val readOnly = handshakeResponse {
            capabilities += listOf(Capability.CAPABILITY_WATCH_STATE, Capability.CAPABILITY_AVAILABILITY)
            providerName = "Example Server"
            companionVersionName = "0.1.0"
        }

        val parsed = HandshakeResponse.parseFrom(readOnly.toByteArray())

        assertTrue(parsed.capabilitiesList.contains(Capability.CAPABILITY_WATCH_STATE))
        assertTrue(!parsed.capabilitiesList.contains(Capability.CAPABILITY_WATCH_STATE_WRITE))
    }

    @Test
    fun `a playback source response round-trips its trickplay, and one with none has none`() {
        val withTrickplay = getPlaybackSourceResponse {
            playbackSessionId = "s1"
            trickplay = trickplay {
                urlTemplate = "https://jellyfin.lan/Videos/abc/Trickplay/320/{index}.jpg"
                thumbnailWidth = 320
                thumbnailHeight = 180
                tileColumns = 10
                tileRows = 10
                intervalMillis = 10_000
                thumbnailCount = 720
            }
        }

        val parsed = GetPlaybackSourceResponse.parseFrom(withTrickplay.toByteArray())
        val without = GetPlaybackSourceResponse.parseFrom(getPlaybackSourceResponse { playbackSessionId = "s1" }.toByteArray())

        assertEquals(withTrickplay, parsed)
        assertTrue(parsed.hasTrickplay())
        assertEquals(10_000L, parsed.trickplay.intervalMillis)
        assertTrue(!without.hasTrickplay())
    }
}

private const val UNKNOWN_REASON = 99
