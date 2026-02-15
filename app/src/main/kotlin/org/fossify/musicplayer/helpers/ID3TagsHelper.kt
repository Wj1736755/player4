package org.fossify.musicplayer.helpers

import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper for reading and converting ID3 TXXX tags from MP3 files.
 * Handles custom tags from SpeechToText project:
 * - TXXX:Content (transcription - normalized text)
 * - TXXX:CREATED_ON_DATE (date in YYYY-MM-DD format)
 * - TXXX:CREATED_ON_TS (ISO 8601 timestamp with timezone)
 * - TXXX:DURATION_SEC (duration in seconds, 1 decimal place)
 * - TXXX:GUID (UUID v4)
 * - TXXX:CHECKSUM_MD5_AUDIO (MD5 hash of audio data only)
 * - TXXX:GENERATION_PARAMS (JSON with generation parameters)
 */
object ID3TagsHelper {

    /**
     * Convert ISO 8601 timestamp string to Unix timestamp (seconds).
     *
     * Supported formats:
     * - "2024-12-08T16:48:07+0100" (ISO 8601 with timezone - RECOMMENDED)
     * - "2025-11-16T14:22:44.168305" (with microseconds)
     * - "2025-11-16T14:22:44" (ISO 8601 without timezone)
     * - "2025-11-16 14:22:44" (space separator)
     *
     * @param isoTimestamp ISO 8601 timestamp string
     * @return Unix timestamp in seconds, or null if parsing fails
     */
    fun parseTimestampToUnix(isoTimestamp: String?): Long? {
        if (isoTimestamp.isNullOrBlank()) return null

        return try {
            // Try different formats (order matters - most specific first)
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ssZ",        // ISO 8601 with timezone: 2024-12-08T16:48:07+0100
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", // With microseconds + timezone
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",  // With microseconds
                "yyyy-MM-dd'T'HH:mm:ss",         // ISO 8601 without timezone
                "yyyy-MM-dd HH:mm:ss"            // Space separator
            )

            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    // Only set default timezone if format doesn't include timezone
                    if (!format.contains('Z')) {
                        sdf.timeZone = TimeZone.getDefault()
                    }
                    val date = sdf.parse(isoTimestamp)
                    return date?.time?.div(1000) // Convert milliseconds to seconds
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert Unix timestamp (seconds) to human-readable date string.
     *
     * @param timestamp Unix timestamp in seconds
     * @param format Date format (default: "yyyy-MM-dd HH:mm:ss")
     * @return Formatted date string
     */
    fun formatTimestamp(timestamp: Long?, format: String = "yyyy-MM-dd HH:mm:ss"): String? {
        if (timestamp == null) return null

        return try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            val date = Date(timestamp * 1000) // Convert seconds to milliseconds
            sdf.format(date)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get date only from Unix timestamp (without time).
     *
     * @param timestamp Unix timestamp in seconds
     * @return Date string in format "yyyy-MM-dd"
     */
    fun getDateFromTimestamp(timestamp: Long?): String? {
        return formatTimestamp(timestamp, "yyyy-MM-dd")
    }

    /**
     * Get time only from Unix timestamp (without date).
     *
     * @param timestamp Unix timestamp in seconds
     * @return Time string in format "HH:mm:ss"
     */
    fun getTimeFromTimestamp(timestamp: Long?): String? {
        return formatTimestamp(timestamp, "HH:mm:ss")
    }

    /**
     * Check if timestamp is within last N days.
     *
     * @param timestamp Unix timestamp in seconds
     * @param days Number of days
     * @return true if timestamp is within last N days
     */
    fun isWithinLastDays(timestamp: Long?, days: Int): Boolean {
        if (timestamp == null) return false

        val now = System.currentTimeMillis() / 1000
        val daysInSeconds = days * 24 * 60 * 60
        return (now - timestamp) <= daysInSeconds
    }

    /**
     * Get start of day timestamp (00:00:00).
     *
     * @param timestamp Unix timestamp in seconds
     * @return Unix timestamp of start of day in seconds
     */
    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp * 1000
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000
    }

    /**
     * Get end of day timestamp (23:59:59).
     *
     * @param timestamp Unix timestamp in seconds
     * @return Unix timestamp of end of day in seconds
     */
    fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp * 1000
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis / 1000
    }

