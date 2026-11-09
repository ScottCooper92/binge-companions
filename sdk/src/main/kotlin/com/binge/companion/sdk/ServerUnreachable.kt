package com.binge.companion.sdk

import com.binge.companion.contracts.rpc.ErrorInfo
import com.binge.companion.contracts.rpc.LocalizedMessage
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import com.binge.companion.contracts.rpc.Status as RpcStatus

/**
 * A failed call marked "signed in, but the server behind the companion is not answering" (#299).
 * Status is `UNAVAILABLE`, or `DEADLINE_EXCEEDED` when [timedOut]; it carries an [ErrorInfo] with
 * [SERVER_UNREACHABLE_REASON] and the cleaned [serverName] under [SERVER_NAME_KEY].
 * A non-blank [message] adds a `LocalizedMessage` in [locale], as [withUserMessage] does.
 * Declare `CAPABILITY_SERVER_UNREACHABLE` first; when to send it is in `common.proto`.
 */
fun serverUnreachable(
    serverName: String? = null,
    timedOut: Boolean = false,
    message: String? = null,
    locale: String = "",
): StatusException {
    val info =
        ErrorInfo
            .newBuilder()
            .setReason(SERVER_UNREACHABLE_REASON)
            .setDomain(USER_MESSAGE_DOMAIN)
    cleanServerName(serverName)?.let { info.putMetadata(SERVER_NAME_KEY, it) }
    val status = if (timedOut) Status.DEADLINE_EXCEEDED else Status.UNAVAILABLE
    val details =
        RpcStatus
            .newBuilder()
            .setCode(status.code.value())
            .addDetails(pack(info.build(), "google.rpc.ErrorInfo"))
    if (!message.isNullOrBlank()) {
        require(locale.isNotBlank()) { "a message needs the locale it is written in" }
        details.addDetails(
            pack(
                LocalizedMessage
                    .newBuilder()
                    .setLocale(locale)
                    .setMessage(message)
                    .build(),
                "google.rpc.LocalizedMessage",
            ),
        )
    }
    val trailers = Metadata().apply { put(STATUS_DETAILS_KEY, details.build().toByteArray()) }
    return StatusException(status, trailers)
}

/**
 * What a host learns from a [serverUnreachable] failure: the server did not answer, and its name if the
 * companion gave one. [serverName] is already cleaned, so a host shows it as it is; null means the companion
 * named none, and the host uses the companion's own name.
 */
data class ServerUnreachable(
    val serverName: String?,
)

/**
 * The server-unreachable detail in [trailers], or null when there is none (the host shows its generic message).
 * Malformed or foreign details, and an `ErrorInfo` with another reason or domain, give null rather than throw.
 * [ServerUnreachable.serverName] is cleaned here, as a companion is a third party: control characters and bidi
 * overrides are removed, the text is trimmed and cut to [SERVER_NAME_MAX_CODE_POINTS] code points.
 */
fun serverUnreachableOf(trailers: Metadata?): ServerUnreachable? {
    val bytes = trailers?.get(STATUS_DETAILS_KEY) ?: return null
    val status =
        try {
            RpcStatus.parseFrom(bytes)
        } catch (_: InvalidProtocolBufferException) {
            return null
        }
    val info =
        status.detailsList
            .unpack(ERROR_INFO_URL, ErrorInfo::parseFrom)
            ?.takeIf { it.reason == SERVER_UNREACHABLE_REASON && it.domain == USER_MESSAGE_DOMAIN }
            ?: return null
    return ServerUnreachable(serverName = cleanServerName(info.getMetadataOrDefault(SERVER_NAME_KEY, "")))
}

/**
 * The same, from the failure itself: null unless it is `UNAVAILABLE` or `DEADLINE_EXCEEDED`, the only codes
 * the detail travels on, and carries the detail.
 */
fun serverUnreachableOf(failure: Throwable): ServerUnreachable? {
    val code = Status.fromThrowable(failure).code
    if (code != Status.Code.UNAVAILABLE && code != Status.Code.DEADLINE_EXCEEDED) return null
    return serverUnreachableOf(Status.trailersFromThrowable(failure))
}

private fun cleanServerName(raw: String?): String? {
    if (raw == null) return null
    val kept = StringBuilder()
    raw.codePoints().forEach { cp ->
        if (!Character.isISOControl(cp) && cp !in BIDI_OVERRIDES && cp !in BIDI_ISOLATES) kept.appendCodePoint(cp)
    }
    val trimmed = kept.toString().trim()
    val cut =
        if (trimmed.codePointCount(0, trimmed.length) > SERVER_NAME_MAX_CODE_POINTS) {
            trimmed.substring(0, trimmed.offsetByCodePoints(0, SERVER_NAME_MAX_CODE_POINTS)).trimEnd()
        } else {
            trimmed
        }
    return cut.ifEmpty { null }
}

/** The `ErrorInfo.reason` of the shared server-unreachable detail. */
const val SERVER_UNREACHABLE_REASON = "SERVER_UNREACHABLE"

/** The `ErrorInfo.metadata` key that holds the display name of the server that did not answer. */
const val SERVER_NAME_KEY = "server_name"

/** The longest server name a host shows, in Unicode code points. */
const val SERVER_NAME_MAX_CODE_POINTS = 60

private val BIDI_OVERRIDES = 0x202A..0x202E
private val BIDI_ISOLATES = 0x2066..0x2069
