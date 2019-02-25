package ing.wbaa.vrdatavisualization

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import java.io.IOException

/** A texture, meant for use with TexturedMesh.  */
internal class Texture
/**
 * Initializes the texture.
 *
 * @param context Context for loading the texture file.
 * @param texturePath Path to the image to use for the texture.
 */
@Throws(IOException::class)
constructor(context: Context, texturePath: String) {
    private val textureId = IntArray(1)

    init {
        GLES20.glGenTextures(1, textureId, 0)
        bind()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_NEAREST
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val textureBitmap = BitmapFactory.decodeStream(context.assets.open(texturePath))
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
        textureBitmap.recycle()
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }

    /** Binds the texture to GL_TEXTURE0.  */
    fun bind() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
    }
}
