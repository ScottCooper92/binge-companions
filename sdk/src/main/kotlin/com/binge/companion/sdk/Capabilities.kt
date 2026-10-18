package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.HandshakeResponse
import com.binge.companion.contracts.request.v1.Version
import io.grpc.Status
import io.grpc.StatusException
import com.binge.companion.contracts.library.v1.Capability as LibraryCapability
import com.binge.companion.contracts.stream.v1.Capability as StreamCapability

/**
 * The handshake a REQUEST companion answers with, from what it has to say.
 *
 * Declare only what this integration can honour for THIS signed-in user. The host hides UI for
 * anything undeclared and never calls a gated rpc without its capability, so an over-declared set
 * puts an affordance in front of a user that the provider will refuse.
 */
fun handshakeResponse(
    capabilities: Set<Capability>,
    providerName: String,
    companionVersionName: String,
    versions: List<Version> = emptyList(),
): HandshakeResponse =
    HandshakeResponse
        .newBuilder()
        .addAllCapabilities(capabilities.sortedBy(Capability::getNumber))
        .setProviderName(providerName)
        .setCompanionVersionName(companionVersionName)
        .addAllVersions(versions)
        .build()

/**
 * Refuses a gated rpc the companion did not declare, as `PERMISSION_DENIED` — the contract's code
 * for "the provider user may not perform this action", which is what an undeclared capability
 * means for the user. A conformant host never gets here; a non-conformant one
 * gets a status it already knows how to render instead of a provider error.
 */
fun Set<Capability>.requireDeclared(capability: Capability) {
    if (capability !in this) {
        throw StatusException(Status.PERMISSION_DENIED.withDescription("$capability was not declared in the handshake"))
    }
}

/**
 * [requireDeclared] for a STREAM companion: refuses a gated STREAM rpc whose capability the
 * handshake did not declare, as the same `PERMISSION_DENIED`. It has its own JVM name because the
 * two overloads erase to the same signature.
 */
@JvmName("requireStreamDeclared")
fun Set<StreamCapability>.requireDeclared(capability: StreamCapability) {
    if (capability !in this) {
        throw StatusException(Status.PERMISSION_DENIED.withDescription("$capability was not declared in the handshake"))
    }
}

/**
 * [requireDeclared] for a LIBRARY companion: refuses a gated LIBRARY rpc whose capability the
 * handshake did not declare, as the same `PERMISSION_DENIED` (`library.proto` documents it). It has
 * its own JVM name for the same reason as the STREAM one.
 */
@JvmName("requireLibraryDeclared")
fun Set<LibraryCapability>.requireDeclared(capability: LibraryCapability) {
    if (capability !in this) {
        throw StatusException(Status.PERMISSION_DENIED.withDescription("$capability was not declared in the handshake"))
    }
}
