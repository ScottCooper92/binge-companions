package com.binge.companion.sdk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.binge.companion.contracts.library.v1.PlayTarget

/**
 * The Intent the host should start for this [PlayTarget], or null when nothing can be
 * started: an unset `target`, or an `AndroidIntent` arm whose package doesn't resolve on
 * this device, since the host must not start an Intent addressed to a package it can't find.
 *
 * The decision is [resolveIntent], tested on the JVM (`PlayTargetTest`); this is the thin
 * Android wrapper around it — matching how [HostPolicy] wraps [HostSecurityPolicy] — with
 * no JVM unit test of [Context]/[Intent] construction itself.
 */
fun PlayTarget.toIntent(context: Context): Intent? =
    when (val resolved = resolveIntent(this) { packageName -> context.packageManager.hasPackage(packageName) }) {
        null -> null
        is ResolvedPlayTarget.App ->
            Intent(resolved.action).apply {
                setPackage(resolved.packageName)
                resolved.dataUri?.let { data = Uri.parse(it) }
                resolved.extras.forEach { (key, value) -> putExtra(key, value) }
            }
        is ResolvedPlayTarget.Web -> Intent(Intent.ACTION_VIEW, Uri.parse(resolved.url))
    }

/** Whether [packageName] resolves on this device, for [toIntent]'s pre-flight check. */
private fun PackageManager.hasPackage(packageName: String): Boolean = runCatching { getPackageInfo(packageName, 0) }.isSuccess
