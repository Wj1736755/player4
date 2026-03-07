package org.fossify.musicplayer.application.ports

import org.fossify.musicplayer.domain.model.AudioMetadata
import java.io.File

interface FileStorage {
    suspend fun saveAudioFile(data: ByteArray, metadata: AudioMetadata): File
    
    suspend fun deleteFile(path: String): Boolean
    
    suspend fun getTempFiles(): List<File>
    
    suspend fun clearTempFiles()
    
    suspend fun clearOldTempFiles(olderThanHours: Int = 24)
    
    fun getTempDirectory(): File
}
