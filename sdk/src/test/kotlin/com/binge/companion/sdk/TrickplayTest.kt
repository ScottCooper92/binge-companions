package com.binge.companion.sdk

import com.binge.companion.contracts.library.v1.Trickplay
import com.binge.companion.contracts.library.v1.copy
import com.binge.companion.contracts.library.v1.trickplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TrickplayTest {
    private val valid: Trickplay =
        trickplay {
            urlTemplate = "https://jellyfin.lan/Videos/abc/Trickplay/320/{index}.jpg"
            thumbnailWidth = 320
            thumbnailHeight = 180
            tileColumns = 10
            tileRows = 10
            intervalMillis = 10_000
            thumbnailCount = 720
        }

    @Test
    fun `a descriptor within every bound has no problem`() {
        assertNull(valid.problem())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "https://h/x/no-placeholder.jpg",
            "https://h/{index}/{index}.jpg",
            "ftp://h/{index}.jpg",
            "{index}://h/x.jpg",
            "https://{index}.example.com/x.jpg",
            "https://h/x.jpg#{index}",
            "https://user:{index}@h/x.jpg",
        ],
    )
    fun `a template that is not one placeholder in the path or the query has a problem`(template: String) {
        assertTrue(valid.copy { urlTemplate = template }.problem() != null, template)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://h/x/{index}.jpg",
            "https://h/x.jpg?sheet={index}",
            "http://10.0.0.5:8096/x?sheet={index}&w=320",
            "https://h?sheet={index}",
        ],
    )
    fun `a placeholder in the path or the query is allowed`(template: String) {
        assertNull(valid.copy { urlTemplate = template }.problem(), template)
    }

    @Test
    fun `a template over 2 KiB has a problem`() {
        val long = "https://h/" + "a".repeat(2048) + "/{index}.jpg"

        assertTrue(valid.copy { urlTemplate = long }.problem()!!.contains("2048"))
    }

    @Test
    fun `each count and size is held to its bound`() {
        assertTrue(valid.copy { thumbnailCount = 0 }.problem() != null)
        assertTrue(valid.copy { thumbnailCount = 10_001 }.problem() != null)
        assertNull(valid.copy { thumbnailCount = 10_000 }.problem())
        assertTrue(valid.copy { thumbnailWidth = 15 }.problem() != null)
        assertTrue(valid.copy { thumbnailHeight = 1_025 }.problem() != null)
        assertTrue(valid.copy { tileColumns = 0 }.problem() != null)
        assertTrue(valid.copy { tileRows = 0 }.problem() != null)
        assertTrue(valid.copy { intervalMillis = 99 }.problem() != null)
        assertNull(valid.copy { intervalMillis = 100 }.problem())
    }

    @Test
    fun `a sheet over 8 million decoded pixels has a problem`() {
        // 1024 x 1024 thumbnails in a 3 x 3 grid is 9.4 million pixels.
        val tooBig = valid.copy {
            thumbnailWidth = 1_024
            thumbnailHeight = 1_024
            tileColumns = 3
            tileRows = 3
        }

        assertTrue(tooBig.problem()!!.contains("8000000"))
        assertNull(tooBig.copy { tileRows = 2 }.problem())
    }

    @Test
    fun `the sheet count rounds up what one sheet holds`() {
        assertEquals(8, valid.sheetCount())
        assertEquals(7, valid.copy { thumbnailCount = 700 }.sheetCount())
        assertEquals(0, valid.copy { tileColumns = 0 }.sheetCount())
    }

    @Test
    fun `a sheet url is the template with the number put in and nothing else`() {
        assertEquals("https://jellyfin.lan/Videos/abc/Trickplay/320/0.jpg", valid.sheetUrl(0))
        assertEquals("https://jellyfin.lan/Videos/abc/Trickplay/320/7.jpg", valid.sheetUrl(7))
    }

    @Test
    fun `a sheet url is refused for a negative index or an unusable template`() {
        assertThrows<IllegalArgumentException> { valid.sheetUrl(-1) }
        assertThrows<IllegalArgumentException> { valid.copy { urlTemplate = "https://h/x.jpg" }.sheetUrl(0) }
    }
}
