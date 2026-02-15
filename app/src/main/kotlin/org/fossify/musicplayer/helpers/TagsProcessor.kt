package org.fossify.musicplayer.helpers

import android.util.Log
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.models.TXXXTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.util.UUID

/**
 * Processor for reading, generating, and writing TXXX tags to MP3 files.
 * 
 * This class provides unified logic for processing tags that is shared between:
 * - SimpleMediaScanner (automatic scanning)
 * - MainActivity.scanID3Tags() (manual tag scanning)
 */
object TagsProcessor {
    
    /**
     * Process TXXX tags for a track file:
     * 1. Read existing tags from file
     * 2. Generate missing GUID if needed
     * 3. Parse and generate missing CREATED_ON_TS from filename if needed
     * 4. Write missing tags to file
     * 5. Return processed tags
     * 
     * @param file MP3 file to process
     * @param writeToFile If true, writes missing tags to file. If false, only reads.
     * @return ProcessedTags containing read/generated tags and modification status
     */
    fun processTrackTags(file: File, writeToFile: Boolean = true): ProcessedTags {
        if (!file.exists() || !file.isFile) {
            Log.w("TagsProcessor", "File does not exist: ${file.absolutePath}")
            return ProcessedTags(null, false)
        }
        
        try {
            // 1. Read existing tags from file
            var tags = TXXXTagsReader.readAllTags(file)
            var tagsModified = false
            
            if (tags != null) {
                Log.d("TagsProcessor", "Read existing tags from: ${file.name} (guid: ${tags.guid}, timestamp: ${tags.createdOnTimestamp})")
            } else {
                Log.d("TagsProcessor", "No TXXX tags found in: ${file.name}")
            }
            
            // 2. Generate and write missing GUID
            val guidToUse = if (tags?.guid == null) {
                val newGuid = ID3TagsHelper.generateGuid()
                
                if (writeToFile && writeTXXXTag(file, "GUID", newGuid)) {
                    tagsModified = true
                    Log.d("TagsProcessor", "✅ Generated and wrote GUID: ${file.name} → $newGuid")
                    newGuid
                } else if (!writeToFile) {
                    Log.d("TagsProcessor", "Generated GUID (not written): ${file.name} → $newGuid")
                    newGuid
                } else {
                    Log.w("TagsProcessor", "Failed to write GUID: ${file.name}")
                    null
                }
            } else {
                tags.guid
            }
            
            // 3. Parse and write missing CREATED_ON_TS from filename
            val timestampToUse = if (tags?.createdOnTimestamp == null) {
                val parsedTimestamp = TXXXTagsWriter.parseTimestampFromFilename(file.name)
                
                if (parsedTimestamp != null) {
                    val timestampMillis = parsedTimestamp * 1000
                    val timestampSec = timestampMillis / 1000
                    val dateStr = ID3TagsHelper.formatDate(timestampSec)
                    val isoStr = ID3TagsHelper.formatTimestampISO(timestampSec)
                    
                    if (writeToFile && writeTXXXTag(file, "CREATED_ON_DATE", dateStr) && writeTXXXTag(file, "CREATED_ON_TS", isoStr)) {
                        tagsModified = true
                        Log.d("TagsProcessor", "✅ Parsed and wrote timestamp: ${file.name} → $parsedTimestamp")
                        parsedTimestamp
                    } else if (!writeToFile) {
                        Log.d("TagsProcessor", "Parsed timestamp (not written): ${file.name} → $parsedTimestamp")
                        parsedTimestamp
                    } else {
                        Log.w("TagsProcessor", "Failed to write timestamp: ${file.name}")
                        null
                    }
                } else {
                    Log.d("TagsProcessor", "Could not parse timestamp from filename: ${file.name}")
                    null
                }
            } else {
                tags.createdOnTimestamp
            }
            
            // 4. Create updated tags object
            val processedTags = if (tags != null || guidToUse != null || timestampToUse != null) {
                TXXXTags(
                    transcription = tags?.transcription,
                    createdOnDate = tags?.createdOnDate,
                    createdOnTimestamp = timestampToUse,
                    duration = tags?.duration,
                    guid = guidToUse,
                    checksumAudio = tags?.checksumAudio,
                    generationParams = tags?.generationParams
                )
            } else {
                null
            }
            
            if (tagsModified && writeToFile) {
                Log.i("TagsProcessor", "✅ Updated tags for: ${file.name}")
            }
            
            return ProcessedTags(processedTags, tagsModified)
            
        } catch (e: Exception) {
            Log.e("TagsProcessor", "Error processing tags for: ${file.absolutePath}", e)
            return ProcessedTags(null, false)
        }
    }
    
