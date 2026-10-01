package com.binge.companion.sdk

import io.grpc.binder.InboundParcelablePolicy

/**
 * Every payload is protobuf, so a Parcelable from the host is a deserialisation surface the
 * contract never uses. It is refused rather than left to the transport default.
 */
internal fun inboundParcelablePolicy(): InboundParcelablePolicy =
    InboundParcelablePolicy.newBuilder().setAcceptParcelableMetadataValues(false).build()
