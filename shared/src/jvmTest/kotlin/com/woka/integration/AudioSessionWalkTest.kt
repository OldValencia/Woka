package com.woka.integration

import androidx.compose.runtime.snapshots.Snapshot
import com.woka.app.AppDependencies
import com.woka.core.data.MediaItem
import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.audio.AudioSession
import com.woka.support.awaitTrue
import com.woka.support.startKoinOnce
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the app's Compose runtime does on every frame: hands state writes to the
 * observers that `snapshotFlow` registers. With no window in a test, nothing else would.
 */
private fun pumped(condition: () -> Boolean): Boolean {
    Snapshot.sendApplyNotifications()
    return condition()
}

/**
 * The session walks the folder with no viewer mounted (AP-003): a track that ends
 * starts the next one, which takes the Now Playing slot over.
 */
class AudioSessionWalkTest {

    @BeforeTest
    fun before() {
        startKoinOnce()
        AppDependencies.settingsRepository.setRepeatMode(RepeatMode.Off)
        AppDependencies.settingsRepository.setShuffle(false)
    }

    private fun tone(name: String) = MediaItem.Audio(File(javaClass.getResource("/media/audio/$name")!!.toURI()))

    @Test
    fun `a track that ends starts the next one with no viewer`() {
        val first = tone("tone.mp3")
        val second = tone("tone.flac")
        val player = AudioSession.createOrGet(first, 0f, listOf(first, second))
        try {
            awaitTrue("the first track to end", 6_000L) { player.reachedEnd || AudioSession.current !== player }
            awaitTrue("the next track", 3_000L) { pumped { AudioSession.current?.file == second.file } }
            assertEquals(second.file.absolutePath, AppDependencies.nowPlaying.source?.path)
            awaitTrue("the last track to end", 6_000L) { pumped { AudioSession.current?.reachedEnd == true } }
            assertEquals(second.file, AudioSession.current?.file)
        } finally {
            AudioSession.current?.let(AudioSession::stop)
            AudioSession.stop(player)
        }
    }
}
