package com.woka.ui.viewer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.milliseconds

internal fun VideoPlayerState.initAudio(
    grabber: FFmpegFrameGrabber,
    audioChannel: Channel<ByteArray>
): SourceDataLine? {
    try {
        val audioFormat = AudioFormat(
            grabber.sampleRate.toFloat(),
            16,
            grabber.audioChannels,
            true,
            false
        )
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        val soundLine = AudioSystem.getLine(info) as SourceDataLine
        soundLineInstance = soundLine
        val bufferSize = (audioFormat.sampleRate * audioFormat.frameSize * 0.1f).toInt()
        soundLine.open(audioFormat, bufferSize)
        setVolumeInternal(soundLine, volume)
        soundLine.start()
        
        audioJob = scope.launch(Dispatchers.IO) {
            var soundLineWasPaused = false
            try {
                while (isActive) {
                    if (!isPlaying || isSeeking) {
                        if (!soundLineWasPaused) {
                            soundLine.stop()
                            soundLineWasPaused = true
                        }
                        delay(50.milliseconds)
                        continue
                    }
                    // Poll instead of blocking on the channel. Above 1x the decoder
                    // can leave it empty, and a blocked receive never reaches the
                    // stop() above — pausing then left the line looping its tail.
                    val byteArray = withTimeoutOrNull(50.milliseconds) {
                        audioChannel.receiveCatching().getOrNull()
                    } ?: continue
                    // An empty buffer used to mean "audio ended", but nothing sends
                    // one deliberately any more — only a producer bug can, and
                    // answering that by stopping playback froze the whole player.
                    if (byteArray.isEmpty()) continue
                    if (soundLineWasPaused) {
                        soundLine.start()
                        soundLineWasPaused = false
                    }
                    setVolumeInternal(soundLine, volume)
                    soundLine.write(byteArray, 0, byteArray.size)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
        return soundLine
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
