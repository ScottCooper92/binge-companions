package com.binge.companion.minifycheck

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.stream.v1.ProbeRequest
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.userMessageOf
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder
import io.grpc.binder.SecurityPolicies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.binge.companion.contracts.library.v1.HandshakeRequest as LibraryHandshakeRequest
import com.binge.companion.contracts.stream.v1.HandshakeRequest as StreamHandshakeRequest

/**
 * A host's whole path to an integration, on a build R8 has shrunk the way a consumer's release build is (#160): bind
 * over a real Binder, handshake, and one rpc on each contract, both ends using only the SDK's consumer keep rules.
 * [MinifiedKeepRulesTest] reads what R8 kept; this proves what is kept still works. A missing rule fails here as it
 * would on a user's device: `ServiceConfigurationError` from `forAddress`, or `Field … not found` on the first parse.
 */
@RunWith(AndroidJUnit4::class)
class MinifiedRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var channel: ManagedChannel

    @Before
    fun bind() {
        channel =
            BinderChannelBuilder
                .forAddress(AndroidComponentAddress.forRemoteComponent(context.packageName, SERVICE_CLASS), context)
                .securityPolicy(SecurityPolicies.internalOnly())
                .build()
    }

    @After
    fun unbind() {
        channel.shutdownNow()
    }

    @Test
    fun requestHandshakesAnswersAndRefusesWithAUserMessage() =
        runBlocking {
            val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)

            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(HandshakeRequest.getDefaultInstance()) }
            assertEquals(RoundTripService.PROVIDER, handshake.providerName)
            assertEquals(1, handshake.capabilitiesCount)

            val status = withTimeout(CALL_TIMEOUT_MS) { stub.getStatus(GetStatusRequest.newBuilder().setMedia(MOVIE).build()) }
            assertEquals(Availability.AVAILABILITY_AVAILABLE, status.status.availability)

            try {
                withTimeout(CALL_TIMEOUT_MS) { stub.submitRequest(SubmitRequestRequest.newBuilder().setMedia(MOVIE).build()) }
                fail("the submit was meant to be refused")
            } catch (e: StatusException) {
                assertEquals(Status.Code.RESOURCE_EXHAUSTED, e.status.code)
                val message = userMessageOf(e.trailers)
                assertEquals(RoundTripService.REASON, message?.reason)
                assertEquals(RoundTripService.MESSAGE, message?.message)
                assertEquals(RoundTripService.LOCALE, message?.locale)
            }
        }

    @Test
    fun libraryHandshakesAndAnswersWithATimestamp() =
        runBlocking {
            val stub = LibraryServiceGrpcKt.LibraryServiceCoroutineStub(channel)

            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(LibraryHandshakeRequest.getDefaultInstance()) }
            assertEquals(RoundTripService.PROVIDER, handshake.providerName)

            val watch = withTimeout(CALL_TIMEOUT_MS) { stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(MOVIE).build()) }
            assertTrue(watch.state.played)
            assertEquals(RoundTripService.LAST_PLAYED, watch.state.lastPlayed.seconds)
        }

    @Test
    fun streamHandshakesAndAnswers() =
        runBlocking {
            val stub = StreamServiceGrpcKt.StreamServiceCoroutineStub(channel)

            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(StreamHandshakeRequest.getDefaultInstance()) }
            assertEquals(RoundTripService.PROVIDER, handshake.providerName)

            val probe = withTimeout(CALL_TIMEOUT_MS) { stub.probe(ProbeRequest.newBuilder().setMedia(MOVIE).build()) }
            assertTrue(probe.mayHaveSources)
        }

    private companion object {
        const val SERVICE_CLASS = "com.binge.companion.minifycheck.RoundTripService"
        const val CALL_TIMEOUT_MS = 10_000L
        val MOVIE: MediaId =
            MediaId
                .newBuilder()
                .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
                .setTmdbId(RoundTripService.TMDB_ID)
                .build()
    }
}
