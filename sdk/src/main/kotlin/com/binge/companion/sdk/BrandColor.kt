package com.binge.companion.sdk

import android.os.Bundle

private const val OPAQUE = 0xFF000000.toInt()
private const val NO_RESOURCE = 0

/**
 * The brand colour a companion declares for a theme, read from its manifest `<meta-data>`: the host's half of
 * [CompanionManifest.META_COLOR] and [CompanionManifest.META_COLOR_DARK]. An opaque ARGB colour, or null when none is
 * usable, and the host then falls back to its own. [resolve] turns a resource id into a colour in the companion's own
 * resources, answering null or throwing `Resources.NotFoundException` for one that is not a colour. The chain, the
 * validation and the contrast rules are in `docs/Architecture.md` > Transport; the rules run in [brandColor].
 */
fun Bundle.readBrandColor(dark: Boolean, resolve: (resourceId: Int) -> Int?): Int? =
    brandColor(dark, readId = { key -> (get(key) as? Int) }, resolve = resolve)

/**
 * The rules behind [readBrandColor], with the Bundle as [readId], so they run on the JVM. A key whose value is not an Int
 * (a literal string, say), or is 0, or whose resource does not resolve, is absent, and the next key is tried. Alpha is
 * dropped: the colour comes back fully opaque, since a translucent brand colour would fade twice under the host's own wash.
 */
internal fun brandColor(
    dark: Boolean,
    readId: (key: String) -> Int?,
    resolve: (resourceId: Int) -> Int?,
): Int? {
    val keys = if (dark) listOf(CompanionManifest.META_COLOR_DARK, CompanionManifest.META_COLOR) else listOf(CompanionManifest.META_COLOR)
    return keys.firstNotNullOfOrNull { key ->
        val id = readId(key)?.takeIf { it != NO_RESOURCE } ?: return@firstNotNullOfOrNull null
        attempt { resolve(id) }.getOrNull()?.let { it or OPAQUE }
    }
}
