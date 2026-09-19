package com.binge.integration.sdk

import com.binge.integration.contracts.library.v1.PlayTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val PACKAGE = "com.example.server"
private const val ACTION = "com.example.server.PLAY"
private const val DATA_URI = "server://play/603"
private const val URL = "https://example.com/play/603"

class PlayTargetTest {
    @Test
    fun `androidIntent carries its package, action, data and extras`() {
        val target =
            playTarget {
                androidIntent(packageName = PACKAGE, action = ACTION, dataUri = DATA_URI, extras = mapOf("season" to "1"))
            }

        assertEquals(PlayTarget.TargetCase.ANDROID_INTENT, target.targetCase)
        assertEquals(PACKAGE, target.androidIntent.packageName)
        assertEquals(ACTION, target.androidIntent.action)
        assertEquals(DATA_URI, target.androidIntent.dataUri)
        assertEquals(listOf("season" to "1"), target.androidIntent.extrasList.map { it.key to it.value })
    }

    @Test
    fun `androidIntent defaults to no data uri and no extras`() {
        val target = playTarget { androidIntent(packageName = PACKAGE, action = ACTION) }

        assertEquals("", target.androidIntent.dataUri)
        assertEquals(emptyList<Pair<String, String>>(), target.androidIntent.extrasList.map { it.key to it.value })
    }

    @Test
    fun `webUrl carries its url`() {
        val target = playTarget { webUrl(URL) }

        assertEquals(PlayTarget.TargetCase.WEB_URL, target.targetCase)
        assertEquals(URL, target.webUrl.url)
    }

    @Test
    fun `the later call wins when both arms are set`() {
        val target =
            playTarget {
                webUrl(URL)
                androidIntent(packageName = PACKAGE, action = ACTION)
            }

        assertEquals(PlayTarget.TargetCase.ANDROID_INTENT, target.targetCase)
    }

    @Test
    fun `an androidIntent target resolves when its package is installed`() {
        val target =
            playTarget { androidIntent(packageName = PACKAGE, action = ACTION, dataUri = DATA_URI, extras = mapOf("season" to "1")) }

        val resolved = resolveIntent(target) { it == PACKAGE }

        assertEquals(
            ResolvedPlayTarget.App(packageName = PACKAGE, action = ACTION, dataUri = DATA_URI, extras = listOf("season" to "1")),
            resolved,
        )
    }

    @Test
    fun `an androidIntent target with a blank data uri resolves with no data`() {
        val target = playTarget { androidIntent(packageName = PACKAGE, action = ACTION) }

        val resolved = resolveIntent(target) { true }

        assertEquals(ResolvedPlayTarget.App(PACKAGE, ACTION, dataUri = null, extras = emptyList()), resolved)
    }

    @Test
    fun `an androidIntent target whose package is not installed resolves to nothing`() {
        val target = playTarget { androidIntent(packageName = PACKAGE, action = ACTION) }

        assertNull(resolveIntent(target) { false })
    }

    @Test
    fun `a webUrl target resolves without asking the package lookup`() {
        val target = playTarget { webUrl(URL) }

        val resolved = resolveIntent(target) { error("must not be called for a webUrl target") }

        assertEquals(ResolvedPlayTarget.Web(URL), resolved)
    }

    @Test
    fun `an unset target resolves to nothing`() {
        val target = PlayTarget.getDefaultInstance()

        assertNull(resolveIntent(target) { true })
    }
}
