package com.binge.companion.sdk

import com.binge.companion.contracts.rpc.ErrorInfo
import com.binge.companion.contracts.rpc.LocalizedMessage
import com.google.protobuf.Any
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import com.binge.companion.contracts.rpc.Status as RpcStatus

/**
 * This status, carrying a sentence the host may show the user (#120). The contracts' headers describe the
 * channel: a `google.rpc.Status` in the `grpc-status-details-bin` trailer, holding an [ErrorInfo] with a
 * machine [reason] and [domain] and a [LocalizedMessage] in [locale], normally `HostInfo.locale`. The
 * status description stays developer text and never carries [message].
 *
 * Built by hand rather than through grpc's `StatusProto`, which needs full protobuf: these are javalite
 * stubs, so `Any` is packed with its type URL directly.
 */
fun Status.withUserMessage(
    reason: String,
    message: String,
    locale: String,
    domain: String = USER_MESSAGE_DOMAIN,
): StatusException {
    val details =
        RpcStatus
            .newBuilder()
            .setCode(code.value())
            .setMessage(description.orEmpty())
            .addDetails(
                pack(
                    ErrorInfo
                        .newBuilder()
                        .setReason(reason)
                        .setDomain(domain)
                        .build(),
                    "google.rpc.ErrorInfo",
                ),
            ).addDetails(
                pack(
                    LocalizedMessage
                        .newBuilder()
                        .setLocale(locale)
                        .setMessage(message)
                        .build(),
                    "google.rpc.LocalizedMessage",
                ),
            ).build()
    val trailers = Metadata().apply { put(STATUS_DETAILS_KEY, details.toByteArray()) }
    return StatusException(this, trailers)
}

/** What a host reads back from a failure's trailers: the reason, if any, and the sentence to show, if any. */
data class UserMessage(
    val reason: String?,
    val message: String,
    val locale: String,
)

/**
 * The user-facing sentence in [trailers], or null when the integration sent none, which is when the host
 * shows its own copy for the code. Malformed or foreign details are ignored rather than thrown: a bad
 * detail must not turn a readable failure into a crash.
 */
fun userMessageOf(trailers: Metadata?): UserMessage? {
    val bytes = trailers?.get(STATUS_DETAILS_KEY) ?: return null
    val status =
        try {
            RpcStatus.parseFrom(bytes)
        } catch (_: InvalidProtocolBufferException) {
            return null
        }
    val localized = status.detailsList.unpack(LOCALIZED_MESSAGE_URL, LocalizedMessage::parseFrom) ?: return null
    val reason = status.detailsList.unpack(ERROR_INFO_URL, ErrorInfo::parseFrom)?.reason
    return UserMessage(reason = reason, message = localized.message, locale = localized.locale)
}

private fun pack(message: MessageLite, fullName: String): Any =
    Any
        .newBuilder()
        .setTypeUrl(TYPE_URL_PREFIX + fullName)
        .setValue(message.toByteString())
        .build()

private fun <T> List<Any>.unpack(typeUrl: String, parse: (com.google.protobuf.ByteString) -> T): T? =
    firstOrNull { it.typeUrl == typeUrl }?.let { packed ->
        try {
            parse(packed.value)
        } catch (_: InvalidProtocolBufferException) {
            null
        }
    }

/** The domain an integration's reasons belong to when it names none of its own. */
const val USER_MESSAGE_DOMAIN = "binge.companion"

private const val TYPE_URL_PREFIX = "type.googleapis.com/"
private const val ERROR_INFO_URL = TYPE_URL_PREFIX + "google.rpc.ErrorInfo"
private const val LOCALIZED_MESSAGE_URL = TYPE_URL_PREFIX + "google.rpc.LocalizedMessage"
private val STATUS_DETAILS_KEY: Metadata.Key<ByteArray> = Metadata.Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER)
