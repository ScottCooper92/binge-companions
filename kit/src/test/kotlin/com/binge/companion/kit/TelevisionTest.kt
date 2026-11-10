package com.binge.companion.kit

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelevisionTest {
    private fun config(uiMode: Int) = Configuration().apply { this.uiMode = uiMode }

    @Test
    fun `the television UI mode type is a television`() {
        assertTrue(config(Configuration.UI_MODE_TYPE_TELEVISION).isTelevision())
    }

    @Test
    fun `other bits in uiMode do not hide it`() {
        assertTrue(config(Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES).isTelevision())
    }

    @Test
    fun `other UI mode types are not a television`() {
        assertFalse(config(Configuration.UI_MODE_TYPE_NORMAL).isTelevision())
        assertFalse(config(Configuration.UI_MODE_TYPE_WATCH or Configuration.UI_MODE_NIGHT_YES).isTelevision())
        assertFalse(config(Configuration.UI_MODE_TYPE_CAR).isTelevision())
    }
}
