package ru.shmr.finance.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinVerifierTest {

    @Test
    fun `verifier accepts the original pin`() {
        val record = PinVerifier.create("1234")

        assertTrue(PinVerifier.matches(record, "1234"))
    }

    @Test
    fun `verifier rejects any other pin`() {
        val record = PinVerifier.create("1234")

        assertFalse(PinVerifier.matches(record, "1235"))
        assertFalse(PinVerifier.matches(record, "4321"))
        assertFalse(PinVerifier.matches(record, ""))
    }

    @Test
    fun `stored material never contains the pin itself`() {
        val record = PinVerifier.create("1234")

        val encoded = PinVerifierCodec.encode(record)

        assertFalse(encoded.contains("1234"))
        assertFalse(String(record.hash, Charsets.ISO_8859_1).contains("1234"))
    }

    @Test
    fun `same pin produces different material every time`() {
        val first = PinVerifier.create("1234")
        val second = PinVerifier.create("1234")

        assertNotEquals(first.salt.toList(), second.salt.toList())
        assertNotEquals(first.hash.toList(), second.hash.toList())
    }

    @Test
    fun `record survives a full encode decode round trip`() {
        val record = PinVerifier.create("9999")

        val restored = PinVerifierCodec.decode(PinVerifierCodec.encode(record))

        assertEquals(record, restored)
        assertTrue(PinVerifier.matches(restored!!, "9999"))
    }

    @Test
    fun `corrupted records decode to null instead of throwing`() {
        val valid = PinVerifierCodec.encode(PinVerifier.create("1234"))

        assertNull(PinVerifierCodec.decode(""))
        assertNull(PinVerifierCodec.decode("garbage"))
        assertNull(PinVerifierCodec.decode(valid.dropLast(1)))
        assertNull(PinVerifierCodec.decode(valid.replaceFirst("1|", "2|")))
        assertNull(PinVerifierCodec.decode(valid.replaceFirst("|", "|x|")))
    }

    @Test
    fun `derivation cost is recorded so verification stays reproducible`() {
        val record = PinVerifier.create("1234")

        assertTrue(record.iterations >= 100_000)
        assertTrue(record.algorithm.startsWith("PBKDF2"))
        assertEquals(16, record.salt.size)
    }
}
