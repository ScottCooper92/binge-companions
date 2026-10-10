package com.binge.companion.sdk

import io.grpc.Metadata
import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The user-facing detail channel (#120): what a companion packs, a host reads back, and nothing leaks into the description. */
class UserMessageTest {
    @Test
    fun `a packed message reads back with its reason and locale`() {
        val failure =
            Status.RESOURCE_EXHAUSTED
                .withDescription("quota spent")
                .withUserMessage(reason = "QUOTA_EXCEEDED", message = "Your request quota resets on Monday.", locale = "en-GB")

        assertEquals(
            UserMessage(reason = "QUOTA_EXCEEDED", message = "Your request quota resets on Monday.", locale = "en-GB"),
            userMessageOf(failure.trailers),
        )
    }

    @Test
    fun `the sentence never goes in the status description`() {
        val failure = Status.FAILED_PRECONDITION.withUserMessage("BLOCKLISTED", "Blocklisted by Grace.", "en")

        assertFalse(
            failure.status.description
                .orEmpty()
                .contains("Grace"),
        )
    }

    @Test
    fun `no detail, or a malformed one, reads as none`() {
        assertNull(userMessageOf(null))
        assertNull(userMessageOf(Metadata()))
        val garbage = Metadata().apply {
            put(
                Metadata.Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER),
                byteArrayOf(1, 2, 3),
            )
        }
        assertNull(userMessageOf(garbage))
    }

    /** The host's side (#167): the failure arrives wrapped, its trailers rebuilt from the wire's bytes. */
    @Test
    fun `a host reads the sentence from a failed call's trailers`() {
        val sent = Status.NOT_FOUND.withUserMessage(reason = "GONE", message = "This title was removed.", locale = "en")
        val key = Metadata.Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER)
        val received = Metadata().apply { put(key, checkNotNull(sent.trailers?.get(key))) }

        val caught = RuntimeException(io.grpc.StatusException(Status.NOT_FOUND, received))

        assertEquals(
            UserMessage(reason = "GONE", message = "This title was removed.", locale = "en"),
            userMessageOf(Status.trailersFromThrowable(caught)),
        )
    }
}
