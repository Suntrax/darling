package com.blissless.anime.api

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio fingerprinting service for detecting OP/ED timestamps.
 *
 * Uses a simplified approach that doesn't require external native libraries:
 * 1. Extract audio from video using MediaCodec
 * 2. Generate energy-based fingerprint (amplitude patterns)
 * 3. Use sliding window comparison to find matches
 *
 * This is optimized for the specific use case of matching anime OP/ED audio
 * within episode videos.
 */
class AudioFingerprinter(private val context: Context) {

    companion object {
        private const val TAG = "AudioFingerprinter"

        // Fingerprint parameters
        private const val SAMPLE_RATE = 22050  // Lower sample rate for faster processing
        private const val FINGERPRINT_DURATION_MS = 30000  // 30 seconds of audio for fingerprint
        private const val HOP_SIZE_MS = 1000   // 1 second hop for sliding window
        private const val FRAME_SIZE_MS = 500   // 500ms frames for energy calculation

        // Match threshold (lower = more strict)
        private const val MATCH_THRESHOLD = 0.65f

        // OP is typically in first 5 minutes, ED in last 10 minutes
        private const val OP_SEARCH_WINDOW_SECONDS = 300   // First 5 minutes
        private const val ED_SEARCH_WINDOW_SECONDS = 600   // Last 10 minutes
    }

    /**
     * Result of fingerprint matching
     */
    data class MatchResult(
        val found: Boolean,
        val startTime: Float?,  // in seconds
        val endTime: Float?,    // in seconds
        val confidence: Float   // 0.0 to 1.0
    )

