package org.fossify.musicplayer.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import com.squareup.moshi.Moshi
import org.fossify.musicplayer.models.GenerationParams
import org.fossify.musicplayer.models.TXXXTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Writer for TXXX tags in MP3 files.
 * 
 * Writes custom tags to MP3 files using jaudiotagger library.
 */
class TXXXTagsWriter(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val generationParamsAdapter = moshi.adapter(GenerationParams::class.java)
    
    companion object {
        /**
         * Parse timestamp from ElevenLabs filename format.
         * Format: ElevenLabs_2024-12-04T07_52_18_Antoni_pre_s50_sb75_se0_b_m2.mp3
         * 
         * STRICT VALIDATION:
         * - Must start with "ElevenLabs_"
         * - Must have exactly "YYYY-MM-DDTHH_MM_SS" format at position 11-29
         * - Year: 2000-2099
         * - Month: 01-12
         * - Day: 01-31
         * - Hour: 00-23
         * - Minute: 00-59
         * - Second: 00-59
         * 
         * @param filename Filename to parse
         * @return Unix timestamp in seconds (UTC), or null if parsing fails or format invalid
         */
        fun parseTimestampFromFilename(filename: String): Long? {
            return try {
                // 1. CHECK PREFIX: Must start with "ElevenLabs_"
                if (!filename.startsWith("ElevenLabs_")) {
                    return null
                }
                
                // 2. CHECK LENGTH: Must have at least 30 characters (prefix + timestamp)
                if (filename.length < 30) {
                    android.util.Log.w("TXXXTagsWriter", "Filename too short: $filename")
                    return null
                }
                
                // 3. EXTRACT: "2024-12-04T07_52_18" from position 11-29 (19 chars)
                val timestampPart = filename.substring(11, 30)
                
                // 4. VALIDATE FORMAT: Must match "YYYY-MM-DDTHH_MM_SS"
                val regex = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2})_(\d{2})_(\d{2})$""")
                val match = regex.matchEntire(timestampPart)
                
                if (match == null) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid timestamp format: $timestampPart in $filename")
                    return null
                }
                
                // 5. EXTRACT COMPONENTS
                val (year, month, day, hour, minute, second) = match.destructured
                
                // 6. VALIDATE RANGES
                val yearInt = year.toInt()
                val monthInt = month.toInt()
                val dayInt = day.toInt()
                val hourInt = hour.toInt()
                val minuteInt = minute.toInt()
                val secondInt = second.toInt()
                
                if (yearInt < 2000 || yearInt > 2099) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid year: $yearInt")
                    return null
                }
                if (monthInt < 1 || monthInt > 12) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid month: $monthInt")
                    return null
                }
                if (dayInt < 1 || dayInt > 31) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid day: $dayInt")
                    return null
                }
                if (hourInt < 0 || hourInt > 23) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid hour: $hourInt")
                    return null
                }
                if (minuteInt < 0 || minuteInt > 59) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid minute: $minuteInt")
                    return null
                }
                if (secondInt < 0 || secondInt > 59) {
                    android.util.Log.w("TXXXTagsWriter", "Invalid second: $secondInt")
                    return null
                }
                
                // 7. CONVERT TO PARSEABLE FORMAT: "2024-12-04T07:52:18"
                val isoFormat = "$year-$month-${day}T$hour:$minute:$second"
                
                // 8. PARSE AS UTC
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.isLenient = false // STRICT parsing - reject invalid dates like 2024-02-31
                
                val date = sdf.parse(isoFormat)
                if (date == null) {
                    android.util.Log.w("TXXXTagsWriter", "Failed to parse date: $isoFormat")
                    return null
                }
                
                // 9. RETURN UNIX TIMESTAMP (seconds)
                val timestampSec = date.time / 1000
                android.util.Log.d("TXXXTagsWriter", "✅ Parsed timestamp from filename: $filename → $timestampSec ($isoFormat UTC)")
                timestampSec
                
            } catch (e: Exception) {
                android.util.Log.w("TXXXTagsWriter", "Exception parsing timestamp from filename: $filename", e)
                null
            }
        }
        
        /**
         * Convert UTC timestamp to Europe/Warsaw timezone.
         * Automatically handles summer/winter time (CEST/CET).
         * 
         * @param utcTimestampSec Unix timestamp in seconds (UTC)
         * @return Unix timestamp in seconds adjusted to Warsaw timezone
         */
        fun convertUtcToWarsaw(utcTimestampSec: Long): Long {
            val utcDate = Date(utcTimestampSec * 1000)
            val warsawTimeZone = TimeZone.getTimeZone("Europe/Warsaw")
            val offsetMillis = warsawTimeZone.getOffset(utcDate.time)
            return utcTimestampSec + (offsetMillis / 1000)
        }
    }

    /**
     * Write all TXXX tags to MP3 file.
     *
     * @param file MP3 file
     * @param tags Tags to write
     * @return true if successful
     */
    fun writeAllTags(file: File, tags: TXXXTags): Boolean {
        if (!file.exists() || !file.isFile) return false

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault as? AbstractID3v2Tag ?: return false

            var tagsWritten = 0
            
            // Write each tag
            tags.transcription?.let { 
                writeTXXXTag(tag, "Content", it)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote Content: ${it.take(50)}...")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped Content (null)")
            
            tags.createdOnDate?.let { 
                writeTXXXTag(tag, "CREATED_ON_DATE", it)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote CREATED_ON_DATE: $it")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped CREATED_ON_DATE (null)")
            
            tags.createdOnTimestamp?.let { 
                val formatted = ID3TagsHelper.formatTimestampISO(it)
                writeTXXXTag(tag, "CREATED_ON_TS", formatted)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote CREATED_ON_TS: $formatted")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped CREATED_ON_TS (null)")
            
            tags.duration?.let { 
                val formatted = ID3TagsHelper.formatDuration(it)
                writeTXXXTag(tag, "DURATION_SEC", formatted)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote DURATION_SEC: $formatted")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped DURATION_SEC (null)")
            
            tags.guid?.let { 
                writeTXXXTag(tag, "GUID", it)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote GUID: $it")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped GUID (null)")
            
            tags.checksumAudio?.let { 
                writeTXXXTag(tag, "CHECKSUM_MD5_AUDIO", it)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote CHECKSUM_MD5_AUDIO: $it")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped CHECKSUM_MD5_AUDIO (null)")
            
            tags.generationParams?.let {
                val json = generationParamsAdapter.toJson(it)
                writeTXXXTag(tag, "GENERATION_PARAMS", json)
                tagsWritten++
                android.util.Log.d("TXXXTagsWriter", "✅ Wrote GENERATION_PARAMS")
            } ?: android.util.Log.w("TXXXTagsWriter", "⚠️ Skipped GENERATION_PARAMS (null)")

            android.util.Log.d("TXXXTagsWriter", "Total tags written: $tagsWritten")
            audioFile.commit()
            true
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsWriter", "Error writing tags", e)
            false
        }
    }

    /**
     * Write single TXXX tag to MP3 file.
     *
     * @param file MP3 file
     * @param tagName Tag description (e.g., "Content", "GUID")
     * @param value Tag value
     * @return true if successful
     */
    fun writeTag(file: File, tagName: String, value: String): Boolean {
        if (!file.exists() || !file.isFile) return false

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault as? AbstractID3v2Tag ?: return false

            writeTXXXTag(tag, tagName, value)

            audioFile.commit()
            true
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsWriter", "Error writing tag $tagName", e)
            false
        }
    }

    /**
     * Write TXXX:Content tag (transcription with normalization).
     *
     * @param file MP3 file
     * @param text Text to write (will be normalized)
     * @return true if successful
     */
    fun writeTranscription(file: File, text: String): Boolean {
        val normalized = ID3TagsHelper.normalizeText(text)
        return writeTag(file, "Content", normalized)
    }

    /**
     * Write TXXX:GUID tag (generates new UUID if null).
     *
     * @param file MP3 file
     * @param guid GUID to write (generates new if null)
     * @return true if successful
     */
    fun writeGuid(file: File, guid: String? = null): Boolean {
        val guidToWrite = guid ?: ID3TagsHelper.generateGuid()
        return writeTag(file, "GUID", guidToWrite)
    }

    /**
     * Write TXXX:CREATED_ON_DATE and TXXX:CREATED_ON_TS tags.
     *
     * @param file MP3 file
     * @param timestamp Unix timestamp in milliseconds (defaults to now)
     * @return true if successful
     */
    fun writeCreatedOnTimestamp(file: File, timestamp: Long = System.currentTimeMillis()): Boolean {
        val timestampSec = timestamp / 1000
        val date = ID3TagsHelper.formatDate(timestampSec)
        val iso = ID3TagsHelper.formatTimestampISO(timestampSec)

        return writeTag(file, "CREATED_ON_DATE", date) &&
                writeTag(file, "CREATED_ON_TS", iso)
    }

    /**
     * Write TXXX:CHECKSUM_MD5_AUDIO tag (calculates from file).
     *
     * @param file MP3 file
     * @return true if successful
     */
    fun writeChecksumAudio(file: File): Boolean {
        val checksum = MD5AudioCalculator.calculate(file) ?: return false
        return writeTag(file, "CHECKSUM_MD5_AUDIO", checksum)
    }

    /**
     * Write TXXX:DURATION_SEC tag (calculates from file).
     *
     * @param file MP3 file
     * @return true if successful
     */
    fun writeDuration(file: File): Boolean {
        val durationMs = getAudioDuration(file) ?: return false
        val durationSec = durationMs / 1000f
        val formatted = ID3TagsHelper.formatDuration(durationSec)
        return writeTag(file, "DURATION_SEC", formatted)
    }

    /**
     * Write TXXX:GENERATION_PARAMS tag.
     *
     * @param file MP3 file
     * @param params Generation parameters
     * @return true if successful
     */
    fun writeGenerationParams(file: File, params: GenerationParams): Boolean {
        val json = generationParamsAdapter.toJson(params)
        return writeTag(file, "GENERATION_PARAMS", json)
    }

    /**
     * Write all tags for file generated by ElevenLabs.
     *
     * @param file MP3 file
     * @param text Input text (will be normalized)
     * @param voiceId Voice ID
     * @param model Model name
     * @param stability Stability parameter
     * @param similarityBoost Similarity boost parameter
     * @param style Style parameter
     * @param speakerBoost Speaker boost parameter
     * @param voice Voice name (optional)
     * @param timestampUnixSec Unix timestamp in seconds UTC (from ElevenLabs API or current time)
     * @return true if successful
     */
    fun writeElevenLabsTags(
        file: File,
        text: String,
        voiceId: String,
        model: String = "eleven_multilingual_v2",
        stability: Float = 0.5f,
        similarityBoost: Float = 0.75f,
        style: Float = 0.0f,
        speakerBoost: Boolean = true,
        voice: String = "Antoni",
        timestampUnixSec: Long? = null
    ): Boolean {
        android.util.Log.d("TXXXTagsWriter", "writeElevenLabsTags for file: ${file.name}, text length: ${text.length}")
        
        val params = GenerationParams.forElevenLabs(
            model = model,
            voice = voice,
            voiceId = voiceId,
            stability = stability,
            similarityBoost = similarityBoost,
            style = style,
            speakerBoost = speakerBoost
        )

        // Hierarchy of timestamp sources (most reliable first):
        // 1. Filename (if ElevenLabs format) - most trustworthy
        // 2. Parameter timestampUnixSec (from ElevenLabs API)
        // 3. Current time (fallback)
        val timestampUtcSec = parseTimestampFromFilename(file.name)
            ?: timestampUnixSec
            ?: (System.currentTimeMillis() / 1000)
        
        val timestampSource = when {
            parseTimestampFromFilename(file.name) != null -> "filename (UTC)"
            timestampUnixSec != null -> "parameter (UTC)"
            else -> "current time"
        }
        
        // Convert UTC to Europe/Warsaw timezone (handles CEST/CET automatically)
        val timestampWarsawSec = convertUtcToWarsaw(timestampUtcSec)
        
        val transcription = ID3TagsHelper.normalizeText(text)
        val guid = ID3TagsHelper.generateGuid()
        val duration = getAudioDuration(file)?.let { it / 1000f }
        val checksum = MD5AudioCalculator.calculate(file)
        
        android.util.Log.d("TXXXTagsWriter", """
            Tags to write:
            - transcription: ${transcription?.take(50)}...
            - guid: $guid
            - duration: $duration
            - checksum: $checksum
            - timestamp source: $timestampSource
            - timestamp UTC: $timestampUtcSec (${Date(timestampUtcSec * 1000)})
            - timestamp Warsaw: $timestampWarsawSec (${Date(timestampWarsawSec * 1000)})
        """.trimIndent())

        val tags = TXXXTags(
            transcription = transcription,
            createdOnDate = ID3TagsHelper.formatDate(timestampWarsawSec),
            createdOnTimestamp = timestampWarsawSec,
            duration = duration,
            guid = guid,
            checksumAudio = checksum,
            generationParams = params
        )

        val result = writeAllTags(file, tags)
        
        // Set file lastModified to Warsaw timestamp
        if (result) {
            file.setLastModified(timestampWarsawSec * 1000)
            android.util.Log.d("TXXXTagsWriter", "Set file lastModified to: ${Date(timestampWarsawSec * 1000)}")
        }
        
        android.util.Log.d("TXXXTagsWriter", "writeAllTags result: $result")
        return result
    }

    /**
     * Write TXXX tag to ID3v2 tag.
     *
     * @param tag ID3v2 tag
     * @param description Tag description (e.g., "Content", "GUID")
     * @param value Tag value
     */
    private fun writeTXXXTag(tag: AbstractID3v2Tag, description: String, value: String) {
        try {
            val frameId = when (tag) {
                is ID3v24Tag -> ID3v24Frames.FRAME_ID_USER_DEFINED_INFO
                is org.jaudiotagger.tag.id3.ID3v23Tag -> org.jaudiotagger.tag.id3.ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO
                else -> return
            }
            
            // IMPORTANT: Remove ALL existing TXXX tags with same description to avoid duplicates
            // This handles 3 cases: first time (none), single existing, multiple existing
            var removedCount = 0
            
            // Get all TXXX frames (may be List or single frame)
            val allFrames = tag.getFields(frameId)
            if (allFrames != null) {
                // Collect all frames with matching description
                val framesToRemove = mutableListOf<org.jaudiotagger.tag.id3.AbstractID3v2Frame>()
                
                allFrames.forEach { frame ->
                    if (frame is org.jaudiotagger.tag.id3.AbstractID3v2Frame) {
                        val body = frame.body
                        if (body is FrameBodyTXXX && body.description == description) {
                            framesToRemove.add(frame)
                        }
                    }
                }
                
                // Remove ALL matching frames
                framesToRemove.forEach { frame ->
                    tag.removeFrame(frame.identifier)
                    removedCount++
                }
                
                if (removedCount > 0) {
                    android.util.Log.d("TXXXTagsWriter", "Removed $removedCount existing TXXX:$description tag(s) to avoid duplicates")
                }
            }
            
            // Create new TXXX frame body with sanitized value
            val frameBody = FrameBodyTXXX()
            frameBody.description = description
            frameBody.text = value.filter { it.code >= 32 }.trim()  // Remove control characters & trim
            frameBody.textEncoding = 3  // UTF-8 encoding
            
            // Create frame and add it
            when (tag) {
                is ID3v24Tag -> {
                    val frame = org.jaudiotagger.tag.id3.ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
                    frame.body = frameBody
                    tag.addFrame(frame)
                }
                is org.jaudiotagger.tag.id3.ID3v23Tag -> {
                    val frame = org.jaudiotagger.tag.id3.ID3v23Frame(org.jaudiotagger.tag.id3.ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO)
                    frame.body = frameBody
                    tag.addFrame(frame)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsWriter", "Error writing TXXX:$description", e)
        }
    }

    /**
     * Get audio duration in milliseconds using MediaMetadataRetriever.
     *
     * @param file Audio file
     * @return Duration in milliseconds, or null on error
     */
    private fun getAudioDuration(file: File): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsWriter", "Error getting duration", e)
            null
        }
    }
}
