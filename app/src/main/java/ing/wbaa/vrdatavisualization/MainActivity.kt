package ing.wbaa.vrdatavisualization

import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import com.google.vr.sdk.base.*
import java.io.IOException
import java.util.*
import javax.microedition.khronos.egl.EGLConfig

class MainActivity : GvrActivity(), GvrView.StereoRenderer {
    private val TAG = "HelloVrActivity"

    private val Z_NEAR = 0.01f
    private val Z_FAR = 10.0f

    private val MIN_TARGET_DISTANCE = 3.0f
    private val MAX_TARGET_DISTANCE = 3.5f

    private val OBJECT_VERTEX_SHADER_CODE = arrayOf(
        "uniform mat4 u_MVP;",
        "attribute vec4 a_Position;",
        "attribute vec2 a_UV;",
        "varying vec2 v_UV;",
        "",
        "void main() {",
        "  v_UV = a_UV;",
        "  gl_Position = u_MVP * a_Position;",
        "}"
    )
    private val OBJECT_FRAGMENT_SHADER_CODE = arrayOf(
        "precision mediump float;",
        "varying vec2 v_UV;",
        "uniform sampler2D u_Texture;",
        "",
        "void main() {",
        "  // The y coordinate of this sample's textures is reversed compared to",
        "  // what OpenGL expects, so we invert the y coordinate.",
        "  gl_FragColor = texture2D(u_Texture, vec2(v_UV.x, 1.0 - v_UV.y));",
        "}"
    )

    private var objectProgram: Int = 0

    private var objectPositionParam: Int = 0
    private var objectUvParam: Int = 0
    private var objectModelViewProjectionParam: Int = 0

    private var targetDistance = MAX_TARGET_DISTANCE

    private var targetObjectMeshes: TexturedMesh? = null
    private var targetObjectNotSelectedTextures: Texture? = null
    private var targetObjectSelectedTextures: Texture? = null
    private var curTargetObject: Int = 0

    private var random: Random? = null

    private var targetPosition: FloatArray = floatArrayOf()
    private var camera: FloatArray = floatArrayOf()
    private var view: FloatArray = floatArrayOf()
    private var headView: FloatArray = floatArrayOf()
    private var modelViewProjection: FloatArray = floatArrayOf()
    private var modelView: FloatArray = floatArrayOf(0f,0f,0f,0f)

    private var modelTarget: FloatArray = floatArrayOf(0f,0f,0f,0f)

    private var tempPosition: FloatArray = floatArrayOf(0f,0f,0f,0f)
    private var headRotation: FloatArray = floatArrayOf(0f,0f,0f,0f)

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeGvrView()

        camera = FloatArray(16)
        view = FloatArray(16)
        modelViewProjection = FloatArray(16)
        modelView = FloatArray(16)
        // Target object first appears directly in front of user.
        targetPosition = floatArrayOf(0.0f, 0.0f, -MIN_TARGET_DISTANCE)
        tempPosition = FloatArray(4)
        headRotation = FloatArray(4)
        modelTarget = FloatArray(16)
        headView = FloatArray(16)

        random = Random()
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

        objectProgram = Util.compileProgram(OBJECT_VERTEX_SHADER_CODE, OBJECT_FRAGMENT_SHADER_CODE)

        objectPositionParam = GLES20.glGetAttribLocation(objectProgram, "a_Position")
        objectUvParam = GLES20.glGetAttribLocation(objectProgram, "a_UV")
        objectModelViewProjectionParam = GLES20.glGetUniformLocation(objectProgram, "u_MVP")

        Util.checkGlError("Object program params")

        updateTargetPosition()

        Util.checkGlError("onSurfaceCreated")

        try {
            targetObjectNotSelectedTextures = Texture(this, "QuadSphere_Blue_BakedDiffuse.png")
            targetObjectMeshes = TexturedMesh(this, "QuadSphere.obj", objectPositionParam, objectUvParam)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to initialize objects", e)
        }
    }

    /** Updates the target object position.  */
    private fun updateTargetPosition() {
        Matrix.setIdentityM(modelTarget, 0)
        Matrix.translateM(modelTarget, 0, targetPosition!![0], targetPosition!![1], targetPosition!![2])

        Util.checkGlError("updateTargetPosition")
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    override fun onNewFrame(headTransform: HeadTransform) {
        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f)

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

        val points = arrayOf(
            floatArrayOf(0.0f, 0.0f, -5.0f),
            floatArrayOf(0.0f, 5.0f, 0.0f),
            floatArrayOf(5.0f, 0.0f, 0.0f),
            floatArrayOf(-5.0f, 0.0f, 0.0f),
            floatArrayOf(5.0f, -5.0f, 0.0f),
            floatArrayOf(0.0f, 0.0f, 5.0f)
        )

        val it = points.iterator()

        while (it.hasNext()) {
            targetPosition = it.next()
            updateTargetPosition()
            Matrix.multiplyMM(modelView, 0, view, 0, modelTarget, 0)
            Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0)
            drawTarget()
        }
    }

    override fun onFinishFrame(viewport: Viewport) {}

    /** Draw the target object.  */
    fun drawTarget() {
        GLES20.glUseProgram(objectProgram)
        GLES20.glUniformMatrix4fv(objectModelViewProjectionParam, 1, false, modelViewProjection, 0)
        targetObjectNotSelectedTextures!!.bind()
        targetObjectMeshes!!.draw()
        Util.checkGlError("drawTarget")
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    override fun onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger")
    }
}