package com.binge.companion.sdk.paging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * One finished network refresh of a cached list: how many rows it wrote. [sequence] tells two
 * refreshes that wrote the same number apart.
 *
 * A screen needs this because the pager cannot say it. When a refresh finishes, the pager reports it
 * at once, but the cache hands over the rows it wrote a moment later. In that gap the list reads as
 * finished and empty. Only the refresh knows whether rows are on the way.
 */
data class ListRefresh(
    val rowsWritten: Int,
    val sequence: Long,
)

/**
 * The latest [ListRefresh] of each list a screen shows, by [K]. A list is missing while its refresh
 * runs and until one has finished, so a stale answer is never read as the current one.
 */
class ListRefreshes<K> {
    private val sequence = AtomicLong()
    private val state = MutableStateFlow<Map<K, ListRefresh>>(emptyMap())

    val latest: StateFlow<Map<K, ListRefresh>> = state.asStateFlow()

    /** What a mediator reports: null as a refresh of [key] starts, then the rows it wrote. */
    fun record(key: K, rowsWritten: Int?) =
        state.update { refreshes ->
            if (rowsWritten == null) refreshes - key else refreshes + (key to ListRefresh(rowsWritten, sequence.incrementAndGet()))
        }
}
