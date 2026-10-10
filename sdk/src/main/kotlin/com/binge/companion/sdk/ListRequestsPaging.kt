package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.ListRequestsRequest
import io.grpc.Status
import io.grpc.StatusException
import java.util.Base64

/** The page size [effectivePageSize] answers for a `page_size` of 0, unless the companion picks its own. */
const val DEFAULT_LIST_PAGE_SIZE = 20

/**
 * The most entries [effectivePageSize] allows in one `ListRequests` page, unless the companion
 * picks its own cap. A Binder transaction is limited to about 1 MB, and each entry carries the
 * title's whole status, so the cap keeps a page well under that whatever the host asks for.
 */
const val MAX_LIST_PAGE_SIZE = 50

/**
 * The page size to serve this request with. A `page_size` of 0 leaves the choice to the companion,
 * so it is [default]. Anything above [max] is cut to [max]; the contract lets a page be shorter
 * than asked. A negative size is `INVALID_ARGUMENT`.
 */
fun ListRequestsRequest.effectivePageSize(default: Int = DEFAULT_LIST_PAGE_SIZE, max: Int = MAX_LIST_PAGE_SIZE): Int {
    if (pageSize < 0) throw invalidArgument("page_size must not be negative, was $pageSize")
    return if (pageSize == 0) default else pageSize.coerceAtMost(max)
}

/**
 * The opaque `page_token` for a `ListRequests` listing that pages by offset.
 *
 * A token holds the offset of the next page, and the filter and `page_size` it was issued under.
 * The contract makes a token valid only with those two, and this is how that is checked. The
 * token is versioned and base64url-encoded, so the host has nothing to read in it. A token this
 * object could not have written is `INVALID_ARGUMENT`.
 *
 * The offset is the companion's own: a `skip`, a row number, whatever its server pages by.
 */
object ListRequestsPageToken {
    private const val VERSION = "v1"
    private const val FIELD_COUNT = 4

    /** The token for the page of [request]'s listing that starts at [skip]. */
    fun issue(request: ListRequestsRequest, skip: Int): String {
        require(skip >= 0) { "skip must not be negative, was $skip" }
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("$VERSION:${request.filterValue}:${request.pageSize}:$skip".toByteArray())
    }

    /**
     * The offset [request]'s token stands for: 0 for the first page. A token that is garbled, from
     * elsewhere, or issued for another filter or `page_size` is `INVALID_ARGUMENT`.
     */
    fun skipOf(request: ListRequestsRequest): Int {
        if (request.pageToken.isEmpty()) return 0
        val token = decode(request.pageToken) ?: throw invalidArgument("page_token was not issued by this integration")
        if (token.filter != request.filterValue || token.pageSize != request.pageSize) {
            throw invalidArgument("page_token was issued for a different filter or page_size")
        }
        return token.skip
    }

    private class Decoded(
        val filter: Int,
        val pageSize: Int,
        val skip: Int,
    )

    /** Null for anything [issue] could not have written. */
    private fun decode(token: String): Decoded? {
        val numbers =
            attempt { String(Base64.getUrlDecoder().decode(token)) }
                .getOrNull()
                ?.split(':')
                ?.takeIf { it.size == FIELD_COUNT && it.first() == VERSION }
                ?.drop(1)
                ?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == FIELD_COUNT - 1 }
                ?: return null
        val (filter, pageSize, skip) = numbers
        return Decoded(filter, pageSize, skip).takeIf { skip >= 0 }
    }
}

private fun invalidArgument(description: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(description))
