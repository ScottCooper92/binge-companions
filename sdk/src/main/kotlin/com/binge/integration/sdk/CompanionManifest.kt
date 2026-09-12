package com.binge.integration.sdk

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
 */
object CompanionManifest {
    /** The intent action a REQUEST companion's Service filters on. */
    const val ACTION_REQUEST = "com.binge.integration.REQUEST"

    /** Display name: a literal, or a string resource in the companion's own package. */
    const val META_NAME = "com.binge.integration.name"

    /** A drawable resource in the companion's own package. Optional; the host has a generic glyph. */
    const val META_ICON = "com.binge.integration.icon"

    /** Comma-separated contract majors served, e.g. `1` or `1,2`. The proto package suffix. */
    const val META_MAJORS = "com.binge.integration.majors"
}
