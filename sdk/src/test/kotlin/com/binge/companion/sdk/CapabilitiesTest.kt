package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.version
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.binge.companion.contracts.library.v1.Capability as LibraryCapability
import com.binge.companion.contracts.stream.v1.Capability as StreamCapability

class CapabilitiesTest {
    @Test
    fun `a handshake carries the declared set in number order`() {
        val response =
            handshakeResponse(
                capabilities = setOf(Capability.CAPABILITY_BLOCK, Capability.CAPABILITY_RETRY),
                providerName = "Example",
                companionVersionName = "1.2.3",
            )

        assertEquals(listOf(Capability.CAPABILITY_RETRY, Capability.CAPABILITY_BLOCK), response.capabilitiesList)
        assertEquals("Example", response.providerName)
        assertEquals("1.2.3", response.companionVersionName)
    }

    @Test
    fun `a handshake carries the version list, and an empty one means the standard version only`() {
        val uhd = version { id = "uhd" }
        val withVersions = handshakeResponse(emptySet(), "Example", "1.2.3", versions = listOf(version { label = "Standard" }, uhd))

        assertEquals(listOf("", "uhd"), withVersions.versionsList.map { it.id })
        assertEquals(0, handshakeResponse(emptySet(), "Example", "1.2.3").versionsCount)
    }

    @Test
    fun `an undeclared capability is refused as permission denied`() {
        val declared = setOf(Capability.CAPABILITY_RETRY)

        val thrown = assertThrows(StatusException::class.java) { declared.requireDeclared(Capability.CAPABILITY_CANCEL) }

        assertEquals(Status.Code.PERMISSION_DENIED, thrown.status.code)
    }

    @Test
    fun `a declared capability passes`() {
        setOf(Capability.CAPABILITY_CANCEL).requireDeclared(Capability.CAPABILITY_CANCEL)
    }

    @Test
    fun `an undeclared stream capability is refused as permission denied`() {
        val declared = emptySet<StreamCapability>()
        val capability = StreamCapability.values().first { it != StreamCapability.UNRECOGNIZED && it.number != 0 }

        val thrown = assertThrows(StatusException::class.java) { declared.requireDeclared(capability) }

        assertEquals(Status.Code.PERMISSION_DENIED, thrown.status.code)
    }

    @Test
    fun `a declared stream capability passes`() {
        val capability = StreamCapability.values().first { it != StreamCapability.UNRECOGNIZED && it.number != 0 }

        setOf(capability).requireDeclared(capability)
    }

    @Test
    fun `an undeclared library capability is refused as permission denied`() {
        val declared = emptySet<LibraryCapability>()
        val capability = LibraryCapability.values().first { it != LibraryCapability.UNRECOGNIZED && it.number != 0 }

        val thrown = assertThrows(StatusException::class.java) { declared.requireDeclared(capability) }

        assertEquals(Status.Code.PERMISSION_DENIED, thrown.status.code)
    }

    @Test
    fun `a declared library capability passes`() {
        val capability = LibraryCapability.values().first { it != LibraryCapability.UNRECOGNIZED && it.number != 0 }

        setOf(capability).requireDeclared(capability)
    }
}
