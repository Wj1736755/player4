package org.fossify.musicplayer.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.fossify.musicplayer.models.GenerationParams
import org.fossify.musicplayer.models.TXXXTags
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integration tests for TXXX tags (Writer → Reader → Validation).
 * 
 * Tests verify that:
 * 1. Writer successfully writes tags
 * 2. Reader successfully reads tags
 * 3. Read values match written values
 * 4. Values conform to documented standards
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TXXXTagsIntegrationTest {

    private lateinit var context: Context
    private lateinit var writer: TXXXTagsWriter
    private lateinit var testFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        writer = TXXXTagsWriter(context)
        
        // Create minimal valid MP3 file for testing
        testFile = File(context.cacheDir, "test_audio.mp3")
        createMinimalMP3File(testFile)
    }

    @After
    fun tearDown() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun `test write and read all tags`() {
        // Given
        val timestamp = System.currentTimeMillis() / 1000
        val originalTags = TXXXTags(
            transcription = "Test transcription, with - punctuation? Yes!",
            createdOnDate = ID3TagsHelper.formatDate(timestamp),
            createdOnTimestamp = timestamp,
            duration = 3.5f,
            guid = ID3TagsHelper.generateGuid(),
            checksumAudio = "4917cf2436a9021114f1adea0c95a797",
            generationParams = GenerationParams.forElevenLabs(
                model = "eleven_multilingual_v2",
                voice = "Antoni",
                voiceId = "ErXwobaYiN019PkySvjV",
                stability = 0.5f,
                similarityBoost = 0.75f,
                style = 0.0f,
                speakerBoost = true
            )
        )

        // When
        val writeSuccess = writer.writeAllTags(testFile, originalTags)
        assertTrue("Write should succeed", writeSuccess)

        val readTags = TXXXTagsReader.readAllTags(testFile)
        assertNotNull("Read should return tags", readTags)

        // Then - verify values match
        readTags?.let { tags ->
            // Transcription should be normalized
            val expectedNormalized = "Test transcription with punctuation Yes."
            assertEquals("Transcription should be normalized", expectedNormalized, tags.transcription)
            
            assertEquals("Date should match", originalTags.createdOnDate, tags.createdOnDate)
            assertEquals("Timestamp should match", originalTags.createdOnTimestamp, tags.createdOnTimestamp)
            assertEquals("Duration should match", originalTags.duration, tags.duration)
            assertEquals("GUID should match", originalTags.guid, tags.guid)
            assertEquals("Checksum should match", originalTags.checksumAudio, tags.checksumAudio)
            
            assertNotNull("Generation params should exist", tags.generationParams)
            assertEquals("Generation type should match", "elevenlabs", tags.generationParams?.type)
        }
    }

    @Test
    fun `test transcription normalization`() {
        // Given
        val inputText = "Hello, world - how are you? Great!"
        val expectedNormalized = "Hello world how are you Great."

        // When
        assertTrue(writer.writeTranscription(testFile, inputText))
        val readText = TXXXTagsReader.readTranscription(testFile)

        // Then
        assertEquals("Transcription should be normalized", expectedNormalized, readText)
    }

    @Test
    fun `test GUID format validation`() {
        // Given - write GUID
        val guid = ID3TagsHelper.generateGuid()
        assertTrue(writer.writeGuid(testFile, guid))

        // When - read GUID
        val readGuid = TXXXTagsReader.readGuid(testFile)

        // Then - validate format
        assertNotNull("GUID should be read", readGuid)
        assertTrue("GUID should be valid UUID v4", ID3TagsHelper.isValidGuid(readGuid))
        assertEquals("GUID should match", guid, readGuid)
        
        // Verify lowercase
        assertEquals("GUID should be lowercase", guid.lowercase(), readGuid)
    }

    @Test
    fun `test timestamp format with timezone`() {
        // Given
        val timestamp = System.currentTimeMillis()
        
        // When
        assertTrue(writer.writeCreatedOnTimestamp(testFile, timestamp))
        val readTimestamp = TXXXTagsReader.readCreatedOnTimestamp(testFile)

        // Then
        assertNotNull("Timestamp should be read", readTimestamp)
        assertEquals("Timestamp should match (seconds)", timestamp / 1000, readTimestamp)
        
        // Verify ISO format with timezone was written
        val rawValue = TXXXTagsReader.readTag(testFile, "CREATED_ON_TS")
        assertNotNull("Raw timestamp should exist", rawValue)
        assertTrue("Timestamp should contain timezone", rawValue?.contains(Regex("[+-]\\d{4}")) == true)
    }

    @Test
    fun `test date format YYYY-MM-DD`() {
        // Given
        val timestamp = System.currentTimeMillis()
        
        // When
        assertTrue(writer.writeCreatedOnTimestamp(testFile, timestamp))
        val readDate = TXXXTagsReader.readCreatedOnDate(testFile)

        // Then
        assertNotNull("Date should be read", readDate)
        assertTrue("Date should be valid format", ID3TagsHelper.isValidDate(readDate))
        
        // Verify format YYYY-MM-DD
        val regex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        assertTrue("Date should match YYYY-MM-DD format", regex.matches(readDate ?: ""))
    }

    @Test
    fun `test duration format with 1 decimal place`() {
        // Given
        val tags = TXXXTags(duration = 3.5f)
        
        // When
        assertTrue(writer.writeAllTags(testFile, tags))
        val readDuration = TXXXTagsReader.readDuration(testFile)

        // Then
        assertNotNull("Duration should be read", readDuration)
        assertEquals("Duration should match", 3.5f, readDuration!!, 0.01f)
        
        // Verify format: always 1 decimal place
        val rawValue = TXXXTagsReader.readTag(testFile, "DURATION_SEC")
        assertTrue("Duration should have 1 decimal place", ID3TagsHelper.isValidDuration(rawValue))
        assertEquals("Duration should be formatted as X.Y", "3.5", rawValue)
    }

    @Test
    fun `test duration with integer value has decimal place`() {
        // Given - integer value 5 seconds
        val tags = TXXXTags(duration = 5.0f)
        
        // When
        assertTrue(writer.writeAllTags(testFile, tags))
        
        // Then
        val rawValue = TXXXTagsReader.readTag(testFile, "DURATION_SEC")
        assertEquals("Integer duration should have .0", "5.0", rawValue)
    }

    @Test
    fun `test checksum MD5 format`() {
        // Given
        val checksum = "4917cf2436a9021114f1adea0c95a797"
        val tags = TXXXTags(checksumAudio = checksum)
        
        // When
        assertTrue(writer.writeAllTags(testFile, tags))
        val readChecksum = TXXXTagsReader.readChecksumAudio(testFile)

        // Then
        assertNotNull("Checksum should be read", readChecksum)
        assertTrue("Checksum should be valid MD5", ID3TagsHelper.isValidMD5(readChecksum))
        assertEquals("Checksum should match", checksum, readChecksum)
        assertEquals("Checksum should be lowercase", checksum.lowercase(), readChecksum)
    }

    @Test
    fun `test generation params JSON serialization`() {
        // Given
        val params = GenerationParams.forElevenLabs(
            model = "eleven_multilingual_v2",
            voice = "Antoni",
            voiceId = "ErXwobaYiN019PkySvjV",
            stability = 0.5f,
            similarityBoost = 0.75f,
            style = 0.0f,
            speakerBoost = true
        )
        
        // When
        assertTrue(writer.writeGenerationParams(testFile, params))
        val readParams = TXXXTagsReader.readGenerationParams(testFile)

        // Then
        assertNotNull("Generation params should be read", readParams)
        assertEquals("Type should match", "elevenlabs", readParams?.type)
        
        val typeParams = readParams?.typeParams
        assertNotNull("TypeParams should exist", typeParams)
        assertEquals("Model should match", "eleven_multilingual_v2", typeParams?.get("model"))
        assertEquals("Voice should match", "Antoni", typeParams?.get("voice"))
        assertEquals("VoiceId should match", "ErXwobaYiN019PkySvjV", typeParams?.get("voice_id"))
    }

    @Test
    fun `test write ElevenLabs tags complete`() {
        // Given
        val inputText = "Test text, with - punctuation?"
        val voiceId = "ErXwobaYiN019PkySvjV"
        
        // When
        val success = writer.writeElevenLabsTags(
            file = testFile,
            text = inputText,
            voiceId = voiceId,
            model = "eleven_multilingual_v2",
            stability = 0.5f,
            similarityBoost = 0.75f,
            style = 0.0f,
            speakerBoost = true,
            voice = "Antoni"
        )
        assertTrue("Write should succeed", success)

        val tags = TXXXTagsReader.readAllTags(testFile)
        assertNotNull("Tags should be read", tags)

        // Then - verify all fields are present and valid
        tags?.let {
            assertNotNull("Transcription should exist", it.transcription)
            assertTrue("Transcription should be normalized", !it.transcription!!.contains(','))
            
            assertNotNull("Date should exist", it.createdOnDate)
            assertTrue("Date should be valid", ID3TagsHelper.isValidDate(it.createdOnDate))
            
            assertNotNull("Timestamp should exist", it.createdOnTimestamp)
            assertTrue("Timestamp should be reasonable", it.createdOnTimestamp!! > 0)
            
            assertNotNull("Duration should exist", it.duration)
            assertTrue("Duration should be positive", it.duration!! > 0)
            
            assertNotNull("GUID should exist", it.guid)
            assertTrue("GUID should be valid", ID3TagsHelper.isValidGuid(it.guid))
            
            assertNotNull("Checksum should exist", it.checksumAudio)
            assertTrue("Checksum should be valid", ID3TagsHelper.isValidMD5(it.checksumAudio))
            
            assertNotNull("Generation params should exist", it.generationParams)
            assertEquals("Generation type should be elevenlabs", "elevenlabs", it.generationParams?.type)
        }
    }

    @Test
    fun `test empty tags`() {
        // Given
        val emptyTags = TXXXTags()
        
        // When
        assertTrue(emptyTags.isEmpty())
        assertFalse(emptyTags.isNotEmpty())
    }

    @Test
    fun `test non-empty tags`() {
        // Given
        val tags = TXXXTags(transcription = "Test")
        
        // When
        assertFalse(tags.isEmpty())
        assertTrue(tags.isNotEmpty())
    }

    /**
     * Create minimal valid MP3 file for testing.
     * This creates a very small MP3 file with minimal MPEG frame.
     */
    private fun createMinimalMP3File(file: File) {
        // Minimal MP3: ID3v2 header + minimal MPEG frame
        // ID3v2.4 header (10 bytes): "ID3" + version + flags + size
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),  // ID3
            0x04, 0x00,  // Version 2.4.0
            0x00,        // Flags
            0x00, 0x00, 0x00, 0x00  // Size (0)
        )
        
        // Minimal MPEG Audio Layer III frame header (4 bytes) + some data
        // Frame sync (11 bits set) + MPEG Audio Layer III + bitrate + samplerate + padding
        val mpegFrame = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte(),  // MPEG frame header
            // Add some dummy audio data (at least a few bytes)
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        
        file.outputStream().use { output ->
            output.write(id3Header)
            output.write(mpegFrame)
        }
    }
}
