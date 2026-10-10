package com.binge.companion.minifycheck

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A host's whole path to an integration, on a build R8 has shrunk the way a consumer's release build is (#160): bind
 * over a real Binder, handshake, and one rpc on each contract, both ends using only the SDK's consumer keep rules.
 * [MinifiedKeepRulesTest] reads what R8 kept; this proves what is kept still works. A missing rule fails here as it
 * would on a user's device: `ServiceConfigurationError` from `forAddress`, or `Field … not found` on the first parse.
 */
@RunWith(AndroidJUnit4::class)
class MinifiedRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var client: RoundTripClient

    @Before
    fun bind() {
        client = RoundTripClient(context)
    }

    @After
    fun unbind() {
        client.close()
    }

    @Test
    fun requestHandshakesAnswersAndRefusesWithAUserMessage() {
        val outcome = client.request()
        assertEquals(RoundTripService.PROVIDER, outcome.providerName)
        assertEquals(1, outcome.capabilityCount)
        assertEquals("AVAILABILITY_AVAILABLE", outcome.availability)
        assertEquals("RESOURCE_EXHAUSTED", outcome.refusalCode)
        assertEquals(RoundTripService.REASON, outcome.refusalReason)
        assertEquals(RoundTripService.MESSAGE, outcome.refusalMessage)
        assertEquals(RoundTripService.LOCALE, outcome.refusalLocale)
    }

    @Test
    fun libraryHandshakesAndAnswersWithATimestamp() {
        val outcome = client.library()
        assertEquals(RoundTripService.PROVIDER, outcome.providerName)
        assertTrue(outcome.played)
        assertEquals(RoundTripService.LAST_PLAYED, outcome.lastPlayedSeconds)
    }

    @Test
    fun streamHandshakesAndAnswers() {
        val outcome = client.stream()
        assertEquals(RoundTripService.PROVIDER, outcome.providerName)
        assertTrue(outcome.mayHaveSources)
    }
}
