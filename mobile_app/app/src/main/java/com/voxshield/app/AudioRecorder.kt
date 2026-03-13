package com.voxshield.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Records microphone audio in 3-second WAV chunks.
 *
 * Usage:
 *   val recorder = AudioRecorder(cacheDir)
 *   recorder.startRecording { wavFile -> sendToApi(wavFile) }
 *   recorder.stopRecording()
 */
class AudioRecorder(private val outputDir: File) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 3000L
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false

    /** Minimum buffer size required by the hardware. */
    private val minBufferSize: Int
        get() = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)

    /**
     * Start continuous recording.  For every 3-second chunk a temporary
     * `.wav` file is created and handed to [onChunkReady].
     */
    fun startRecording(onChunkReady: (File) -> Unit) {
        if (isRecording) {
            Log.w(TAG, "Already recording – ignoring duplicate start")
            return
        }

        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2) // at least 1 s buffer

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise")
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread({
            val bytesPerSample = 2 // PCM-16
            val chunkBytes = (SAMPLE_RATE * (CHUNK_DURATION_MS / 1000.0) * bytesPerSample).toInt()
            val buffer = ByteArray(1024)

            while (isRecording) {
                try {
                    val wavFile = File(outputDir, "vox_chunk_${System.currentTimeMillis()}.wav")
                    val fos = FileOutputStream(wavFile)

                    // Write placeholder WAV header (44 bytes)
                    fos.write(ByteArray(44))

                    var totalBytesWritten = 0
                    while (isRecording && totalBytesWritten < chunkBytes) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                            totalBytesWritten += read
                        }
                    }
                    fos.close()

                    // Patch WAV header with correct sizes
                    writeWavHeader(wavFile, totalBytesWritten)

                    Log.i(TAG, "Chunk ready: ${wavFile.name} (${totalBytesWritten} bytes)")
                    onChunkReady(wavFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during recording chunk", e)
                }
            }
        }, "VoxShield-Recorder")

        recordingThread?.start()
        Log.i(TAG, "Recording started")
    }

    /** Stop recording and release resources. */
    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordingThread = null
        Log.i(TAG, "Recording stopped")
    }

    /** Write a valid RIFF/WAV header into the first 44 bytes of the file. */
    private fun writeWavHeader(file: File, dataSize: Int) {
        val raf = RandomAccessFile(file, "rw")
        val totalSize = dataSize + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8   // mono, 16-bit

        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeInt(Integer.reverseBytes(totalSize))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeInt(Integer.reverseBytes(16))      // sub-chunk size
        raf.writeShort(java.lang.Short.reverseBytes(1).toInt())  // PCM
        raf.writeShort(java.lang.Short.reverseBytes(1).toInt())  // mono
        raf.writeInt(Integer.reverseBytes(SAMPLE_RATE))
        raf.writeInt(Integer.reverseBytes(byteRate))
        raf.writeShort(java.lang.Short.reverseBytes(2).toInt())  // block align
        raf.writeShort(java.lang.Short.reverseBytes(16).toInt()) // bits per sample
        raf.writeBytes("data")
        raf.writeInt(Integer.reverseBytes(dataSize))
        raf.close()
    }
}
