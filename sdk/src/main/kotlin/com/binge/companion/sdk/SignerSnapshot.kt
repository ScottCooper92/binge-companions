package com.binge.companion.sdk

import android.os.Build
import java.security.MessageDigest

/**
 * What the platform reports about a package's signing, as plain values: whether it has several
 * current signers, and the current signers' encoded certificates. The [android.content.pm.PackageManager]
 * lookup that fills it is the only Android-typed part; every refusal below runs on the JVM.
 */
internal class SignerSnapshot(
    val hasMultipleSigners: Boolean,
    val certificates: List<ByteArray>,
)

/**
 * The current signer's digest, or nothing. Every branch that returns nothing fails closed:
 *
 * - below API 28 only the lineage-less `GET_SIGNATURES` exists, and the host refuses to discover
 *   companions there anyway, so refusing here keeps the two ends' floors the same;
 * - a lookup that throws (an uninstalled or hidden package) or returns no signing info has no signer;
 * - several current signers have no single identity, and one hash cannot identify an app signed by two.
 *
 * [load] is a lambda so a lookup failure is one of the cases tested rather than one the JVM cannot reach.
 */
internal fun signerDigests(sdkInt: Int, load: () -> SignerSnapshot?): Set<String> {
    if (sdkInt < Build.VERSION_CODES.P) return emptySet()
    val snapshot = runCatching(load).getOrNull() ?: return emptySet()
    if (snapshot.hasMultipleSigners) return emptySet()
    return snapshot.certificates.map(::sha256Hex).toSet()
}

internal fun sha256Hex(certificate: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(certificate)
        .joinToString(separator = "") { "%02x".format(it) }
