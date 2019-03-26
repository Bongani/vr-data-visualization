package ing.wbaa.vrdatavisualization

import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import com.google.vr.sdk.base.*
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import org.jetbrains.anko.*
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class MainActivity : GvrActivity(), GvrView.StereoRenderer {
    private val TAG = "HelloVrActivity"

    private val COORDS_PER_VERTEX = 3
    private val triCoords = floatArrayOf(
        0.0f,  0.6f, -1.0f, // top
        -0.5f, -0.3f, -1.0f, // bottom left
        0.5f, -0.3f, -1.0f  // bottom right
    )
    private val triVertexCount = triCoords.size / COORDS_PER_VERTEX
    private var triVerticesBuffer: FloatBuffer? = null

    private var simpleVertexShader: Int = 0
    private var simpleFragmentShader: Int = 0
    private var triProgram: Int = 0
    private var triColorParam: Int = 0
    private var triPositionParam: Int = 0
    private var triMVPMatrixParam: Int = 0

    private val Z_NEAR = 0.01f
    private val Z_FAR = 10.0f

    private var camera: FloatArray = floatArrayOf()
    private var view: FloatArray = floatArrayOf()
    private var headView: FloatArray = floatArrayOf()
    private var modelTarget: FloatArray = floatArrayOf(0f,0f,0f,0f)
    private var modelView: FloatArray = floatArrayOf(0f,0f,0f,0f)
    private var modelViewProjection: FloatArray = floatArrayOf()

    private var headRotation: FloatArray = floatArrayOf(0f,0f,0f,0f)

    private var cols = ArrayList<FloatArray>()

    private var points = ArrayList<FloatArray>()
    private var labels = ArrayList<Int>()

    private var curLabel: Int = 0
    private var currentPosition: FloatArray = floatArrayOf()

    private var cameraPos: Float = 0.0f

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeGvrView()
        readData()

        camera = FloatArray(16)
        view = FloatArray(16)
        headRotation = FloatArray(4)
        headView = FloatArray(16)
        modelTarget = FloatArray(16)
        modelView = FloatArray(16)
        modelViewProjection = FloatArray(16)

        cols.add(floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f))
        cols.add(floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f))
        cols.add(floatArrayOf(0.0f, 0.0f, 1.0f, 0.0f))
        cols.add(floatArrayOf(0.5f, 0.5f, 0.5f, 0.0f))
        cols.add(floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f))
        cols.add(floatArrayOf(0.5f, 0.0f, 0.5f, 0.0f))
        cols.add(floatArrayOf(0.0f, 0.5f, 0.5f, 0.0f))
        cols.add(floatArrayOf(0.0f, 0.0f, 0.5f, 0.0f))
        cols.add(floatArrayOf(0.0f, 0.5f, 0.0f, 0.0f))
        cols.add(floatArrayOf(0.5f, 0.0f, 0.0f, 0.0f))
    }

    fun initializeGvrView() {
        setContentView(R.layout.common_ui)

        val gvrView = findViewById(R.id.gvr_view) as GvrView
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8)

        gvrView.setRenderer(this)
        gvrView.setTransitionViewEnabled(true)

        // Enable Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
        // Daydream controller input for basic interactions using the existing Cardboard trigger API.
        gvrView.enableCardboardTriggerEmulation()

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true)
        }

        setGvrView(gvrView)
    }

    private fun readData() {
        doAsync {
            val file = URL("https://topwebers.com/mnist.csv").readText()
            uiThread {
                val lines = file.split("\n").toTypedArray()
                var i = 0
                for(line in lines) {
                    if(line!="" && i%5==0) {
                        val nums = line.split(",").toTypedArray()
                        points.add(floatArrayOf(nums[0].toFloat()/2,
                            nums[1].toFloat()/2,
                            nums[2].toFloat()/2))
                        labels.add(nums[3].toFloat().toInt())
                    }
                    i += 1
                    Log.i(TAG, i.toString())
                }
            }
        }
    }

    override fun onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown")
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged")
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     *
     *
     * OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    override fun onSurfaceCreated(config: EGLConfig) {
        Log.i(TAG, "onSurfaceCreated")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        compileShaders()
        prepareRenderingTriangle()
    }

    private fun compileShaders() {
        var shader = "uniform mat4 u_MVP;\n" +
                "attribute vec4 a_Position;\n" +
                "void main() {\n" +
                "   gl_Position = u_MVP * a_Position;\n" +
                "}\n"
        simpleVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, shader)

        shader = "precision mediump float;\n" +
                "uniform vec4 u_Color;\n" +
                "void main() {\n" +
                "    gl_FragColor = u_Color;\n" +
                "}"
        simpleFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, shader)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)

        return shader
    }

    private fun prepareRenderingTriangle() {
        // Allocate buffers
        // initialize vertex byte buffer for shape coordinates (4 */ bytes per float)
        val bb = ByteBuffer.allocateDirect(triCoords.size * 4)
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder())

        // create a floating point buffer from the ByteBuffer
        triVerticesBuffer = bb.asFloatBuffer()
        // add the coordinates to the FloatBuffer
        triVerticesBuffer?.put(triCoords)
        // set the buffer to read the first coordinate
        triVerticesBuffer?.position(0)

        // Create GL program
        // create empty OpenGL ES Program
        triProgram = GLES20.glCreateProgram()
        // add the vertex shader to program
        GLES20.glAttachShader(triProgram, simpleVertexShader)
        // add the fragment shader to program
        GLES20.glAttachShader(triProgram, simpleFragmentShader)
        // build OpenGL ES program executable
        GLES20.glLinkProgram(triProgram)
        // set program as current
        GLES20.glUseProgram(triProgram)

        // Get shader params
        // get handle to vertex shader's a_Position member
        triPositionParam = GLES20.glGetAttribLocation(triProgram, "a_Position")
        // enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(triPositionParam)
        // get handle to fragment shader's u_Color member
        triColorParam = GLES20.glGetUniformLocation(triProgram, "u_Color")
        // get handle to shape's transformation matrix
        triMVPMatrixParam = GLES20.glGetUniformLocation(triProgram, "u_MVP")
    }

    /** Updates the target object position.  */
    private fun updateTargetPosition() {
        Matrix.setIdentityM(modelTarget, 0)
        Matrix.translateM(modelTarget, 0, currentPosition!![0], currentPosition!![1], currentPosition!![2])

        Util.checkGlError("updateTargetPosition")
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    override fun onNewFrame(headTransform: HeadTransform) {
        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera, 0, cameraPos, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f)

        headTransform.getHeadView(headView!!, 0)

        // Update the 3d audio engine with the most recent head rotation.
        headTransform.getQuaternion(headRotation!!, 0)

        Util.checkGlError("onNewFrame")
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    override fun onDrawEye(eye: Eye) {
//        val a = floatArrayOf(1.0f,0.0f,0.0f,0.0f)
//        Matrix.rotateM(a, 0,
//            headRotation[0], headRotation[1],
//            headRotation[2], headRotation[3])

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // The clear color doesn't matter here because it's completely obscured by
        // the room. However, the color buffer is still cleared because it may
        // improve performance.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.eyeView, 0, camera, 0)

        // Build the ModelView and ModelViewProjection matrices
        // for calculating the position of the target object.
        val perspective = eye.getPerspective(Z_NEAR, Z_FAR)

        val it = points.iterator()
        val it_label = labels.iterator()
        while (it.hasNext()) {
            currentPosition = it.next()
            curLabel = it_label.next()
            updateTargetPosition()
            Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, 0)
            Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0)
            drawTriangle()
        }
    }

    private fun drawTriangle() {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(triProgram)

        // Pass the MVP transformation to the shader
        GLES20.glUniformMatrix4fv(triMVPMatrixParam, 1, false, modelViewProjection, 0)

        // Prepare the coordinate data
        GLES20.glVertexAttribPointer(
            triPositionParam, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, 0, triVerticesBuffer
        )

        // Set color for drawing
        val col = cols.get(curLabel)
        GLES20.glUniform4fv(triColorParam, 1, col, 0)

        // Draw the model
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triVertexCount)
    }

    override fun onFinishFrame(viewport: Viewport) {}

    /**
     * Called when the Cardboard trigger is pulled.
     */
    override fun onCardboardTrigger() {
        cameraPos += 1
        Log.i("hoi", "onCardboardTrigger")
    }
}