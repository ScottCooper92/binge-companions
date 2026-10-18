package com.binge.companion.contracts

import com.binge.companion.contracts.notifications.v1.AcknowledgeNotificationsRequest
import com.binge.companion.contracts.notifications.v1.AcknowledgeNotificationsResponse
import com.binge.companion.contracts.notifications.v1.HandshakeRequest
import com.binge.companion.contracts.notifications.v1.HandshakeResponse
import com.binge.companion.contracts.notifications.v1.ListNotificationsRequest
import com.binge.companion.contracts.notifications.v1.ListNotificationsResponse
import com.binge.companion.contracts.notifications.v1.NotificationsServiceGrpc
import com.binge.companion.contracts.notifications.v1.NotificationsServiceGrpcKt
import com.binge.companion.contracts.notifications.v1.SetNotificationForwardingRequest
import com.binge.companion.contracts.notifications.v1.SetNotificationForwardingResponse
import com.binge.companion.contracts.notifications.v1.acknowledgeNotificationsRequest
import com.binge.companion.contracts.notifications.v1.handshakeResponse
import com.binge.companion.contracts.notifications.v1.listNotificationsResponse
import com.binge.companion.contracts.notifications.v1.setNotificationForwardingRequest
import com.binge.companion.contracts.v1.ForwardedNotification
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.forwardedNotification
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private val RPC_SHAPES =
    mapOf(
        "Handshake" to MethodType.UNARY,
        "SetNotificationForwarding" to MethodType.UNARY,
        "ListNotifications" to MethodType.UNARY,
        "AcknowledgeNotifications" to MethodType.UNARY,
    )

/**
 * NOTIFICATIONS v1 driven over an in-process gRPC channel, as the REQUEST and STREAM ones are:
 * `buf` guards the wire shape, and these pin the service as a running gRPC surface (the recorded
 * rpcs, that each is reachable, and that a failure is a status code).
 */
class NotificationsServiceContractTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    private fun serve(service: NotificationsServiceGrpcKt.NotificationsServiceCoroutineImplBase): ManagedChannel {
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
        val methods = NotificationsServiceGrpc.getServiceDescriptor().methods.associate { it.bareMethodName!! to it.type }

        assertEquals(RPC_SHAPES, methods)
    }

    @Test
    fun `every rpc is reachable and an unimplemented one fails as a status code, not a response`() {
        val channel = serve(object : NotificationsServiceGrpcKt.NotificationsServiceCoroutineImplBase() {})

        NotificationsServiceGrpc.getServiceDescriptor().methods.forEach { method ->
            val thrown = assertThrows(StatusRuntimeException::class.java, { callWithEmptyRequest(channel, method) }, method.fullMethodName)
            assertEquals(Status.Code.UNIMPLEMENTED, thrown.status.code, method.fullMethodName)
        }
    }

    @Test
    fun `a handshake answers with no session and an empty capability set`() =
        runBlocking {
            val channel =
                serve(
                    object : NotificationsServiceGrpcKt.NotificationsServiceCoroutineImplBase() {
                        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse = handshakeResponse { }
                    },
                )

            val response = NotificationsServiceGrpcKt
                .NotificationsServiceCoroutineStub(
                    channel,
                ).handshake(HandshakeRequest.getDefaultInstance())

            assertEquals(0, response.capabilitiesCount)
        }

    @Test
    fun `forwarding turns on, queued notifications list oldest first, and an acknowledgement removes them`() =
        runBlocking {
            val queue = mutableListOf<ForwardedNotification>()
            var forwarding = false
            val channel =
                serve(
                    object : NotificationsServiceGrpcKt.NotificationsServiceCoroutineImplBase() {
                        override suspend fun setNotificationForwarding(
                            request: SetNotificationForwardingRequest,
                        ): SetNotificationForwardingResponse {
                            forwarding = request.enabled
                            if (forwarding) {
                                queue += forwardedNotification {
                                    id = "a"
                                    title = "Nosferatu is available"
                                    text = "Your request is ready to watch"
                                    media = mediaId {
                                        mediaType = MediaType.MEDIA_TYPE_MOVIE
                                        tmdbId = TMDB_ID
                                    }
                                    kind = "request_available"
                                }
                                queue += forwardedNotification {
                                    id = "b"
                                    title = "3 requests approved"
                                    kind = "request_approved"
                                }
                            } else {
                                queue.clear()
                            }
                            return SetNotificationForwardingResponse.getDefaultInstance()
                        }

                        override suspend fun listNotifications(request: ListNotificationsRequest): ListNotificationsResponse =
                            listNotificationsResponse { notifications += queue }

                        override suspend fun acknowledgeNotifications(
                            request: AcknowledgeNotificationsRequest,
                        ): AcknowledgeNotificationsResponse {
                            queue.removeAll { it.id in request.idsList }
                            return AcknowledgeNotificationsResponse.getDefaultInstance()
                        }
                    },
                )
            val stub = NotificationsServiceGrpcKt.NotificationsServiceCoroutineStub(channel)

            stub.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })
            val listed = stub.listNotifications(ListNotificationsRequest.getDefaultInstance())
            stub.acknowledgeNotifications(acknowledgeNotificationsRequest { ids += "a" })
            val afterAck = stub.listNotifications(ListNotificationsRequest.getDefaultInstance())

            assertEquals(true, forwarding)
            assertEquals(listOf("a", "b"), listed.notificationsList.map { it.id })
            val first = listed.notificationsList.first()
            assertEquals(TMDB_ID, first.media.tmdbId)
            assertEquals("request_available", first.kind)
            assertEquals(false, listed.notificationsList.last().hasMedia())
            assertEquals(listOf("b"), afterAck.notificationsList.map { it.id })
        }

    /** Builds the empty request the method's own marshaller parses, so the call needs no hand-written message. */
    @Suppress("UNCHECKED_CAST")
    private fun callWithEmptyRequest(channel: ManagedChannel, descriptor: MethodDescriptor<*, *>) {
        val method = descriptor as MethodDescriptor<Any, Any>
        val request = method.parseRequest(ByteArrayInputStream(ByteArray(0)))
        ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, request)
    }

    private companion object {
        const val TMDB_ID = 1399
    }
}
