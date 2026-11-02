package com.binge.companion.sdk

import android.content.Intent
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.binder.SecurityPolicy
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Stands in for a companion's Service: one empty gRPC service and the permissive policy. */
class TestIntegrationService : IntegrationService() {
    override fun services(): List<BindableService> = listOf(BindableService { ServerServiceDefinition.builder("test.Service").build() })

    override fun hostPolicy(): SecurityPolicy = HostPolicy.anyCaller()
}

/**
 * [IntegrationService] stands up a Binder-transport gRPC server when Android creates it, and hands out that transport's binder
 * on bind (#282). The Parcelable refusal it installs is [inboundParcelablePolicy], tested apart; the per-service security
 * policy is grpc-binder's and is exercised across a real Binder by the device lane.
 */
@RunWith(RobolectricTestRunner::class)
class IntegrationServiceTest {
    @Test
    fun `a created Service hands the host a binder`() {
        val controller = Robolectric.buildService(TestIntegrationService::class.java).create()

        assertNotNull(controller.get().onBind(Intent()))

        controller.destroy()
    }

    @Test
    fun `a Service that was never created has no binder to hand out`() {
        val service = Robolectric.buildService(TestIntegrationService::class.java).get()

        assertNull(service.onBind(Intent()))
    }

    @Test
    fun `destroying the Service shuts its server down without error`() {
        val controller = Robolectric.buildService(TestIntegrationService::class.java).create()

        controller.destroy()
    }
}
