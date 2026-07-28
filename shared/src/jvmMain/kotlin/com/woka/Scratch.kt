package com.woka

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA64LE
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.nio.ByteBuffer

fun main() {
    val file = File("D:\\Movies\\Avatar.The.Last.Airbender.S02.2160p.NF.WEB-DL.DV.HDR.H.265\\Avatar.The.Last.Airbender.S02E01.2160p.NF.WEB-DL.DV.HDR.H.265.RGzsRutracker.mkv")
    
    val grabber = FFmpegFrameGrabber(file)
    grabber.setVideoOption("hwaccel", "auto")
    grabber.pixelFormat = AV_PIX_FMT_RGBA64LE
    grabber.start()
    
    val frame = grabber.grabImage()
    if (frame != null) {
        println("Width: " + frame.imageWidth)
        println("Height: " + frame.imageHeight)
        println("Channels: " + frame.imageChannels)
        println("Depth: " + frame.imageDepth)
        println("Stride: " + frame.imageStride)
        val buf = frame.image[0]
        println("Buffer type: " + buf.javaClass.name)
        println("Buffer capacity: " + buf.capacity())
    }
    
    grabber.stop()
    grabber.release()
}
