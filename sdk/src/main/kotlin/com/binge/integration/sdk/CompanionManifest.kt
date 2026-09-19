package com.binge.integration.sdk

import android.content.Intent

/**
 * The strings a companion's `AndroidManifest.xml` must carry for the host to find it.
 *
 * The host reads these WITHOUT binding. It lists the companion and decides whether the two can
 * parse each other from manifest data alone, so a companion process never starts just to be
 * listed. A typo here is silent: the Service exists, the host sees it, and it is reported as
 * declaring no contract at all.
 *
 * ```xml
 * <service android:name=".RequestCompanionService" android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.binge.integration.REQUEST" />
 *     </intent-filter>
 *     <meta-data android:name="com.binge.integration.name" android:value="@string/companion_name" />
 *     <meta-data android:name="com.binge.integration.icon" android:resource="@drawable/ic_companion" />
 *     <meta-data android:name="com.binge.integration.majors" android:value="1" />
 * </service>
 * ```
 *
 * A companion that declares `CAPABILITY_ADVANCED_OPTIONS` also exports the Activity the host hands
 * the title to. The `DEFAULT` category is what lets the host resolve it by action and package —
 * and by any other app, which is why the Activity checks its caller with [HandOffPolicy] before
 * it acts on the extras, as the Service does with [HostPolicy]:
 *
 * ```xml
 * <activity android:name=".AdvancedRequestActivity" android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.binge.integration.ADVANCED_REQUEST" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *     </intent-filter>
 * </activity>
 * ```
 *
 * A companion with a screen of its own to manage may also export it on [ACTION_SETTINGS], the
 * same way; the host shows a way into it on the companion's row only when the Activity resolves.
 *
 * A companion may serve REQUEST and LIBRARY from the same exported Service, or from two — which it
 * is stays the companion's business, since consent and the certificate pin are per package, not per
 * Service. A Service whose `<intent-filter>` names only one of [ACTION_REQUEST] / [ACTION_LIBRARY]
 * declares [META_MAJORS] as shown above; one that names both declares [META_MAJORS_REQUEST] and
 * [META_MAJORS_LIBRARY] instead, so each contract's majors stay unambiguous:
 *
 * ```xml
 * <service android:name=".CompanionService" android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.binge.integration.REQUEST" />
 *     </intent-filter>
 *     <intent-filter>
 *         <action android:name="com.binge.integration.LIBRARY" />
 *     </intent-filter>
 *     <meta-data android:name="com.binge.integration.name" android:value="@string/companion_name" />
 *     <meta-data android:name="com.binge.integration.icon" android:resource="@drawable/ic_companion" />
 *     <meta-data android:name="com.binge.integration.majors.request" android:value="1" />
 *     <meta-data android:name="com.binge.integration.majors.library" android:value="1" />
 * </service>
 * ```
 */
object CompanionManifest {
    /** The intent action a REQUEST companion's Service filters on. */
    const val ACTION_REQUEST = "com.binge.integration.REQUEST"

    /** The intent action a LIBRARY companion's Service filters on. */
    const val ACTION_LIBRARY = "com.binge.integration.LIBRARY"

    /** Display name: a literal, or a string resource in the companion's own package. */
    const val META_NAME = "com.binge.integration.name"

    /** A drawable resource in the companion's own package. Optional; the host has a generic glyph. */
    const val META_ICON = "com.binge.integration.icon"

    /**
     * Comma-separated contract majors served, e.g. `1` or `1,2`. The proto package suffix.
     *
     * Use this bare key when the Service's `<intent-filter>` names exactly one of [ACTION_REQUEST]
     * / [ACTION_LIBRARY]. A Service that filters on **both** declares majors per contract instead,
     * with [META_MAJORS_REQUEST] / [META_MAJORS_LIBRARY] — a bare, unsuffixed `majors` on a
     * multi-action Service cannot say which contract's package it names, so the host must not
     * guess from it. See the class doc for the two-action manifest shape.
     *
     * A host reads this as **either a String or an Int**, and must read both: aapt types a bare
     * number as an Int, so `android:value="1"` never arrives as a String while `"1,2"` does. Read
     * only the String form and every single-major companion reports as declaring nothing. The same
     * applies to [META_MAJORS_REQUEST] and [META_MAJORS_LIBRARY].
     */
    const val META_MAJORS = "com.binge.integration.majors"

    /** [META_MAJORS], scoped to [ACTION_REQUEST], for a Service that also filters on [ACTION_LIBRARY]. */
    const val META_MAJORS_REQUEST = "com.binge.integration.majors.request"

    /** [META_MAJORS], scoped to [ACTION_LIBRARY], for a Service that also filters on [ACTION_REQUEST]. */
    const val META_MAJORS_LIBRARY = "com.binge.integration.majors.library"

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
     * own settings or hub, taking no extras and answering nothing. The host resolves it by action
     * and package, as it does the advanced hand-off, and starts it with `startActivityForResult`
     * too, discarding the result — that is what gives the companion a `callingPackage` it can
     * trust, so it admits the caller with [HandOffCallerPolicy.permits], the same as the advanced
     * hand-off. Optional: a companion with nothing to manage declares nothing, and the host shows
     * nothing.
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
