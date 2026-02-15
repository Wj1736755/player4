package org.fossify.musicplayer.helpers

import com.squareup.moshi.Moshi
import org.fossify.musicplayer.models.GenerationParams
import org.fossify.musicplayer.models.TXXXTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File

/**
 * Reader for TXXX tags in MP3 files.
 * 
 * Reads custom tags from MP3 files using jaudiotagger library.
 */
object TXXXTagsReader {

    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val generationParamsAdapter = moshi.adapter(GenerationParams::class.java)

    /**
     * Read all TXXX tags from MP3 file.
     *
     * @param file MP3 file
     * @return TXXXTags object, or null on error
     */
    fun readAllTags(file: File): TXXXTags? {
        if (!file.exists() || !file.isFile) {
            android.util.Log.w("TXXXTagsReader", "File does not exist or is not a file: ${file.absolutePath}")
            return null
        }

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag as? AbstractID3v2Tag
            if (tag == null) {
                android.util.Log.w("TXXXTagsReader", "No ID3v2 tag found in file: ${file.name}")
                return null
            }

            val transcription = readTXXXTag(tag, "Content")
            val guid = readTXXXTag(tag, "GUID")
            val checksum = readTXXXTag(tag, "CHECKSUM_MD5_AUDIO")
            
            android.util.Log.d("TXXXTagsReader", """
                Read tags from ${file.name}:
                - transcription: ${transcription?.take(50)}...
                - guid: $guid
                - checksum: $checksum
            """.trimIndent())

            TXXXTags(
                transcription = transcription,
                createdOnDate = readTXXXTag(tag, "CREATED_ON_DATE"),
                createdOnTimestamp = readCreatedOnTimestamp(file),
                duration = readDuration(file),
                guid = guid,
                checksumAudio = checksum,
                generationParams = readGenerationParams(file)
            )
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsReader", "Error reading tags from ${file.name}", e)
            null
        }
    }

    /**
     * Read single TXXX tag from MP3 file.
     *
     * @param file MP3 file
     * @param tagName Tag description (e.g., "Content", "GUID")
     * @return Tag value, or null if not found
     */
    fun readTag(file: File, tagName: String): String? {
        if (!file.exists() || !file.isFile) return null

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag as? AbstractID3v2Tag ?: return null
            readTXXXTag(tag, tagName)
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsReader", "Error reading tag $tagName", e)
            null
        }
    }

    /**
     * Read TXXX:Content tag (transcription).
     *
     * @param file MP3 file
     * @return Transcription text, or null if not found
     */
    fun readTranscription(file: File): String? {
        return readTag(file, "Content")
    }

    /**
     * Read TXXX:GUID tag.
     *
     * @param file MP3 file
     * @return GUID, or null if not found
     */
    fun readGuid(file: File): String? {
        return readTag(file, "GUID")
    }

    /**
     * Read TXXX:CREATED_ON_TS tag (as Unix timestamp).
     *
     * @param file MP3 file
     * @return Unix timestamp in seconds, or null if not found
     */
    fun readCreatedOnTimestamp(file: File): Long? {
        val rawValue = readTag(file, "CREATED_ON_TS") ?: return null
        return ID3TagsHelper.parseTimestampToUnix(rawValue)
    }

    /**
     * Read TXXX:CHECKSUM_MD5_AUDIO tag.
     *
     * @param file MP3 file
     * @return MD5 checksum, or null if not found
     */
    fun readChecksumAudio(file: File): String? {
        return readTag(file, "CHECKSUM_MD5_AUDIO")
    }

    /**
     * Read TXXX:DURATION_SEC tag (as Float).
     *
     * @param file MP3 file
     * @return Duration in seconds, or null if not found
     */
    fun readDuration(file: File): Float? {
        val rawValue = readTag(file, "DURATION_SEC") ?: return null
        return ID3TagsHelper.parseDuration(rawValue)
    }

    /**
     * Read TXXX:GENERATION_PARAMS tag (as GenerationParams object).
     *
     * @param file MP3 file
     * @return GenerationParams object, or null if not found
     */
    fun readGenerationParams(file: File): GenerationParams? {
        val json = readTag(file, "GENERATION_PARAMS") ?: return null
        return try {
            generationParamsAdapter.fromJson(json)
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsReader", "Error parsing GENERATION_PARAMS", e)
            null
        }
    }

    /**
     * Read TXXX:CREATED_ON_DATE tag.
     *
     * @param file MP3 file
     * @return Date string (YYYY-MM-DD), or null if not found
     */
    fun readCreatedOnDate(file: File): String? {
        return readTag(file, "CREATED_ON_DATE")
    }

    /**
     * Read TXXX tag from ID3v2 tag.
     *
     * @param tag ID3v2 tag
     * @param description Tag description (e.g., "Content", "GUID")
     * @return Tag value, or null if not found
     */
    private fun readTXXXTag(tag: AbstractID3v2Tag, description: String): String? {
        return try {
            when (tag) {
                is ID3v24Tag -> {
                    val frames = tag.getFields("TXXX")
                    for (frame in frames) {
                        if (frame is org.jaudiotagger.tag.id3.AbstractID3v2Frame) {
                            val body = frame.body
                            if (body is FrameBodyTXXX) {
                                if (body.description == description) {
                                    return body.text
                                }
                            }
                        }
                    }
                    null
                }
                is ID3v23Tag -> {
                    val frames = tag.getFields("TXXX")
                    for (frame in frames) {
                        if (frame is org.jaudiotagger.tag.id3.AbstractID3v2Frame) {
                            val body = frame.body
                            if (body is FrameBodyTXXX) {
                                if (body.description == description) {
                                    return body.text
                                }
                            }
                        }
                    }
                    null
                }
                else -> {
                    // Fallback
                    tag.getFirst("TXXX:$description")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TXXXTagsReader", "Error reading TXXX:$description", e)
            null
        }
    }
}
