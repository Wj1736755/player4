package org.fossify.musicplayer.helpers

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ID3TagsHelper.
 */
class ID3TagsHelperTest {

    @Test
    fun `test normalizeText removes punctuation`() {
        val input = "Hello, world - how are you? Great!"
        val expected = "Hello world how are you Great."
        assertEquals(expected, ID3TagsHelper.normalizeText(input))
    }

    @Test
    fun `test normalizeText handles multiple spaces`() {
        val input = "Test    with     multiple   spaces"
        val expected = "Test with multiple spaces."
        assertEquals(expected, ID3TagsHelper.normalizeText(input))
    }

    @Test
    fun `test normalizeText adds period`() {
        val input = "Test without period"
        val expected = "Test without period."
        assertEquals(expected, ID3TagsHelper.normalizeText(input))
    }

    @Test
    fun `test normalizeText keeps existing period`() {
        val input = "Test with period."
        val expected = "Test with period."
        assertEquals(expected, ID3TagsHelper.normalizeText(input))
    }

    @Test
    fun `test normalizeText handles Polish characters`() {
        val input = "Zażółć gęślą jaźń, może - tak?"
        val expected = "Zażółć gęślą jaźń może tak."
        assertEquals(expected, ID3TagsHelper.normalizeText(input))
    }

    @Test
    fun `test normalizeText handles empty string`() {
        assertEquals("", ID3TagsHelper.normalizeText(""))
        assertEquals("", ID3TagsHelper.normalizeText(null))
        assertEquals("", ID3TagsHelper.normalizeText("   "))
    }

    @Test
    fun `test formatDuration with 1 decimal place`() {
        assertEquals("3.0", ID3TagsHelper.formatDuration(3.0f))
        assertEquals("5.2", ID3TagsHelper.formatDuration(5.2f))
        assertEquals("10.7", ID3TagsHelper.formatDuration(10.678f))
    }

    @Test
    fun `test parseDuration valid format`() {
        assertEquals(3.0f, ID3TagsHelper.parseDuration("3.0")!!, 0.01f)
        assertEquals(5.2f, ID3TagsHelper.parseDuration("5.2")!!, 0.01f)
        assertEquals(10.7f, ID3TagsHelper.parseDuration("10.7")!!, 0.01f)
    }

    @Test
    fun `test parseDuration invalid format`() {
        assertNull(ID3TagsHelper.parseDuration("invalid"))
        assertNull(ID3TagsHelper.parseDuration(""))
        assertNull(ID3TagsHelper.parseDuration(null))
    }

    @Test
    fun `test isValidDuration format`() {
        assertTrue(ID3TagsHelper.isValidDuration("3.0"))
        assertTrue(ID3TagsHelper.isValidDuration("5.2"))
        assertTrue(ID3TagsHelper.isValidDuration("10.7"))
        
        assertFalse(ID3TagsHelper.isValidDuration("3"))  // Missing decimal
        assertFalse(ID3TagsHelper.isValidDuration("3."))  // Missing digit after decimal
        assertFalse(ID3TagsHelper.isValidDuration("3.00"))  // Two decimal places
        assertFalse(ID3TagsHelper.isValidDuration("invalid"))
        assertFalse(ID3TagsHelper.isValidDuration(""))
        assertFalse(ID3TagsHelper.isValidDuration(null))
    }

    @Test
    fun `test formatDate`() {
        val timestamp = 1733670487L  // 2024-12-08 16:48:07 UTC
        val date = ID3TagsHelper.formatDate(timestamp)
        assertTrue(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `test formatTimestampISO with timezone`() {
        val timestamp = 1733670487L
        val iso = ID3TagsHelper.formatTimestampISO(timestamp)
        // Should contain timezone like +0100 or -0500
        assertTrue(iso.contains(Regex("[+-]\\d{4}")))
        assertTrue(iso.startsWith("20"))  // Year should start with 20xx
    }

    @Test
    fun `test parseTimestampToUnix with timezone`() {
        val input = "2024-12-08T16:48:07+0100"
        val timestamp = ID3TagsHelper.parseTimestampToUnix(input)
        assertNotNull(timestamp)
        assertTrue(timestamp!! > 0)
    }

    @Test
    fun `test parseTimestampToUnix without timezone`() {
        val input = "2024-12-08T16:48:07"
        val timestamp = ID3TagsHelper.parseTimestampToUnix(input)
        assertNotNull(timestamp)
        assertTrue(timestamp!! > 0)
    }

    @Test
    fun `test parseDateToUnix`() {
        val input = "2024-12-08"
        val timestamp = ID3TagsHelper.parseDateToUnix(input)
        assertNotNull(timestamp)
        assertTrue(timestamp!! > 0)
    }

    @Test
    fun `test isValidDate format`() {
        assertTrue(ID3TagsHelper.isValidDate("2024-12-08"))
        assertTrue(ID3TagsHelper.isValidDate("2025-01-01"))
        
        assertFalse(ID3TagsHelper.isValidDate("2024/12/08"))  // Wrong separator
        assertFalse(ID3TagsHelper.isValidDate("24-12-08"))  // Wrong year format
        assertFalse(ID3TagsHelper.isValidDate("invalid"))
        assertFalse(ID3TagsHelper.isValidDate(""))
        assertFalse(ID3TagsHelper.isValidDate(null))
    }

    @Test
    fun `test generateGuid format`() {
        val guid = ID3TagsHelper.generateGuid()
        assertTrue(ID3TagsHelper.isValidGuid(guid))
        assertEquals(guid, guid.lowercase())  // Should be lowercase
    }

    @Test
    fun `test isValidGuid format`() {
        assertTrue(ID3TagsHelper.isValidGuid("43137785-2508-473b-83cf-f47712d74a82"))
        assertTrue(ID3TagsHelper.isValidGuid("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        
        assertFalse(ID3TagsHelper.isValidGuid("43137785-2508-473B-83CF-F47712D74A82"))  // Uppercase
        assertFalse(ID3TagsHelper.isValidGuid("43137785-2508-473b-83cf"))  // Too short
        assertFalse(ID3TagsHelper.isValidGuid("invalid-guid"))
        assertFalse(ID3TagsHelper.isValidGuid(""))
        assertFalse(ID3TagsHelper.isValidGuid(null))
    }

    @Test
    fun `test isValidMD5 format`() {
        assertTrue(ID3TagsHelper.isValidMD5("4917cf2436a9021114f1adea0c95a797"))
        assertTrue(ID3TagsHelper.isValidMD5("abcdef0123456789abcdef0123456789"))
        
        assertFalse(ID3TagsHelper.isValidMD5("4917CF2436A9021114F1ADEA0C95A797"))  // Uppercase
        assertFalse(ID3TagsHelper.isValidMD5("4917cf2436a9021114f1adea0c95a79"))  // Too short
        assertFalse(ID3TagsHelper.isValidMD5("invalid-md5"))
        assertFalse(ID3TagsHelper.isValidMD5(""))
        assertFalse(ID3TagsHelper.isValidMD5(null))
    }
}
