package com.binge.companion.sdk

import android.os.Bundle

/**
 * The contract majors a companion's Service declares for [action], read from its manifest `<meta-data>`: the
 * host's half of [CompanionManifest.META_MAJORS]. [filteredActions] is the actions the Service's `<intent-filter>`s
 * name. An [action] that is not filtered on, or is not a contract action, has no majors, whatever keys exist.
 * Scoping of the keys is in [servedMajors]. String and Int values both parse, as `docs/Architecture.md` > Transport
 * requires; the parsing is tested on the JVM.
 */
fun Bundle.readServedMajors(action: String, filteredActions: Set<String>): Set<Int> =
    servedMajors(action, filteredActions) { key ->
        // getString on an Int value answers null, so the Int form is read on its own.
        getString(key) ?: if (containsKey(key)) getInt(key).toString() else null
    }

/**
 * The parsing behind [readServedMajors], with the Bundle as [read], so it runs on the JVM. A Service filtering on more
 * than one contract action reads only the scoped key for [action]; one filtering on a single contract action reads
 * only the bare key, whatever other actions it names. An [action] outside [filteredActions], or one that is not a
 * contract action, is `emptySet()`, since the bare key names the majors of the Service's one contract action and no
 * other. A value is a comma-separated list of
 * majors; a piece that is not a positive number is ignored rather than failing the whole declaration.
 */
internal fun servedMajors(
    action: String,
    filteredActions: Set<String>,
    read: (key: String) -> String?,
): Set<Int> {
    if (action !in filteredActions || scopedMajorsKey(action) == null) return emptySet()
    val contractActions = filteredActions.count { scopedMajorsKey(it) != null }
    val key = if (contractActions > 1) scopedMajorsKey(action) else CompanionManifest.META_MAJORS
    val value = key?.let(read) ?: return emptySet()
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
        CompanionManifest.ACTION_NOTIFICATIONS -> CompanionManifest.META_MAJORS_NOTIFICATIONS
        else -> null
    }
