package com.binge.companion.sdk

import com.binge.companion.contracts.stream.v1.ListServedTitlesRequest
import java.util.Locale
import java.util.zip.CRC32

/** The page size [effectivePageSize] answers for a `ListServedTitles` `page_size` of 0. */
const val DEFAULT_SERVED_TITLES_PAGE_SIZE = 100

/** The most titles one `ListServedTitles` page may hold; the contract's own cap. */
const val MAX_SERVED_TITLES_PAGE_SIZE = 200

/**
 * Refuses a STREAM `ListServedTitles` the contract calls a bad request, as `INVALID_ARGUMENT`:
 * a `region` that is not two ASCII letters, or a negative `page_size`. An empty
 * `allowed_certifications` is valid, and admits no certification.
 */
fun ListServedTitlesRequest.requireValid() {
    if (region.length != 2 || !region.all { it in 'a'..'z' || it in 'A'..'Z' }) {
        throw invalidArgument("region must be two letters, was \"$region\"")
    }
    if (pageSize < 0) throw invalidArgument("page_size must not be negative, was $pageSize")
}

/**
 * The page size to serve this `ListServedTitles` with: 0 is [default], anything above [max] is cut
 * to it, and a negative size is `INVALID_ARGUMENT`.
 */
fun ListServedTitlesRequest.effectivePageSize(
    default: Int = DEFAULT_SERVED_TITLES_PAGE_SIZE,
    max: Int = MAX_SERVED_TITLES_PAGE_SIZE,
): Int = effectivePageSize(pageSize, default, max)

/**
 * The opaque `page_token` for a `ListServedTitles` listing that pages by offset. It holds the
 * offset of the next page, the `page_size` it was issued under and a CRC-32 digest of the filter
 * (`region`, `allowed_certifications`, `include_unrated`), so a token is refused with another
 * filter or size. The digest is a consistency guard on one device, not a security check. It
 * ignores the case of `region` and the order of the certifications, as the contract does.
 */
object ServedTitlesPageToken {
    private val codec = OffsetPageTokenCodec(version = "st1", fieldCount = 3)

    /** The token for the page of [request]'s listing that starts at [skip]. */
    fun issue(request: ListServedTitlesRequest, skip: Int): String {
        require(skip >= 0) { "skip must not be negative, was $skip" }
        return codec.encode(filterDigest(request), request.pageSize, skip)
    }

    /**
     * The offset [request]'s token stands for: 0 for the first page. A token that is garbled, from
     * elsewhere, or issued for another filter or `page_size` is `INVALID_ARGUMENT`.
     */
    fun skipOf(request: ListServedTitlesRequest): Int {
        if (request.pageToken.isEmpty()) return 0
        val (digest, pageSize, skip) = codec.decode(request.pageToken)
            ?: throw invalidArgument("page_token was not issued by this integration")
        if (digest != filterDigest(request) || pageSize != request.pageSize) {
            throw invalidArgument("page_token was issued for a different filter or page_size")
        }
        return skip
    }

    private fun filterDigest(request: ListServedTitlesRequest): Int {
        val canonical = listOf(request.region.uppercase(Locale.ROOT)) +
            request.allowedCertificationsList.distinct().sorted() +
            request.includeUnrated.toString()
        return CRC32().apply { update(canonical.joinToString("\u0000").toByteArray()) }.value.toInt()
    }
}
