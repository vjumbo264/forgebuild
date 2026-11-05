package com.motionsalt.ccstudio.renderer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import com.motionsalt.ccstudio.core.ColorMath
import com.motionsalt.ccstudio.core.FittedTransform
import com.motionsalt.ccstudio.core.TuneState
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

/**
 * Keyflaw Effect Host OpenGL ES 2.0 / 3.0 Renderer.
 *
 * Implements Keyflaw host contract:
 * - Direct execution of Keyflaw shaders from assets/effects/
 * - Framebuffer Object (FBO) ping-pong between effect passes
 * - Half-texel corrected 3D LUT texture sampling (2D strip layout)
 * - Uniform-only display-rate updates during slider drags (30+ fps budget)
 * - Zero CPU readbacks and zero per-frame bitmap allocations in the hot path
 * - Real-time ViewMode compositing (Result, Before, Wipe with flip, Diff with gain, Channels)
 * - Frame time and FPS telemetry for the in-app Diagnostics screen
 */
class KeyflawHostRenderer(private val context: Context) : GLSurfaceView.Renderer {

    enum class ViewMode {
        RESULT,
        WIPE,
        SIDE_BY_SIDE,
        TOP_BOTTOM,
        VS_TARGET,
        DIFFERENCE,
        CHANNELS,
        SCOPES
    }

    // Geometry buffers
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Textures
    private var baseTextureId = 0
    private var targetTextureId = 0
    private var lutTextureId = 0
    private var pendingBaseBitmap: Bitmap? = null
    private var pendingTargetBitmap: Bitmap? = null
    private var pendingLutTransform: FittedTransform? = null
    private var lutSize = 33

    // Framebuffer objects for multi-pass / effect stacking ping-pong
    private val fboIds = IntArray(2)
    private val fboTextureIds = IntArray(2)
    private var fboWidth = 1280
    private var fboHeight = 720

    // Program cache: effectId -> Program
    private val programCache = HashMap<String, Int>()
    private val uniformLocations = HashMap<String, MutableMap<String, Int>>()

    // Built-in programs
    private var lutProgramId = 0
    private var viewModeProgramId = 0

    // Active state (thread-safe updates)
    @Volatile var activeTuneState: TuneState = TuneState.defaultState()
    @Volatile var activeViewMode: ViewMode = ViewMode.RESULT
    @Volatile var isPressingBefore: Boolean = false
    @Volatile var wipeFraction: Float = 0.5f
    @Volatile var isWipeVertical: Boolean = true
    @Volatile var diffGain: Float = 2.0f
    @Volatile var selectedChannel: Int = 0 // 0: all, 1: R, 2: G, 3: B, 4: Luma

    // Dynamic uniform overrides for slider drags
    private val paramOverrides = ConcurrentHashMap<String, ConcurrentHashMap<String, Float>>()
    private val colorOverrides = ConcurrentHashMap<String, ConcurrentHashMap<String, FloatArray>>()

    // Telemetry
    @Volatile var averageFrameTimeMs: Float = 0f
        private set
    @Volatile var currentFps: Float = 60f
        private set
    @Volatile var glRendererString: String = "Initializing"
        private set
    @Volatile var glVersionString: String = "OpenGL ES 2.0"
        private set
    @Volatile var compiledEffectsCount: Int = 0
        private set

    private var frameCount = 0
    private var rollingFrameTimeSum = 0L

