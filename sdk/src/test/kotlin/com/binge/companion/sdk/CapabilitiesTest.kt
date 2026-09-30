package com.binge.companion.sdk

import com.binge.companion.contracts.request.v1.Capability
import io.grpc.Status
import io.grpc.StatusException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CapabilitiesTest {
    @Test
    fun `a handshake carries the declared set in number order`() {
        val response =
            handshakeResponse(
                capabilities = setOf(Capability.CAPABILITY_BLOCK, Capability.CAPABILITY_REQUEST_4K),
                providerName = "Example",
                companionVersionName = "1.2.3",
            )

        assertEquals(listOf(Capability.CAPABILITY_REQUEST_4K, Capability.CAPABILITY_BLOCK), response.capabilitiesList)
        assertEquals("Example", response.providerName)
        assertEquals("1.2.3", response.companionVersionName)
    }

    @Test
    fun `an undeclared capability is refused as permission denied`() {
        val declared = setOf(Capability.CAPABILITY_REQUEST_4K)

        val thrown = assertThrows(StatusException::class.java) { declared.requireDeclared(Capability.CAPABILITY_CANCEL) }

        assertEquals(Status.Code.PERMISSION_DENIED, thrown.status.code)
    }

    @Test
    fun `a declared capability passes`() {
        setOf(Capability.CAPABILITY_CANCEL).requireDeclared(Capability.CAPABILITY_CANCEL)
    }
}
