package com.binge.companion.minifycheck

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.binge.companion.contracts.library.v1.LibraryServiceGrpc
import com.binge.companion.contracts.request.v1.RequestServiceGrpc
import com.binge.companion.contracts.stream.v1.StreamServiceGrpc
import com.binge.companion.sdk.userMessageOf
import io.grpc.Metadata
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder

/**
 * What a host's release build keeps of the SDK, and nothing more: it opens a Binder channel, holds every
 * contract's service descriptor, and reads the user message from a failure's trailers, which R8 cannot
 * know are empty. It reads no field
 * of any message, which is the case the contracts' consumer rule exists for. R8 would otherwise strip
 * every field the app never reads, and protobuf-javalite finds fields by name on the first parse.
 *
 * [MinifiedKeepRulesTest] reads what R8 kept of this. Nothing runs it.
 */
class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channel =
            BinderChannelBuilder
                .forAddress(AndroidComponentAddress.forRemoteComponent(PACKAGE, CLASS), this)
                .build()
        val services =
            listOf(
                RequestServiceGrpc.getServiceDescriptor(),
                LibraryServiceGrpc.getServiceDescriptor(),
                StreamServiceGrpc.getServiceDescriptor(),
            )
        Log.i(TAG, "${services.flatMap { it.methods }.size} methods; ${userMessageOf(Metadata())}")
        channel.shutdownNow()
    }

    private companion object {
        const val TAG = "MinifyCheck"
        const val PACKAGE = "com.example.companion"
        const val CLASS = "com.example.companion.IntegrationService"
    }
}
