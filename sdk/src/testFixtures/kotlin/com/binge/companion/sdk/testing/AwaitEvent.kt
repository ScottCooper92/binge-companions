package com.binge.companion.sdk.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Subscribes to [events] before returning, and awaits the first one.
 *
 * One-shot events are usually a `MutableSharedFlow` with no replay. An emission made while nothing
 * collects is dropped. So `act(); events.first()` is a race, and the test loses it by hanging. It
 * wins only when the action happens to suspend long enough for a collector to start.
 *
 * [CoroutineStart.UNDISPATCHED] makes it deterministic. The body runs on the caller's thread up to
 * its first suspension, and `first()` suspends after it has registered. A plain `async` is queued
 * on the test scheduler and has registered nothing yet.
 */
fun <T> CoroutineScope.awaitEvent(events: Flow<T>): Deferred<T> = async(start = CoroutineStart.UNDISPATCHED) { events.first() }

/**
 * Subscribes to [events] before returning, and awaits the first one matching [predicate].
 *
 * The same guarantee as the overload above, for a test that waits for one event while others can
 * arrive first.
 */
fun <T> CoroutineScope.awaitEvent(events: Flow<T>, predicate: suspend (T) -> Boolean): Deferred<T> =
    async(start = CoroutineStart.UNDISPATCHED) { events.first(predicate) }
