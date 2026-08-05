package com.woka.feature.viewer.video

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect

class HdrImagePainter(private val bitmap: ImageBitmap) : Painter() {

    private val effect by lazy {
        RuntimeEffect.makeForShader("""
            uniform vec2 scale;
            uniform shader image;
            const float m1 = 0.1593017578125, m2 = 78.84375;
            const float c1 = 0.8359375, c2 = 18.8515625, c3 = 18.6875;
            vec3 pqEotf(vec3 e) {                    
                vec3 p = pow(e, vec3(1.0/m2));
                vec3 num = max(p - c1, 0.0);
                vec3 den = c2 - c3 * p;
                return pow(num / den, vec3(1.0/m1));
            }
            const mat3 bt2020to709 = mat3(
                 1.6605, -0.1246, -0.0182,
                -0.5876,  1.1329, -0.1006,
                -0.0728, -0.0083,  1.1187);
            vec3 hable(vec3 x){                      
                const float A=0.15,B=0.50,C=0.10,D=0.20,E=0.02,F=0.30;
                return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
            }

            half4 main(float2 coord){
                vec3 e = image.eval(coord * scale).rgb;
                
                vec3 lin = pqEotf(e);                
                lin *= (10000.0/100.0);             
                lin = bt2020to709 * lin;             
                const float W = 11.2;
                vec3 mapped = hable(lin) / hable(vec3(W));
                vec3 srgb = pow(clamp(mapped, 0.0, 1.0), vec3(1.0/2.2)); 
                
                return half4(srgb, 1.0);
            }
        """)
    }

    override val intrinsicSize: Size
        get() = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    override fun DrawScope.onDraw() {
        val scaleX = intrinsicSize.width / size.width
        val scaleY = intrinsicSize.height / size.height
        
        val dataBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(scaleX).putFloat(scaleY).array()
            
        val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val skiaShader = skiaImage.makeShader()
        
        val shader = effect.makeShader(Data.makeFromBytes(dataBytes), arrayOf(skiaShader), null)
        val shaderBrush = ShaderBrush(shader.asComposeShader())
        
        drawRect(
            brush = shaderBrush,
            size = size
        )
    }
}
