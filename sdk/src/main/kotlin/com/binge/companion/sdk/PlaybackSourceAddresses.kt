package com.binge.companion.sdk

/**
 * Whether a LIBRARY `PlaybackSource` may use `http`: [addresses] (raw bytes, as the host resolved them) is not empty
 * and every one is private by the rule in `library.proto`'s header. `100.64/10` counts only with [overlayNetworkUp].
 * `::ffff:a.b.c.d` is read by its IPv4 address; other forms, and any array not 4 or 16 bytes, are not private.
 * It only classifies. The kit's `AddressRange` is a separate table for an app's own policies, not this rule.
 */
fun isCleartextAllowed(addresses: List<ByteArray>, overlayNetworkUp: Boolean): Boolean =
    addresses.isNotEmpty() && addresses.all { it.isPrivateAddress(overlayNetworkUp) }

private fun ByteArray.isPrivateAddress(overlayNetworkUp: Boolean): Boolean =
    when (size) {
        IPV4_BYTES -> isPrivateV4(overlayNetworkUp)
        IPV6_BYTES ->
            if (isV4Mapped()) {
                copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES).isPrivateV4(overlayNetworkUp)
            } else {
                isPrivateV6()
            }
        else -> false
    }

private fun ByteArray.isPrivateV4(overlayNetworkUp: Boolean): Boolean {
    val first = this[0].toInt() and BYTE_MASK
    val second = this[1].toInt() and BYTE_MASK
    return when (first) {
        LOOPBACK_FIRST, TEN_FIRST -> true
        LINK_LOCAL_FIRST -> second == LINK_LOCAL_SECOND
        PRIVATE_172_FIRST -> second in PRIVATE_172_SECOND
        PRIVATE_192_FIRST -> second == PRIVATE_192_SECOND
        SHARED_FIRST -> overlayNetworkUp && second in SHARED_SECOND
        else -> false
    }
}

private fun ByteArray.isPrivateV6(): Boolean {
    val first = this[0].toInt() and BYTE_MASK
    val second = this[1].toInt() and BYTE_MASK
    val loopback = (0 until IPV6_BYTES - 1).all { this[it].toInt() == 0 } && this[IPV6_BYTES - 1].toInt() == 1
    val linkLocal = first == LINK_LOCAL_V6_FIRST && (second and LINK_LOCAL_V6_MASK) == LINK_LOCAL_V6_SECOND
    val uniqueLocal = (first and UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_FIRST
    return loopback || linkLocal || uniqueLocal
}

/** `::ffff:0:0/96` and nothing else: ten zero bytes, then `ff ff`. */
private fun ByteArray.isV4Mapped(): Boolean =
    (0 until MAPPED_ZERO_BYTES).all { this[it].toInt() == 0 } &&
        (this[MAPPED_ZERO_BYTES].toInt() and BYTE_MASK) == BYTE_MASK &&
        (this[MAPPED_ZERO_BYTES + 1].toInt() and BYTE_MASK) == BYTE_MASK

private const val IPV4_BYTES = 4
private const val IPV6_BYTES = 16
private const val MAPPED_ZERO_BYTES = 10
private const val BYTE_MASK = 0xFF
private const val LOOPBACK_FIRST = 127
private const val TEN_FIRST = 10
private const val LINK_LOCAL_FIRST = 169
private const val LINK_LOCAL_SECOND = 254
private const val PRIVATE_172_FIRST = 172
private val PRIVATE_172_SECOND = 16..31
private const val PRIVATE_192_FIRST = 192
private const val PRIVATE_192_SECOND = 168
private const val SHARED_FIRST = 100
private val SHARED_SECOND = 64..127
private const val LINK_LOCAL_V6_FIRST = 0xFE
private const val LINK_LOCAL_V6_MASK = 0xC0
private const val LINK_LOCAL_V6_SECOND = 0x80
private const val UNIQUE_LOCAL_MASK = 0xFE
private const val UNIQUE_LOCAL_FIRST = 0xFC
