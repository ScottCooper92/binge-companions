package com.binge.companion.sdk.paging

/** Where a `take`/`skip` list stands after one page: done, or the `skip` of the next. */
data class PageCursor(
    val endReached: Boolean,
    val nextSkip: Int?,
)

/**
 * The cursor after the page that started at [skip], for a server that pages by `take`/`skip` and reports
 * [totalPages]. A server that sends no page count, or a zero, is taken to have sent its last page.
 */
fun pageCursorAfter(
    skip: Int,
    pageSize: Int,
    totalPages: Int?,
): PageCursor {
    require(pageSize > 0) { "pageSize must be positive, was $pageSize" }
    val pages = totalPages?.takeIf { it > 0 } ?: (skip / pageSize + 1)
    val endReached = skip / pageSize + 1 >= pages
    return PageCursor(endReached = endReached, nextSkip = if (endReached) null else skip + pageSize)
}