    init {
        // Standard full-screen quad: 2 triangles
        val quadVertices = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        val quadTexCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadTexCoords)
        texCoordBuffer.position(0)
    }

    fun setBaseBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        val maxEdge = 1280
        val scale = if (max(bitmap.width, bitmap.height) > maxEdge) {
            maxEdge.toFloat() / max(bitmap.width, bitmap.height)
        } else 1.0f
        val scaled = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        pendingBaseBitmap = scaled
    }

    fun setTargetBitmap(bitmap: Bitmap?) {
        pendingTargetBitmap = bitmap
    }

    fun setLutTransform(transform: FittedTransform?) {
        pendingLutTransform = transform
    }

    fun setEffectParam(effectId: String, paramKey: String, value: Float) {
        val map = paramOverrides.getOrPut(effectId) { ConcurrentHashMap() }
        map[paramKey] = value
    }

    fun setEffectColorParam(effectId: String, paramKey: String, r: Float, g: Float, b: Float) {
        val map = colorOverrides.getOrPut(effectId) { ConcurrentHashMap() }
        map[paramKey] = floatArrayOf(r, g, b)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glRendererString = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
        glVersionString = GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES 2.0"

        GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1.0f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        initBuiltInPrograms()
        preloadEffectShaders()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        setupFbos(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val startTime = SystemClock.elapsedRealtimeNanos()

        handlePendingTextures()

        if (baseTextureId == 0) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        var srcTexture = baseTextureId
        var currentFboIndex = 0
        val stack = activeTuneState.stack

        // Step 1: Render 3D LUT if enabled
        val lutItem = stack.firstOrNull { it.effectId == "ms_lut" && it.enabled }
        if (lutItem != null && lutTextureId != 0) {
            val intensity = paramOverrides["ms_lut"]?.get("intensity")
                ?: (lutItem.params["intensity"] as? Number)?.toFloat()
                ?: 1.0f

            if (intensity > 0.001f) {
                bindFbo(currentFboIndex)
                renderLutPass(srcTexture, intensity)
                srcTexture = fboTextureIds[currentFboIndex]
                currentFboIndex = 1 - currentFboIndex
            }
        }

        // Step 2: Render each enabled effect in the stack
        for (item in stack) {
            if (!item.enabled || item.effectId == "ms_lut") continue
            val programId = programCache[item.effectId] ?: continue

            bindFbo(currentFboIndex)
            renderEffectPass(programId, item.effectId, srcTexture, item.params)
            srcTexture = fboTextureIds[currentFboIndex]
            currentFboIndex = 1 - currentFboIndex
        }

        // Step 3: Composite final result to default framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        renderViewMode(srcTexture)

        val elapsedNanos = SystemClock.elapsedRealtimeNanos() - startTime
        rollingFrameTimeSum += elapsedNanos
        frameCount++
        if (frameCount >= 30) {
            averageFrameTimeMs = (rollingFrameTimeSum.toFloat() / frameCount) / 1_000_000f
            currentFps = if (averageFrameTimeMs > 0.001f) (1000f / averageFrameTimeMs).coerceAtMost(60f) else 60f
            rollingFrameTimeSum = 0L
            frameCount = 0
        }
    }

    private fun handlePendingTextures() {
        pendingBaseBitmap?.let { bmp ->
            baseTextureId = uploadBitmapTexture(bmp, baseTextureId)
            pendingBaseBitmap = null
        }
        pendingTargetBitmap?.let { bmp ->
            targetTextureId = uploadBitmapTexture(bmp, targetTextureId)
            pendingTargetBitmap = null
        }
        pendingLutTransform?.let { transform ->
            lutSize = transform.gridSize
            val strip = ColorMath.generateLut2DStrip(transform, lutSize)
            lutTextureId = uploadStripTexture(strip, lutTextureId)
            pendingLutTransform = null
        }
    }

    private fun setupFbos(width: Int, height: Int) {
        fboWidth = width.coerceAtLeast(1)
        fboHeight = height.coerceAtLeast(1)

        if (fboIds[0] != 0) GLES20.glDeleteFramebuffers(2, fboIds, 0)
        if (fboTextureIds[0] != 0) GLES20.glDeleteTextures(2, fboTextureIds, 0)

        GLES20.glGenFramebuffers(2, fboIds, 0)
        GLES20.glGenTextures(2, fboTextureIds, 0)

        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureIds[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                fboWidth, fboHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTextureIds[i], 0
            )
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun bindFbo(index: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[index])
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun uploadBitmapTexture(bitmap: Bitmap, oldTextureId: Int): Int {
        var texId = oldTextureId
        if (texId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return texId
    }

    private fun uploadStripTexture(strip: com.motionsalt.ccstudio.core.StackRenderer.PixelBuffer, oldTextureId: Int): Int {
        var texId = oldTextureId
        if (texId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]
        }
        val byteBuf = ByteBuffer.allocateDirect(strip.width * strip.height * 4)
            .order(ByteOrder.nativeOrder())
        for (y in 0 until strip.height) {
            for (x in 0 until strip.width) {
                byteBuf.put((strip.getR(x, y) * 255f).toInt().coerceIn(0, 255).toByte())
                byteBuf.put((strip.getG(x, y) * 255f).toInt().coerceIn(0, 255).toByte())
                byteBuf.put((strip.getB(x, y) * 255f).toInt().coerceIn(0, 255).toByte())
                byteBuf.put(255.toByte())
            }
        }
        byteBuf.position(0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            strip.width, strip.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuf
        )
        return texId
    }

    private fun initBuiltInPrograms() {
        val vertexShaderSource = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTex;
            void main() {
                vTex = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        val lutFragmentSource = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture;
            uniform sampler2D uLutStrip;
            uniform float uLutSize;
            uniform float uIntensity;
            varying vec2 vTex;

            vec3 sampleLut(vec3 col, float size) {
                float b = col.b * (size - 1.0);
                float z0 = floor(b);
                float z1 = min(size - 1.0, z0 + 1.0);
                float fz = fract(b);

                float u0 = (z0 * size + col.r * (size - 1.0) + 0.5) / (size * size);
                float u1 = (z1 * size + col.r * (size - 1.0) + 0.5) / (size * size);
                float v = (col.g * (size - 1.0) + 0.5) / size;

                vec3 col0 = texture2D(uLutStrip, vec2(u0, v)).rgb;
                vec3 col1 = texture2D(uLutStrip, vec2(u1, v)).rgb;

                return mix(col0, col1, fz);
            }

            void main() {
                vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
                vec3 rgb = c.rgb / max(c.a, 0.0001);
                vec3 graded = sampleLut(clamp(rgb, 0.0, 1.0), uLutSize);
                vec3 res = mix(rgb, graded, uIntensity);
                gl_FragColor = vec4(res * c.a, c.a);
            }
        """.trimIndent()

        lutProgramId = createProgram(vertexShaderSource, lutFragmentSource)

        val viewModeFragmentSource = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture;
            uniform sampler2D uBefore;
            uniform sampler2D uTarget;
            uniform int uViewMode;
            uniform float uWipeDivider;
            uniform int uWipeVertical;
            uniform float uDiffGain;
            uniform int uSelectedChannel;
            varying vec2 vTex;

            void main() {
                vec4 res = texture2D(uTexture, vTex);
                vec4 before = texture2D(uBefore, vTex);
                vec4 target = texture2D(uTarget, vTex);

                if (uViewMode == 0) {
                    gl_FragColor = res;
                } else if (uViewMode == 1) {
                    float coord = (uWipeVertical == 1) ? vTex.x : vTex.y;
                    if (coord < uWipeDivider) {
                        gl_FragColor = before;
                    } else if (abs(coord - uWipeDivider) < 0.002) {
                        gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    } else {
                        gl_FragColor = res;
                    }
                } else if (uViewMode == 2) {
                    if (vTex.x < 0.5) {
                        gl_FragColor = texture2D(uBefore, vec2(vTex.x * 2.0, vTex.y));
                    } else {
                        gl_FragColor = texture2D(uTexture, vec2((vTex.x - 0.5) * 2.0, vTex.y));
                    }
                } else if (uViewMode == 3) {
                    if (vTex.y < 0.5) {
                        gl_FragColor = texture2D(uBefore, vec2(vTex.x, vTex.y * 2.0));
                    } else {
                        gl_FragColor = texture2D(uTexture, vec2(vTex.x, (vTex.y - 0.5) * 2.0));
                    }
                } else if (uViewMode == 4) {
                    if (vTex.x < 0.5) {
                        gl_FragColor = res;
                    } else if (abs(vTex.x - 0.5) < 0.002) {
                        gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    } else {
                        gl_FragColor = target;
                    }
                } else if (uViewMode == 5) {
                    vec3 diff = abs(res.rgb - before.rgb) * uDiffGain;
                    gl_FragColor = vec4(diff, 1.0);
                } else if (uViewMode == 6) {
                    float r = res.r; float g = res.g; float b = res.b;
                    float luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    if (uSelectedChannel == 1) gl_FragColor = vec4(r, 0.0, 0.0, 1.0);
                    else if (uSelectedChannel == 2) gl_FragColor = vec4(0.0, g, 0.0, 1.0);
                    else if (uSelectedChannel == 3) gl_FragColor = vec4(0.0, 0.0, b, 1.0);
                    else if (uSelectedChannel == 4) gl_FragColor = vec4(vec3(luma), 1.0);
                    else gl_FragColor = res;
                } else {
                    gl_FragColor = res;
                }
            }
        """.trimIndent()

        viewModeProgramId = createProgram(vertexShaderSource, viewModeFragmentSource)
    }

    private fun preloadEffectShaders() {
        val effectDirs = listOf(
            "ms_brightness_contrast", "ms_exposure_gamma", "ms_highlights_shadows",
            "ms_white_balance", "ms_hue_saturation", "ms_color_balance",
            "ms_selective_color", "ms_channel_mixer", "ms_levels_curves",
            "ms_tritone", "ms_duotone", "ms_gradient_map", "ms_split_toning",
            "ms_sharpen", "ms_unsharp_mask", "ms_clarity",
            "ms_glow", "ms_bloom_halation", "ms_haze_flare", "ms_vignette", "ms_grain"
        )
        val vSource = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTex;
            void main() {
                vTex = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        for (effId in effectDirs) {
            try {
                val assetPath = "effects/$effId/shader.glsl"
                context.assets.open(assetPath).use { stream ->
                    val fSource = InputStreamReader(stream).readText()
                    val prog = createProgram(vSource, fSource)
                    if (prog != 0) {
                        programCache[effId] = prog
                        compiledEffectsCount++
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun renderLutPass(inputTextureId: Int, intensity: Float) {
        GLES20.glUseProgram(lutProgramId)
        bindQuadAttributes(lutProgramId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(lutProgramId, "uTexture"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(lutProgramId, "uLutStrip"), 1)

        GLES20.glUniform1f(GLES20.glGetUniformLocation(lutProgramId, "uLutSize"), lutSize.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(lutProgramId, "uIntensity"), intensity)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderEffectPass(programId: Int, effectId: String, inputTextureId: Int, baseParams: Map<String, Any>) {
        GLES20.glUseProgram(programId)
        bindQuadAttributes(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uTexture"), 0)

        val texelLoc = GLES20.glGetUniformLocation(programId, "uTexelSize")
        if (texelLoc != -1) {
            GLES20.glUniform2f(texelLoc, 1.0f / fboWidth, 1.0f / fboHeight)
        }

        val timeLoc = GLES20.glGetUniformLocation(programId, "uTime")
        if (timeLoc != -1) {
            val time = (SystemClock.elapsedRealtime() % 100000L) / 1000.0f
            GLES20.glUniform1f(timeLoc, time)
        }

        val overrides = paramOverrides[effectId]
        val cOverrides = colorOverrides[effectId]

        for ((key, value) in baseParams) {
            val uniformName = "u" + key.replaceFirstChar { it.uppercase() }
            val loc = GLES20.glGetUniformLocation(programId, uniformName)
            if (loc == -1) continue

            val cVal = cOverrides?.get(key)
            if (cVal != null) {
                GLES20.glUniform3f(loc, cVal[0], cVal[1], cVal[2])
                continue
            }

            val floatVal = overrides?.get(key) ?: (value as? Number)?.toFloat()
            if (floatVal != null) {
                GLES20.glUniform1f(loc, floatVal)
            } else if (value is String && value.startsWith("#")) {
                val rgb = parseHex(value)
                GLES20.glUniform3f(loc, rgb[0], rgb[1], rgb[2])
            }
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderViewMode(resultTextureId: Int) {
        GLES20.glUseProgram(viewModeProgramId)
        bindQuadAttributes(viewModeProgramId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (isPressingBefore) baseTextureId else resultTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uTexture"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uBefore"), 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (targetTextureId != 0) targetTextureId else baseTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uTarget"), 2)

        val vmOrdinal = if (isPressingBefore) 0 else activeViewMode.ordinal
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uViewMode"), vmOrdinal)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(viewModeProgramId, "uWipeDivider"), wipeFraction)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uWipeVertical"), if (isWipeVertical) 1 else 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(viewModeProgramId, "uDiffGain"), diffGain)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(viewModeProgramId, "uSelectedChannel"), selectedChannel)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun bindQuadAttributes(programId: Int) {
        val posLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        if (posLoc != -1) {
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        }
        val texLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        if (texLoc != -1) {
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vShader == 0) return 0
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fShader == 0) {
            GLES20.glDeleteShader(vShader)
            return 0
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun parseHex(hex: String): FloatArray {
        val clean = hex.removePrefix("#")
        val num = clean.toLongOrNull(16) ?: 0L
        val r = ((num shr 16) and 0xFF) / 255.0f
        val g = ((num shr 8) and 0xFF) / 255.0f
        val b = (num and 0xFF) / 255.0f
        return floatArrayOf(r, g, b)
    }
}
