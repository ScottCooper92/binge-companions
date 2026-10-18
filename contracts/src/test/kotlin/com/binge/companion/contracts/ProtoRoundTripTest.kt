package com.binge.companion.contracts

import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.RequestEntry
import com.binge.companion.contracts.request.v1.RequestInfo
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.VersionStatus
import com.binge.companion.contracts.request.v1.mediaFileInfo
import com.binge.companion.contracts.request.v1.requestEntry
import com.binge.companion.contracts.request.v1.requestInfo
import com.binge.companion.contracts.request.v1.requestStatus
import com.binge.companion.contracts.request.v1.versionStatus
import com.binge.companion.contracts.v1.ForwardedNotification
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.forwardedNotification
import com.binge.companion.contracts.v1.mediaId
import com.google.protobuf.timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProtoRoundTripTest {
    @Test
    fun `MediaId round-trips through bytes`() {
        val id = mediaId {
            mediaType = MediaType.MEDIA_TYPE_MOVIE
            tmdbId = 550
        }

        val parsed = MediaId.parseFrom(id.toByteArray())

        assertEquals(id, parsed)
    }

    @Test
    fun `unknown capability values survive a parse by an older peer`() {
        // An older host must tolerate capabilities added after it shipped. Lite parsing
        // keeps unrecognised enum numbers, so nothing is lost on a re-serialise.
        val fromNewerPeer = HandshakeResponse
            .newBuilder()
            .addCapabilitiesValue(9999)
            .addCapabilities(Capability.CAPABILITY_REQUEST_4K)
            .build()

        val parsed = HandshakeResponse.parseFrom(fromNewerPeer.toByteArray())

        assertEquals(2, parsed.capabilitiesValueList.size)
        assertEquals(Capability.CAPABILITY_REQUEST_4K, parsed.capabilitiesList[1])
    }

    @Test
    fun `a version carries a movie's file info and reads as absent when unset`() {
        val withFile = versionStatus {
            is4K = true
            fileInfo = mediaFileInfo {
                fileName = "Movie.Title.2026.2160p.WEB-DL.mkv"
                sizeBytes = 42_000_000_000L
                resolution = "2160p"
                videoCodec = "HEVC"
            }
        }

        val parsed = VersionStatus.parseFrom(withFile.toByteArray())
        assertEquals(withFile.fileInfo, parsed.fileInfo)
        assertEquals(true, parsed.hasFileInfo())
        assertEquals(false, VersionStatus.parseFrom(versionStatus { }.toByteArray()).hasFileInfo())
    }

    @Test
    fun `RequestStatus carries each version apart, with its own availability and watch link`() {
        val status = requestStatus {
            versions += versionStatus { availability = Availability.AVAILABILITY_PENDING }
            versions += versionStatus {
                is4K = true
                availability = Availability.AVAILABILITY_AVAILABLE
                watchUrl = "https://media.example/4k/603"
            }
        }

        val parsed = RequestStatus.parseFrom(status.toByteArray())
        val standard = parsed.versionsList.single { !it.is4K }
        val fourK = parsed.versionsList.single { it.is4K }
        assertEquals(Availability.AVAILABILITY_PENDING, standard.availability)
        assertEquals("", standard.watchUrl)
        assertEquals(Availability.AVAILABILITY_AVAILABLE, fourK.availability)
        assertEquals("https://media.example/4k/603", fourK.watchUrl)
    }

    @Test
    fun `RequestInfo carries is_4k and reads false when the field is absent`() {
        val fourK = requestInfo {
            id = "7"
            is4K = true
        }

        assertEquals(true, RequestInfo.parseFrom(fourK.toByteArray()).is4K)
        assertEquals(false, RequestInfo.parseFrom(requestInfo { id = "7" }.toByteArray()).is4K)
    }

    @Test
    fun `a RequestEntry round-trips its title, status and request`() {
        val entry =
            requestEntry {
                media = mediaId {
                    mediaType = MediaType.MEDIA_TYPE_TV
                    tmdbId = 1399
                }
                status = requestStatus { versions += versionStatus { availability = Availability.AVAILABILITY_AVAILABLE } }
                request = requestInfo {
                    id = "req-12"
                    seasonNumbers += listOf(1, 2)
                }
            }

        assertEquals(entry, RequestEntry.parseFrom(entry.toByteArray()))
    }

    @Test
    fun `a forwarded notification round-trips every field, and reads no title when media is unset`() {
        val aboutATitle = forwardedNotification {
            id = "seerr:available:42"
            title = "Nosferatu is available"
            text = "Your request is ready to watch"
            media = mediaId {
                mediaType = MediaType.MEDIA_TYPE_MOVIE
                tmdbId = 426063
            }
            kind = "request_available"
            createdAt = timestamp { seconds = 1_792_000_000 }
        }
        val summary = forwardedNotification {
            id = "seerr:approved:7"
            title = "3 requests approved"
            kind = "request_approved"
        }

        val parsed = ForwardedNotification.parseFrom(aboutATitle.toByteArray())
        val parsedSummary = ForwardedNotification.parseFrom(summary.toByteArray())

        assertEquals(aboutATitle, parsed)
        assertEquals(true, parsed.hasMedia())
        assertEquals(false, parsedSummary.hasMedia())
        assertEquals(false, parsedSummary.hasCreatedAt())
    }
}
