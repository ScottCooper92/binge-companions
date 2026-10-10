package com.binge.companion.sdk

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class AttemptTest {
    @Test
    fun `a block that returns is a success`() {
        assertEquals(Result.success(42), attempt { 42 })
    }

    @Test
    fun `an ordinary failure is kept as a failure`() {
        val result = attempt { throw IOException("offline") }

        assertInstanceOf(IOException::class.java, result.exceptionOrNull())
    }

    @Test
    fun `cancellation is rethrown rather than kept`() {
        assertThrows<CancellationException> { attempt { throw CancellationException("scope cancelled") } }
    }
}
