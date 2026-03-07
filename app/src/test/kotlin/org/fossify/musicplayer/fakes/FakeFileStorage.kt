package org.fossify.musicplayer.fakes

import org.fossify.musicplayer.application.ports.FileStorage
import org.fossify.musicplayer.domain.model.AudioMetadata
import java.io.File

class FakeFileStorage : FileStorage {
    
    private val files = mutableMapOf<String, ByteArray>()
    private val metadata = mutableMapOf<String, AudioMetadata>()
    
    override suspend fun saveAudioFile(data: ByteArray, metadata: AudioMetadata): File {
        val filename = "test_audio_${metadata.guid}.mp3"
        files[filename] = data
        this.metadata[filename] = metadata
        return File(filename)
    }
    
    override suspend fun deleteFile(path: String): Boolean {
        val filename = File(path).name
        return files.remove(filename) != null
    }
    
    override suspend fun getTempFiles(): List<File> {
        return files.keys.map { File(it) }
    }
    
    override suspend fun clearTempFiles() {
        files.clear()
        metadata.clear()
    }
    
    override suspend fun clearOldTempFiles(olderThanHours: Int) {
    }
    
    override fun getTempDirectory(): File {
        return File("temp")
    }
    
    fun getFileData(filename: String): ByteArray? = files[filename]
    
    fun getFileMetadata(filename: String): AudioMetadata? = metadata[filename]
    
    fun getFileCount(): Int = files.size
    
    fun clear() {
        files.clear()
        metadata.clear()
    }
}
