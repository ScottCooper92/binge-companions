package com.binge.companion.sdk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

/**
 * Installs [packageName] in Robolectric's package manager with [lineage] as its signing certificates, oldest first, and maps
 * [uid] to it, the two lookups `HostPolicy` and `signerSha256s` make. An empty [lineage] installs the package with no
 * signing info at all.
 */
internal fun installPackage(
    context: Context,
    packageName: String,
    uid: Int,
    lineage: List<ByteArray> = emptyList(),
) {
    val info = PackageInfo().apply { this.packageName = packageName }
    if (lineage.isNotEmpty()) {
        val signing = Shadow.newInstanceOf(SigningInfo::class.java)
        val shadow = Shadow.extract<ShadowSigningInfo>(signing)
        val signatures = lineage.map { Signature(it) }.toTypedArray()
        shadow.setSignatures(arrayOf(signatures.last()))
        shadow.setPastSigningCertificates(signatures)
        info.signingInfo = signing
    }
    val packages = shadowOf(context.packageManager)
    packages.installPackage(info)
    packages.setPackagesForUid(uid, packageName)
}
