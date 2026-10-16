package com.binge.companion.sdk.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private const val PAGE_SIZE = 2

/** A list of numbered items [total] long, in pages of [PAGE_SIZE], that records what it was asked for. */
private class NumberedSource(
    private val total: Int,
    private val reportsPageCount: Boolean = true,
    private val failure: Throwable? = null,
) : OffsetPagingSource<Int>(PAGE_SIZE) {
    val asked = mutableListOf<Pair<Int, Int>>()

    override suspend fun loadPage(take: Int, skip: Int): OffsetPage<Int> {
        asked += take to skip
        failure?.let { throw it }
        val items = (skip until minOf(skip + take, total)).toList()
        return OffsetPage(items, totalPages = if (reportsPageCount) (total + take - 1) / take else null)
    }
}

class OffsetPagingSourceTest {
    private fun load(source: PagingSource<Int, Int>, key: Int?) =
        runBlocking { source.load(PagingSource.LoadParams.Refresh(key, PAGE_SIZE, false)) }

    @Test
    fun `the first page has no previous key and a next one`() {
        val source = NumberedSource(total = 5)

        val page = load(source, null) as PagingSource.LoadResult.Page

        assertEquals(listOf(0, 1), page.data)
        assertNull(page.prevKey)
        assertEquals(1, page.nextKey)
        assertEquals(listOf(PAGE_SIZE to 0), source.asked)
    }

    @Test
    fun `a page asks for its own skip`() {
        val source = NumberedSource(total = 7)

        val page = load(source, 2) as PagingSource.LoadResult.Page

        assertEquals(listOf(4, 5), page.data)
        assertEquals(1, page.prevKey)
        assertEquals(3, page.nextKey)
        assertEquals(listOf(PAGE_SIZE to 4), source.asked)
    }

    @Test
    fun `the last page has no next key`() {
        val page = load(NumberedSource(total = 5), 2) as PagingSource.LoadResult.Page

        assertEquals(listOf(4), page.data)
        assertNull(page.nextKey)
    }

    @Test
    fun `a server that sends no page count sent its last page`() {
        val page = load(NumberedSource(total = 5, reportsPageCount = false), null) as PagingSource.LoadResult.Page

        assertNull(page.nextKey)
    }

    @Test
    fun `a failure is a load error the list can retry from`() {
        val boom = IllegalStateException("offline")

        val result = load(NumberedSource(total = 5, failure = boom), null)

        assertSame(boom, (result as PagingSource.LoadResult.Error).throwable)
    }

    @Test
    fun `cancellation is not turned into a load error`() {
        assertThrows(CancellationException::class.java) { load(NumberedSource(total = 5, failure = CancellationException("gone")), null) }
    }

    @Test
    fun `a refresh restarts at the page the user was on`() {
        val source = NumberedSource(total = 9)
        val page = load(source, 2) as PagingSource.LoadResult.Page
        val state = PagingState(listOf(page), anchorPosition = 0, config = PagingConfig(PAGE_SIZE), leadingPlaceholderCount = 0)

        assertEquals(2, source.getRefreshKey(state))
    }

    @Test
    fun `with no anchor there is no refresh key`() {
        val state = PagingState<Int, Int>(emptyList(), anchorPosition = null, config = PagingConfig(PAGE_SIZE), leadingPlaceholderCount = 0)

        assertNull(NumberedSource(total = 5).getRefreshKey(state))
    }

    @Test
    fun `a page size that is not positive is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            object : OffsetPagingSource<Int>(0) {
                override suspend fun loadPage(take: Int, skip: Int) = OffsetPage(emptyList<Int>(), null)
            }
        }
    }
}
