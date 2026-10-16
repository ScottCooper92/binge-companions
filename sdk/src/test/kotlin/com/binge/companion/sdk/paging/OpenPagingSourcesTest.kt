package com.binge.companion.sdk.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A source that holds nothing: only whether it has been invalidated matters here. */
private class EmptySource : PagingSource<Int, String>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> = LoadResult.Page(emptyList(), null, null)

    override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
}

class OpenPagingSourcesTest {
    private val sources = OpenPagingSources<Int, String>()

    @Test
    fun `track hands back the source it was given`() {
        val source = EmptySource()

        assertSame(source, sources.track(source))
    }

    @Test
    fun `a write that lands invalidates every source handed out`() =
        runBlocking {
            val first = sources.track(EmptySource())
            val second = sources.track(EmptySource())

            val answer = sources.afterWrite { 42 }

            assertEquals(42, answer)
            assertTrue(first.invalid)
            assertTrue(second.invalid)
        }

    @Test
    fun `a write that fails invalidates nothing`() =
        runBlocking {
            val source = sources.track(EmptySource())

            assertThrows(IllegalStateException::class.java) { runBlocking { sources.afterWrite { error("the write failed") } } }

            assertFalse(source.invalid)
        }

    @Test
    fun `a source invalidated on its own does not disturb the others`() =
        runBlocking {
            val gone = sources.track(EmptySource())
            gone.invalidate()
            val kept = sources.track(EmptySource())

            sources.afterWrite { }

            assertTrue(kept.invalid)
            assertTrue(gone.invalid)
        }
}
