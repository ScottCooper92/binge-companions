package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.ListRequestsRequest
import com.binge.companion.contracts.request.v1.RequestFilter
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class ListRequestsPagingTest {
    private fun request(
        filter: RequestFilter = RequestFilter.REQUEST_FILTER_ALL,
        pageSize: Int = 20,
        pageToken: String = "",
    ): ListRequestsRequest =
        ListRequestsRequest
            .newBuilder()
            .setFilter(filter)
            .setPageSize(pageSize)
            .setPageToken(pageToken)
            .build()

    private fun encode(raw: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())

    private fun assertInvalidArgument(block: () -> Unit) {
        val error = assertThrows<StatusException> { block() }
        assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }

    @Test
    fun `the first page has no token and starts at zero`() {
        assertEquals(0, ListRequestsPageToken.skipOf(request()))
    }

    @Test
    fun `a token round-trips to the skip it was issued for`() {
        val first = request(pageSize = 10)
        val token = ListRequestsPageToken.issue(first, skip = 30)

        assertEquals(30, ListRequestsPageToken.skipOf(first.toBuilder().setPageToken(token).build()))
    }

    @Test
    fun `the token format is the versioned one, base64url without padding`() {
        val token = ListRequestsPageToken.issue(request(RequestFilter.REQUEST_FILTER_MINE, pageSize = 20), skip = 40)

        assertEquals(encode("v1:${RequestFilter.REQUEST_FILTER_MINE_VALUE}:20:40"), token)
    }

    @Test
    fun `a token from elsewhere or garbled is INVALID_ARGUMENT`() {
        listOf(
            "not base64 !",
            encode("hello"),
            encode("v2:2:20:40"),
            encode("v1:2:20"),
            encode("v1:x:20:40"),
            encode("v1:2:x:40"),
            encode("v1:2:20:x"),
        ).forEach { token ->
            assertInvalidArgument { ListRequestsPageToken.skipOf(request(pageToken = token)) }
        }
    }

    @Test
    fun `a token with a negative skip is INVALID_ARGUMENT`() {
        val token = encode("v1:${RequestFilter.REQUEST_FILTER_ALL_VALUE}:20:-1")

        assertInvalidArgument { ListRequestsPageToken.skipOf(request(pageToken = token)) }
    }

    @Test
    fun `a token is refused under another filter or page size`() {
        val token = ListRequestsPageToken.issue(request(RequestFilter.REQUEST_FILTER_ALL, pageSize = 20), skip = 20)

        assertInvalidArgument { ListRequestsPageToken.skipOf(request(RequestFilter.REQUEST_FILTER_MINE, 20, token)) }
        assertInvalidArgument { ListRequestsPageToken.skipOf(request(RequestFilter.REQUEST_FILTER_ALL, 25, token)) }
    }

    @Test
    fun `a negative skip cannot be issued`() {
        assertThrows<IllegalArgumentException> { ListRequestsPageToken.issue(request(), skip = -1) }
    }

    @Test
    fun `a page size of zero is the default`() {
        assertEquals(DEFAULT_LIST_PAGE_SIZE, request(pageSize = 0).effectivePageSize())
        assertEquals(15, request(pageSize = 0).effectivePageSize(default = 15))
    }

    @Test
    fun `a page size within the cap is kept`() {
        assertEquals(7, request(pageSize = 7).effectivePageSize())
        assertEquals(MAX_LIST_PAGE_SIZE, request(pageSize = MAX_LIST_PAGE_SIZE).effectivePageSize())
    }

    @Test
    fun `a page size above the cap is cut to it`() {
        assertEquals(MAX_LIST_PAGE_SIZE, request(pageSize = 1_000).effectivePageSize())
        assertEquals(30, request(pageSize = 1_000).effectivePageSize(max = 30))
    }

    @Test
    fun `a negative page size is INVALID_ARGUMENT`() {
        assertInvalidArgument { request(pageSize = -1).effectivePageSize() }
    }
}
