package com.binge.companion.sdk.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.binge.companion.sdk.attempt

/** One fetched page; [totalPages] is null when the server sends no page count, which reads as the last page. */
data class OffsetPage<T>(
    val items: List<T>,
    val totalPages: Int?,
)

/**
 * The shape of a list a server pages by `take`/`skip`, keyed by integer page index. The base owns the
 * key arithmetic and turns any failure into a load error the list can retry from, with cancellation
 * left to propagate ([attempt]). A subclass supplies one page.
 *
 * Use it for the list a companion shows on its own screens; a companion that answers `ListRequests`
 * pages with [com.binge.companion.sdk.ListRequestsPageToken] instead.
 */
abstract class OffsetPagingSource<T : Any>(
    private val pageSize: Int,
) : PagingSource<Int, T>() {
    protected abstract suspend fun loadPage(take: Int, skip: Int): OffsetPage<T>

    final override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0
        return attempt {
            val result = loadPage(take = pageSize, skip = page * pageSize)
            val totalPages = result.totalPages ?: (page + 1)
            LoadResult.Page(
                data = result.items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (page + 1 >= totalPages) null else page + 1,
            )
        }.getOrElse { failure -> LoadResult.Error(failure) }
    }

    final override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