    /**
     * Validate GUID format (UUID v4).
     *
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (lowercase)
     *
     * @param guid GUID string to validate
     * @return true if valid UUID v4 format
     */
    fun isValidGuid(guid: String?): Boolean {
        if (guid.isNullOrBlank()) return false

        val regex = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
        return guid.matches(regex)
    }

    /**
     * Validate MD5 checksum format.
     *
     * Format: 32 hexadecimal characters (lowercase)
     *
     * @param checksum MD5 checksum string to validate
     * @return true if valid MD5 format
     */
    fun isValidMD5(checksum: String?): Boolean {
        if (checksum.isNullOrBlank()) return false

        val regex = Regex("^[a-f0-9]{32}$")
        return checksum.matches(regex)
    }

    /**
     * Normalize text (transcription) according to TEXT_NORMALIZATION_ALGORITHM.md
     *
     * Algorithm:
     * 1. Strip whitespace from beginning and end
     * 2. Remove 4 punctuation characters: , - ? !
     * 3. Normalize spaces (multiple → single)
     * 4. Strip whitespace again
     * 5. Add period at the end (if missing)
     *
     * @param text Text to normalize
     * @return Normalized text
     */
    fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""

        var normalized = text

        // 1. Strip whitespace
        normalized = normalized.trim()

        // 2. Remove punctuation: , - ? !
        normalized = normalized.replace(Regex("[,\\-?!]"), "")

        // 3. Normalize spaces (multiple → single)
        normalized = normalized.replace(Regex("\\s+"), " ")

        // 4. Strip whitespace again
        normalized = normalized.trim()

        // 5. Add period at the end (if missing)
        if (normalized.isNotEmpty() && !normalized.endsWith(".")) {
            normalized += "."
        }

        return normalized
    }

    /**
     * Parse duration string to Float.
     *
     * Format: "3.0", "5.2" (always 1 decimal place)
     *
     * @param durationStr Duration string
     * @return Duration in seconds as Float, or null if parsing fails
     */
    fun parseDuration(durationStr: String?): Float? {
        if (durationStr.isNullOrBlank()) return null

        return try {
            durationStr.toFloat()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format duration to string with 1 decimal place.
     *
     * Format: "3.0", "5.2"
     *
     * @param seconds Duration in seconds
     * @return Formatted duration string
     */
    fun formatDuration(seconds: Float): String {
        return "%.1f".format(Locale.US, seconds)
    }

    /**
     * Validate duration format.
     *
     * Format: Number with 1 decimal place (e.g., "3.0", "5.2")
     *
     * @param durationStr Duration string to validate
     * @return true if valid duration format
     */
    fun isValidDuration(durationStr: String?): Boolean {
        if (durationStr.isNullOrBlank()) return false

        val regex = Regex("^\\d+\\.\\d$")
        return durationStr.matches(regex)
    }

    /**
     * Format date to YYYY-MM-DD format.
     *
     * @param timestamp Unix timestamp in seconds
     * @return Date string in format "YYYY-MM-DD"
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = Date(timestamp * 1000)
        return sdf.format(date)
    }

    /**
     * Format timestamp to ISO 8601 with timezone.
     *
     * Format: "YYYY-MM-DDTHH:MM:SS+HHMM"
     * Example: "2024-12-08T16:48:07+0100"
     *
     * @param timestamp Unix timestamp in seconds
     * @return ISO 8601 timestamp string with timezone
     */
    fun formatTimestampISO(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val date = Date(timestamp * 1000)
        return sdf.format(date)
    }

    /**
     * Parse date string (YYYY-MM-DD) to Unix timestamp.
     *
     * @param dateStr Date string in format "YYYY-MM-DD"
     * @return Unix timestamp in seconds (start of day), or null if parsing fails
     */
    fun parseDateToUnix(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            val date = sdf.parse(dateStr)
            date?.time?.div(1000)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validate date format (YYYY-MM-DD).
     *
     * @param dateStr Date string to validate
     * @return true if valid date format
     */
    fun isValidDate(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank()) return false

        val regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        return dateStr.matches(regex)
    }

    /**
     * Generate new UUID v4 (lowercase).
     *
     * @return UUID v4 string in lowercase
     */
    fun generateGuid(): String {
        return java.util.UUID.randomUUID().toString().lowercase()
    }
}
