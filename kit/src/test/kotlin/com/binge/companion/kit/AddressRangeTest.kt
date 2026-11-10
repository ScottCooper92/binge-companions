package com.binge.companion.kit

import org.junit.jupiter.api.Assertions.assertArrayEquals
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
}
