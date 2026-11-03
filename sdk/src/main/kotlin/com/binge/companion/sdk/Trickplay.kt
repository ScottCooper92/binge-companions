package com.binge.companion.sdk

import com.binge.companion.contracts.library.v1.Trickplay
import java.net.URI

private const val PLACEHOLDER = "{index}"
private const val MAX_TEMPLATE_CHARS = 2048
private const val MAX_THUMBNAILS = 10_000
private const val MIN_THUMBNAIL_SIDE = 16
private const val MAX_THUMBNAIL_SIDE = 1_024
private const val MAX_SHEET_PIXELS = 8_000_000L
private const val MIN_INTERVAL_MILLIS = 100L

/**
 * Why the host would drop this [Trickplay], or null when it would keep it, by the bounds `library.proto` states on the
 * message. A companion checks the descriptor with this before it sends one, and sends none when it returns a problem: the
 * host drops the whole descriptor, never the source, so a bad one is thumbnails lost for nothing.
 *
 * It covers what a companion can know: the template (one `{index}`, in the path or the query, at most 2 KiB, http or https)
 * and the counts and sizes. It cannot know the bytes of a sheet, how many the host holds, or whether an `http` host is
 * private, which the host checks as it does for the source's own `url`.
 */
fun Trickplay.problem(): String? =
    templateProblem(urlTemplate)
        ?: when {
            thumbnailCount !in 1..MAX_THUMBNAILS -> "thumbnail_count must be 1 to $MAX_THUMBNAILS, was $thumbnailCount"
            thumbnailWidth !in MIN_THUMBNAIL_SIDE..MAX_THUMBNAIL_SIDE ->
                "thumbnail_width must be $MIN_THUMBNAIL_SIDE to $MAX_THUMBNAIL_SIDE, was $thumbnailWidth"
            thumbnailHeight !in MIN_THUMBNAIL_SIDE..MAX_THUMBNAIL_SIDE ->
                "thumbnail_height must be $MIN_THUMBNAIL_SIDE to $MAX_THUMBNAIL_SIDE, was $thumbnailHeight"
            tileColumns < 1 -> "tile_columns must be at least 1, was $tileColumns"
            tileRows < 1 -> "tile_rows must be at least 1, was $tileRows"
            sheetPixels() > MAX_SHEET_PIXELS -> "a sheet is ${sheetPixels()} pixels, over the $MAX_SHEET_PIXELS cap"
            intervalMillis < MIN_INTERVAL_MILLIS -> "interval_millis must be at least $MIN_INTERVAL_MILLIS, was $intervalMillis"
            else -> null
        }

/** How many tile sheets the title has: its thumbnails over what one sheet holds, rounded up. 0 for a descriptor with no grid. */
fun Trickplay.sheetCount(): Int {
    val perSheet = tileColumns.toLong() * tileRows
    return if (perSheet < 1) 0 else ((thumbnailCount + perSheet - 1) / perSheet).toInt()
}

/**
 * The URL of sheet [index], counted from 0: the template with the one `{index}` replaced by that number and nothing else.
 * It never leaves the placeholder unfilled and never builds another URL, so the host and a companion's own tests agree
 * on what is fetched.
 */
fun Trickplay.sheetUrl(index: Int): String {
    require(index >= 0) { "a sheet index is not negative, was $index" }
    require(templateProblem(urlTemplate) == null) { "the template is not usable: ${templateProblem(urlTemplate)}" }
    return urlTemplate.replace(PLACEHOLDER, index.toString())
}

private fun Trickplay.sheetPixels(): Long = thumbnailWidth.toLong() * tileColumns * thumbnailHeight * tileRows

private fun templateProblem(template: String): String? {
    if (template.isEmpty()) return "url_template is empty"
    if (template.length > MAX_TEMPLATE_CHARS) return "url_template is over $MAX_TEMPLATE_CHARS characters"
    if (template.split(PLACEHOLDER).size != 2) return "url_template must hold exactly one $PLACEHOLDER"
    // The placeholder may stand only in the path or the query: not in the scheme, the authority, a user-info part or the fragment.
    val uri = parseUri(template.replace(PLACEHOLDER, "0")) ?: return "url_template is not a URL once {index} is filled"
    if (uri.scheme != "https" && uri.scheme != "http") return "url_template must be http or https"
    if (uri.host.isNullOrEmpty()) return "url_template has no host"
    val beforePlaceholder = template.substringBefore(PLACEHOLDER).substringAfter("://")
    val authorityEnd = beforePlaceholder.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val inPathOrQuery = authorityEnd >= 0 && beforePlaceholder[authorityEnd] != '#' && !beforePlaceholder.contains('#')
    return if (inPathOrQuery) null else "$PLACEHOLDER must be in the path or the query"
}

private fun parseUri(text: String): URI? = attempt { URI(text) }.getOrNull()
