package com.binge.companion.contracts

import com.binge.companion.contracts.stream.v1.Capability
import com.binge.companion.contracts.stream.v1.HandshakeRequest
import com.binge.companion.contracts.stream.v1.HandshakeResponse
import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.stream.v1.ResolveRequest
import com.binge.companion.contracts.stream.v1.ResolveResponse
import com.binge.companion.contracts.stream.v1.StreamServiceGrpc
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.stream.v1.handshakeResponse
import com.binge.companion.contracts.stream.v1.resolveRequest
import com.binge.companion.contracts.stream.v1.resolveResponse
import com.binge.companion.contracts.stream.v1.sourceQuality
import com.binge.companion.contracts.stream.v1.streamSource
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.episodeRef
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private val RPC_SHAPES =
    mapOf(
        "Handshake" to MethodType.UNARY,
        "Resolve" to MethodType.SERVER_STREAMING,
        "Probe" to MethodType.UNARY,
        "ReportSource" to MethodType.UNARY,
    )

/**
 * STREAM v1 driven over an in-process gRPC channel, as [RequestServiceContractTest] drives REQUEST:
 * the set of rpcs and whether each streams, that every one is served and reachable, that a failure
 * is a status code rather than a response field, and that `Resolve` streams sources in the order the
 * integration found them and then completes. Adding an rpc fails the first test on purpose, so the
 * change lands with its shape recorded here.
 */
class StreamServiceContractTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private fun serve(service: StreamServiceGrpcKt.StreamServiceCoroutineImplBase): ManagedChannel {
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
        val methods = StreamServiceGrpc.getServiceDescriptor().methods.associate { it.bareMethodName!! to it.type }

        assertEquals(RPC_SHAPES, methods)
    }

    @Test
    fun `every rpc is reachable and an unimplemented one fails as a status code, not a response`() {
        val channel = serve(object : StreamServiceGrpcKt.StreamServiceCoroutineImplBase() {})

        StreamServiceGrpc.getServiceDescriptor().methods.forEach { method ->
            val thrown = assertThrows(StatusRuntimeException::class.java, { callWithEmptyRequest(channel, method) }, method.fullMethodName)
            assertEquals(Status.Code.UNIMPLEMENTED, thrown.status.code, method.fullMethodName)
        }
    }

    @Test
    fun `a handshake with no session is UNAUTHENTICATED, as the header says`() =
        runBlocking {
            val channel =
                serve(
                    object : StreamServiceGrpcKt.StreamServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
                            throw StatusException(Status.UNAUTHENTICATED)
                    },
                )

            val thrown =
                assertThrows(StatusException::class.java) {
                    runBlocking { StreamServiceGrpcKt.StreamServiceCoroutineStub(channel).handshake(HandshakeRequest.getDefaultInstance()) }
                }

            assertEquals(Status.Code.UNAUTHENTICATED, thrown.status.code)
        }

    @Test
    fun `Resolve streams every source in the order found, with its episode request intact, then completes`() =
        runBlocking {
            var seen: ResolveRequest? = null
            val channel =
                serve(
                    object : StreamServiceGrpcKt.StreamServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
                            handshakeResponse {
                                capabilities += Capability.CAPABILITY_PROBE
                                providerName = "Stubstream"
                            }

                        override fun resolve(request: ResolveRequest): Flow<ResolveResponse> =
                            flow {
                                seen = request
                                RESOLUTIONS.forEachIndexed { index, resolution ->
                                    emit(
                                        resolveResponse {
                                            source = streamSource {
                                                id = "source-$index"
                                                this.source = playbackSource {
                                                    url = "https://example.invalid/$index.mp4"
                                                    expiresAt = timestamp { seconds = EXPIRES_AT_SECONDS }
                                                }
                                                quality = sourceQuality { this.resolution = resolution }
                                            }
                                        },
                                    )
                                }
                            }
                    },
                )
            val stub = StreamServiceGrpcKt.StreamServiceCoroutineStub(channel)

            val handshake = stub.handshake(HandshakeRequest.getDefaultInstance())
            val sources =
                stub
                    .resolve(
                        resolveRequest {
                            media = mediaId {
                                mediaType = MediaType.MEDIA_TYPE_TV
                                tmdbId = TMDB_ID
                            }
                            episode = episodeRef {
                                seasonNumber = 1
                                episodeNumber = 3
                            }
                        },
                    ).toList()

            assertEquals(listOf(Capability.CAPABILITY_PROBE), handshake.capabilitiesList)
            assertEquals(RESOLUTIONS, sources.map { it.source.quality.resolution })
            assertEquals(listOf("source-0", "source-1", "source-2"), sources.map { it.source.id })
            val firstSource = sources.first().source.source
            assertEquals(EXPIRES_AT_SECONDS, firstSource.expiresAt.seconds)
            val received = checkNotNull(seen)
            assertEquals(TMDB_ID, received.media.tmdbId)
            assertEquals(1, received.episode.seasonNumber)
            assertEquals(3, received.episode.episodeNumber)
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
        val RESOLUTIONS =
            listOf(Resolution.RESOLUTION_UHD_2160, Resolution.RESOLUTION_HD_1080, Resolution.RESOLUTION_SD)
    }
}
