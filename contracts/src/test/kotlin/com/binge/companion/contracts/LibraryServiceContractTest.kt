package com.binge.companion.contracts

import com.binge.companion.contracts.library.v1.Capability
import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.HandshakeResponse
import com.binge.companion.contracts.library.v1.LibraryServiceGrpc
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.PlaybackEvent
import com.binge.companion.contracts.library.v1.PlaybackMethod
import com.binge.companion.contracts.library.v1.PlaybackSegmentKind
import com.binge.companion.contracts.library.v1.ReportPlaybackRequest
import com.binge.companion.contracts.library.v1.ReportPlaybackResponse
import com.binge.companion.contracts.library.v1.episodeRef
import com.binge.companion.contracts.library.v1.getPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.getPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.handshakeResponse
import com.binge.companion.contracts.library.v1.playbackChapter
import com.binge.companion.contracts.library.v1.playbackDeviceProfile
import com.binge.companion.contracts.library.v1.playbackSegment
import com.binge.companion.contracts.library.v1.reportPlaybackRequest
import com.binge.companion.contracts.library.v1.subtitleTrack
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.httpHeader
import com.binge.companion.contracts.v1.mediaId
import com.binge.companion.contracts.v1.playbackSource
import com.google.protobuf.timestamp
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private val RPC_SHAPES =
    mapOf(
        "Handshake" to MethodType.UNARY,
        "GetAvailability" to MethodType.UNARY,
        "ObserveAvailability" to MethodType.SERVER_STREAMING,
        "GetPlayTarget" to MethodType.UNARY,
        "GetWatchState" to MethodType.UNARY,
        "ObserveWatchState" to MethodType.SERVER_STREAMING,
        "SetPlayed" to MethodType.UNARY,
        "ListContinueWatching" to MethodType.UNARY,
        "ListNextUp" to MethodType.UNARY,
        "GetPlaybackSource" to MethodType.UNARY,
        "ReportPlayback" to MethodType.UNARY,
    )

/**
 * LIBRARY v1 driven over an in-process gRPC channel, as [StreamServiceContractTest] drives STREAM: the set of
 * rpcs and whether each streams, that every one is served and reachable, that a failure is a status code
 * rather than a response field, and that a playback sitting round-trips — the source with what the player
 * needs beside it, then the progress the host reports against the session it was given. Adding an rpc fails
 * the first test on purpose, so the change lands with its shape recorded here.
 */
class LibraryServiceContractTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private fun serve(service: LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase): ManagedChannel {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(service)
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return channel
    }

    @AfterEach
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
    }

    @Test
    fun `the service exposes exactly the recorded rpcs, each with its recorded shape`() {
        val methods = LibraryServiceGrpc.getServiceDescriptor().methods.associate { it.bareMethodName!! to it.type }

        assertEquals(RPC_SHAPES, methods)
    }

    @Test
    fun `every rpc is reachable and an unimplemented one fails as a status code, not a response`() {
        val channel = serve(object : LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase() {})

        LibraryServiceGrpc.getServiceDescriptor().methods.forEach { method ->
            val thrown = assertThrows(StatusRuntimeException::class.java, { callWithEmptyRequest(channel, method) }, method.fullMethodName)
            assertEquals(Status.Code.UNIMPLEMENTED, thrown.status.code, method.fullMethodName)
        }
    }

    @Test
    fun `a handshake with no server session is UNAUTHENTICATED, as the header says`() =
        runBlocking {
            val channel =
                serve(
                    object : LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
                            throw StatusException(Status.UNAUTHENTICATED)
                    },
                )

            val thrown =
                assertThrows(StatusException::class.java) {
                    runBlocking {
                        LibraryServiceGrpcKt
                            .LibraryServiceCoroutineStub(
                                channel,
                            ).handshake(HandshakeRequest.getDefaultInstance())
                    }
                }

            assertEquals(Status.Code.UNAUTHENTICATED, thrown.status.code)
        }

    @Test
    fun `a playback sitting round-trips - the source with its tracks and markers, then progress against its session`() =
        runBlocking {
            var sourceRequest: GetPlaybackSourceRequest? = null
            val reports = mutableListOf<ReportPlaybackRequest>()
            val channel =
                serve(
                    object : LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
                            handshakeResponse {
                                capabilities += Capability.CAPABILITY_PLAYBACK_SOURCE
                                capabilities += Capability.CAPABILITY_WATCH_STATE_WRITE
                                capabilities += Capability.CAPABILITY_PLAYBACK_PROGRESS
                                providerName = "Stubfin"
                            }

                        override suspend fun getPlaybackSource(request: GetPlaybackSourceRequest): GetPlaybackSourceResponse {
                            sourceRequest = request
                            return getPlaybackSourceResponse {
                                source = playbackSource {
                                    url = "http://192.168.1.20:8096/Videos/abc/master.m3u8"
                                    mimeType = "application/x-mpegURL"
                                    headers += httpHeader {
                                        name = "Authorization"
                                        value = "MediaBrowser Token=\"t\""
                                    }
                                    expiresAt = timestamp { seconds = EXPIRES_AT_SECONDS }
                                }
                                playbackSessionId = SESSION_ID
                                resumePositionMillis = RESUME_MS
                                runtimeMillis = RUNTIME_MS
                                method = PlaybackMethod.PLAYBACK_METHOD_TRANSCODE
                                subtitles += subtitleTrack {
                                    url = "http://192.168.1.20:8096/Videos/abc/Subtitles/3/Stream.vtt"
                                    language = "en"
                                    label = "English (SDH)"
                                    mimeType = "text/vtt"
                                    hearingImpaired = true
                                }
                                chapters += playbackChapter {
                                    startMillis = 0
                                    title = "Cold open"
                                }
                                segments += playbackSegment {
                                    kind = PlaybackSegmentKind.PLAYBACK_SEGMENT_KIND_INTRO
                                    startMillis = INTRO_START_MS
                                    endMillis = INTRO_END_MS
                                }
                            }
                        }

                        override suspend fun reportPlayback(request: ReportPlaybackRequest): ReportPlaybackResponse {
                            reports += request
                            return ReportPlaybackResponse.getDefaultInstance()
                        }
                    },
                )
            val stub = LibraryServiceGrpcKt.LibraryServiceCoroutineStub(channel)
            val episode = episodeRef {
                seasonNumber = 2
                episodeNumber = 5
            }
            val media = mediaId {
                mediaType = MediaType.MEDIA_TYPE_TV
                tmdbId = TMDB_ID
            }

            val handshake = stub.handshake(HandshakeRequest.getDefaultInstance())
            val response =
                stub.getPlaybackSource(
                    getPlaybackSourceRequest {
                        this.media = media
                        this.episode = episode
                        device = playbackDeviceProfile {
                            videoCodecs += "video/hevc"
                            maxWidth = UHD_WIDTH
                            maxHeight = UHD_HEIGHT
                            supportsHdr = true
                        }
                    },
                )
            stub.reportPlayback(
                reportPlaybackRequest {
                    this.media = media
                    this.episode = episode
                    playbackSessionId = response.playbackSessionId
                    event = PlaybackEvent.PLAYBACK_EVENT_STARTED
                    positionMillis = response.resumePositionMillis
                },
            )
            stub.reportPlayback(
                reportPlaybackRequest {
                    this.media = media
                    this.episode = episode
                    playbackSessionId = response.playbackSessionId
                    event = PlaybackEvent.PLAYBACK_EVENT_FINISHED
                    positionMillis = RUNTIME_MS
                    runtimeMillis = RUNTIME_MS
                },
            )

            assertTrue(Capability.CAPABILITY_PLAYBACK_SOURCE in handshake.capabilitiesList)
            val asked = checkNotNull(sourceRequest)
            assertEquals(TMDB_ID, asked.media.tmdbId)
            assertEquals(5, asked.episode.episodeNumber)
            assertEquals(listOf("video/hevc"), asked.device.videoCodecsList)
            assertTrue(asked.playerPackage.isEmpty(), "the host's own player sends no package")
            assertEquals(SESSION_ID, response.playbackSessionId)
            assertEquals(RESUME_MS, response.resumePositionMillis)
            assertEquals(PlaybackMethod.PLAYBACK_METHOD_TRANSCODE, response.method)
            assertEquals("text/vtt", response.subtitlesList.single().mimeType)
            assertEquals(PlaybackSegmentKind.PLAYBACK_SEGMENT_KIND_INTRO, response.segmentsList.single().kind)
            assertEquals(
                listOf(PlaybackEvent.PLAYBACK_EVENT_STARTED, PlaybackEvent.PLAYBACK_EVENT_FINISHED),
                reports.map { it.event },
            )
            assertTrue(reports.all { it.playbackSessionId == SESSION_ID })
            assertEquals(RESUME_MS, reports.first().positionMillis)
        }

    /** Builds the empty request the method's own marshaller parses, so the call needs no hand-written message. */
    @Suppress("UNCHECKED_CAST")
    private fun callWithEmptyRequest(channel: ManagedChannel, descriptor: MethodDescriptor<*, *>) {
        val method = descriptor as MethodDescriptor<Any, Any>
        val request = method.parseRequest(ByteArrayInputStream(ByteArray(0)))
        when (method.type) {
            MethodType.SERVER_STREAMING ->
                ClientCalls
                    .blockingServerStreamingCall(
                        channel,
                        method,
                        CallOptions.DEFAULT,
                        request,
                    ).forEach { }
            else -> ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, request)
        }
    }

    private companion object {
        const val TMDB_ID = 1399
        const val EXPIRES_AT_SECONDS = 1_900_000_000L
        const val SESSION_ID = "sess-42"
        const val RESUME_MS = 754_000L
        const val RUNTIME_MS = 3_312_000L
        const val INTRO_START_MS = 60_000L
        const val INTRO_END_MS = 150_000L
        const val UHD_WIDTH = 3840
        const val UHD_HEIGHT = 2160
    }
}