    /**
     * Apply processed tags to a Track object.
     * 
     * @param track Track to update
     * @param tags Tags to apply
     */
    fun applyTagsToTrack(track: Track, tags: TXXXTags?) {
        if (tags == null) return
        
        track.transcription = tags.transcription
        track.transcriptionNormalized = tags.transcription?.let { ID3TagsHelper.normalizeText(it) }
        track.guid = tags.guid?.let { UUID.fromString(it) }
        track.tagTxxxCreatedAtUnix = tags.createdOnTimestamp
        track.checksumAudio = tags.checksumAudio
        
        // If file has CREATED_ON_TS tag, use it as added_at_timestamp_unix
        tags.createdOnTimestamp?.let { timestamp ->
            track.addedAtTimestampUnix = timestamp.toInt()
        }
    }
    
    /**
     * Result of tag processing.
     * 
     * @param tags Processed tags (may contain read + generated values)
     * @param modified True if tags were written to file
     */
    data class ProcessedTags(
        val tags: TXXXTags?,
        val modified: Boolean
    )
    
    /**
     * Write a single TXXX tag to MP3 file.
     * 
     * @param file MP3 file
     * @param description Tag description (e.g., "GUID", "CREATED_ON_TS")
     * @param value Tag value
     * @return true if successful
     */
    private fun writeTXXXTag(file: File, description: String, value: String): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault as? AbstractID3v2Tag ?: return false
            
            // Get frame ID
            val frameId = when (tag) {
                is ID3v24Tag -> ID3v24Frames.FRAME_ID_USER_DEFINED_INFO
                is org.jaudiotagger.tag.id3.ID3v23Tag -> org.jaudiotagger.tag.id3.ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO
                else -> "TXXX"
            }
            
            // Remove existing frame with this description
            val framesToRemove = mutableListOf<org.jaudiotagger.tag.id3.AbstractID3v2Frame>()
            for (frame in tag.getFields(frameId)) {
                if (frame is org.jaudiotagger.tag.id3.AbstractID3v2Frame) {
                    val body = frame.body
                    if (body is FrameBodyTXXX && body.description == description) {
                        framesToRemove.add(frame)
                    }
                }
            }
            framesToRemove.forEach { tag.removeFrame(it.identifier) }
            
            // Create and add new frame
            when (tag) {
                is ID3v24Tag -> {
                    val frame = org.jaudiotagger.tag.id3.ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
                    frame.body = FrameBodyTXXX(org.jaudiotagger.tag.id3.valuepair.TextEncoding.ISO_8859_1, description, value)
                    tag.addFrame(frame)
                }
                is org.jaudiotagger.tag.id3.ID3v23Tag -> {
                    val frame = org.jaudiotagger.tag.id3.ID3v23Frame(org.jaudiotagger.tag.id3.ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO)
                    frame.body = FrameBodyTXXX(org.jaudiotagger.tag.id3.valuepair.TextEncoding.ISO_8859_1, description, value)
                    tag.addFrame(frame)
                }
            }
            
            audioFile.commit()
            true
        } catch (e: Exception) {
            Log.e("TagsProcessor", "Error writing TXXX:$description", e)
            false
        }
    }
}
