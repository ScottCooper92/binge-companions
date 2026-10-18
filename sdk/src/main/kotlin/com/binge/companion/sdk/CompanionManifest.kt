package com.binge.companion.sdk

import android.content.Intent
import com.binge.companion.contracts.v1.MediaType

/**
 * The strings a companion's `AndroidManifest.xml` must carry for the host to find it, read
 * without binding — a typo here is silent, since the Service still exists and is reported as
 * declaring no contract at all.
 *
 * See `docs/Architecture.md` > Transport for the manifest XML shape and how a Service serving several of REQUEST, LIBRARY, STREAM and
 * NOTIFICATIONS scopes its majors per contract with [META_MAJORS_REQUEST] / [META_MAJORS_LIBRARY] / [META_MAJORS_STREAM] /
 * [META_MAJORS_NOTIFICATIONS], and > Hand-offs for the
 * Activities on [ACTION_ADVANCED_REQUEST] / [ACTION_VIEW_REQUEST] / [ACTION_SETTINGS].
 */
object CompanionManifest {
    /** The intent action a REQUEST companion's Service filters on. */
    const val ACTION_REQUEST = "com.binge.companion.REQUEST"

    /** The intent action a LIBRARY companion's Service filters on. */
    const val ACTION_LIBRARY = "com.binge.companion.LIBRARY"

    /** The intent action a STREAM companion's Service filters on. */
    const val ACTION_STREAM = "com.binge.companion.STREAM"

    /**
     * The intent action a NOTIFICATIONS companion's Service filters on. A companion that also serves
     * REQUEST exports one Service with both actions, and the host learns it forwards notifications from
     * this action, not from a capability.
     */
    const val ACTION_NOTIFICATIONS = "com.binge.companion.NOTIFICATIONS"

    /** Display name: a literal, or a string resource in the companion's own package. */
    const val META_NAME = "com.binge.companion.name"

    /** A drawable resource in the companion's own package. Optional; the host has a generic glyph. */
    const val META_ICON = "com.binge.companion.icon"

    /**
     * Comma-separated contract majors served, e.g. `1` or `1,2`. Bare key for a Service naming one
     * of [ACTION_REQUEST] / [ACTION_LIBRARY] / [ACTION_STREAM] / [ACTION_NOTIFICATIONS], whatever private
     * actions it adds; one naming several uses [META_MAJORS_REQUEST] / [META_MAJORS_LIBRARY] /
     * [META_MAJORS_STREAM] / [META_MAJORS_NOTIFICATIONS], since
     * a bare key can't say which contract's majors it names.
     *
     * Read as either a String or an Int: aapt types a bare number as an Int, so `"1"` never
     * arrives as a String while `"1,2"` does — same for the three scoped keys above.
     */
    const val META_MAJORS = "com.binge.companion.majors"

    /** [META_MAJORS], scoped to [ACTION_REQUEST], for a Service that also filters on another contract action. */
    const val META_MAJORS_REQUEST = "com.binge.companion.majors.request"

    /** [META_MAJORS], scoped to [ACTION_LIBRARY], for a Service that also filters on another contract action. */
    const val META_MAJORS_LIBRARY = "com.binge.companion.majors.library"

    /** [META_MAJORS], scoped to [ACTION_STREAM], for a Service that also filters on another contract action. */
    const val META_MAJORS_STREAM = "com.binge.companion.majors.stream"

    /** [META_MAJORS], scoped to [ACTION_NOTIFICATIONS], for a Service that also filters on another contract action. */
    const val META_MAJORS_NOTIFICATIONS = "com.binge.companion.majors.notifications"

    /**
     * The action of the Activity behind `CAPABILITY_ADVANCED_OPTIONS`. The host starts it for a
     * result, scoped to the companion's package, with the title in the `EXTRA_*` keys below; the
     * companion owns the picker and the submit, and answers `RESULT_OK` once it has submitted. The
     * host re-reads the title's status whatever the result. [Intent.toAdvancedRequest] reads it,
     * after [HandOffPolicy] has admitted the caller.
     */
    const val ACTION_ADVANCED_REQUEST = "com.binge.companion.ADVANCED_REQUEST"

    /**
     * The action of an Activity a companion may export to show one existing request in its own UI.
     * Optional. The host starts it for a result, scoped to the companion's package, with the request's
     * id in [EXTRA_REQUEST_ID]. It discards the result but re-reads the request's status on return,
     * and offers the affordance only when something resolves. The companion checks the caller with
     * [HandOffCallerPolicy.permits] before it reads the extra.
     */
    const val ACTION_VIEW_REQUEST = "com.binge.companion.VIEW_REQUEST"

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

    /** `String`: the `Version.id` the user asked for, from the handshake's version list. Absent or empty is the standard version. */
    const val EXTRA_VERSION_ID = "com.binge.companion.extra.VERSION_ID"

    /**
     * `String`: the id of the request to show on [ACTION_VIEW_REQUEST] — the `RequestInfo.id` the request
     * status already returns, opaque to the host and never empty.
     */
    const val EXTRA_REQUEST_ID = "com.binge.companion.extra.REQUEST_ID"

    /**
     * The scheme of the link Binge answers to open a title: `binge://title/<movie|tv>/<tmdbId>`. A companion
     * that offers "open in Binge" builds it with [hostTitleUri] and starts it with `ACTION_VIEW`. To ask first
     * whether Binge is installed and answers, declare
     * `<queries><intent><action android:name="android.intent.action.VIEW" /><data android:scheme="binge" /></intent></queries>`
     * in the manifest, or package visibility hides the answer on API 30+.
     */
    const val HOST_TITLE_SCHEME = "binge"

    /**
     * The link Binge answers for one title (see [HOST_TITLE_SCHEME]). Part of the platform rather than
     * knowledge a companion learns by sharing an author with the host (binge-seerr#684).
     */
    fun hostTitleUri(mediaType: MediaType, tmdbId: Int): String {
        val type =
            when (mediaType) {
                MediaType.MEDIA_TYPE_MOVIE -> "movie"
                MediaType.MEDIA_TYPE_TV -> "tv"
                else -> throw IllegalArgumentException("A title link needs a movie or a TV show, not $mediaType")
            }
        require(tmdbId > 0) { "A title link needs a TMDB id, not $tmdbId" }
        return "$HOST_TITLE_SCHEME://title/$type/$tmdbId"
    }

    /**
     * The link Binge answers to open its own page for one companion, `binge://companions/<package>`: where the user
     * turns it on and chooses what Binge shows from it. A companion passes its own package name, to say "turn me on in
     * Binge" after its user signs in. It grants nothing: Binge opens its Companions list when the package is not one it
     * found, and consent stays the user's switch. Same scheme, trust stance and `<queries>` as [hostTitleUri].
     */
    fun hostCompanionUri(packageName: String): String {
        require(PACKAGE_NAME.matches(packageName)) { "A companion link needs a package name, not '$packageName'" }
        return "$HOST_TITLE_SCHEME://companions/$packageName"
    }

    /**
     * Binge's Google Play listing, for a companion offering "Get Binge" when [hostTitleUri] resolves to nothing.
     * The https form opens in the Play Store app where there is one and in a browser where there is not.
     */
    const val HOST_PLAY_LISTING_URL = "https://play.google.com/store/apps/details?id=${BingeHosts.RELEASE_PACKAGE_NAME}"

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
}
