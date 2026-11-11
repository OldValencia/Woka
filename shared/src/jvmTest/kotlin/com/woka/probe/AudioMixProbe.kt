package com.woka.probe

import com.woka.core.ui.image.SoundMix
import com.woka.feature.viewer.video.engine.audio.ramp
import java.io.File
import kotlin.math.abs
import kotlin.test.Test

/** TEMPORARY (ME-244): bounded PCM scheduling and export capability, not an implemented player. */
class AudioMixProbe {
    @Test
    fun `measure multiple audio sources on an edited clock`() {
        val root = File("build/audio-244-245").absoluteFile.also { it.mkdirs() }
        val rows = mutableListOf<String>()
        audioFixtures(root)
        val began = System.nanoTime()
        val film = readAudio(File(root, "film.wav"))
        val music = readAudio(File(root, "music.wav"))
        val surround = readAudio(File(root, "surround.flac"))
        rows += "decodeNormalizeMs=${(System.nanoTime() - began) / 1_000_000} stereo48kFrames film=${film.size / 2} music=${music.size / 2} surround=${surround.size / 2}"
        val sped = audioTempo(audioSlice(film, 6200, 8200), 2f)
        rows += "atempo film 2x expectedFrames=48000 rawFrames=${sped.size / 2}"
        val clips = listOf(audioSlice(film, 1130, 2810), audioSlice(film, 4200, 5990),
            sped.copyOf(96_000), ShortArray(48_000))
        val edited = joinAudio(clips)
        val smoothed = clips.map(::smoothEdges)
        val loop = audioSlice(music, 1000, 1800)
        val smoothLoop = smoothEdges(loop)
        rows += "joinStepLSB raw=${clips.zipWithNext(::edgeJump)} ramp5ms=${smoothed.zipWithNext(::edgeJump)}"
        rows += "repeatStepLSB raw=${edgeJump(loop, loop)} ramp5ms=${edgeJump(smoothLoop, smoothLoop)}"
        val fastMusic = audioTempo(audioSlice(music, 300, 3300), 1.25f)
        rows += "atempo music 1.25x expectedFrames=115200 rawFrames=${fastMusic.size / 2}"
        val entries = listOf(
            AudioEntry(edited, 0, SoundMix(volume = 0.8f, fadeInMs = 200, fadeOutMs = 300)),
            AudioEntry(fastMusic.copyOf(230_400), 650 * 48, SoundMix(volume = 0.7f, fadeInMs = 150, fadeOutMs = 250)),
            AudioEntry(loop, 2000 * 48, SoundMix(volume = 0.4f), repeat = true),
            AudioEntry(audioSlice(surround, 500, 2500), 1200 * 48, SoundMix(volume = 0.5f))
        )
        val frames = edited.size / 2
        val mixStart = System.nanoTime()
        val whole = mixAudio(entries, 0, frames, frames)
        rows += "mix4 timelineFrames=$frames expectedMs=4970 mixMs=${(System.nanoTime() - mixStart) / 1_000_000}"
        for (block in listOf(240, 960, 4096)) {
            val chunks = (0 until frames step block).map { from -> mixAudio(entries, from, minOf(block, frames - from), frames) }
            val replay = joinAudio(chunks)
            rows += "block=$block differences=${whole.indices.count { whole[it] != replay[it] }}"
        }
        for (ms in listOf(123, 650, 1680, 3470, 4000)) {
            val from = ms * 48
            val replay = mixAudio(entries, from, 480, frames)
            val expected = whole.copyOfRange(from * 2, (from + 480) * 2)
            rows += "schedule seekMs=$ms differences=${expected.indices.count { expected[it] != replay[it] }}"
        }
        val reference = nativeAudioMix(audioSlice(film, 1130, 2810), music.copyOf(161_280))
        val simple = mixAudio(listOf(AudioEntry(audioSlice(film, 1130, 2810), 0, SoundMix(volume = 0.5f)),
            AudioEntry(music.copyOf(161_280), 0, SoundMix(volume = 0.5f))), 0, 80_640, 80_640)
        rows += "nativeAmix oracle ${audioDifference(simple, reference)}"
        val overload = mixAudio(listOf(AudioEntry(film, 0, SoundMix(volume = 4f)),
            AudioEntry(film, 0, SoundMix(volume = 4f))), 0, 48_000, 48_000)
        rows += "overload clippedSamples=${overload.count { it == Short.MIN_VALUE || it == Short.MAX_VALUE }} of ${overload.size}; no automatic normalization"
        for (codec in listOf("pcm_s16le", "alac", "aac")) {
            val output = File(root, "mix-${codec}.${if (codec == "pcm_s16le") "wav" else "m4a"}")
            val start = System.nanoTime()
            writeAudio(output, whole, codec)
            val took = (System.nanoTime() - start) / 1_000_000
            val decoded = readAudio(output)
            rows += "export $codec ms=$took decodedFrames=${decoded.size / 2} ${audioDifference(whole, decoded)}"
        }
        for (name in listOf("film.wav", "music.wav", "surround.flac")) audioGrabber(File(root, name)).use { grabber ->
            for (target in listOf(1130, 2870, 4210)) {
                val start = System.nanoTime()
                grabber.setTimestamp(target * 1000L)
                val frame = grabber.grabSamples() ?: error("No seek audio")
                rows += "decoderSeek $name targetMs=$target landedUs=${grabber.timestamp} ms=${(System.nanoTime() - start) / 1_000_000} buffers=${frame.samples.size}"
            }
        }
        File(root, "mix-results.txt").writeText(rows.joinToString("\n"))
        rows.forEach { println("AUDIO244 $it") }
    }

    private fun smoothEdges(pcm: ShortArray): ShortArray = pcm.copyOf().also { copy ->
        ramp(copy, 2, MIX_RATE / 200, rising = true)
        ramp(copy, 2, MIX_RATE / 200, rising = false)
    }

    private fun edgeJump(a: ShortArray, b: ShortArray): Int =
        (0..1).maxOf { channel -> abs(a[a.size - 2 + channel].toInt() - b[channel]) }
}

internal data class AudioEntry(val pcm: ShortArray, val starts: Int, val mix: SoundMix, val repeat: Boolean = false)

/** Accumulate before clipping; entry positions and fades use the result clock, never packet order. */
internal fun mixAudio(entries: List<AudioEntry>, start: Int, count: Int, total: Int): ShortArray {
    val output = ShortArray(count * 2)
    for (index in 0 until count) for (channel in 0..1) {
        var sum = 0f
        for (entry in entries) {
            val into = start + index - entry.starts
            val length = entry.pcm.size / 2
            if (into < 0 || length == 0 || (!entry.repeat && into >= length)) continue
            val at = if (entry.repeat) into % length else into
            val gain = entry.mix.gainAt(into * 1000L / MIX_RATE,
                (if (entry.repeat) total - entry.starts else length) * 1000L / MIX_RATE)
            sum += entry.pcm[at * 2 + channel] * gain
        }
        output[index * 2 + channel] = sum.toInt().coerceIn(-32768, 32767).toShort()
    }
    return output
}
