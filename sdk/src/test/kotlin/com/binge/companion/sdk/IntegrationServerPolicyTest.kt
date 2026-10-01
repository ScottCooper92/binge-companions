package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IntegrationServerPolicyTest {
    @Test
    fun `parcelable metadata from the host is refused`() {
        assertFalse(inboundParcelablePolicy().shouldAcceptParcelableMetadataValues())
    }
}
