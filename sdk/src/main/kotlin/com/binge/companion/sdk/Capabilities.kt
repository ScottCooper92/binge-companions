package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeResponse
import io.grpc.Status
import io.grpc.StatusException

/**
 * The handshake a REQUEST companion answers with, from the three things it has to say.
 *
 * Declare only what this connection can honour for THIS signed-in user. The host hides UI for
 * anything undeclared and never calls a gated rpc without its capability, so an over-declared set
 * puts an affordance in front of a user that the provider will refuse.
 */
fun handshakeResponse(
    capabilities: Set<Capability>,
    providerName: String,
    companionVersionName: String,
): HandshakeResponse =
    HandshakeResponse
        .newBuilder()
        .addAllCapabilities(capabilities.sortedBy(Capability::getNumber))
        .setProviderName(providerName)
        .setCompanionVersionName(companionVersionName)
        .build()

/**
 * Refuses a gated rpc the companion did not declare, as `PERMISSION_DENIED` — the contract's code
 * for "the provider user may not perform this action", which is what an undeclared capability
 * means for the user on this connection. A conformant host never gets here; a non-conformant one
 * gets a status it already knows how to render instead of a provider error.
 */
fun Set<Capability>.requireDeclared(capability: Capability) {
    if (capability !in this) {
        throw StatusException(Status.PERMISSION_DENIED.withDescription("$capability was not declared in the handshake"))
    }
}
