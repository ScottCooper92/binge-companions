package com.binge.companion.sdk

import io.grpc.Status
import io.grpc.StatusException

/**
 * This list of season numbers, checked for an rpc: a negative number, a repeat, or more than
 * [MAX_SEASON_NUMBERS] is `INVALID_ARGUMENT`. Season 0 is allowed, since specials are a season.
 *
 * An rpc's `season_numbers` comes from the host, and a bad list passed on to a server tends to
 * fail later, far from the cause. Refusing it up front names the problem. The hand-off Activity's
 * list is cleaned by [advancedRequestOf] instead, because an Activity has no status to answer with.
 * The contract sets no cap on the rpc's list; [MAX_SEASON_NUMBERS] is the SDK's, shared with the hand-off.
 */
fun List<Int>.checkedSeasonNumbers(): List<Int> {
    val problem =
        when {
            any { it < 0 } -> "season numbers are never negative"
            size != toSet().size -> "a season is named more than once"
            size > MAX_SEASON_NUMBERS -> "at most $MAX_SEASON_NUMBERS seasons, was $size"
            else -> return this
        }
    throw StatusException(Status.INVALID_ARGUMENT.withDescription(problem))
}
