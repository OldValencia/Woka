package io.iris.ui.viewer.player

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
                for (byteArray in audioChannel) {
                    if (byteArray.isEmpty()) {
                        isPlaying = false
                        currentPositionMs = durationMs
                        continue
                    }
                    while (!isPlaying || isSeeking) {
                        if (!soundLineWasPaused) {
                            soundLine.stop()
                            soundLineWasPaused = true
                        }
                        delay(50.milliseconds)
                    }
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
