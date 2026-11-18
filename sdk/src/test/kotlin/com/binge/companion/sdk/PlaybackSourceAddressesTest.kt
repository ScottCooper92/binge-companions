package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSourceAddressesTest {
    private fun v4(text: String): ByteArray = text.split('.').map { it.toInt().toByte() }.toByteArray()

    /** Eight hex groups, written out in full so no test depends on a parser. */
    private fun v6(vararg groups: Int): ByteArray {
        require(groups.size == 8)
        return groups.flatMap { listOf((it shr 8).toByte(), it.toByte()) }.toByteArray()
    }

    private fun mapped(text: String): ByteArray {
        val octets = v4(text)
        return v6(
            0,
            0,
            0,
            0,
            0,
            0xFFFF,
            (octets[0].toInt() and 0xFF shl 8) or (octets[1].toInt() and 0xFF),
            (octets[2].toInt() and 0xFF shl 8) or (octets[3].toInt() and 0xFF),
        )
    }

    private fun allowed(address: ByteArray, overlay: Boolean = false) = isCleartextAllowed(listOf(address), overlay)

    @Test
    fun `the contract examples hold`() {
        assertTrue(allowed(mapped("10.0.0.5")))
        assertFalse(allowed(mapped("8.8.8.8")))
    }

    @Test
    fun `IPv4 loopback, link-local and RFC 1918 are private whatever the overlay says`() {
        listOf(
            "127.0.0.1",
            "127.255.255.255",
            "169.254.0.0",
            "169.254.255.255",
            "10.0.0.0",
            "10.255.255.255",
            "172.16.0.0",
            "172.31.255.255",
            "192.168.0.0",
            "192.168.255.255",
        ).forEach {
            assertTrue(allowed(v4(it), overlay = false), it)
            assertTrue(allowed(v4(it), overlay = true), it)
        }
    }

    @Test
    fun `the edges just outside each IPv4 range are not private`() {
        listOf(
            "126.255.255.255",
            "128.0.0.0",
            "169.253.255.255",
            "169.255.0.0",
            "9.255.255.255",
            "11.0.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "192.167.255.255",
            "192.169.0.0",
            "8.8.8.8",
            "0.0.0.0",
            "224.0.0.1",
        ).forEach {
            assertFalse(allowed(v4(it), overlay = false), it)
            assertFalse(allowed(v4(it), overlay = true), it)
        }
    }

    @Test
    fun `100_64 slash 10 is private only while an overlay network is up`() {
        listOf("100.64.0.0", "100.100.1.1", "100.127.255.255").forEach {
            assertTrue(allowed(v4(it), overlay = true), it)
            assertFalse(allowed(v4(it), overlay = false), it)
        }
        listOf("100.63.255.255", "100.128.0.0").forEach {
            assertFalse(allowed(v4(it), overlay = true), it)
        }
    }

    @Test
    fun `IPv6 loopback, link-local and unique-local are private whatever the overlay says`() {
        listOf(
            v6(0, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFE80, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFEBF, 0xFFFF, 0, 0, 0, 0, 0, 1),
            v6(0xFC00, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFDFF, 0, 0, 0, 0, 0, 0, 1),
        ).forEach {
            assertTrue(allowed(it, overlay = false))
            assertTrue(allowed(it, overlay = true))
        }
    }

    @Test
    fun `the IPv6 edges just outside each range are not private`() {
        listOf(
            v6(0, 0, 0, 0, 0, 0, 0, 0),
            v6(0, 0, 0, 0, 0, 0, 0, 2),
            v6(0xFEC0, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFE7F, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFE00, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFB00, 0, 0, 0, 0, 0, 0, 1),
            v6(0xFF02, 0, 0, 0, 0, 0, 0, 1),
            v6(0x2001, 0xDB8, 0, 0, 0, 0, 0, 1),
        ).forEach {
            assertFalse(allowed(it, overlay = false))
            assertFalse(allowed(it, overlay = true))
        }
    }

    @Test
    fun `Tailscale's IPv6 block is unique-local, so private with no overlay up`() {
        assertTrue(allowed(v6(0xFD7A, 0x115C, 0xA1E0, 0, 0, 0, 0, 1), overlay = false))
    }

    @Test
    fun `an IPv4-mapped address follows its embedded IPv4 address and the same overlay condition`() {
        assertTrue(allowed(mapped("127.0.0.1")))
        assertTrue(allowed(mapped("192.168.1.2")))
        assertFalse(allowed(mapped("172.32.0.1")))
        assertTrue(allowed(mapped("100.64.0.1"), overlay = true))
        assertFalse(allowed(mapped("100.64.0.1"), overlay = false))
        assertFalse(allowed(mapped("8.8.8.8"), overlay = true))
    }

    @Test
    fun `forms that embed an IPv4 address but are not unwrapped are not private`() {
        // NAT64 64:ff9b::10.0.0.5
        assertFalse(allowed(v6(0x64, 0xFF9B, 0, 0, 0, 0, 0x0A00, 0x0005), overlay = true))
        // 6to4 2002:0a00:0005::
        assertFalse(allowed(v6(0x2002, 0x0A00, 0x0005, 0, 0, 0, 0, 0), overlay = true))
        // IPv4-compatible ::10.0.0.5
        assertFalse(allowed(v6(0, 0, 0, 0, 0, 0, 0x0A00, 0x0005), overlay = true))
        // IPv4-translated ::ffff:0:10.0.0.5
        assertFalse(allowed(v6(0, 0, 0, 0, 0xFFFF, 0, 0x0A00, 0x0005), overlay = true))
    }

    @Test
    fun `an empty list is refused`() {
        assertFalse(isCleartextAllowed(emptyList(), overlayNetworkUp = true))
    }

    @Test
    fun `one address that is not private refuses the whole list`() {
        assertFalse(isCleartextAllowed(listOf(v4("10.0.0.1"), v4("8.8.8.8")), overlayNetworkUp = true))
        assertFalse(isCleartextAllowed(listOf(v4("8.8.8.8"), v4("10.0.0.1")), overlayNetworkUp = true))
        assertTrue(isCleartextAllowed(listOf(v4("10.0.0.1"), mapped("192.168.0.1")), overlayNetworkUp = false))
    }

    @Test
    fun `an array that is neither 4 nor 16 bytes is not private`() {
        assertFalse(allowed(ByteArray(0)))
        assertFalse(allowed(byteArrayOf(10, 0, 0)))
        assertFalse(allowed(byteArrayOf(10, 0, 0, 1, 1)))
        assertFalse(allowed(ByteArray(15)))
        assertFalse(allowed(ByteArray(17)))
    }
}
