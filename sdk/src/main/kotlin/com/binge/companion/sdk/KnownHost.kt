package com.binge.companion.sdk

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
 * Binge itself, as the host every companion serves. Release Binge is listed by the certificates
 * it may carry; any other signer under its package name is refused. Debug Binge is signed with
 * each developer's local debug key and cannot be listed; a debug companion admits any caller
 * instead (see [HostPolicy.anyCaller]).
 */
object BingeHosts {
    const val RELEASE_PACKAGE_NAME = "com.cooper.binge.app"
    const val DEBUG_PACKAGE_NAME = "com.cooper.binge.app.debug"

    /**
     * Digests of the certificates release Binge is distributed under. A set, so a build signed by another key
     * can be admitted the day one is actually distributed, with that distribution named beside its digest.
     *
     * Only Play App Signing's today. The upload key is deliberately absent: Play re-signs every APK it
     * distributes, so no installed Binge carries it, and pinning it would turn a leaked upload keystore, the
     * key Play App Signing makes recoverable, into host impersonation on any sideloading device (#116).
     */
    val RELEASE_CERTIFICATE_SHA256S: Set<String> =
        setOf(
            // Play App Signing (CN=Android, O=Google Inc.): what every Play-installed Binge carries.
            "65fa2c78c3be151ee11ea896d067d1284605690f78a9d6a4367a55e714301f97",
        )

    val release: KnownHost = KnownHost(RELEASE_PACKAGE_NAME, RELEASE_CERTIFICATE_SHA256S)
}

internal fun normaliseSha256(digest: String): String = digest.replace(":", "").trim().lowercase()
