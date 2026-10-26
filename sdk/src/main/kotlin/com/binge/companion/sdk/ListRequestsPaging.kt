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
 * so it is [default]. Anything above [max] is cut to [max], [default] included; the contract lets a
 * page be shorter than asked. A negative size is `INVALID_ARGUMENT`.
 */
fun ListRequestsRequest.effectivePageSize(default: Int = DEFAULT_LIST_PAGE_SIZE, max: Int = MAX_LIST_PAGE_SIZE): Int =
    effectivePageSize(pageSize, default, max)

/** [pageSize] as a list rpc serves it; see [ListRequestsRequest.effectivePageSize]. */
internal fun effectivePageSize(
    pageSize: Int,
    default: Int,
    max: Int,
): Int {
    if (pageSize < 0) throw invalidArgument("page_size must not be negative, was $pageSize")
    return (if (pageSize == 0) default else pageSize).coerceAtMost(max)
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
    private val codec = OffsetPageTokenCodec(version = "v1", fieldCount = 3)

    /** The token for the page of [request]'s listing that starts at [skip]. */
    fun issue(request: ListRequestsRequest, skip: Int): String {
        require(skip >= 0) { "skip must not be negative, was $skip" }
        return codec.encode(request.filterValue, request.pageSize, skip)
    }

    /**
     * The offset [request]'s token stands for: 0 for the first page. A token that is garbled, from
     * elsewhere, or issued for another filter or `page_size` is `INVALID_ARGUMENT`.
     */
    fun skipOf(request: ListRequestsRequest): Int {
        if (request.pageToken.isEmpty()) return 0
        val (filter, pageSize, skip) = codec.decode(request.pageToken)
            ?: throw invalidArgument("page_token was not issued by this integration")
        if (filter != request.filterValue || pageSize != request.pageSize) {
            throw invalidArgument("page_token was issued for a different filter or page_size")
        }
        return skip
    }
}

/**
 * Writes and reads the versioned, base64url offset tokens the paging helpers issue: the version,
 * then [fieldCount] integers, the last of them the offset. Shared so every list rpc's token is
 * built and checked the same way, and a token from one rpc never parses as another's, because
 * each rpc has its own [version].
 */
internal class OffsetPageTokenCodec(
    private val version: String,
    private val fieldCount: Int,
) {
    fun encode(vararg numbers: Int): String {
        check(numbers.size == fieldCount) { "expected $fieldCount numbers, was ${numbers.size}" }
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString((listOf(version) + numbers.map(Int::toString)).joinToString(":").toByteArray())
    }

    /** The numbers [encode] wrote, or null for anything it could not have written, a negative offset included. */
    fun decode(token: String): List<Int>? =
        attempt { String(Base64.getUrlDecoder().decode(token)) }
            .getOrNull()
            ?.split(':')
            ?.takeIf { it.size == fieldCount + 1 && it.first() == version }
            ?.drop(1)
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == fieldCount && it.last() >= 0 }
}

internal fun invalidArgument(description: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(description))
