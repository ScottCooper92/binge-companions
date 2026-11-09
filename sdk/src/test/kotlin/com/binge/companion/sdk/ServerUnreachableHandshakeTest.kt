package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * A failed `Handshake` may carry the server-unreachable detail, and a host reads it there although no
 * capability set came back to declare it (#299). Driven through the generated client over an in-process
 * channel, so the detail travels the wire as a real trailer.
 */
class ServerUnreachableHandshakeTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @AfterEach
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun failedHandshake(failure: StatusException): StatusException {
        val name = InProcessServerBuilder.generateName()
        server =
            InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(DownProvider(failure))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
        return assertThrows(StatusException::class.java) {
            runBlocking { stub.handshake(HandshakeRequest.getDefaultInstance()) }
        }
    }

    @Test
    fun `the reader accepts the detail on a failed handshake with no capability declared`() {
        val received = failedHandshake(serverUnreachable("Living room Jellyfin"))

        assertEquals(Status.Code.UNAVAILABLE, received.status.code)
        assertEquals(ServerUnreachable("Living room Jellyfin"), serverUnreachableOf(received.trailers))
        assertEquals(ServerUnreachable("Living room Jellyfin"), serverUnreachableOf(received))
    }

    @Test
    fun `a handshake that timed out carries it as DEADLINE_EXCEEDED`() {
        val received = failedHandshake(serverUnreachable("Seerr", timedOut = true))

        assertEquals(Status.Code.DEADLINE_EXCEEDED, received.status.code)
        assertEquals(ServerUnreachable("Seerr"), serverUnreachableOf(received))
    }

    private class DownProvider(
        private val failure: StatusException,
    ) : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse = throw failure
    }
}
