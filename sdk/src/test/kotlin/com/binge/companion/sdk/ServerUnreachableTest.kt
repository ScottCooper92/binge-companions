package com.binge.companion.sdk

import com.binge.companion.contracts.rpc.ErrorInfo
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.binge.companion.contracts.rpc.Status as RpcStatus

/** The server-unreachable detail (#299): what a companion raises, what a host reads back, and what it ignores. */
class ServerUnreachableTest {
    @Test
    fun `a raised failure reads back with its server name`() {
        val failure = serverUnreachable(serverName = "Living room Jellyfin")

        assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
        assertEquals(ServerUnreachable("Living room Jellyfin"), serverUnreachableOf(failure.trailers))
    }

    @Test
    fun `a timeout is DEADLINE_EXCEEDED with the same detail`() {
        val failure = serverUnreachable(serverName = "Seerr", timedOut = true)

        assertEquals(Status.Code.DEADLINE_EXCEEDED, failure.status.code)
        assertEquals(ServerUnreachable("Seerr"), serverUnreachableOf(failure.trailers))
    }

    @Test
    fun `no name reads as null, and so does a blank one`() {
        listOf(null, "", "   ", "\u0000‮").forEach { name ->
            assertEquals(ServerUnreachable(null), serverUnreachableOf(serverUnreachable(name).trailers), "'$name'")
        }
    }

    @Test
    fun `the detail is an ErrorInfo with the documented reason, domain and key`() {
        val status = RpcStatus.parseFrom(serverUnreachable("Plex").trailers!!.get(STATUS_DETAILS_KEY)!!)
        val info = ErrorInfo.parseFrom(status.detailsList.single().value)

        assertEquals("type.googleapis.com/google.rpc.ErrorInfo", status.detailsList.single().typeUrl)
        assertEquals("SERVER_UNREACHABLE", info.reason)
        assertEquals("binge.companion", info.domain)
        assertEquals(mapOf("server_name" to "Plex"), info.metadataMap)
        assertEquals(Status.Code.UNAVAILABLE.value(), status.code)
    }

    /** The host cannot trust a third-party companion, and every host reads through this parser. */
    @Test
    fun `the reader cleans a name a hostile companion sent`() {
        val nasty = "  ‮Evil⁦ server\u0007\n  "
        val sent = detailsOnly(reason = "SERVER_UNREACHABLE", domain = "binge.companion", name = nasty)

        assertEquals(ServerUnreachable("Evil server"), serverUnreachableOf(sent))
    }

    @Test
    fun `a long name is cut to 60 code points without splitting a pair`() {
        val emoji = "📺" // one code point, two chars
        val sent = detailsOnly(reason = "SERVER_UNREACHABLE", domain = "binge.companion", name = emoji.repeat(100))

        val name = serverUnreachableOf(sent)!!.serverName!!
        assertEquals(60, name.codePointCount(0, name.length))
        assertEquals(emoji.repeat(60), name)
        assertEquals(60, serverUnreachableOf(serverUnreachable("a".repeat(100)).trailers)!!.serverName!!.length)
    }

    @Test
    fun `a cut never leaves trailing space`() {
        val sent = detailsOnly("SERVER_UNREACHABLE", "binge.companion", "a".repeat(59) + " bcd")

        assertEquals("a".repeat(59), serverUnreachableOf(sent)!!.serverName)
    }

    @Test
    fun `another reason or domain is not this detail`() {
        assertNull(serverUnreachableOf(detailsOnly("QUOTA_EXCEEDED", "binge.companion", "x")))
        assertNull(serverUnreachableOf(detailsOnly("SERVER_UNREACHABLE", "example.com", "x")))
    }

    @Test
    fun `no detail, a malformed one, or a user message alone reads as none`() {
        assertNull(serverUnreachableOf(null as Metadata?))
        assertNull(serverUnreachableOf(Metadata()))
        val garbage = Metadata().apply { put(STATUS_DETAILS_KEY, byteArrayOf(1, 2, 3)) }
        assertNull(serverUnreachableOf(garbage))
        val userOnly = Status.RESOURCE_EXHAUSTED.withUserMessage("QUOTA", "Resets Monday.", "en")
        assertNull(serverUnreachableOf(userOnly.trailers))
    }

    @Test
    fun `a message rides beside it and the user message reader still sees it`() {
        val failure = serverUnreachable("Jellyfin", message = "Jellyfin isn't answering.", locale = "en-GB")

        assertEquals(ServerUnreachable("Jellyfin"), serverUnreachableOf(failure.trailers))
        assertEquals(
            UserMessage(reason = "SERVER_UNREACHABLE", message = "Jellyfin isn't answering.", locale = "en-GB"),
            userMessageOf(failure.trailers),
        )
    }

    @Test
    fun `a message without its locale is refused`() {
        assertThrows(IllegalArgumentException::class.java) { serverUnreachable("Jellyfin", message = "Down.") }
    }

    @Test
    fun `without a message there is no user message`() {
        assertNull(userMessageOf(serverUnreachable("Jellyfin").trailers))
    }

    @Test
    fun `a host reads it from the failure itself, only on the two retryable codes`() {
        assertEquals(ServerUnreachable("Emby"), serverUnreachableOf(serverUnreachable("Emby")))
        assertEquals(ServerUnreachable("Emby"), serverUnreachableOf(serverUnreachable("Emby", timedOut = true)))
        // The same bytes on any other code are not this signal.
        val wrongCode = StatusException(Status.UNAUTHENTICATED, serverUnreachable("Emby").trailers)
        assertNull(serverUnreachableOf(wrongCode))
        // A transport failure has no detail at all.
        assertNull(serverUnreachableOf(StatusException(Status.UNAVAILABLE)))
        assertNull(serverUnreachableOf(IllegalStateException("not grpc")))
    }

    private fun detailsOnly(reason: String, domain: String, name: String): Metadata {
        val info =
            ErrorInfo
                .newBuilder()
                .setReason(reason)
                .setDomain(domain)
                .putMetadata("server_name", name)
                .build()
        val status =
            RpcStatus
                .newBuilder()
                .setCode(Status.Code.UNAVAILABLE.value())
                .addDetails(pack(info, "google.rpc.ErrorInfo"))
                .build()
        return Metadata().apply { put(STATUS_DETAILS_KEY, status.toByteArray()) }
    }
}
