package com.binge.integration.sdk

import android.content.Intent
import com.binge.integration.contracts.v1.MediaType

/**
 * The title a host hands to the Activity behind `CAPABILITY_ADVANCED_OPTIONS`, so the companion can
 * request it with its own options — destination, profile, folder — in its own UI. Media identity
 * is the contract's: a media type and a TMDB id, never a provider's own id.
 */
data class AdvancedRequest(
    val mediaType: MediaType,
    val tmdbId: Int,
    /** TV only: the seasons the user picked in the host; empty for the companion's own default. */
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

/** The Intent-free half of [toAdvancedRequest], so its validation runs on the JVM. */
fun advancedRequestOf(
    mediaTypeNumber: Int,
    tmdbId: Int,
    seasonNumbers: IntArray?,
    is4k: Boolean,
): AdvancedRequest? {
    val mediaType = MediaType.forNumber(mediaTypeNumber)?.takeIf { it != MediaType.MEDIA_TYPE_UNSPECIFIED } ?: return null
    if (tmdbId <= 0) return null
    return AdvancedRequest(mediaType, tmdbId, seasonNumbers?.toList().orEmpty(), is4k)
}
