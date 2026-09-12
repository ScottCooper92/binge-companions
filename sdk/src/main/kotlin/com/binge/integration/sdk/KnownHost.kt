package com.binge.integration.sdk

/**
 * A host app a companion is willing to serve: its package name and the SHA-256 digests of the
 * signing certificates it may carry, as lowercase hex without separators.
 *
 * Both halves are needed. A package name is re-claimable on a device that allows sideloading,
 * which is exactly the device this platform targets, so the certificate is what makes the
 * allowlist mean "that app" rather than "anything calling itself that".
 */
data class KnownHost(
    val packageName: String,
    val certificateSha256s: Set<String>,
) {
    init {
        require(packageName.isNotBlank()) { "A known host needs a package name" }
    }

    /** The same host with digests normalised, so a caller pasting `AB:CD` or uppercase still matches. */
    internal fun normalised(): KnownHost = copy(certificateSha256s = certificateSha256s.map(::normaliseSha256).toSet())
}

/**
 * Binge itself, as the host every companion serves. The release certificate digest is empty
 * until it is published — a companion built against this list refuses release Binge, which is
 * the fail-closed default rather than an oversight. Debug Binge is signed with each developer's
 * local debug key and cannot be listed; a debug companion admits any caller instead (see
 * [HostPolicy.anyCaller]).
 */
object BingeHosts {
    const val RELEASE_PACKAGE_NAME = "com.cooper.binge.app"
    const val DEBUG_PACKAGE_NAME = "com.cooper.binge.app.debug"

    /** Digests of the certificates release Binge is signed with. Empty until published. */
    val RELEASE_CERTIFICATE_SHA256S: Set<String> = emptySet()

    val release: KnownHost = KnownHost(RELEASE_PACKAGE_NAME, RELEASE_CERTIFICATE_SHA256S)
}

internal fun normaliseSha256(digest: String): String = digest.replace(":", "").trim().lowercase()
