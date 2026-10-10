package com.binge.companion.sdk

import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SeasonNumbersTest {
    private fun assertInvalidArgument(seasons: List<Int>) {
        val error = assertThrows<StatusException> { seasons.checkedSeasonNumbers() }
        assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }

    @Test
    fun `a clean list is answered unchanged, season zero and order included`() {
        assertEquals(listOf(3, 0, 1), listOf(3, 0, 1).checkedSeasonNumbers())
        assertEquals(emptyList<Int>(), emptyList<Int>().checkedSeasonNumbers())
    }

    @Test
    fun `a negative season is INVALID_ARGUMENT`() {
        assertInvalidArgument(listOf(1, -1))
    }

    @Test
    fun `a repeated season is INVALID_ARGUMENT`() {
        assertInvalidArgument(listOf(1, 2, 1))
    }

    @Test
    fun `the cap is allowed and one more is INVALID_ARGUMENT`() {
        val atCap = (0 until MAX_SEASON_NUMBERS).toList()

        assertEquals(atCap, atCap.checkedSeasonNumbers())
        assertInvalidArgument((0..MAX_SEASON_NUMBERS).toList())
    }
}
