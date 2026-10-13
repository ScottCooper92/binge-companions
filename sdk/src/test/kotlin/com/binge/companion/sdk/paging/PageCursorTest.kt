package com.binge.companion.sdk.paging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PageCursorTest {
    @Test
    fun `a middle page points at the next skip`() {
        assertEquals(PageCursor(endReached = false, nextSkip = 40), pageCursorAfter(skip = 20, pageSize = 20, totalPages = 5))
    }

    @Test
    fun `the last page is the end`() {
        assertEquals(PageCursor(endReached = true, nextSkip = null), pageCursorAfter(skip = 80, pageSize = 20, totalPages = 5))
    }

    @Test
    fun `a page past the count is the end`() {
        assertEquals(PageCursor(endReached = true, nextSkip = null), pageCursorAfter(skip = 200, pageSize = 20, totalPages = 5))
    }

    @Test
    fun `a server that sends no page count sent its last page`() {
        assertEquals(PageCursor(endReached = true, nextSkip = null), pageCursorAfter(skip = 0, pageSize = 20, totalPages = null))
    }

    @Test
    fun `a page count of zero is no page count`() {
        assertEquals(PageCursor(endReached = true, nextSkip = null), pageCursorAfter(skip = 20, pageSize = 20, totalPages = 0))
    }

    @Test
    fun `a page size that is not positive is refused`() {
        assertThrows(IllegalArgumentException::class.java) { pageCursorAfter(skip = 0, pageSize = 0, totalPages = 5) }
    }
}
