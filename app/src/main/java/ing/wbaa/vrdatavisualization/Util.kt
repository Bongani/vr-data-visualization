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

import android.opengl.GLU.gluErrorString

import android.opengl.GLES20
import android.opengl.Matrix
import android.text.TextUtils
import android.util.Log

/** Utility functions.  */
/* package */ internal object Util {
    private val TAG = "Util"

    /** Debug builds should fail quickly. Release versions of the app should have this disabled.  */
    private val HALT_ON_GL_ERROR = true

    /**
     * Checks GLES20.glGetError and fails quickly if the state isn't GL_NO_ERROR.
     *
     * @param label Label to report in case of error.
     */
    fun checkGlError(label: String) {
        var error = GLES20.glGetError()
        var lastError: Int
        if (error != GLES20.GL_NO_ERROR) {
            do {
                lastError = error
                Log.e(TAG, label + ": glError " + gluErrorString(lastError))
                error = GLES20.glGetError()
            } while (error != GLES20.GL_NO_ERROR)

            if (HALT_ON_GL_ERROR) {
                throw RuntimeException("glError " + gluErrorString(lastError))
            }
        }
    }

    /**
     * Builds a GL shader program from vertex & fragment shader code. The vertex and fragment shaders
     * are passed as arrays of strings in order to make debugging compilation issues easier.
     *
     * @param vertexCode GLES20 vertex shader program.
     * @param fragmentCode GLES20 fragment shader program.
     * @return GLES20 program id.
     */
    fun compileProgram(vertexCode: Array<String>, fragmentCode: Array<String>): Int {
        checkGlError("Start of compileProgram")
        // prepare shaders and OpenGL program
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertexShader, TextUtils.join("\n", vertexCode))
        GLES20.glCompileShader(vertexShader)
        checkGlError("Compile vertex shader")

        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragmentShader, TextUtils.join("\n", fragmentCode))
        GLES20.glCompileShader(fragmentShader)
        checkGlError("Compile fragment shader")

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        // Link and check for errors.
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val errorMsg = "Unable to link shader program: \n" + GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, errorMsg)
            if (HALT_ON_GL_ERROR) {
                throw RuntimeException(errorMsg)
            }
        }
        checkGlError("End of compileProgram")

        return program
    }

    /**
     * Computes the angle between two vectors; see
     * https://en.wikipedia.org/wiki/Vector_projection#Definitions_in_terms_of_a_and_b.
     */
    fun angleBetweenVectors(vec1: FloatArray, vec2: FloatArray): Float {
        val cosOfAngle = dotProduct(vec1, vec2) / (vectorNorm(vec1) * vectorNorm(vec2))
        return Math.acos(Math.max(-1.0f, Math.min(1.0f, cosOfAngle)).toDouble()).toFloat()
    }

    private fun dotProduct(vec1: FloatArray, vec2: FloatArray): Float {
        return vec1[0] * vec2[0] + vec1[1] * vec2[1] + vec1[2] * vec2[2]
    }

    private fun vectorNorm(vec: FloatArray): Float {
        return Matrix.length(vec[0], vec[1], vec[2])
    }
}
/** Class only contains static methods.  */
