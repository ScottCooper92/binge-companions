package com.binge.integration.sdk

import android.content.Intent

/**
 * The strings a companion's `AndroidManifest.xml` must carry for the host to find it: the intent
 * actions, `<meta-data>` keys and hand-off Activity extras named below. The host reads the
 * Service's `<meta-data>` without binding, so a typo here is silent — the Service is found and
 * reported as declaring no contract at all. See `docs/Architecture.md` > Transport and >
 * Hand-offs for the manifest XML shape and the mutual-verification story behind [HandOffPolicy].
 */
object CompanionManifest {
    /** The intent action a REQUEST companion's Service filters on. */
    const val ACTION_REQUEST = "com.binge.integration.REQUEST"

    /** Display name: a literal, or a string resource in the companion's own package. */
    const val META_NAME = "com.binge.integration.name"

    /** A drawable resource in the companion's own package. Optional; the host has a generic glyph. */
    const val META_ICON = "com.binge.integration.icon"

    /**
     * Comma-separated contract majors served, e.g. `1` or `1,2`. The proto package suffix.
     *
     * A host reads this as **either a String or an Int**, and must read both: aapt types a bare
     * number as an Int, so `android:value="1"` never arrives as a String while `"1,2"` does. Read
     * only the String form and every single-major companion reports as declaring nothing.
     */
    const val META_MAJORS = "com.binge.integration.majors"

    /**
     * The action of the Activity behind `CAPABILITY_ADVANCED_OPTIONS`. The host starts it for a
     * result, scoped to the companion's package, with the title in the `EXTRA_*` keys below; the
     * companion owns the picker and the submit, and answers `RESULT_OK` once it has submitted. The
     * host re-reads the title's status whatever the result. [Intent.toAdvancedRequest] reads it,
     * after [HandOffPolicy] has admitted the caller.
     */
    const val ACTION_ADVANCED_REQUEST = "com.binge.integration.ADVANCED_REQUEST"

    /**
     * The action of an Activity a companion may export for the host's "manage" affordance: its
     * own settings or hub, taking no extras and answering nothing. Started the same way as
     * [ACTION_ADVANCED_REQUEST] — for a result, so `callingPackage` is populated for
     * [HandOffCallerPolicy.permits] — but the result itself is discarded. Optional: a companion
     * with nothing to manage declares nothing. See `docs/Architecture.md` > Hand-offs.
     */
    const val ACTION_SETTINGS = "com.binge.integration.SETTINGS"

    /** `Int`: the title's `binge.integration.v1.MediaType` number — `1` for a movie, `2` for TV. */
    const val EXTRA_MEDIA_TYPE = "com.binge.integration.extra.MEDIA_TYPE"

    /** `Int`: the title's TMDB id. */
    const val EXTRA_TMDB_ID = "com.binge.integration.extra.TMDB_ID"

    /** `IntArray`, TV only: the seasons the user picked in the host. Absent or empty means the companion's default. */
    const val EXTRA_SEASON_NUMBERS = "com.binge.integration.extra.SEASON_NUMBERS"

    /** `Boolean`: whether the user asked for the 4K version. */
    const val EXTRA_IS_4K = "com.binge.integration.extra.IS_4K"
}
