package com.binge.integration.sdk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.grpc.BindableService
import io.grpc.Server
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderServerBuilder
import io.grpc.binder.IBinderReceiver
import io.grpc.binder.InboundParcelablePolicy
import io.grpc.binder.SecurityPolicy
import io.grpc.binder.ServerSecurityPolicy

/**
 * The exported Service a companion app declares: it hosts a gRPC server on the Binder transport
 * and hands the host that transport's own IBinder from [onBind].
 *
 * A subclass supplies the generated service implementations and the caller policy; everything
 * about standing the server up is here so a companion author does not re-derive it. The server
 * is built once in [onCreate] — Android calls that once and [onBind] once per client, and a server
 * per bind would leak the first when a second host connected.
 */
abstract class IntegrationService : Service() {
    private val receiver = IBinderReceiver()
    private var server: Server? = null

    /** The contract implementations this Service serves, e.g. a `RequestServiceCoroutineImplBase`. */
    protected abstract fun services(): List<BindableService>

    /** Who may bind. [HostPolicy.pinned] in release; [HostPolicy.anyCaller] only in a debug build. */
    protected abstract fun hostPolicy(): SecurityPolicy

    override fun onCreate() {
        super.onCreate()
        val policy = hostPolicy()
        val services = services()
        val serverPolicy = ServerSecurityPolicy.newBuilder()
        // One policy per service NAME: a service this builder is not told about is refused by
        // grpc-binder's default, which is the safe direction for a service the author forgot.
        services.forEach { serverPolicy.servicePolicy(it.bindService().serviceDescriptor.name, policy) }
        server = BinderServerBuilder
            .forAddress(AndroidComponentAddress.forContext(this), receiver)
            .securityPolicy(serverPolicy.build())
            // Every payload is protobuf; a Parcelable from the host is a deserialisation surface
            // the contract never uses, so it is refused rather than left to the transport default.
            .inboundParcelablePolicy(
                InboundParcelablePolicy.newBuilder().setAcceptParcelableMetadataValues(false).build(),
            ).apply { services.forEach(::addService) }
            .build()
            .start()
    }

    /** Non-null once [onCreate] has run: the receiver is filled by `build()`, not `start()`. */
    override fun onBind(intent: Intent?): IBinder? = receiver.get()

    override fun onDestroy() {
        server?.shutdownNow()
        server = null
        super.onDestroy()
    }
}
