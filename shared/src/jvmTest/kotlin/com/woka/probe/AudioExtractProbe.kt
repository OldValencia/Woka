package com.woka.probe

import java.io.File
import kotlin.test.Test

/** TEMPORARY (ME-245): copy eligibility, cut accuracy, encoder padding and selected-track safety. */
class AudioExtractProbe {
    @Test
    fun `measure audio extraction to m4a`() {
        val root = File("build/audio-244-245").absoluteFile.also { it.mkdirs() }
        val rows = mutableListOf<String>()
        audioFixtures(root)
        val codecs = listOf("aac" to "m4a", "alac" to "m4a", "libmp3lame" to "mp3",
            "ac3" to "ac3", "flac" to "flac", "libopus" to "opus", "pcm_s16le" to "wav")
        for ((codec, extension) in codecs) {
            val source = File(root, "extract-source-$codec.$extension")
            audioCommand(root, "encode-$codec", "-i", File(root, "film.wav").path, "-c:a", codec, source.path)
            val copied = File(root, "copy-$codec.m4a")
            val began = System.nanoTime()
            runCatching {
                copyAudioPackets(source, copied)
                val took = (System.nanoTime() - began) / 1_000_000
                val original = readAudio(source)
                val decoded = readAudio(copied)
                rows += "copy $codec ms=$took ${audioDescription(copied)} ${audioDifference(original, decoded)}"
            }.onFailure { rows += "copy $codec REJECTED ${it.message?.take(150)}" }
            if (codec !in listOf("aac", "alac")) {
                val fallback = File(root, "fallback-$codec.m4a")
                val start = System.nanoTime()
                val pcm = readAudio(source)
                writeAudio(fallback, pcm, "aac")
                rows += "fallback $codec ms=${(System.nanoTime() - start) / 1_000_000} ${audioDescription(fallback)} frames=${readAudio(fallback).size / 2}"
            }
            if (codec in listOf("aac", "alac")) {
                cutTrial(root, source, codec, listOf(1130 to 2810, 4200 to 5990), "two-cuts", rows)
                cutTrial(root, source, codec, (0 until 24).map { 113 + it * 197 to 186 + it * 197 }, "24-cuts", rows)
            }
        }
        audioCommand(root, "second-track", "-i", File(root, "music.wav").path,
            "-c:a", "aac", File(root, "second-track.m4a").path)
        val multi = File(root, "two-track.mkv")
        audioCommand(root, "two-track", "-f", "lavfi", "-i", "color=c=black:s=64x64:r=1:d=12",
            "-i", File(root, "extract-source-aac.m4a").path, "-i", File(root, "second-track.m4a").path,
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-c:v", "mpeg4", "-c:a", "copy", multi.path)
        for (stream in listOf(1, 2)) runCatching {
            val out = File(root, "selected-$stream.m4a")
            copyAudioPackets(multi, out, selected = stream)
            rows += "selected stream=$stream ${audioDescription(out)} decoded48kFrames=${readAudio(out).size / 2}"
        }.onFailure { rows += "selected stream=$stream FAILED ${it.message}" }
        rows += "selected source stream1=AAC stereo 48000, stream2=AAC mono 44100"
        File(root, "extract-results.txt").writeText(rows.joinToString("\n"))
        rows.forEach { println("AUDIO245 $it") }
    }

    private fun cutTrial(root: File, source: File, codec: String, cuts: List<Pair<Int, Int>>, label: String, rows: MutableList<String>) {
        val expectedMs = cuts.sumOf { it.second - it.first }
        val original = readAudio(source)
        val exact = joinAudio(cuts.map { (a, b) -> audioSlice(original, a, b) })
        val target = File(root, "$codec-$label-copy.m4a")
        runCatching {
            copyAudioPackets(source, target, cuts)
            val decoded = readAudio(target)
            rows += "$codec $label copy expectedMs=$expectedMs actualMs=${decoded.size / 96.0} deltaMs=${decoded.size / 96.0 - expectedMs} ${audioDescription(target)}"
        }.onFailure { rows += "$codec $label copy FAILED ${it.message}" }
        for (encoder in listOf("aac", "alac")) {
            val encoded = File(root, "$codec-$label-$encoder.m4a")
            writeAudio(encoded, exact, encoder)
            val decoded = readAudio(encoded)
            rows += "$codec $label encode=$encoder expectedMs=$expectedMs actualMs=${decoded.size / 96.0} ${audioDifference(exact, decoded)}"
        }
    }
}
