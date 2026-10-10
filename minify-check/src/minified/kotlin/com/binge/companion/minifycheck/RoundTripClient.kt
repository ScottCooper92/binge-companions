package com.binge.companion.minifycheck

import android.content.Context
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
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
import io.grpc.StatusException
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder
import io.grpc.binder.SecurityPolicies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.binge.companion.contracts.library.v1.HandshakeRequest as LibraryHandshakeRequest
import com.binge.companion.contracts.stream.v1.HandshakeRequest as StreamHandshakeRequest

/**
 * The host side of the minified round trip (#160): bind [RoundTripService] over a real Binder, handshake, and make one
 * rpc on each contract. It lives in the app, not in the test APK, so R8 shrinks it as it shrinks a minified host, with
 * the consumer keep rules and nothing else. The test APK holds none of the app's classpath, so client code there would
 * be missing at run time. [MinifiedRoundTripTest] calls this class and asserts on the plain values it returns.
 *
 * This class and its result types are `:minify-check`'s one entry point, kept by `minified-test-rules.pro`.
 */
class RoundTripClient(
    context: Context,
) {
    /** What `RequestService` answered: the handshake, `GetStatus`, and the refused `SubmitRequest`. */
    class RequestOutcome(
        val providerName: String,
        val capabilityCount: Int,
        val availability: String,
        val refusalCode: String,
        val refusalReason: String?,
        val refusalMessage: String?,
        val refusalLocale: String?,
    )

    /** What `LibraryService` answered. */
    class LibraryOutcome(
        val providerName: String,
        val played: Boolean,
        val lastPlayedSeconds: Long,
    )

    /** What `StreamService` answered. */
    class StreamOutcome(
        val providerName: String,
        val mayHaveSources: Boolean,
    )

    private val channel: ManagedChannel =
        BinderChannelBuilder
            .forAddress(AndroidComponentAddress.forRemoteComponent(context.packageName, SERVICE_CLASS), context)
            .securityPolicy(SecurityPolicies.internalOnly())
            .build()

    fun request(): RequestOutcome =
        runBlocking {
            val stub = RequestServiceGrpcKt.RequestServiceCoroutineStub(channel)
            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(HandshakeRequest.getDefaultInstance()) }
            val status = withTimeout(CALL_TIMEOUT_MS) { stub.getStatus(GetStatusRequest.newBuilder().setMedia(MOVIE).build()) }
            val refusal =
                try {
                    withTimeout(CALL_TIMEOUT_MS) { stub.submitRequest(SubmitRequestRequest.newBuilder().setMedia(MOVIE).build()) }
                    null
                } catch (e: StatusException) {
                    e
                }
            val message = refusal?.let { userMessageOf(it.trailers) }
            RequestOutcome(
                providerName = handshake.providerName,
                capabilityCount = handshake.capabilitiesCount,
                availability = status.status.availability.name,
                refusalCode = refusal?.status?.code?.name ?: "NOT_REFUSED",
                refusalReason = message?.reason,
                refusalMessage = message?.message,
                refusalLocale = message?.locale,
            )
        }

    fun library(): LibraryOutcome =
        runBlocking {
            val stub = LibraryServiceGrpcKt.LibraryServiceCoroutineStub(channel)
            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(LibraryHandshakeRequest.getDefaultInstance()) }
            val watch = withTimeout(CALL_TIMEOUT_MS) { stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(MOVIE).build()) }
            LibraryOutcome(handshake.providerName, watch.state.played, watch.state.lastPlayed.seconds)
        }

    fun stream(): StreamOutcome =
        runBlocking {
            val stub = StreamServiceGrpcKt.StreamServiceCoroutineStub(channel)
            val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(StreamHandshakeRequest.getDefaultInstance()) }
            val probe = withTimeout(CALL_TIMEOUT_MS) { stub.probe(ProbeRequest.newBuilder().setMedia(MOVIE).build()) }
            StreamOutcome(handshake.providerName, probe.mayHaveSources)
        }

    fun close() {
        channel.shutdownNow()
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
