package com.binge.companion.sdk

import com.binge.companion.contracts.stream.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.stream.v1.ListServedTitlesRequest
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ServedTitlesTest {
    private fun request(
        region: String = "US",
        certs: List<String> = listOf("PG", "G"),
        unrated: Boolean = false,
        pageSize: Int = 50,
        token: String = "",
    ): ListServedTitlesRequest =
        ListServedTitlesRequest
            .newBuilder()
            .setRegion(region)
            .addAllAllowedCertifications(certs)
            .setIncludeUnrated(unrated)
            .setPageSize(pageSize)
            .setPageToken(token)
            .build()

    private fun assertInvalid(block: () -> Unit) {
        val e = assertThrows(StatusException::class.java) { block() }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
    }

    @Test
    fun `a valid request passes, with or without certifications`() {
        request().requireValid()
        request(region = "gb", certs = emptyList()).requireValid()
    }

    @Test
    fun `a bad region or a negative page size is refused`() {
        listOf("", "U", "USA", "1S", "é1").forEach { r -> assertInvalid { request(region = r).requireValid() } }
        assertInvalid { request(pageSize = -1).requireValid() }
    }

    @Test
    fun `page size defaults, passes through and is capped at 200`() {
        assertEquals(DEFAULT_SERVED_TITLES_PAGE_SIZE, request(pageSize = 0).effectivePageSize())
        assertEquals(50, request(pageSize = 50).effectivePageSize())
        assertEquals(MAX_SERVED_TITLES_PAGE_SIZE, request(pageSize = 1000).effectivePageSize())
        assertInvalid { request(pageSize = -1).effectivePageSize() }
    }

    @Test
    fun `the first page starts at zero and a token round trips`() {
        assertEquals(0, ServedTitlesPageToken.skipOf(request()))
        val token = ServedTitlesPageToken.issue(request(), 120)
        assertEquals(120, ServedTitlesPageToken.skipOf(request(token = token)))
    }

    @Test
    fun `region case and certification order do not change the filter`() {
        val token = ServedTitlesPageToken.issue(request(), 40)
        assertEquals(40, ServedTitlesPageToken.skipOf(request(region = "us", certs = listOf("G", "PG", "G"), token = token)))
    }

    @Test
    fun `a token is refused with another filter or page size`() {
        val token = ServedTitlesPageToken.issue(request(), 40)
        assertInvalid { ServedTitlesPageToken.skipOf(request(region = "GB", token = token)) }
        assertInvalid { ServedTitlesPageToken.skipOf(request(certs = listOf("G"), token = token)) }
        assertInvalid { ServedTitlesPageToken.skipOf(request(unrated = true, token = token)) }
        assertInvalid { ServedTitlesPageToken.skipOf(request(pageSize = 51, token = token)) }
    }

    @Test
    fun `a garbled or foreign token is refused`() {
        assertInvalid { ServedTitlesPageToken.skipOf(request(token = "!!!")) }
        val other = ContinueWatchingPageToken.issue(
            ListContinueWatchingRequest
                .newBuilder()
                .setPageSize(50)
                .build(),
            10,
        )
        assertInvalid { ServedTitlesPageToken.skipOf(request(token = other)) }
    }

    @Test
    fun `a negative skip cannot be issued`() {
        assertThrows(IllegalArgumentException::class.java) { ServedTitlesPageToken.issue(request(), -1) }
    }
}
