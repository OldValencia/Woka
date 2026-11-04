package com.woka.feature.editor.tools.sound

import com.woka.core.data.MediaKind
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.image.SoundMix
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.ToolStage

/**
 * `Audio` — mute, volume and two fades (ME-240, `audio.md` §3.2). **A manifest and four
 * specs, and no UI code at all**: the acceptance test the design set for the tool
 * architecture. Under All Clips it is the whole film's; under This Clip, one clip's.
 *
 * Its stage is a [SoundStage], not a raster one, so the picture's fold drops it and the
 * readers — the editor's band, the viewer, the render — read it through [mixIn].
 */
object SoundTool : EditorTool {

    val Mute = BoolParam("mute", "Mute")
    val Volume = FloatParam("volume", "Volume", 0f..200f, default = 100f, unit = "%")
    val FadeIn = FloatParam("fadeIn", "Fade in", 0f..10f, default = 0f, unit = "s", step = 0.1f)
    val FadeOut = FloatParam("fadeOut", "Fade out", 0f..10f, default = 0f, unit = "s", step = 0.1f)

    override val manifest = ToolManifest(
        id = "sound",
        title = "Audio",
        summary = "Mute, volume and fades",
        icon = Icons.Static.SpeakerWave2Fill,
        domain = ToolDomain.Sound,
        order = 0,
        supports = setOf(MediaKind.Video),
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Continuous
    )

    override val params: List<ParamSpec<*>> = listOf(Mute, Volume, FadeIn, FadeOut)

    override fun stage(params: ParamSet): ToolStage? = mixOf(params)?.let { SoundStage(it) }

    /** The sound the last of [ops] sets, or null when none of them touches it. */
    fun mixIn(ops: List<ToolOp>): SoundMix? =
        ops.lastOrNull { it.toolId == manifest.id }?.let { mixOf(it.params) }

    private fun mixOf(params: ParamSet): SoundMix? {
        val mix = SoundMix(
            mute = params[Mute],
            volume = params[Volume] / 100f,
            fadeInMs = (params[FadeIn] * 1000f).toLong(),
            fadeOutMs = (params[FadeOut] * 1000f).toLong()
        )
        return mix.takeUnless { it == SoundMix() }
    }
}

/** What the sound tool folds to: the mix, for the readers that play or render sound. */
data class SoundStage(val mix: SoundMix) : ToolStage
