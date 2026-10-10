package com.binge.companion.sdk

import kotlin.coroutines.cancellation.CancellationException

/**
 * [block]'s result as a [Result], like [runCatching], except that cancellation is rethrown.
 *
 * `runCatching` also catches the [CancellationException] a cancelled coroutine throws. The code
 * after it then runs in a scope that is gone, and sets an error state or answers a status as if
 * the call had failed. Use this wherever you would put `runCatching` around a suspending call.
 */
inline fun <T> attempt(block: () -> T): Result<T> = runCatching(block).onFailure { if (it is CancellationException) throw it }
