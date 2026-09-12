package com.binge.integration.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import java.security.MessageDigest

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
     */
    fun pinned(context: Context, hosts: Collection<KnownHost>): SecurityPolicy =
        HostSecurityPolicy(
            hosts = hosts,
            packagesForUid = { uid -> context.packageManager.getPackagesForUid(uid).orEmpty().toList() },
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
 * signer is one the allowlist names. A package that cannot be read, or is signed by several keys,
 * has no signer here and is refused — one hash cannot identify an app signed by two.
 */
class HostSecurityPolicy(
    hosts: Collection<KnownHost>,
    private val packagesForUid: (uid: Int) -> List<String>,
    private val signerSha256s: (packageName: String) -> Set<String>,
) : SecurityPolicy() {
    private val allowed: Map<String, Set<String>> =
        hosts.map(KnownHost::normalised).associate { it.packageName to it.certificateSha256s }

    override fun checkAuthorization(uid: Int): Status {
        val packages = packagesForUid(uid)
        val host = packages.firstOrNull { it in allowed }
            ?: return Status.PERMISSION_DENIED.withDescription("uid $uid is not a known host")
        val signers = signerSha256s(host).map(::normaliseSha256)
        return if (signers.any { it in allowed.getValue(host) }) {
            Status.OK
        } else {
            Status.PERMISSION_DENIED.withDescription("$host is not signed by a certificate this companion trusts")
        }
    }
}

/**
 * The current signer's digest, or nothing. API 28 and up only: below it the lineage-less
 * `GET_SIGNATURES` is all there is, and the host refuses to discover companions there anyway, so
 * refusing here keeps the two ends' floors the same rather than checking weakly on one of them.
 */
private fun PackageManager.signerSha256s(packageName: String): Set<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptySet()
    val info = runCatching { getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
    val signing = info?.signingInfo ?: return emptySet()
    if (signing.hasMultipleSigners()) return emptySet()
    return signing.apkContentsSigners.orEmpty().map(Signature::sha256).toSet()
}

private fun Signature.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
