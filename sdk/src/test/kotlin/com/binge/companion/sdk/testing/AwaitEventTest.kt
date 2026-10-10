package com.binge.companion.sdk.testing

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwaitEventTest {
    @Test
    fun `it has subscribed by the time it returns, so an emission straight after is caught`() =
        runBlocking {
            val events = MutableSharedFlow<String>(extraBufferCapacity = 1)

            val event = awaitEvent(events)

            assertEquals(1, events.subscriptionCount.value)
            assertTrue(events.tryEmit("saved"))
            assertEquals("saved", event.await())
        }

    @Test
    fun `the predicate overload skips events until one matches`() =
        runBlocking {
            val events = MutableSharedFlow<String>(extraBufferCapacity = 2)

            val event = awaitEvent(events) { it.startsWith("s") }

            assertTrue(events.tryEmit("notice"))
            assertTrue(events.tryEmit("saved"))
            assertEquals("saved", event.await())
        }
}
