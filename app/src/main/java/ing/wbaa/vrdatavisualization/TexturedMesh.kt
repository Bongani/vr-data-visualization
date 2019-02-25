/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ing.wbaa.vrdatavisualization

import android.content.Context
import android.opengl.GLES20
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** Renders an object loaded from an OBJ file.  */
internal class TexturedMesh
/**
 * Initializes the mesh from an .obj file.
 *
 * @param context Context for loading the .obj file.
 * @param objFilePath Path to the .obj file.
 * @param positionAttrib The position attribute in the shader.
 * @param uvAttrib The UV attribute in the shader.
 */
@Throws(IOException::class)
constructor(context: Context, objFilePath: String, private val positionAttrib: Int, private val uvAttrib: Int) {

    private val vertices: FloatBuffer
    private val uv: FloatBuffer
    private val indices: ShortBuffer

    init {
        val objInputStream = context.assets.open(objFilePath)
        val obj = ObjUtils.convertToRenderable(ObjReader.read(objInputStream))
        objInputStream.close()

        val intIndices = ObjData.getFaceVertexIndices(obj, 3)
        vertices = ObjData.getVertices(obj)
        uv = ObjData.getTexCoords(obj, 2)

        // Convert int indices to shorts (GLES doesn't support int indices)
        indices = ByteBuffer.allocateDirect(2 * intIndices.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        while (intIndices.hasRemaining()) {
            indices.put(intIndices.get().toShort())
        }
        indices.rewind()
    }

    /**
     * Draws the mesh. Before this is called, u_MVP should be set with glUniformMatrix4fv(), and a
     * texture should be bound to GL_TEXTURE0.
     */
    fun draw() {
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(uvAttrib)
        GLES20.glVertexAttribPointer(uvAttrib, 2, GLES20.GL_FLOAT, false, 0, uv)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.limit(), GLES20.GL_UNSIGNED_SHORT, indices)
    }

    companion object {
        private val TAG = "TexturedMesh"
    }
}
