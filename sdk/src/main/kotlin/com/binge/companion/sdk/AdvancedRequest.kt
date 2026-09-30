package com.binge.companion.sdk

import android.content.Intent
import com.binge.companion.contracts.v1.MediaType

/**
 * The title a host hands to the Activity behind `CAPABILITY_ADVANCED_OPTIONS`, so the companion can
 * request it with its own options — destination, profile, folder — in its own UI. Media identity
 * is the contract's: a media type and a TMDB id, never a provider's own id.
 */
data class AdvancedRequest(
    val mediaType: MediaType,
    val tmdbId: Int,
    /**
     * TV only: the seasons the user picked in the host; empty for the companion's own default.
     * Never negative, never repeated, at most [MAX_SEASON_NUMBERS], and always empty for a movie.
     */
    val seasonNumbers: List<Int>,
    val is4k: Boolean,
)

/**
 * The [AdvancedRequest] a hand-off Intent carries, or null when it names no requestable title — a
 * missing or unknown media type, or no TMDB id — which the Activity answers by finishing.
 */
fun Intent.toAdvancedRequest(): AdvancedRequest? =
    advancedRequestOf(
        mediaTypeNumber = getIntExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_UNSPECIFIED_VALUE),
        tmdbId = getIntExtra(CompanionManifest.EXTRA_TMDB_ID, 0),
        seasonNumbers = getIntArrayExtra(CompanionManifest.EXTRA_SEASON_NUMBERS),
        is4k = getBooleanExtra(CompanionManifest.EXTRA_IS_4K, false),
    )

/** The most seasons a hand-off may carry; further picks are dropped rather than passed on. */
const val MAX_SEASON_NUMBERS = 100

/**
 * The Intent-free half of [toAdvancedRequest], so its validation runs on the JVM. The extras are
 * whatever the caller chose to send, so a season list is cleaned here rather than left for every
 * companion to defend against: negative numbers and repeats are dropped, the list is capped, and a
 * movie carries none. Season 0 is kept, since specials are a season.
 */
fun advancedRequestOf(
    mediaTypeNumber: Int,
    tmdbId: Int,
    seasonNumbers: IntArray?,
    is4k: Boolean,
): AdvancedRequest? {
    val mediaType = MediaType.forNumber(mediaTypeNumber)?.takeIf { it != MediaType.MEDIA_TYPE_UNSPECIFIED } ?: return null
    if (tmdbId <= 0) return null
    val seasons = if (mediaType == MediaType.MEDIA_TYPE_TV) cleanSeasons(seasonNumbers) else emptyList()
    return AdvancedRequest(mediaType, tmdbId, seasons, is4k)
}

private fun cleanSeasons(seasonNumbers: IntArray?): List<Int> =
    seasonNumbers
        ?.toList()
        .orEmpty()
        .filter { it >= 0 }
        .distinct()
        .take(MAX_SEASON_NUMBERS)