    /**
     * Audio fingerprint consisting of energy patterns
     */
    data class AudioFingerprint(
        val duration: Float,           // Duration in seconds
        val energyProfile: FloatArray, // Normalized energy values per frame
        val peakPattern: IntArray      // Pattern of energy peaks/dips
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioFingerprint) return false
            return duration == other.duration &&
                    energyProfile.contentEquals(other.energyProfile) &&
                    peakPattern.contentEquals(other.peakPattern)
        }

        override fun hashCode(): Int {
            var result = duration.hashCode()
            result = 31 * result + energyProfile.contentHashCode()
            result = 31 * result + peakPattern.contentHashCode()
            return result
        }
    }

    /**
     * Extract fingerprint from an OP/ED audio/video URL
     * Downloads a segment and generates a fingerprint
     */
    suspend fun extractFingerprintFromUrl(url: String): AudioFingerprint? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extracting fingerprint from: ${url.take(50)}...")

                // Download audio file
                val audioFile = downloadAudioSegment(url, FINGERPRINT_DURATION_MS)
                if (audioFile == null) {
                    Log.e(TAG, "Failed to download audio segment")
                    return@withContext null
                }

                // Generate fingerprint
                val fingerprint = generateFingerprint(audioFile)

                // Cleanup
                audioFile.delete()

                fingerprint
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting fingerprint", e)
                null
            }
        }
    }

    /**
     * Extract fingerprint from local file path
     */
    suspend fun extractFingerprintFromFile(filePath: String): AudioFingerprint? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "File not found: $filePath")
                    return@withContext null
                }

                generateFingerprint(file)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting fingerprint from file", e)
                null
            }
        }
    }

    /**
     * Find OP timestamp in episode by matching fingerprint
     * Only searches the first 5 minutes for efficiency
     */
    suspend fun findOpTimestamp(
        episodePath: String,
        opFingerprint: AudioFingerprint,
        episodeDuration: Float
    ): MatchResult? {
        return findTimestamp(
            episodePath = episodePath,
            fingerprint = opFingerprint,
            searchWindowSeconds = OP_SEARCH_WINDOW_SECONDS.coerceAtMost(episodeDuration.toInt()),
            episodeDuration = episodeDuration,
            searchType = "OP"
        )
    }

    /**
     * Find ED timestamp in episode by matching fingerprint
     * Searches the last portion of the episode
     */
    suspend fun findEdTimestamp(
        episodePath: String,
        edFingerprint: AudioFingerprint,
        episodeDuration: Float
    ): MatchResult? {
        // For ED, we search the last portion of the episode
        // ED typically starts around episode_length - (ED_duration + some buffer)
        val searchStart = (episodeDuration - ED_SEARCH_WINDOW_SECONDS).coerceAtLeast(0f)

        return findTimestamp(
            episodePath = episodePath,
            fingerprint = edFingerprint,
            searchWindowSeconds = ED_SEARCH_WINDOW_SECONDS,
            episodeDuration = episodeDuration,
            searchType = "ED",
            searchStartOffset = searchStart
        )
    }

    /**
     * Generic timestamp finding using sliding window fingerprint comparison
     */
    private suspend fun findTimestamp(
        episodePath: String,
        fingerprint: AudioFingerprint,
        searchWindowSeconds: Int,
        episodeDuration: Float,
        searchType: String,
        searchStartOffset: Float = 0f
    ): MatchResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Finding $searchType timestamp in episode (searching from ${searchStartOffset}s)")

            val episodeFile = File(episodePath)
            if (!episodeFile.exists()) {
                Log.e(TAG, "Episode file not found: $episodePath")
                return@withContext null
            }

            // Extract episode audio fingerprint for the search window
            val episodeFingerprint = extractEpisodeSegmentFingerprint(
                episodeFile,
                searchStartOffset,
                searchWindowSeconds
            )

            if (episodeFingerprint == null) {
                Log.e(TAG, "Failed to extract episode segment fingerprint")
                return@withContext null
            }

            // Find best match using sliding window
            val matchPosition = findBestMatchPosition(
                episodeFingerprint,
                fingerprint
            )

            if (matchPosition != null) {
                val startTime = searchStartOffset + matchPosition.first
                val endTime = startTime + fingerprint.duration

                Log.d(TAG, "Found $searchType at ${startTime}s - ${endTime}s (confidence: ${matchPosition.second})")

                MatchResult(
                    found = true,
                    startTime = startTime,
                    endTime = endTime.coerceAtMost(episodeDuration),
                    confidence = matchPosition.second
                )
            } else {
                Log.d(TAG, "No $searchType match found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding $searchType timestamp", e)
            null
        }
    }

    /**
     * Download audio segment from URL
     */
    private fun downloadAudioSegment(url: String, durationMs: Int): File? {
        return try {
            // Create temp file
            val tempFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.webm")

            val connection = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode != 200) {
                Log.e(TAG, "Failed to download audio: HTTP ${connection.responseCode}")
                return null
            }

            // Download to temp file (limited size for fingerprinting)
            val maxBytes = 5 * 1024 * 1024 // 5MB max for fingerprint segment

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < maxBytes) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }
            }

            connection.disconnect()

            if (tempFile.length() > 0) {
                tempFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading audio segment", e)
            null
        }
    }

    /**
     * Generate fingerprint from audio file
     * Uses energy-based pattern matching instead of full chromaprint
     */
    private fun generateFingerprint(audioFile: File): AudioFingerprint? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                Log.e(TAG, "No audio track found in file")
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            // Extract audio samples and compute energy profile
            val samples = extractAudioSamples(extractor, audioFormat)

            extractor.release()

            if (samples.isEmpty()) {
                Log.e(TAG, "No audio samples extracted")
                return null
            }

            // Generate energy profile
            val energyProfile = computeEnergyProfile(samples)

            // Generate peak pattern
            val peakPattern = computePeakPattern(energyProfile)

            // Calculate duration
            val duration = samples.size.toFloat() / SAMPLE_RATE

            AudioFingerprint(
                duration = duration,
                energyProfile = energyProfile,
                peakPattern = peakPattern
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprint", e)
            null
        }
    }

    /**
     * Extract audio samples from media using MediaCodec
     */
    private fun extractAudioSamples(extractor: MediaExtractor, format: MediaFormat): FloatArray {
        val samples = mutableListOf<Float>()
        val sampleBuffer = mutableListOf<Byte>()

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)

        // Configure decoder
        decoder.configure(format, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val inputBuffers = decoder.inputBuffers
        val outputBuffers = decoder.outputBuffers
        var isEOS = false
        var maxSamples = SAMPLE_RATE * 60 // Limit to 60 seconds for fingerprint

        while (samples.size < maxSamples) {
            // Feed input
            if (!isEOS) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = inputBuffers[inputIndex]
                    inputBuffer.clear()

                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isEOS = true
                    } else {
                        val presentationTime = extractor.sampleTime
                        decoder.queueInputBuffer(
                            inputIndex, 0, sampleSize, presentationTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            // Get output
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = outputBuffers[outputIndex]

                // Read PCM data
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                sampleBuffer.addAll(chunk.toList())

                decoder.releaseOutputBuffer(outputIndex, false)

                // Process accumulated samples
                if (sampleBuffer.size >= 4096) {
                    val processedSamples = processPcmData(sampleBuffer.toByteArray())
                    samples.addAll(processedSamples.toList())
                    sampleBuffer.clear()
                }
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break
            }
        }

        decoder.stop()
        decoder.release()

        return samples.toFloatArray()
    }

    /**
     * Process PCM audio data to normalized float samples
     */
    private fun processPcmData(data: ByteArray): FloatArray {
        // Assume 16-bit PCM
        val samples = FloatArray(data.size / 2)
        val byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        for (i in samples.indices) {
            val short = byteBuffer.short
            samples[i] = short.toFloat() / Short.MAX_VALUE
        }

        // Downsample to target sample rate and normalize
        return downsample(samples, 44100, SAMPLE_RATE)
    }

    /**
     * Downsample audio to lower sample rate
     */
    private fun downsample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples

        val ratio = fromRate.toFloat() / toRate
        val newLength = (samples.size / ratio).toInt()
        val result = FloatArray(newLength)

        for (i in 0 until newLength) {
            val srcIndex = (i * ratio).toInt()
            if (srcIndex < samples.size) {
                result[i] = samples[srcIndex]
            }
        }

        return result
    }

    /**
     * Compute energy profile from samples
     * Divides audio into frames and computes RMS energy for each
     */
    private fun computeEnergyProfile(samples: FloatArray): FloatArray {
        val frameSize = (SAMPLE_RATE * FRAME_SIZE_MS / 1000).toInt()
        val numFrames = samples.size / frameSize
        val energyProfile = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            val start = i * frameSize
            val end = (start + frameSize).coerceAtMost(samples.size)

            var sumSquares = 0.0
            for (j in start until end) {
                sumSquares += samples[j] * samples[j]
            }

            energyProfile[i] = sqrt(sumSquares / (end - start)).toFloat()
        }

        // Normalize to 0-1 range
        val maxEnergy = energyProfile.maxOrNull() ?: 1f
        if (maxEnergy > 0) {
            for (i in energyProfile.indices) {
                energyProfile[i] /= maxEnergy
            }
        }

        return energyProfile
    }

    /**
     * Compute peak/dip pattern from energy profile
     * Returns array of: 1 = peak, -1 = dip, 0 = neutral
     */
    private fun computePeakPattern(energyProfile: FloatArray): IntArray {
        if (energyProfile.size < 3) return IntArray(0)

        val pattern = IntArray(energyProfile.size)
        val threshold = 0.1f // 10% change threshold

        for (i in 1 until energyProfile.size - 1) {
            val prev = energyProfile[i - 1]
            val curr = energyProfile[i]
            val next = energyProfile[i + 1]

            // Peak detection
            if (curr > prev && curr > next && curr - prev > threshold) {
                pattern[i] = 1
            }
            // Dip detection
            else if (curr < prev && curr < next && prev - curr > threshold) {
                pattern[i] = -1
            }
            else {
                pattern[i] = 0
            }
        }

        return pattern
    }

    /**
     * Extract fingerprint from episode segment
     */
    private fun extractEpisodeSegmentFingerprint(
        episodeFile: File,
        startOffset: Float,
        durationSeconds: Int
    ): AudioFingerprint? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(episodeFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            // Seek to start offset
            val seekTimeUs = (startOffset * 1000000).toLong()
            extractor.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            // Extract samples for duration
            val samples = extractAudioSamplesForDuration(
                extractor,
                audioFormat,
                durationSeconds
            )

            extractor.release()

            if (samples.isEmpty()) return null

            val energyProfile = computeEnergyProfile(samples)
            val peakPattern = computePeakPattern(energyProfile)
            val actualDuration = samples.size.toFloat() / SAMPLE_RATE

            AudioFingerprint(
                duration = actualDuration,
                energyProfile = energyProfile,
                peakPattern = peakPattern
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting episode segment fingerprint", e)
            null
        }
    }

    /**
     * Extract audio samples for a specific duration
     */
    private fun extractAudioSamplesForDuration(
        extractor: MediaExtractor,
        format: MediaFormat,
        maxDurationSeconds: Int
    ): FloatArray {
        val samples = mutableListOf<Float>()
        val sampleBuffer = mutableListOf<Byte>()

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)

        decoder.configure(format, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false
        val maxSamples = SAMPLE_RATE * maxDurationSeconds

        val inputBuffers = decoder.inputBuffers
        val outputBuffers = decoder.outputBuffers

        while (samples.size < maxSamples) {
            if (!isEOS) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = inputBuffers[inputIndex]
                    inputBuffer.clear()

                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isEOS = true
                    } else {
                        val presentationTime = extractor.sampleTime
                        decoder.queueInputBuffer(
                            inputIndex, 0, sampleSize, presentationTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = outputBuffers[outputIndex]
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                sampleBuffer.addAll(chunk.toList())

                decoder.releaseOutputBuffer(outputIndex, false)

                if (sampleBuffer.size >= 4096) {
                    val processedSamples = processPcmData(sampleBuffer.toByteArray())
                    samples.addAll(processedSamples.toList())
                    sampleBuffer.clear()
                }
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break
            }
        }

        decoder.stop()
        decoder.release()

        return samples.toFloatArray()
    }

    /**
     * Find best match position using fingerprint comparison
     * Returns (position in seconds, confidence) or null if no match
     */
    private fun findBestMatchPosition(
        episodeFingerprint: AudioFingerprint,
        targetFingerprint: AudioFingerprint
    ): Pair<Float, Float>? {
        val episodeProfile = episodeFingerprint.energyProfile
        val targetProfile = targetFingerprint.energyProfile

        if (episodeProfile.isEmpty() || targetProfile.isEmpty()) return null
        if (episodeProfile.size < targetProfile.size) return null

        val windowSize = targetProfile.size
        val numWindows = episodeProfile.size - windowSize

        var bestPosition = 0
        var bestSimilarity = 0f

        // Slide window across episode profile
        for (offset in 0..numWindows step (HOP_SIZE_MS / FRAME_SIZE_MS).toInt()) {
            val similarity = computeSimilarity(
                episodeProfile, offset, windowSize,
                targetProfile
            )

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestPosition = offset
            }
        }

        // Also compare peak patterns for validation
        val peakSimilarity = comparePeakPatterns(
            episodeFingerprint.peakPattern,
            bestPosition,
            targetFingerprint.peakPattern
        )

        // Combined score
        val combinedScore = (bestSimilarity * 0.7f + peakSimilarity * 0.3f)

        if (combinedScore >= MATCH_THRESHOLD) {
            // Convert frame position to seconds
            val positionSeconds = bestPosition * FRAME_SIZE_MS / 1000f
            return Pair(positionSeconds, combinedScore)
        }

        return null
    }

    /**
     * Compute similarity between two energy profiles
     * Uses normalized cross-correlation
     */
    private fun computeSimilarity(
        profile: FloatArray,
        offset: Int,
        windowSize: Int,
        target: FloatArray
    ): Float {
        if (offset + windowSize > profile.size) return 0f

        var sumProduct = 0.0
        var sumProfileSq = 0.0
        var sumTargetSq = 0.0

        for (i in 0 until windowSize) {
            val p = profile[offset + i]
            val t = target[i]

            sumProduct += p * t
            sumProfileSq += p * p
            sumTargetSq += t * t
        }

        val denom = sqrt(sumProfileSq * sumTargetSq)
        if (denom < 0.0001) return 0f

        return (sumProduct / denom).toFloat()
    }

    /**
     * Compare peak patterns for additional validation
     */
    private fun comparePeakPatterns(
        episodePattern: IntArray,
        offset: Int,
        targetPattern: IntArray
    ): Float {
        if (offset + targetPattern.size > episodePattern.size) return 0f

        var matches = 0
        var total = 0

        for (i in targetPattern.indices) {
            val ep = episodePattern[offset + i]
            val tp = targetPattern[i]

            if (tp != 0) {  // Only count non-neutral positions
                total++
                if (ep == tp) {
                    matches++
                }
            }
        }

        return if (total > 0) matches.toFloat() / total else 0f
    }
}
