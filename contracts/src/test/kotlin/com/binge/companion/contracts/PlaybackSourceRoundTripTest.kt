package com.binge.companion.contracts

import com.binge.companion.contracts.stream.v1.DynamicRange
import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.stream.v1.sourceQuality
import com.binge.companion.contracts.stream.v1.streamSource
import com.binge.companion.contracts.stream.v1.subtitleTrack
import com.binge.companion.contracts.v1.PlaybackSource
import com.binge.companion.contracts.v1.httpHeader
import com.binge.companion.contracts.v1.playbackSource
import com.google.protobuf.timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The shared `PlaybackSource` and the STREAM source that wraps it survive the wire with the fields
 * the host's rules read: the expiry, the headers by name, the structured quality. `buf` guards the
 * schema; this guards that the lite runtime carries every field a host decision depends on.
 */
class PlaybackSourceRoundTripTest {
    @Test
    fun `a playback source keeps its expiry and its headers in order`() {
        val minted = playbackSource {
            url = "https://cdn.example.invalid/title.m3u8"
            headers += httpHeader {
                name = "Authorization"
                value = "Bearer token"
            }
            headers += httpHeader {
                name = "User-Agent"
                value = "Companion/1.0"
            }
            expiresAt = timestamp { seconds = EXPIRES_AT_SECONDS }
            mimeType = "application/x-mpegURL"
        }

        val parsed = PlaybackSource.parseFrom(minted.toByteArray())

        assertEquals(minted, parsed)
        assertTrue(parsed.hasExpiresAt())
        assertEquals(EXPIRES_AT_SECONDS, parsed.expiresAt.seconds)
        assertEquals(listOf("Authorization", "User-Agent"), parsed.headersList.map { it.name })
    }

    @Test
    fun `a source without an expiry reads as having none, so a host can drop it`() {
        val minted = playbackSource { url = "https://cdn.example.invalid/title.mp4" }

        val parsed = PlaybackSource.parseFrom(minted.toByteArray())

        assertFalse(parsed.hasExpiresAt())
    }

    @Test
    fun `a stream source round-trips its structured quality and subtitles`() {
        val found = streamSource {
            id = "abc"
            source = playbackSource {
                url = "https://cdn.example.invalid/title.mkv"
                expiresAt = timestamp { seconds = EXPIRES_AT_SECONDS }
            }
            quality = sourceQuality {
                resolution = Resolution.RESOLUTION_UHD_2160
                dynamicRange = DynamicRange.DYNAMIC_RANGE_HDR
                videoCodec = "HEVC"
                audio = "TrueHD Atmos"
                sizeBytes = SIZE_BYTES
            }
            label = "Title.2026.2160p.WEB-DL"
            subtitles += subtitleTrack {
                url = "https://cdn.example.invalid/title.en.vtt"
                language = "en"
            }
        }

        val parsed = StreamSource.parseFrom(found.toByteArray())

        assertEquals(found, parsed)
        assertEquals(Resolution.RESOLUTION_UHD_2160, parsed.quality.resolution)
        assertEquals(DynamicRange.DYNAMIC_RANGE_HDR, parsed.quality.dynamicRange)
        assertEquals(SIZE_BYTES, parsed.quality.sizeBytes)
        assertEquals("en", parsed.getSubtitles(0).language)
    }

    private companion object {
        const val EXPIRES_AT_SECONDS = 1_900_000_000L
        const val SIZE_BYTES = 12_345_678_901L
    }
}
