package com.binge.companion.kit

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

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
    private val network: ByteArray = InetAddress.getByName(spec.substringBefore('/')).address
    private val bits: Int = spec.substringAfter('/').toInt()

    operator fun contains(address: ByteArray): Boolean =
        address.size == network.size && (0 until bits).all { address.bitAt(it) == network.bitAt(it) }

    private fun ByteArray.bitAt(index: Int): Int =
        (this[index / Byte.SIZE_BITS].toInt() shr (Byte.SIZE_BITS - 1 - index % Byte.SIZE_BITS)) and 1
}

private const val IPV4_OCTETS = 4
private const val MAX_OCTET = 255

/** What an IPv6 literal, lowercased, is written with; anything else is a name and never reaches the parser. */
private val IPV6_CHARS = "0123456789abcdef:.".toSet()

/**
 * The bytes of [host] when it is an IP literal, or null for a name. [host] may keep the brackets of an IPv6 literal.
 * Nothing is resolved: a dotted quad is read here, and only something written like an IPv6 literal reaches
 * [InetAddress], which parses a literal without a lookup. An IPv4-mapped literal (`::ffff:192.168.1.2`) comes back as
 * the four bytes of the IPv4 address it maps.
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

private fun ipv4Bytes(name: String): ByteArray? =
    name
        .split('.')
        .map { it.toIntOrNull() }
        .takeIf { octets -> octets.size == IPV4_OCTETS && octets.all { it != null && it in 0..MAX_OCTET } }
        ?.map { (it ?: 0).toByte() }
        ?.toByteArray()

private fun ipv6Bytes(name: String): ByteArray? {
    if (!name.contains(':') || !name.all { it in IPV6_CHARS }) return null
    val parsed =
        try {
            InetAddress.getByName(name)
        } catch (_: UnknownHostException) {
            return null
        }
    return when (parsed) {
        is Inet6Address, is Inet4Address -> parsed.address
        else -> null
    }
}
