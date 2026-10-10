package com.binge.companion.minifycheck

import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.GetWatchStateResponse
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.WatchState
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.GetStatusResponse
import com.binge.companion.contracts.request.v1.HandshakeRequest
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.RequestServiceGrpcKt
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.SubmitRequestRequest
import com.binge.companion.contracts.request.v1.SubmitRequestResponse
import com.binge.companion.contracts.stream.v1.ProbeRequest
import com.binge.companion.contracts.stream.v1.ProbeResponse
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.sdk.IntegrationService
import com.binge.companion.sdk.handshakeResponse
import com.binge.companion.sdk.withUserMessage
import com.google.protobuf.Timestamp
import io.grpc.BindableService
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.binder.SecurityPolicies
import io.grpc.binder.SecurityPolicy
import com.binge.companion.contracts.library.v1.Capability as LibraryCapability
import com.binge.companion.contracts.library.v1.HandshakeRequest as LibraryHandshakeRequest
import com.binge.companion.contracts.library.v1.HandshakeResponse as LibraryHandshakeResponse
import com.binge.companion.contracts.stream.v1.Capability as StreamCapability
import com.binge.companion.contracts.stream.v1.HandshakeRequest as StreamHandshakeRequest
import com.binge.companion.contracts.stream.v1.HandshakeResponse as StreamHandshakeResponse

/**
 * The integration side of the minified round trip (#160): every contract, a handshake and one rpc each, served by the
 * SDK's [IntegrationService] exactly as a companion serves them. Each answer is fixed, and each rpc refuses a request
 * whose media did not arrive intact, so [MinifiedRoundTripTest] proves a parse in both directions. `SubmitRequest`
 * always fails with a user message, so the test parses a `google.rpc.Status` trailer, and `GetWatchState` carries a
 * `Timestamp`. Those are the two well-known types the contracts import.
 *
 * Only this app's own uid may bind, which is the test.
 */
class RoundTripService : IntegrationService() {
    override fun services(): List<BindableService> = listOf(Request(), Library(), Stream())

    override fun hostPolicy(): SecurityPolicy = SecurityPolicies.internalOnly()

    private class Request : RequestServiceGrpcKt.RequestServiceCoroutineImplBase() {
        override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
            handshakeResponse(setOf(Capability.CAPABILITY_OBSERVE_STATUS), PROVIDER, VERSION)

        override suspend fun getStatus(request: GetStatusRequest): GetStatusResponse {
            request.media.requireSent()
            return GetStatusResponse
                .newBuilder()
                .setStatus(RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_AVAILABLE))
                .build()
        }

        override suspend fun submitRequest(request: SubmitRequestRequest): SubmitRequestResponse {
            request.media.requireSent()
            throw Status.RESOURCE_EXHAUSTED.withDescription("round trip").withUserMessage(REASON, MESSAGE, LOCALE)
        }
    }

    private class Library : LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase() {
        override suspend fun handshake(request: LibraryHandshakeRequest): LibraryHandshakeResponse =
            LibraryHandshakeResponse
                .newBuilder()
                .addCapabilities(LibraryCapability.CAPABILITY_AVAILABILITY)
                .setProviderName(PROVIDER)
                .setCompanionVersionName(VERSION)
                .build()

        override suspend fun getWatchState(request: GetWatchStateRequest): GetWatchStateResponse {
            request.media.requireSent()
            return GetWatchStateResponse
                .newBuilder()
                .setState(WatchState.newBuilder().setPlayed(true).setLastPlayed(Timestamp.newBuilder().setSeconds(LAST_PLAYED)))
                .build()
        }
    }

    private class Stream : StreamServiceGrpcKt.StreamServiceCoroutineImplBase() {
        override suspend fun handshake(request: StreamHandshakeRequest): StreamHandshakeResponse =
            StreamHandshakeResponse
                .newBuilder()
                .addCapabilities(StreamCapability.CAPABILITY_PROBE)
                .setProviderName(PROVIDER)
                .setCompanionVersionName(VERSION)
                .build()

        override suspend fun probe(request: ProbeRequest): ProbeResponse {
            request.media.requireSent()
            return ProbeResponse.newBuilder().setMayHaveSources(true).build()
        }
    }

    companion object {
        const val PROVIDER = "Round trip"
        const val VERSION = "1"
        const val TMDB_ID = 603
        const val REASON = "QUOTA_EXCEEDED"
        const val MESSAGE = "Quota spent"
        const val LOCALE = "en"
        const val LAST_PLAYED = 1_700_000_000L

        /** A media id that lost its fields on the way here would read as the default instance. */
        private fun MediaId.requireSent() {
            if (tmdbId != TMDB_ID) throw StatusException(Status.INVALID_ARGUMENT.withDescription("media arrived as $this"))
        }
    }
}
