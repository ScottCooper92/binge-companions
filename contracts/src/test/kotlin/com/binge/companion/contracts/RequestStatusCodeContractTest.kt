package com.binge.companion.contracts

import com.binge.companion.contracts.request.v1.EditRequestRequest
import com.binge.companion.contracts.request.v1.EditRequestResponse
import com.binge.companion.contracts.request.v1.GetAttentionRequest
import com.binge.companion.contracts.request.v1.GetAttentionResponse
import com.binge.companion.contracts.request.v1.ObserveAttentionRequest
import com.binge.companion.contracts.request.v1.ObserveAttentionResponse
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.request.v1.UnblockTitleRequest
import com.binge.companion.contracts.request.v1.UnblockTitleResponse
import com.binge.companion.contracts.request.v1.attention
import com.binge.companion.contracts.request.v1.editRequestRequest
import com.binge.companion.contracts.request.v1.getAttentionResponse
import com.binge.companion.contracts.request.v1.observeAttentionResponse
import com.binge.companion.contracts.request.v1.submitRequestRequest
import com.binge.companion.contracts.request.v1.unblockTitleRequest
import com.binge.companion.contracts.rpc.LocalizedMessage
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.mediaId
import com.google.protobuf.Any
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.binge.companion.contracts.rpc.Status as RpcStatus

/**
 * The status codes `request.proto` documents, driven through the generated client against a stub that
 * answers as the contract says a provider must. The stub is the contract's reading, not a provider:
 * what fails here is the stubs, the service shape or the documented mapping drifting apart.
 */
class RequestStatusCodeContractTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private fun stub(connected: Boolean = true): RequestServiceGrpcKt.RequestServiceCoroutineStub {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(DocumentedProvider(connected))
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
    }

    @AfterEach
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
    }

    private fun edit(id: Int, seasons: List<Int>): EditRequestRequest =
        editRequestRequest {
            requestId = id
            seasonNumbers += seasons
        }

    private fun codeOf(block: suspend () -> Unit): Status.Code =
        assertThrows(StatusException::class.java) { runBlocking { block() } }.status.code

    @Test
    fun `EditRequest with an empty season set is INVALID_ARGUMENT`() {
        val stub = stub()

        assertEquals(Status.Code.INVALID_ARGUMENT, codeOf { stub.editRequest(edit(KNOWN_ID, emptyList())) })
    }

    @Test
    fun `EditRequest for an unknown id is NOT_FOUND`() {
        val stub = stub()

        assertEquals(Status.Code.NOT_FOUND, codeOf { stub.editRequest(edit(UNKNOWN_ID, listOf(1))) })
    }

    @Test
    fun `EditRequest the user may not make is PERMISSION_DENIED`() {
        val stub = stub()

        assertEquals(Status.Code.PERMISSION_DENIED, codeOf { stub.editRequest(edit(FOREIGN_ID, listOf(1))) })
    }

    @Test
    fun `a valid EditRequest answers OK with an empty response`() =
        runBlocking {
            assertEquals(EditRequestResponse.getDefaultInstance(), stub().editRequest(edit(KNOWN_ID, listOf(1, 2))))
        }

    /** The header's user-facing detail channel: a google.rpc.Status in the trailers survives the channel intact (#120). */
    @Test
    fun `a rich error detail survives the channel, and the description stays developer text`() {
        val stub = stub()
        val request =
            submitRequestRequest {
                media = mediaId {
                    mediaType = MediaType.MEDIA_TYPE_MOVIE
                    tmdbId = 1
                }
            }

        val failure = assertThrows(StatusException::class.java) { runBlocking { stub.submitRequest(request) } }
        val details = RpcStatus.parseFrom(failure.trailers!!.get(STATUS_DETAILS_KEY))
        val localized = LocalizedMessage.parseFrom(details.detailsList.single().value)

        assertEquals(Status.Code.RESOURCE_EXHAUSTED, failure.status.code)
        assertEquals(QUOTA_SENTENCE, localized.message)
        assertEquals("quota spent", failure.status.description)
    }

    @Test
    fun `UnblockTitle for a title not on the blocklist is NOT_FOUND`() {
        val stub = stub()
        val request =
            unblockTitleRequest {
                media = mediaId {
                    mediaType = MediaType.MEDIA_TYPE_MOVIE
                    tmdbId = 1
                }
            }

        assertEquals(Status.Code.NOT_FOUND, codeOf { stub.unblockTitle(request) })
    }

    @Test
    fun `GetAttention reports a broken session as data and a missing connection as UNAUTHENTICATED`() =
        runBlocking {
            val broken = stub().getAttention(GetAttentionRequest.getDefaultInstance())
            assertEquals(true, broken.attention.needsReconnect)

            val disconnected = stub(connected = false)
            assertEquals(Status.Code.UNAUTHENTICATED, codeOf { disconnected.getAttention(GetAttentionRequest.getDefaultInstance()) })
        }

    @Test
    fun `ObserveAttention streams its values and then fails the stream with UNAUTHENTICATED`() =
        runBlocking {
            val seen = mutableListOf<Int>()
            val failure =
                assertThrows(StatusException::class.java) {
                    runBlocking {
                        stub().observeAttention(ObserveAttentionRequest.getDefaultInstance()).collect { seen += it.attention.pendingCount }
                    }
                }

            assertEquals(listOf(2, 5), seen)
            assertEquals(Status.Code.UNAUTHENTICATED, failure.status.code)
        }

    private class DocumentedProvider(
        private val connected: Boolean,
    ) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
        override suspend fun editRequest(request: EditRequestRequest): EditRequestResponse =
            when {
                request.requestId == UNKNOWN_ID -> throw Status.NOT_FOUND.asException()
                request.requestId == FOREIGN_ID -> throw Status.PERMISSION_DENIED.asException()
                request.seasonNumbersList.isEmpty() -> throw Status.INVALID_ARGUMENT.asException()
                else -> EditRequestResponse.getDefaultInstance()
            }

        override suspend fun unblockTitle(request: UnblockTitleRequest): UnblockTitleResponse = throw Status.NOT_FOUND.asException()

        /** A spent quota with the sentence the user needs, sent as the header's rich error detail. */
        override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse {
            val details =
                RpcStatus
                    .newBuilder()
                    .setCode(Status.Code.RESOURCE_EXHAUSTED.value())
                    .addDetails(
                        Any
                            .newBuilder()
                            .setTypeUrl("type.googleapis.com/google.rpc.LocalizedMessage")
                            .setValue(
                                LocalizedMessage
                                    .newBuilder()
                                    .setLocale("en")
                                    .setMessage(QUOTA_SENTENCE)
                                    .build()
                                    .toByteString(),
                            ),
                    ).build()
            val trailers = Metadata().apply { put(STATUS_DETAILS_KEY, details.toByteArray()) }
            throw StatusException(Status.RESOURCE_EXHAUSTED.withDescription("quota spent"), trailers)
        }

        override suspend fun getAttention(request: GetAttentionRequest): GetAttentionResponse =
            if (connected) {
                getAttentionResponse { attention = attention { needsReconnect = true } }
            } else {
                throw Status.UNAUTHENTICATED.asException()
            }

        override fun observeAttention(request: ObserveAttentionRequest): Flow<ObserveAttentionResponse> =
            flow {
                listOf(2, 5).forEach { count -> emit(observeAttentionResponse { attention = attention { pendingCount = count } }) }
                throw Status.UNAUTHENTICATED.asException()
            }
    }

    private companion object {
        const val KNOWN_ID = 7
        const val UNKNOWN_ID = 404
        const val FOREIGN_ID = 403
    }
}

private const val QUOTA_SENTENCE = "Your request quota resets on Monday."
private val STATUS_DETAILS_KEY: Metadata.Key<ByteArray> = Metadata.Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER)
