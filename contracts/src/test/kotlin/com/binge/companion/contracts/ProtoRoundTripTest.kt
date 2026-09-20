package com.binge.companion.contracts

import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeResponse
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
}
