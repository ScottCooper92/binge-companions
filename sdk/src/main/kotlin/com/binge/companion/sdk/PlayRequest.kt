package com.binge.companion.sdk

import android.content.Intent
import com.binge.companion.contracts.v1.MediaType

/**
 * The title a host hands to the Activity behind `CAPABILITY_PLAY`, so the companion can start the
 * media server's own app or web client for it. Media identity is the contract's: a media type and a
 * TMDB id, never a provider's own id. The host builds every field itself; nothing here came from a
 * companion.
 */
data class PlayRequest(
    val mediaType: MediaType,
    val tmdbId: Int,
    /** TV only: the season to play from. Null with no [episodeNumber] means the whole series, and the companion chooses where to resume. */
    val seasonNumber: Int?,
    /** TV only, and only beside a [seasonNumber]: the episode to play. */
    val episodeNumber: Int?,
)

/**
 * The [PlayRequest] a hand-off Intent carries, or null when it is not a play request: another
 * action, a missing or unknown media type, no TMDB id, or a season or episode that does not fit the
 * title. The Activity answers null by finishing. Call it only after [HandOffPolicy] has admitted the
 * caller.
 */
fun Intent.toPlayRequest(): PlayRequest? =
    playRequestOf(
        action = action,
        mediaTypeNumber = getIntExtra(CompanionManifest.EXTRA_MEDIA_TYPE, MediaType.MEDIA_TYPE_UNSPECIFIED_VALUE),
        tmdbId = getIntExtra(CompanionManifest.EXTRA_TMDB_ID, 0),
        seasonNumber = if (hasExtra(CompanionManifest.EXTRA_SEASON)) getIntExtra(CompanionManifest.EXTRA_SEASON, 0) else null,
        episodeNumber = if (hasExtra(CompanionManifest.EXTRA_EPISODE)) getIntExtra(CompanionManifest.EXTRA_EPISODE, 0) else null,
    )

/**
 * The Intent-free half of [toPlayRequest], so its refusals run on the JVM. The extras are whatever
 * the caller chose to send, so a request that does not make sense is refused rather than repaired:
 * a movie carries no season or episode, an episode needs its season, a season is never negative
 * (season 0 is specials) and an episode number is at least 1.
 */
fun playRequestOf(
    action: String?,
    mediaTypeNumber: Int,
    tmdbId: Int,
    seasonNumber: Int?,
    episodeNumber: Int?,
): PlayRequest? {
    if (action != CompanionManifest.ACTION_PLAY) return null
    val mediaType = MediaType.forNumber(mediaTypeNumber)?.takeIf { it != MediaType.MEDIA_TYPE_UNSPECIFIED } ?: return null
    if (tmdbId <= 0) return null
    if (seasonNumber != null && seasonNumber < 0) return null
    if (episodeNumber != null && (episodeNumber < 1 || seasonNumber == null)) return null
    if (mediaType == MediaType.MEDIA_TYPE_MOVIE && (seasonNumber != null || episodeNumber != null)) return null
    return PlayRequest(mediaType, tmdbId, seasonNumber, episodeNumber)
}
