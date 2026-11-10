package com.binge.companion.kit

/**
 * The address ranges a companion's host classifiers read, in one table. A companion usually has more than one
 * classifier (cleartext consent, a local-network permission, TV reachability) and they disagree on purpose. Which
 * ranges each one counts is policy, and stays in the app. What a range is is not, so it is written once, here.
 */
enum class AddressRange(
    vararg specs: String,
) {
    /** `127.0.0.0/8` and `::1`: this device. */
    Loopback("127.0.0.0/8", "::1/128"),

    /** `169.254.0.0/16` and `fe80::/10`. */
    LinkLocal("169.254.0.0/16", "fe80::/10"),

    /** RFC 1918. */
    Private("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"),

    /** The shared address space carriers and VPNs, Tailscale among them, hand out. */
    Cgnat("100.64.0.0/10"),

    /** IPv6 unique-local, Tailscale's block included. */
    UniqueLocal("fc00::/7"),

    /** Tailscale's own IPv6 block: unique-local in form, an overlay network in practice. */
    Tailscale("fd7a:115c:a1e0::/48"),
    ;

    private val networks = specs.map(::Cidr)

    /** Whether [address], as the bytes [ipLiteralBytes] returns, falls in this range. */
    operator fun contains(address: ByteArray): Boolean = networks.any { address in it }
}

/** A network as an address and a prefix length, written the usual way (`10.0.0.0/8`) so no number needs explaining. */
private class Cidr(
    spec: String,
) {
    private val network: ByteArray = checkNotNull(ipLiteralBytes(spec.substringBefore('/'))) { "$spec is not a literal" }
    private val bits: Int = spec.substringAfter('/').toInt()

    operator fun contains(address: ByteArray): Boolean =
        address.size == network.size && (0 until bits).all { address.bitAt(it) == network.bitAt(it) }

    private fun ByteArray.bitAt(index: Int): Int =
        (this[index / Byte.SIZE_BITS].toInt() shr (Byte.SIZE_BITS - 1 - index % Byte.SIZE_BITS)) and 1
}

private const val IPV4_OCTETS = 4
private const val IPV4_BYTES = 4
private const val IPV6_BYTES = 16
private const val IPV6_GROUPS = 8
private const val GROUP_BYTES = 2
private const val LOW_BYTE = 1
private const val MAX_OCTET = 255
private const val MAX_OCTET_DIGITS = 3
private const val MAX_GROUP_DIGITS = 4
private const val HEX_RADIX = 16
private const val MAPPED_PREFIX_ZEROS = 10
private const val BYTE_MASK = 0xFF

/**
 * The bytes of [host] when it is an IP literal, or null for a name; IPv6 brackets are allowed. Nothing is resolved and
 * nothing reaches `InetAddress`, which on Android falls back to a DNS lookup for a string it cannot parse. Both forms
 * are read strictly here: `010.0.0.1` and `+10.0.0.1` are names, since a resolver may read a leading zero as octal. A
 * zone (`%eth0`) makes an IPv6 host a name. `::ffff:a.b.c.d` comes back as the four bytes of the IPv4 address it maps.
 */
fun ipLiteralBytes(host: String): ByteArray? {
    val name =
        host
            .trim()
            .trimEnd('.')
            .removePrefix("[")
            .removeSuffix("]")
            .lowercase()
    return ipv4Bytes(name) ?: ipv6Bytes(name)
}

/** Whether [this] address falls in any of [ranges]. */
fun ByteArray.isIn(vararg ranges: AddressRange): Boolean = ranges.any { this in it }

private fun ipv4Bytes(name: String): ByteArray? {
    val octets = name.split('.')
    if (octets.size != IPV4_OCTETS) return null
    val bytes = ByteArray(IPV4_BYTES)
    octets.forEachIndexed { index, octet ->
        bytes[index] = (decimalOctet(octet) ?: return null).toByte()
    }
    return bytes
}

/** An octet written as 0 to 255 in plain digits, with no leading zero; null for anything else. */
private fun decimalOctet(text: String): Int? =
    text
        .takeIf { it.length in 1..MAX_OCTET_DIGITS && it.all { c -> c in '0'..'9' } && (it.length == 1 || it[0] != '0') }
        ?.toInt()
        ?.takeIf { it <= MAX_OCTET }

private fun ipv6Bytes(name: String): ByteArray? {
    if (!name.contains(':')) return null
    val halves = name.split("::")
    if (halves.size > 2) return null
    val compressed = halves.size == 2
    val head = if (compressed) groups(halves[0], endsSection = false) else groups(name, endsSection = true)
    val tail = if (compressed) groups(halves[1], endsSection = true) else emptyList()
    if (head == null || tail == null) return null
    // "::" stands for at least one group of zeros, and without it all eight groups are written.
    val missing = IPV6_GROUPS - head.size - tail.size
    if (if (compressed) missing < 1 else missing != 0) return null
    val all = head + List(missing) { 0 } + tail
    val bytes = ByteArray(IPV6_BYTES)
    all.forEachIndexed { index, group ->
        bytes[index * GROUP_BYTES] = (group shr Byte.SIZE_BITS).toByte()
        bytes[index * GROUP_BYTES + LOW_BYTE] = (group and BYTE_MASK).toByte()
    }
    return bytes.unwrapMapped()
}

/**
 * The 16-bit groups of one side of an IPv6 literal, or null when it is malformed. An empty side has no groups. The
 * last element may be an IPv4 literal, worth two groups, but only on the side that ends the address ([endsSection]).
 */
private fun groups(text: String, endsSection: Boolean): List<Int>? {
    if (text.isEmpty()) return emptyList()
    val parts = text.split(':')
    val result = mutableListOf<Int>()
    parts.forEachIndexed { index, part ->
        if (index == parts.lastIndex && endsSection && part.contains('.')) {
            val v4 = ipv4Bytes(part) ?: return null
            v4.toList().chunked(GROUP_BYTES).forEach { (high, low) ->
                result += ((high.toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (low.toInt() and BYTE_MASK)
            }
        } else {
            val ok = part.length in 1..MAX_GROUP_DIGITS && part.all { it in '0'..'9' || it in 'a'..'f' }
            if (!ok) return null
            result += part.toInt(HEX_RADIX)
        }
    }
    return result
}

/** The four bytes of the IPv4 address an IPv4-mapped address (`::ffff:a.b.c.d`) maps; any other address unchanged. */
private fun ByteArray.unwrapMapped(): ByteArray {
    val mapped =
        (0 until MAPPED_PREFIX_ZEROS).all { this[it].toInt() == 0 } &&
            (this[MAPPED_PREFIX_ZEROS].toInt() and BYTE_MASK) == BYTE_MASK &&
            (this[MAPPED_PREFIX_ZEROS + 1].toInt() and BYTE_MASK) == BYTE_MASK
    return if (mapped) copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES) else this
}
