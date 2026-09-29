package com.binge.companion.contracts

import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.mediaFileInfo
import com.binge.companion.contracts.request.v1.requestStatus
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.mediaId
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
    fun `RequestStatus carries a movie's file info and reads as absent when unset`() {
        val withFile = requestStatus {
            fileInfo = mediaFileInfo {
                fileName = "Movie.Title.2026.2160p.WEB-DL.mkv"
                sizeBytes = 42_000_000_000L
                resolution = "2160p"
                videoCodec = "HEVC"
            }
        }

        val parsed = RequestStatus.parseFrom(withFile.toByteArray())
        assertEquals(withFile.fileInfo, parsed.fileInfo)
        assertEquals(true, parsed.hasFileInfo())
        assertEquals(false, RequestStatus.parseFrom(requestStatus { }.toByteArray()).hasFileInfo())
    }
}
