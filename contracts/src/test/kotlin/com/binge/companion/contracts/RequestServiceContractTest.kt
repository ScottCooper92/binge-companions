package com.binge.companion.contracts

import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.ObserveStatusRequest
import com.binge.companion.contracts.request.v1.ObserveStatusResponse
import com.binge.companion.contracts.request.v1.RequestServiceGrpc
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.request.v1.handshakeResponse
import com.binge.companion.contracts.request.v1.observeStatusResponse
import com.binge.companion.contracts.request.v1.requestStatus
import com.binge.companion.contracts.request.v1.submitRequestRequest
import com.binge.companion.contracts.request.v1.submitRequestResponse
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.mediaId
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.MethodType
import io.grpc.Server
import io.grpc.Status
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
        "SubmitRequest" to MethodType.UNARY,
        "GetStatus" to MethodType.UNARY,
        "ObserveStatus" to MethodType.SERVER_STREAMING,
        "ListRequests" to MethodType.UNARY,
        "GetStatuses" to MethodType.UNARY,
        "EditRequest" to MethodType.UNARY,
        "GetAdvancedRequestOptions" to MethodType.UNARY,
        "GetDestinationOptions" to MethodType.UNARY,
        "SubmitAdvancedRequest" to MethodType.UNARY,
        "CancelRequest" to MethodType.UNARY,
        "ApproveRequest" to MethodType.UNARY,
        "DeclineRequest" to MethodType.UNARY,
        "RetryRequest" to MethodType.UNARY,
        "ReportIssue" to MethodType.UNARY,
        "BlockTitle" to MethodType.UNARY,
        "UnblockTitle" to MethodType.UNARY,
        "GetAttention" to MethodType.UNARY,
        "ObserveAttention" to MethodType.SERVER_STREAMING,
    )

/**
 * REQUEST v1 driven over an in-process gRPC channel, which needs no Binder, emulator or device: the
 * transport is the only part of the contract that does.
 *
 * `buf` guards the wire shape of the messages. What it cannot see is the service as a running gRPC
 * surface, so these pin that: the set of rpcs and whether each streams, that every one of them is
 * actually served and reachable, and that a failure arrives as a gRPC status code rather than as a
 * field on a response, which is the contract's error model. Adding an rpc is additive and allowed, and
 * fails the first test on purpose so the change is deliberate and lands with its shape recorded here.
 */
class RequestServiceContractTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private fun serve(service: RequestServiceGrpcKt.RequestServiceCoroutineImplBase): ManagedChannel {
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
        val methods = RequestServiceGrpc.getServiceDescriptor().methods.associate { it.bareMethodName!! to it.type }

        assertEquals(RPC_SHAPES, methods)
    }

    @Test
    fun `every rpc is reachable and an unimplemented one fails as a status code, not a response`() {
        val channel = serve(object : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {})

        RequestServiceGrpc.getServiceDescriptor().methods.forEach { method ->
            val thrown = assertThrows(StatusRuntimeException::class.java, { callWithEmptyRequest(channel, method) }, method.fullMethodName)
            assertEquals(Status.Code.UNIMPLEMENTED, thrown.status.code, method.fullMethodName)
        }
    }

    @Test
    fun `a handshake and a submit carry their fields across the channel`() =
        runBlocking {
            var seen: SubmitRequestRequest? = null
            val channel =
                serve(
                    object : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
                            handshakeResponse {
                                capabilities += Capability.CAPABILITY_REQUEST_4K
                                providerName = "Stubbarr"
                            }

                        override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse {
                            seen = request
                            return submitRequestResponse { requestId = REQUEST_ID }
                        }
                    },
                )
            val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)

            val handshake = stub.handshake(HandshakeRequest.getDefaultInstance())
            val submit =
                stub.submitRequest(
                    submitRequestRequest {
                        media = mediaId {
                            mediaType = MediaType.MEDIA_TYPE_TV
                            tmdbId = TMDB_ID
                        }
                        seasonNumbers += listOf(1, 2)
                        is4K = true
                    },
                )

            assertEquals(listOf(Capability.CAPABILITY_REQUEST_4K), handshake.capabilitiesList)
            assertEquals("Stubbarr", handshake.providerName)
            assertEquals(REQUEST_ID, submit.requestId)
            val received = checkNotNull(seen)
            assertEquals(MediaType.MEDIA_TYPE_TV, received.media.mediaType)
            assertEquals(TMDB_ID, received.media.tmdbId)
            assertEquals(listOf(1, 2), received.seasonNumbersList)
            assertEquals(true, received.is4K)
        }

    @Test
    fun `ObserveStatus streams every update in order and then completes`() =
        runBlocking {
            val channel =
                serve(
                    object : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
                        override fun observeStatus(request: ObserveStatusRequest): Flow<ObserveStatusResponse> =
                            flow {
                                AVAILABILITY_SEQUENCE.forEach { state ->
                                    emit(observeStatusResponse { status = requestStatus { availability = state } })
                                }
                            }
                    },
                )

            val updates = RequestServiceGrpcKt
                .RequestServiceCoroutineStub(
                    channel,
                ).observeStatus(ObserveStatusRequest.getDefaultInstance())
                .toList()

            assertEquals(AVAILABILITY_SEQUENCE, updates.map { it.status.availability })
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
        const val REQUEST_ID = 4321
        const val TMDB_ID = 1399
        val AVAILABILITY_SEQUENCE =
            listOf(Availability.AVAILABILITY_PENDING, Availability.AVAILABILITY_PROCESSING, Availability.AVAILABILITY_AVAILABLE)
    }
}
