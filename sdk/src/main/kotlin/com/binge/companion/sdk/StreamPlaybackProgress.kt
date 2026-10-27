package com.binge.companion.sdk

import com.binge.companion.contracts.stream.v1.ForgetProgressRequest
import com.binge.companion.contracts.stream.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.stream.v1.PlaybackProgress
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.PlaybackEvent
import com.google.protobuf.Timestamp
import java.time.Instant

/**
 * Refuses a STREAM `ReportPlayback` the contract calls a bad request, as `INVALID_ARGUMENT`: an
 * unusable `MediaId`, an episode on a movie or none on a series, an episode with `season_number` < 0
 * or `episode_number` < 1, an unknown or UNSPECIFIED `event`, or a negative position or runtime.
 * `source_id` is never checked, because an id the companion does not remember is not an error.
 */
fun ReportPlaybackRequest.requireValid() {
    val problem =
        titleProblem(media, if (hasEpisode()) episode else null, episodeRequired = true)
            ?: when {
                event == PlaybackEvent.PLAYBACK_EVENT_UNSPECIFIED || event == PlaybackEvent.UNRECOGNIZED ->
                    "event must be a known PlaybackEvent, was $eventValue"
                positionMillis < 0 -> "position_millis must not be negative, was $positionMillis"
                runtimeMillis < 0 -> "runtime_millis must not be negative, was $runtimeMillis"
                else -> return
            }
    throw invalidArgument(problem)
}

/**
 * Refuses a STREAM `ForgetProgress` the contract calls a bad request, as `INVALID_ARGUMENT`: an
 * unusable `MediaId`, an episode on a movie, or an episode with `season_number` < 0 or
 * `episode_number` < 1. A series with no episode is valid, and forgets the whole series.
 */
fun ForgetProgressRequest.requireValid() {
    titleProblem(media, if (hasEpisode()) episode else null, episodeRequired = false)?.let { throw invalidArgument(it) }
}

/**
 * The progress for one title or episode once [report], received at [now], is applied to the
 * [progress] held before, by `ReportPlaybackRequest`'s rule in stream.proto. Null means hold nothing:
 * a finished movie, or a position of 0. A finished episode is `completed` at its runtime. [report]
 * is checked with [requireValid] first. Where progress is kept, and whether to drop a position
 * too near the start, is the companion's choice.
 */
fun applyReport(
    progress: PlaybackProgress?,
    report: ReportPlaybackRequest,
    now: Instant,
): PlaybackProgress? {
    report.requireValid()
    val runtime = report.runtimeMillis.takeIf { it > 0 } ?: progress?.runtimeMillis ?: 0
    val builder = PlaybackProgress.newBuilder().setRuntimeMillis(runtime).setLastPlayed(now.toTimestamp())
    return when {
        report.event != PlaybackEvent.PLAYBACK_EVENT_FINISHED ->
            if (report.positionMillis == 0L) null else builder.setPositionMillis(report.positionMillis).build()
        report.media.mediaType == MediaType.MEDIA_TYPE_MOVIE -> null
        else ->
            builder
                .setPositionMillis(runtime.takeIf { it > 0 } ?: report.positionMillis)
                .setCompleted(true)
                .build()
    }
}

/**
 * This progress as `ResolveResponse.resume`: null for none, and null for a completed episode, which
 * the contract never offers as a resume point.
 */
fun PlaybackProgress?.asResume(): PlaybackProgress? = this?.takeUnless { it.completed }

/**
 * The page size to serve this `ListContinueWatching` with, by the same rule as `ListRequests` pages
 * by: 0 is [default], anything above [max] is cut to it, and a negative size is `INVALID_ARGUMENT`.
 */
fun ListContinueWatchingRequest.effectivePageSize(default: Int = DEFAULT_LIST_PAGE_SIZE, max: Int = MAX_LIST_PAGE_SIZE): Int =
    effectivePageSize(pageSize, default, max)

/**
 * The opaque `page_token` for a `ListContinueWatching` listing that pages by offset, built as
 * [ListRequestsPageToken] builds its own. A token holds the offset of the next page and the
 * `page_size` it was issued under, and is versioned apart from `ListRequests`' tokens, so one never
 * reads as the other. A token this object could not have written is `INVALID_ARGUMENT`.
 */
object ContinueWatchingPageToken {
    private val codec = OffsetPageTokenCodec(version = "cw1", fieldCount = 2)

    /** The token for the page of [request]'s listing that starts at [skip]. */
    fun issue(request: ListContinueWatchingRequest, skip: Int): String {
        require(skip >= 0) { "skip must not be negative, was $skip" }
        return codec.encode(request.pageSize, skip)
    }

    /**
     * The offset [request]'s token stands for: 0 for the first page. A token that is garbled, from
     * elsewhere, or issued for another `page_size` is `INVALID_ARGUMENT`.
     */
    fun skipOf(request: ListContinueWatchingRequest): Int {
        if (request.pageToken.isEmpty()) return 0
        val (pageSize, skip) = codec.decode(request.pageToken) ?: throw invalidArgument("page_token was not issued by this integration")
        if (pageSize != request.pageSize) throw invalidArgument("page_token was issued for a different page_size")
        return skip
    }
}

/** What is wrong with this title and episode as a request names them, or null when nothing is. */
private fun titleProblem(
    media: MediaId,
    episode: EpisodeRef?,
    episodeRequired: Boolean,
): String? {
    val known = media.mediaType == MediaType.MEDIA_TYPE_MOVIE || media.mediaType == MediaType.MEDIA_TYPE_TV
    return when {
        !known || media.tmdbId <= 0 -> "media must be a movie or a series with a tmdb_id above 0"
        media.mediaType == MediaType.MEDIA_TYPE_MOVIE && episode != null -> "a movie has no episode"
        media.mediaType == MediaType.MEDIA_TYPE_TV && episode == null && episodeRequired -> "a series needs its episode"
        episode != null && (episode.seasonNumber < 0 || episode.episodeNumber < 1) ->
            "episode must have season_number >= 0 and episode_number >= 1"
        else -> null
    }
}

private fun Instant.toTimestamp(): Timestamp =
    Timestamp
        .newBuilder()
        .setSeconds(epochSecond)
        .setNanos(nano)
        .build()
