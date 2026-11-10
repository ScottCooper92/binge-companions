package com.binge.companion.kit

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddressRangeTest {
    private fun bytes(host: String) = checkNotNull(ipLiteralBytes(host)) { "$host is not a literal" }

    @Test
    fun `link-local is all of fe80 slash 10`() {
        assertTrue(bytes("fe90::1").isIn(AddressRange.LinkLocal))
        assertTrue(bytes("169.254.1.1").isIn(AddressRange.LinkLocal))
        assertFalse(bytes("fec0::1").isIn(AddressRange.LinkLocal))
    }

    @Test
    fun `a short literal that starts with fd is not unique-local`() {
        // fd::1 is 00fd::1.
        assertFalse(bytes("fd::1").isIn(AddressRange.UniqueLocal))
    }

    @Test
    fun `an IPv4-mapped literal is the IPv4 address it maps`() {
        assertArrayEquals(bytes("192.168.1.2"), bytes("::ffff:c0a8:102"))
        assertTrue(bytes("::ffff:c0a8:102").isIn(AddressRange.Private))
        assertTrue(bytes("[::ffff:192.168.1.2]").isIn(AddressRange.Private))
    }

    @Test
    fun `the ranges hold their edges`() {
        assertTrue(bytes("172.31.255.255") in AddressRange.Private)
        assertFalse(bytes("172.32.0.0") in AddressRange.Private)
        assertTrue(bytes("100.127.255.255") in AddressRange.Cgnat)
        assertFalse(bytes("100.128.0.0") in AddressRange.Cgnat)
        assertTrue(bytes("[fd7a:115c:a1e0::1]") in AddressRange.Tailscale)
        assertTrue(bytes("fd7a:115c:a1e0::1") in AddressRange.UniqueLocal)
        assertTrue(bytes("127.0.0.1") in AddressRange.Loopback)
        assertTrue(bytes("::1") in AddressRange.Loopback)
    }

    @Test
    fun `an address is never in a range of the other family`() {
        assertFalse(bytes("10.0.0.1") in AddressRange.UniqueLocal)
        assertFalse(bytes("fd00::1") in AddressRange.Private)
    }

    @Test
    fun `isIn is true when any range holds the address`() {
        assertTrue(bytes("10.0.0.1").isIn(AddressRange.Loopback, AddressRange.Private))
        assertFalse(bytes("8.8.8.8").isIn(AddressRange.Loopback, AddressRange.Private, AddressRange.Cgnat))
    }

    @Test
    fun `a name, or a number that is not an address, is not an IP literal`() {
        assertNull(ipLiteralBytes("seerr.lan"))
        assertNull(ipLiteralBytes("300.1.1.1"))
        assertNull(ipLiteralBytes("nas"))
        assertNull(ipLiteralBytes("1:2:3:4:5:6:7:8:9"))
    }

    @Test
    fun `a trailing dot and surrounding space are ignored`() {
        assertArrayEquals(bytes("10.0.0.1"), bytes(" 10.0.0.1. "))
    }

    @Test
    fun `an IPv4 literal has no sign and no leading zero`() {
        // A resolver may read 010.0.0.1 as octal, which is 8.0.0.1. It is a name here, never a private address.
        listOf(
            "010.0.0.1",
            "10.0.0.01",
            "+10.0.0.1",
            "-0.0.0.0",
            "10.0.0.+1",
            "0x10.0.0.1",
            "1..2.3",
            "1.2.3",
            "1.2.3.4.5",
            "256.0.0.1",
            "1.2.3.1000",
            "",
        ).forEach { assertNull(ipLiteralBytes(it), it) }
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes("0.0.0.0"))
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1), bytes("255.255.255.255"))
    }

    @Test
    fun `a malformed IPv6 literal is a name, and never reaches the resolver`() {
        listOf(
            "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7",
            "a:b",
            "fe80::1::2",
            ":::",
            "1:::2",
            "::g",
            "12345::1",
            ":1:2:3:4:5:6:7",
            "1:2:3:4:5:6:7:",
            "1:2:3:4:5:6:7::8",
            "::1.2.3",
            "::01.2.3.4",
            "1.2.3.4::",
            "::1.2.3.4:5",
            "fe80::1%eth0",
            "::ffff:010.0.0.1",
            "1:2:3:4:5:6:7:1.2.3.4",
        ).forEach { assertNull(ipLiteralBytes(it), it) }
    }

    @Test
    fun `IPv6 literals are read in every compressed form`() {
        assertArrayEquals(ByteArray(16), bytes("::"))
        assertArrayEquals(ByteArray(15) + byteArrayOf(1), bytes("::1"))
        assertArrayEquals(byteArrayOf(0, 1) + ByteArray(14), bytes("1::"))
        assertArrayEquals(byteArrayOf(0, 1) + ByteArray(12) + byteArrayOf(0, 2), bytes("1::2"))
        assertArrayEquals(byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8), bytes("1:2:3:4:5:6:7:8"))
        assertArrayEquals(byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 0), bytes("1:2:3:4:5:6:7::"))
        assertArrayEquals(bytes("FE80::A"), bytes("fe80::a"))
    }

    @Test
    fun `only an IPv4-mapped literal is unwrapped to four bytes`() {
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 2), bytes("::ffff:192.168.1.2"))
        assertEquals(16, bytes("::1.2.3.4").size)
        assertEquals(16, bytes("64:ff9b::1.2.3.4").size)
        assertEquals(16, bytes("::fffe:c0a8:102").size)
        assertFalse(bytes("::1.2.3.4").isIn(AddressRange.Private, AddressRange.Loopback))
    }
}
