package com.binge.integration.sdk

import android.content.Context
import android.util.Log

/**
 * The companion half of the mutual check for a hand-off Activity — the one behind
 * `CAPABILITY_ADVANCED_OPTIONS`, and the settings one on `CompanionManifest.ACTION_SETTINGS`.
 * The Activity is exported and resolvable by action, so any app on
 * the device can start it with extras of its own choosing; this is what says whether the one that
 * did is a host the companion serves, before the Activity acts on what it was handed.
 */
object HandOffPolicy {
    /**
     * Admits only [hosts]: the caller must be a listed package, currently signed by one of its
     * listed certificates. This is the policy a release companion ships with, normally
     * `HandOffPolicy.pinned(this, listOf(BingeHosts.release))`, the same list its Service pins.
     */
    fun pinned(context: Context, hosts: Collection<KnownHost>): HandOffCallerPolicy =
        HandOffCallerPolicy(PinnedHosts(hosts) { packageName -> context.packageManager.signerSha256s(packageName) })

    /**
     * Admits every caller that started the Activity for a result, and says so in the log each
     * time. For debug builds only, for the reason [HostPolicy.anyCaller] gives: debug Binge is
     * signed with a key no allowlist can name. Select it with `BuildConfig.DEBUG`, never a flag.
     */
    fun anyCaller(tag: String = "BingeCompanion"): HandOffCallerPolicy =
        HandOffCallerPolicy(pinned = null) { message -> Log.w(tag, message) }
}

/**
 * The decision for one caller, testable on the JVM. A null [permits] argument is refused under
 * every policy: `Activity.callingPackage` is set only when the caller started the Activity for a
 * result, which is how a host starts every hand-off, including the settings one — it answers
 * nothing, but is still started with `startActivityForResult` so `callingPackage` is populated.
 * Its absence means this was not a host.
 *
 * `Activity.referrer` is not an alternative here: it is read from `Intent.EXTRA_REFERRER` /
 * `EXTRA_REFERRER_NAME` before it falls back to the system-tracked caller, and those are ordinary
 * extras any app can set on the Intent it starts the Activity with. Only `callingPackage`, which
 * the system sets and a caller cannot forge, is a caller identity this check may trust.
 */
class HandOffCallerPolicy internal constructor(
    private val pinned: PinnedHosts?,
    private val warn: (message: String) -> Unit = {},
) {
    /** Whether the Activity may act on its extras, given `Activity.callingPackage`. */
    fun permits(callingPackage: String?): Boolean {
        if (callingPackage == null) return false
        if (pinned == null) {
            warn("Admitting hand-off from $callingPackage without verification (debug-only policy)")
            return true
        }
        return pinned.isTrusted(callingPackage)
    }
}
