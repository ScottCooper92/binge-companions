package com.binge.companion.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import io.grpc.Status
import io.grpc.binder.SecurityPolicy

/**
 * The companion half of the platform's mutual check: who may bind to this companion's Service.
 *
 * An exported Service is reachable by every app on the device, and a companion's fronts the
 * user's authenticated provider session. Without this check any app could drive that session.
 * grpc-binder asks the policy once per connection with the CALLER's uid, before any rpc is
 * delivered; a refusal is `PERMISSION_DENIED` and the host sees it as a rejection.
 */
object HostPolicy {
    /**
     * Admits only [hosts]: the caller's uid must resolve to a listed package, and that package
     * must currently be signed by one of its listed certificates. This is the policy a release
     * companion ships with, normally `HostPolicy.pinned(context, listOf(BingeHosts.release))`.
     * Only the Android lookups are wired here; the decision is [HostSecurityPolicy] and the signer
     * rules are [signerDigests], both tested on the JVM.
     */
    fun pinned(context: Context, hosts: Collection<KnownHost>): SecurityPolicy =
        HostSecurityPolicy(
            hosts = hosts,
            packagesForUid = { uid ->
                context.packageManager
                    .getPackagesForUid(uid)
                    .orEmpty()
                    .toList()
            },
            signerSha256s = { packageName -> context.packageManager.signerSha256s(packageName) },
        )

    /**
     * Admits every caller, and says so in the log each time. For debug builds only — Binge's
     * debug build is signed with each developer's own key, which no allowlist can name — and
     * never for a release build, where it hands the user's provider session to any app on the
     * device. A companion should select it with `BuildConfig.DEBUG`, not a flag a user can flip.
     */
    fun anyCaller(tag: String = "BingeCompanion"): SecurityPolicy =
        object : SecurityPolicy() {
            override fun checkAuthorization(uid: Int): Status {
                Log.w(tag, "Admitting caller uid=$uid without verification (debug-only policy)")
                return Status.OK
            }
        }
}

/**
 * The pinned check with its two Android lookups as functions, so the decision is testable on
 * the JVM: whether a uid's packages include a known host, and whether that host's current
 * signer is one the allowlist names. The second half is [PinnedHosts], shared with the hand-off
 * Activity's [HandOffCallerPolicy], which knows its caller by package rather than by uid.
 */
class HostSecurityPolicy(
    hosts: Collection<KnownHost>,
    private val packagesForUid: (uid: Int) -> List<String>,
    signerSha256s: (packageName: String) -> Set<String>,
) : SecurityPolicy() {
    private val pinned = PinnedHosts(hosts, signerSha256s)

    override fun checkAuthorization(uid: Int): Status {
        val host = packagesForUid(uid).firstOrNull(pinned::isKnown)
            ?: return Status.PERMISSION_DENIED.withDescription("uid $uid is not a known host")
        return if (pinned.isTrusted(host)) {
            Status.OK
        } else {
            Status.PERMISSION_DENIED.withDescription("$host is not signed by a certificate this companion trusts")
        }
    }
}

/**
 * The allowlist half of a pinned check: is this package a known host, and is its current signer
 * one the host's entry names. A package that cannot be read, or is signed by several keys, has no
 * signer here and is not trusted — one hash cannot identify an app signed by two.
 */
internal class PinnedHosts(
    hosts: Collection<KnownHost>,
    private val signerSha256s: (packageName: String) -> Set<String>,
) {
    private val allowed: Map<String, Set<String>> =
        hosts.map(KnownHost::normalised).associate { it.packageName to it.certificateSha256s }

    fun isKnown(packageName: String): Boolean = packageName in allowed

    fun isTrusted(packageName: String): Boolean {
        val certificates = allowed[packageName] ?: return false
        return signerSha256s(packageName).map(::normaliseSha256).any { it in certificates }
    }
}

/**
 * The current signer's digest for [packageName], or nothing. Only the Android lookup lives here;
 * every fail-closed decision is [signerDigests], which is tested on the JVM.
 */
internal fun PackageManager.signerSha256s(packageName: String): Set<String> =
    signerDigests(Build.VERSION.SDK_INT) {
        getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.let { signing ->
            SignerSnapshot(
                hasMultipleSigners = signing.hasMultipleSigners(),
                certificates = signing.apkContentsSigners.orEmpty().map(Signature::toByteArray),
            )
        }
    }
