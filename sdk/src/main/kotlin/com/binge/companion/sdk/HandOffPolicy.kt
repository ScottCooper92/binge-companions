package com.binge.companion.sdk

import android.content.Context
import android.util.Log

/**
 * The companion half of the mutual check for a hand-off Activity — the one behind
 * `CAPABILITY_ADVANCED_OPTIONS`, and the settings one on `CompanionManifest.ACTION_SETTINGS`.
 * The Activity is exported and resolvable by action, so any app on
 * the device can start it with extras of its own choosing; this is what says whether the one that
 * did is a host the companion serves, before the Activity acts on what it was handed.
 *
 * This object only wires Android lookups; the decision is [HandOffCallerPolicy], tested on the JVM.
 */
object HandOffPolicy {
    /**
     * Admits only [hosts]: the caller must be a listed package whose signing lineage
     * includes one of its listed certificates. This is the policy a release companion ships with, normally
     * `HandOffPolicy.pinned(this, listOf(BingeHosts.release))`, the same list its Service pins.
     */
    fun pinned(context: Context, hosts: Collection<KnownHost>): HandOffCallerPolicy =
        HandOffCallerPolicy(PinnedHosts(hosts) { packageName -> context.packageManager.signerSha256s(packageName) })

    /**
     * Admits a caller that is one of [packageNames], under any certificate, and says so in the log each
     * time: the hand-off twin of [HostPolicy.anyCertificateOf], and a debug companion's policy for the
     * same reason (#122). Select it with `BuildConfig.DEBUG`, never a flag.
     */
    fun anyCertificateOf(
        packageNames: Collection<String> = BingeHosts.PACKAGE_NAMES,
        tag: String = "BingeCompanion",
    ): HandOffCallerPolicy = HandOffCallerPolicy(pinned = null, packageNames = packageNames.toSet()) { message -> Log.w(tag, message) }

    /**
     * Admits every caller that started the Activity for a result, and says so in the log each time. For a
     * conformance harness or a companion author's own test host, as [HostPolicy.anyCaller] is; a
     * companion's own debug build uses [anyCertificateOf].
     */
    fun anyCaller(tag: String = "BingeCompanion"): HandOffCallerPolicy =
        HandOffCallerPolicy(pinned = null) { message -> Log.w(tag, message) }
}

/**
 * The decision for one caller, testable on the JVM. A null [permits] argument is refused under
 * every policy: `Activity.callingPackage` is set only when the caller started the Activity for a
 * result — which is how a host starts every hand-off, including the settings one — and its
 * absence means this was not a host. `Activity.referrer` is not an alternative: it is populated
 * from ordinary Intent extras before falling back to the system-tracked caller, so any app could
 * forge it. See `docs/Architecture.md` > Hand-offs.
 */
class HandOffCallerPolicy internal constructor(
    private val pinned: PinnedHosts?,
    private val packageNames: Set<String>? = null,
    private val warn: (message: String) -> Unit = {},
) {
    /** Whether the Activity may act on its extras, given `Activity.callingPackage`. */
    fun permits(callingPackage: String?): Boolean {
        if (callingPackage == null) return false
        if (pinned != null) return pinned.isTrusted(callingPackage)
        if (packageNames != null && callingPackage !in packageNames) return false
        warn("Admitting hand-off from $callingPackage without verifying its certificate (debug-only policy)")
        return true
    }
}
