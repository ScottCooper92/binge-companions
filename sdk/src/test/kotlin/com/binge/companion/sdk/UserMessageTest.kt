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
}
