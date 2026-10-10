package com.binge.companion.sdk

import android.os.Bundle

/**
 * The contract majors a companion's Service declares for [action], read from its manifest `<meta-data>`: the
 * host's half of [CompanionManifest.META_MAJORS]. A Service that filters on several actions declares them per
 * contract, so the key scoped to [action] is read first. A single-action Service uses the bare key, which is the
 * fallback. A host that reads only the bare key reports a multi-action companion as declaring nothing.
 *
 * Both String and Int values are read, as `docs/Architecture.md` > Transport requires: aapt types `"1"` as an
 * Int and `"1,2"` as a String. The parsing is [servedMajors], tested on the JVM.
 */
fun Bundle.readServedMajors(action: String): Set<Int> =
    servedMajors(action) { key ->
        // getString on an Int value answers null, so the Int form is read on its own.
        getString(key) ?: if (containsKey(key)) getInt(key).toString() else null
    }

/**
 * The parsing behind [readServedMajors], with the Bundle as [read], so it runs on the JVM. The scoped key for
 * [action] wins when present; otherwise the bare key. A value is a comma-separated list of majors; a piece
 * that is not a positive number is ignored rather than failing the whole declaration.
 */
internal fun servedMajors(action: String, read: (key: String) -> String?): Set<Int> {
    val value = scopedMajorsKey(action)?.let(read) ?: read(CompanionManifest.META_MAJORS) ?: return emptySet()
    return value
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .toSet()
}

private fun scopedMajorsKey(action: String): String? =
    when (action) {
        CompanionManifest.ACTION_REQUEST -> CompanionManifest.META_MAJORS_REQUEST
        CompanionManifest.ACTION_LIBRARY -> CompanionManifest.META_MAJORS_LIBRARY
        CompanionManifest.ACTION_STREAM -> CompanionManifest.META_MAJORS_STREAM
        else -> null
    }
