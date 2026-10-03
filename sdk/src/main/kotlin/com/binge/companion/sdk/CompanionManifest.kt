package com.binge.companion.sdk

import android.content.Intent

/**
 * The strings a companion's `AndroidManifest.xml` must carry for the host to find it, read
 * without binding — a typo here is silent, since the Service still exists and is reported as
 * declaring no contract at all.
 *
 * See `docs/Architecture.md` > Transport for the manifest XML shape and how a Service serving both REQUEST and LIBRARY
 * scopes its majors per contract with [META_MAJORS_REQUEST] / [META_MAJORS_LIBRARY], and > Hand-offs for the Activities
 * on [ACTION_ADVANCED_REQUEST] / [ACTION_SETTINGS].
 */
object CompanionManifest {
    /** The intent action a REQUEST companion's Service filters on. */
    const val ACTION_REQUEST = "com.binge.companion.REQUEST"

    /** The intent action a LIBRARY companion's Service filters on. */
    const val ACTION_LIBRARY = "com.binge.companion.LIBRARY"

    /** The intent action a STREAM companion's Service filters on. */
    const val ACTION_STREAM = "com.binge.companion.STREAM"

    /** Display name: a literal, or a string resource in the companion's own package. */
    const val META_NAME = "com.binge.companion.name"

    /** A drawable resource in the companion's own package. Optional; the host has a generic glyph. */
    const val META_ICON = "com.binge.companion.icon"

    /**
     * Comma-separated contract majors served, e.g. `1` or `1,2`. Bare key for a Service naming
     * only one of [ACTION_REQUEST] / [ACTION_LIBRARY] / [ACTION_STREAM]; one serving several uses
     * [META_MAJORS_REQUEST] / [META_MAJORS_LIBRARY] / [META_MAJORS_STREAM] instead, since a bare
     * key can't say which contract's majors it names.
     *
     * Read as either a String or an Int: aapt types a bare number as an Int, so `"1"` never
     * arrives as a String while `"1,2"` does — same for the three scoped keys above.
     */
    const val META_MAJORS = "com.binge.companion.majors"

    /** [META_MAJORS], scoped to [ACTION_REQUEST], for a Service that also filters on [ACTION_LIBRARY]. */
    const val META_MAJORS_REQUEST = "com.binge.companion.majors.request"

    /** [META_MAJORS], scoped to [ACTION_LIBRARY], for a Service that also filters on [ACTION_REQUEST]. */
    const val META_MAJORS_LIBRARY = "com.binge.companion.majors.library"

    /** [META_MAJORS], scoped to [ACTION_STREAM], for a Service that also filters on another action. */
    const val META_MAJORS_STREAM = "com.binge.companion.majors.stream"

    /**
     * The action of the Activity behind `CAPABILITY_ADVANCED_OPTIONS`. The host starts it for a
     * result, scoped to the companion's package, with the title in the `EXTRA_*` keys below; the
     * companion owns the picker and the submit, and answers `RESULT_OK` once it has submitted. The
     * host re-reads the title's status whatever the result. [Intent.toAdvancedRequest] reads it,
     * after [HandOffPolicy] has admitted the caller.
     */
    const val ACTION_ADVANCED_REQUEST = "com.binge.companion.ADVANCED_REQUEST"

    /**
     * The action of an Activity a companion may export for the host's "manage" affordance:
     * its own settings or hub, taking no extras and answering nothing. Optional — a companion
     * with nothing to manage declares nothing.
     *
     * The host resolves it by action and package and starts it with `startActivityForResult`,
     * discarding the result — that's what gives the companion a `callingPackage` to check with
     * [HandOffCallerPolicy.permits], the same as the advanced hand-off.
     */
    const val ACTION_SETTINGS = "com.binge.companion.SETTINGS"

    /** `Int`: the title's `binge.companion.v1.MediaType` number — `1` for a movie, `2` for TV. */
    const val EXTRA_MEDIA_TYPE = "com.binge.companion.extra.MEDIA_TYPE"

    /** `Int`: the title's TMDB id. */
    const val EXTRA_TMDB_ID = "com.binge.companion.extra.TMDB_ID"

    /** `IntArray`, TV only: the seasons the user picked in the host. Absent or empty means the companion's default. */
    const val EXTRA_SEASON_NUMBERS = "com.binge.companion.extra.SEASON_NUMBERS"

    /** `Boolean`: whether the user asked for the 4K version. */
    const val EXTRA_IS_4K = "com.binge.companion.extra.IS_4K"
}
