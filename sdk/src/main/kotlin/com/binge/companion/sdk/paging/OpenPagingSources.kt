package com.binge.companion.sdk.paging

import androidx.paging.PagingSource
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The paging sources a store has handed out and that are still valid, so the store can invalidate them itself
 * once one of its own writes commits.
 *
 * A database's own source (Room's, say) ignores its invalidation tracker until its first load has returned,
 * so a write that commits in that window is dropped and a cold list can stay on its skeleton for good.
 * Invalidating from the store, after the commit, does not depend on that timing, and invalidating twice is
 * harmless. This class holds [PagingSource]s only and knows nothing about the database.
 */
class OpenPagingSources<Key : Any, Value : Any> {
    private val open = CopyOnWriteArraySet<PagingSource<Key, Value>>()

    /** Hands [source] out, kept until it is invalidated. */
    fun track(source: PagingSource<Key, Value>): PagingSource<Key, Value> {
        open += source
        source.registerInvalidatedCallback { open -= source }
        return source
    }

    /** Runs [write], then invalidates every source handed out, so each reads again what the write left. */
    suspend fun <T> afterWrite(write: suspend () -> T): T = write().also { open.forEach { source -> source.invalidate() } }
}
