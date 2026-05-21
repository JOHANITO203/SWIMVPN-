package com.swimvpn.app.ui.orb3d

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val ORB_VERTEX_STRIDE_FLOATS = 8
private const val ORB_VERTEX_STRIDE_BYTES = ORB_VERTEX_STRIDE_FLOATS * 4
private const val ORB_WIRE_STRIDE_FLOATS = 4
private const val ORB_WIRE_STRIDE_BYTES = ORB_WIRE_STRIDE_FLOATS * 4
private const val ORB_TWO_PI = (PI * 2.0).toFloat()

internal class Orb3DRenderer : GLSurfaceView.Renderer {
    @Volatile private var state: SwimParticleOrbState = SwimParticleOrbState.CONNECTED
    @Volatile private var reducedMotion: Boolean = false
    @Volatile private var quality: OrbRenderQuality = OrbRenderQuality.Auto

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private val lineVbo = IntArray(1)
    private val pointVbo = IntArray(1)
    private val particleSeeds = buildParticleSeeds(OrbRenderQuality.High.counts.particleCount)
    private val skinQuad = allocateFloatBuffer(8).apply {
        put(-1f).put(-1f)
        put(1f).put(-1f)
        put(-1f).put(1f)
        put(1f).put(1f)
        position(0)
    }

    private var shader: Orb3DShaderProgram? = null
    private var skinShader: OrbSkinShaderProgram? = null
    private var wireShader: OrbWireShaderProgram? = null
    private var buffer: FloatBuffer = allocateFloatBuffer(96_000)
    private var wireBuffer: FloatBuffer = allocateFloatBuffer(96_000)
    private var startNanos: Long = System.nanoTime()
    private var width: Int = 1
    private var height: Int = 1
    private var frameIndex: Int = 0
    private var cachedLineVertexCount: Int = 0
    private var lineBufferDirty: Boolean = true
    private var lastLineState: SwimParticleOrbState = state
    private var lastLineReducedMotion: Boolean = reducedMotion
    private var lastLineQuality: OrbRenderQuality = quality
    private var previousState: SwimParticleOrbState = state
    private var stateTransition: OrbFerrofluidTransitionProfile = state.ferrofluidTransitionFrom(state)
    private var stateTransitionStartNanos: Long = startNanos
    @Volatile private var touchX: Float = 0.5f
    @Volatile private var touchY: Float = 0.5f
    @Volatile private var touchDown: Boolean = false
    @Volatile private var lastTouchNanos: Long = 0L
    @Volatile private var impactTouchWave: OrbTouchWaveProfile? = null

    fun update(
        state: SwimParticleOrbState,
        reducedMotion: Boolean,
        quality: OrbRenderQuality,
    ) {
        if (this.state != state || this.reducedMotion != reducedMotion || this.quality != quality) {
            lineBufferDirty = true
        }
        if (this.state != state) {
            previousState = this.state
            stateTransition = state.ferrofluidTransitionFrom(previousState)
            stateTransitionStartNanos = System.nanoTime()
        }
        this.state = state
        this.reducedMotion = reducedMotion
        this.quality = quality
    }

    fun updateTouch(x: Float, y: Float, isDown: Boolean) {
        touchX = x.coerceIn(0f, 1f)
        touchY = y.coerceIn(0f, 1f)
        touchDown = isDown
        if (isDown) {
            lastTouchNanos = System.nanoTime()
            impactTouchWave = state.orbTouchWaveProfile()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startNanos = System.nanoTime()
        shader?.release()
        skinShader?.release()
        wireShader?.release()
        shader = Orb3DShaderProgram().also { it.create() }
        skinShader = OrbSkinShaderProgram().also { it.create() }
        wireShader = OrbWireShaderProgram().also { it.create() }

        GLES30.glGenBuffers(1, lineVbo, 0)
        GLES30.glGenBuffers(1, pointVbo, 0)
        cachedLineVertexCount = 0
        lineBufferDirty = true
        frameIndex = 0
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
        val aspect = this.width / this.height.toFloat()
        Matrix.perspectiveM(projection, 0, 38f, aspect, 0.1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.55f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val activeReducedMotion = reducedMotion
        val config = state.config(activeReducedMotion)
        val counts = quality.resolve(activeReducedMotion)
        val nowNanos = System.nanoTime()
        val time = (nowNanos - startNanos) / 1_000_000_000f
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val touchStrength = currentTouchStrength(time)
        updateMvp()

        GLES30.glLineWidth(config.lineWidth)
        drawGpuWire(wireShader, counts, config, time, activeReducedMotion, touchStrength)

        // Home integration keeps the orb transparent: no opaque skin plate behind the power button.
        // Particles and the second glass/diffusion overlay remain disabled until visual QA validates the base mesh.
        frameIndex += 1
    }

    private fun updateMvp() {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
    }

    private fun drawGpuWire(
        activeWireShader: OrbWireShaderProgram?,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
        activeReducedMotion: Boolean,
        touchStrength: Float,
    ) {
        val shader = activeWireShader ?: return
        if (config.liquidOverlay.wireVisibilityUnderSkin <= 0f) return
        val stateChanged = lastLineState != state ||
            lastLineReducedMotion != activeReducedMotion ||
            lastLineQuality != quality
        val shouldRefreshLines = lineBufferDirty || stateChanged || cachedLineVertexCount <= 0
        val breathPulse = config.breathPulse(time)

        if (shouldRefreshLines) {
            cachedLineVertexCount = fillWireParams(counts)
            uploadWireBuffer(lineVbo[0], cachedLineVertexCount)
            lineBufferDirty = false
            lastLineState = state
            lastLineReducedMotion = activeReducedMotion
            lastLineQuality = quality
        }

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniformMatrix4fv(shader.mvpLocation, 1, false, mvp, 0)
        GLES30.glUniform1f(shader.timeLocation, time)
        GLES30.glUniform1f(shader.radiusScaleLocation, config.radiusScale(time))
        GLES30.glUniform1f(shader.deformationLocation, config.deformation)
        GLES30.glUniform1f(shader.waveSpeedLocation, config.motion.waveSpeed)
        GLES30.glUniform1f(shader.lightFlowSpeedLocation, config.motion.lightFlowSpeed)
        GLES30.glUniform1f(
            shader.lineAlphaLocation,
            config.lineAlpha *
                config.visualIntensity.lineAlphaMultiplier *
                config.liquidOverlay.wireVisibilityUnderSkin,
        )
        GLES30.glUniform1f(shader.saturationLocation, config.visualIntensity.saturationBoost)
        GLES30.glUniform1f(shader.bumpCountLocation, config.breathing.bumpCount.toFloat())
        GLES30.glUniform1f(shader.bumpAmplitudeLocation, config.breathing.bumpAmplitude * (1f + breathPulse * 0.42f))
        GLES30.glUniform1f(shader.bumpSpeedLocation, config.breathing.bumpSpeed * (1f + breathPulse * 0.18f))
        GLES30.glUniform1f(shader.flowAccelerationLocation, config.breathing.flowAcceleration + breathPulse * 0.20f + touchStrength * config.breathing.touchBoost * 0.25f)
        GLES30.glUniform1f(shader.instabilityJitterLocation, config.breathing.instabilityJitter)
        GLES30.glUniform2f(shader.touchLocation, touchX, touchY)
        GLES30.glUniform1f(shader.touchStrengthLocation, touchStrength * config.breathing.touchBoost)
        GLES30.glUniform1f(shader.fiberHighlightLocation, config.holographicPolish.fiberHighlight)
        GLES30.glUniform1f(shader.depthSeparationLocation, config.holographicPolish.depthSeparation)
        GLES30.glUniform1f(shader.runnerIntensityLocation, config.holographicPolish.runnerIntensity)
        GLES30.glUniform1f(shader.backlineOpacityReductionLocation, config.holographicPolish.backlineOpacityReduction)
        GLES30.glUniform1f(shader.backlineSaturationReductionLocation, config.holographicPolish.backlineSaturationReduction)
        GLES30.glUniform1f(shader.backlineSoftnessLocation, config.holographicPolish.backlineSoftness)
        GLES30.glUniform1f(shader.grapheneMeshSofteningLocation, config.holographicPolish.grapheneMeshSoftening)
        GLES30.glUniform1f(shader.wireClarityReductionLocation, config.holographicPolish.wireClarityReduction)
        drawWireBuffer(lineVbo[0], cachedLineVertexCount)
    }

    private fun drawProceduralSkinOverlay(
        activeSkinShader: OrbSkinShaderProgram?,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
        touchStrength: Float,
        touchAge: Float,
        touchWave: OrbTouchWaveProfile,
        transitionProgress: Float,
        transitionEased: Float,
    ) {
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        drawProceduralSkin(
            activeSkinShader = activeSkinShader,
            counts = counts,
            config = config,
            time = time,
            touchStrength = touchStrength,
            touchAge = touchAge,
            touchWave = touchWave,
            transitionProgress = transitionProgress,
            transitionEased = transitionEased,
            overlayPass = true,
        )
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
    }

    private fun drawProceduralSkin(
        activeSkinShader: OrbSkinShaderProgram?,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
        touchStrength: Float,
        touchAge: Float,
        touchWave: OrbTouchWaveProfile,
        transitionProgress: Float,
        transitionEased: Float,
        overlayPass: Boolean = false,
    ) {
        val shader = activeSkinShader ?: return
        val grain = counts.visualLayers.proceduralSkinGrain
        if (grain <= 0f) return
        val breathPulse = config.breathPulse(time)

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniform1f(shader.timeLocation, time)
        GLES30.glUniform1f(shader.aspectLocation, width / height.toFloat())
        GLES30.glUniform1f(shader.grainLocation, grain)
        GLES30.glUniform1f(shader.intensityLocation, config.skinIntensity + breathPulse * 0.035f)
        GLES30.glUniform1f(shader.haloLocation, counts.visualLayers.proceduralHaloRing)
        GLES30.glUniform1f(shader.haloPulseLocation, config.breathing.haloPulse + breathPulse * 0.20f)
        GLES30.glUniform1f(shader.bumpCountLocation, config.breathing.bumpCount.toFloat())
        GLES30.glUniform1f(shader.bumpAmplitudeLocation, config.breathing.bumpAmplitude * (1f + breathPulse * 0.52f))
        GLES30.glUniform2f(shader.touchLocation, touchX, touchY)
        GLES30.glUniform1f(shader.touchStrengthLocation, touchStrength * config.breathing.touchBoost)
        GLES30.glUniform1f(shader.liquidAlphaLocation, config.liquidOverlay.overlayAlpha)
        GLES30.glUniform1f(shader.liquidFlowSpeedLocation, config.liquidOverlay.flowSpeed)
        GLES30.glUniform1f(shader.liquidCausticScaleLocation, config.liquidOverlay.causticScale)
        GLES30.glUniform1f(shader.liquidIridescenceLocation, config.liquidOverlay.iridescence)
        GLES30.glUniform1f(shader.liquidCoolingLocation, config.liquidOverlay.cooling)
        GLES30.glUniform1f(shader.mercuryReflectionLocation, config.liquidOverlay.mercuryReflection)
        GLES30.glUniform1f(shader.symbioteViscosityLocation, config.liquidOverlay.symbioteViscosity)
        GLES30.glUniform1f(shader.coreDropletAlphaLocation, config.liquidOverlay.coreDropletAlpha)
        GLES30.glUniform1f(shader.spikeDensityLocation, config.liquidOverlay.spikeDensity)
        GLES30.glUniform1f(shader.spikeHeightLocation, config.liquidOverlay.spikeHeight)
        GLES30.glUniform1f(shader.spikeSharpnessLocation, config.liquidOverlay.spikeSharpness)
        GLES30.glUniform1f(shader.magneticPulseStrengthLocation, config.liquidOverlay.magneticPulseStrength)
        GLES30.glUniform1f(shader.fieldStabilityLocation, config.liquidOverlay.fieldStability)
        GLES30.glUniform1f(shader.touchAgeLocation, touchAge)
        GLES30.glUniform1f(shader.waveAmplitudeLocation, touchWave.waveAmplitude)
        GLES30.glUniform1f(shader.waveSpeedLocation, touchWave.waveSpeed)
        GLES30.glUniform1f(shader.waveFrequencyLocation, touchWave.waveFrequency)
        GLES30.glUniform1f(shader.waveDampingLocation, touchWave.waveDamping)
        GLES30.glUniform1f(shader.waveFalloffLocation, touchWave.waveFalloff)
        GLES30.glUniform1f(shader.edgeDisplacementLocation, touchWave.edgeDisplacement)
        GLES30.glUniform1f(shader.forwardPushLocation, touchWave.forwardPush)
        GLES30.glUniform1f(shader.surfaceWarpAmplitudeLocation, config.surfaceTurbulence.surfaceWarpAmplitude)
        GLES30.glUniform1f(shader.edgeChaosAmplitudeLocation, config.surfaceTurbulence.edgeChaosAmplitude)
        GLES30.glUniform1f(shader.lobeVarietyLocation, config.surfaceTurbulence.lobeVariety)
        GLES30.glUniform1f(shader.frontDepthPushLocation, config.surfaceTurbulence.frontDepthPush)
        GLES30.glUniform1f(shader.fieldRestlessnessLocation, config.surfaceTurbulence.fieldRestlessness)
        GLES30.glUniform1f(shader.fieldLineStrengthLocation, config.dropletField.fieldLineStrength)
        GLES30.glUniform1f(shader.clusterStrengthLocation, config.dropletField.clusterStrength)
        GLES30.glUniform1f(shader.dropletGlossLocation, config.dropletField.dropletGloss)
        GLES30.glUniform1f(shader.magneticAttractionLocation, config.dropletField.magneticAttraction)
        GLES30.glUniform1f(shader.clusterRestlessnessLocation, config.dropletField.clusterRestlessness)
        GLES30.glUniform1f(shader.shapeLobeCountLocation, config.shapeState.lobeCount.toFloat())
        GLES30.glUniform1f(shader.shapeLobeHeightLocation, config.shapeState.lobeHeight)
        GLES30.glUniform1f(shader.shapeLobeSharpnessLocation, config.shapeState.lobeSharpness)
        GLES30.glUniform1f(shader.shapeDropletChanceLocation, config.shapeState.dropletChance)
        GLES30.glUniform1f(shader.shapeSymmetryLocation, config.shapeState.symmetry)
        GLES30.glUniform1f(shader.shapeRestlessnessLocation, config.shapeState.restlessness)
        GLES30.glUniform1f(shader.shapeReabsorptionSpeedLocation, config.shapeState.reabsorptionSpeed)
        GLES30.glUniform1f(shader.shapeEdgeContainmentLocation, config.shapeState.edgeContainment)
        GLES30.glUniform1f(shader.volumeFogAlphaLocation, config.holographicPolish.volumeFogAlpha)
        GLES30.glUniform1f(shader.breathTextureAlphaLocation, config.holographicPolish.breathTextureAlpha)
        GLES30.glUniform1f(shader.depthSeparationLocation, config.holographicPolish.depthSeparation)
        GLES30.glUniform1f(shader.rimPrismLocation, config.holographicPolish.rimPrism)
        GLES30.glUniform1f(shader.centerClarityLocation, config.holographicPolish.centerClarity)
        GLES30.glUniform1f(shader.membraneAlphaLocation, config.holographicPolish.membraneAlpha)
        GLES30.glUniform1f(shader.microReliefAlphaLocation, config.holographicPolish.microReliefAlpha)
        GLES30.glUniform1f(shader.specularSheenLocation, config.holographicPolish.specularSheen)
        GLES30.glUniform1f(shader.iridescentFilmLocation, config.holographicPolish.iridescentFilm)
        GLES30.glUniform1f(shader.rimMaterializationLocation, config.holographicPolish.rimMaterialization)
        GLES30.glUniform1f(shader.depthFogCenterAlphaLocation, config.holographicPolish.depthFogCenterAlpha)
        GLES30.glUniform1f(shader.depthFogMiddleAlphaLocation, config.holographicPolish.depthFogMiddleAlpha)
        GLES30.glUniform1f(shader.glassShellAlphaLocation, config.holographicPolish.glassShellAlpha)
        GLES30.glUniform1f(shader.premiumGrainAlphaLocation, config.holographicPolish.premiumGrainAlpha)
        GLES30.glUniform1f(shader.glassStreakAlphaLocation, config.holographicPolish.glassStreakAlpha)
        GLES30.glUniform1f(shader.overlayPassLocation, if (overlayPass) 1f else 0f)
        GLES30.glUniform1f(shader.frostedGlassAlphaLocation, config.holographicPolish.frostedGlassAlpha)
        GLES30.glUniform1f(shader.dichroicReflectionLocation, config.holographicPolish.dichroicReflection)
        GLES30.glUniform1f(shader.internalRefractionLocation, config.holographicPolish.internalRefraction)
        GLES30.glUniform1f(shader.microPrismCoatingLocation, config.holographicPolish.microPrismCoating)
        GLES30.glUniform1f(shader.smokedGlassAlphaLocation, config.holographicPolish.smokedGlassAlpha)
        GLES30.glUniform1f(shader.magneticDustAlphaLocation, config.holographicPolish.magneticDustAlpha)
        GLES30.glUniform1f(shader.thinFresnelRimLocation, config.holographicPolish.thinFresnelRim)
        GLES30.glUniform1f(shader.opalescentDiffusionAlphaLocation, config.holographicPolish.opalescentDiffusionAlpha)
        GLES30.glUniform1f(shader.subsurfaceScatteringLocation, config.holographicPolish.subsurfaceScattering)
        GLES30.glUniform1f(shader.innerMilkyGlowLocation, config.holographicPolish.innerMilkyGlow)
        GLES30.glUniform1f(shader.corePulseScaleLocation, config.ferrofluidMotion.corePulseScale)
        GLES30.glUniform1f(shader.corePulseDurationLocation, config.ferrofluidMotion.corePulseDurationSeconds)
        GLES30.glUniform1f(shader.internalDriftAmplitudeLocation, config.ferrofluidMotion.internalDriftAmplitudePx)
        GLES30.glUniform1f(shader.internalDriftDurationLocation, config.ferrofluidMotion.internalDriftDurationSeconds)
        GLES30.glUniform1f(shader.fluidDriftAmplitudeLocation, config.ferrofluidMotion.fluidDriftAmplitudePx)
        GLES30.glUniform1f(shader.fluidDriftDurationLocation, config.ferrofluidMotion.fluidDriftDurationSeconds)
        GLES30.glUniform1f(shader.contourActivityZonesLocation, config.ferrofluidMotion.contourActivityZones.toFloat())
        GLES30.glUniform1f(shader.spikeBaseLengthLocation, config.ferrofluidMotion.spikeBaseLengthPx)
        GLES30.glUniform1f(shader.spikeGrowthBonusLocation, config.ferrofluidMotion.spikeGrowthBonusPx)
        GLES30.glUniform1f(shader.spikeActivityLocation, config.ferrofluidMotion.spikeActivity)
        GLES30.glUniform1f(shader.spikeCycleDurationLocation, config.ferrofluidMotion.spikeCycleDurationSeconds)
        GLES30.glUniform1f(shader.spikeMicroJitterLocation, config.ferrofluidMotion.microJitter)
        GLES30.glUniform1f(shader.highlightTravelLocation, config.ferrofluidMotion.highlightTravelPx)
        GLES30.glUniform1f(shader.highlightIntensityBoostLocation, config.ferrofluidMotion.highlightIntensityBoost)
        GLES30.glUniform1f(shader.highlightResponseLagLocation, config.ferrofluidMotion.highlightResponseLagSeconds)
        GLES30.glUniform1f(shader.rimBreathingLocation, config.ferrofluidMotion.rimBreathing)
        GLES30.glUniform1f(shader.motionIrregularityLocation, config.ferrofluidMotion.irregularity)
        GLES30.glUniform1f(shader.highlightFlickerLocation, config.ferrofluidMotion.highlightFlicker)
        GLES30.glUniform1f(shader.edgeTickVisibilityLocation, config.ferrofluidMotion.edgeTickVisibility)
        GLES30.glUniform1f(shader.transitionProgressLocation, transitionProgress)
        GLES30.glUniform1f(shader.transitionEasedLocation, transitionEased)

        skinQuad.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, skinQuad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun currentTouchStrength(time: Float): Float {
        val lastTouchSeconds = (lastTouchNanos - startNanos) / 1_000_000_000f
        val age = (time - lastTouchSeconds).coerceAtLeast(0f)
        val releaseEcho = kotlin.math.exp(-age * 2.7f)
        return if (touchDown) 1f else releaseEcho.coerceIn(0f, 1f)
    }

    private fun currentTouchAge(time: Float): Float {
        if (lastTouchNanos == 0L) return 99f
        val lastTouchSeconds = (lastTouchNanos - startNanos) / 1_000_000_000f
        return (time - lastTouchSeconds).coerceAtLeast(0f)
    }

    private fun currentTouchWave(config: Orb3DStateConfig, touchAge: Float): OrbTouchWaveProfile {
        val impact = impactTouchWave
        return if (impact != null && touchAge < 1.6f) impact else config.touchWave
    }

    private fun currentStateTransitionProgress(nowNanos: Long): Float {
        val durationNanos = stateTransition.durationMillis * 1_000_000L
        if (durationNanos <= 0L) return 1f
        return ((nowNanos - stateTransitionStartNanos).toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
    }

    private fun easeTransition(progress: Float, easing: OrbMotionEasing): Float {
        val t = progress.coerceIn(0f, 1f)
        return when (easing) {
            OrbMotionEasing.Linear -> t
            OrbMotionEasing.EaseOutCubic -> 1f - (1f - t) * (1f - t) * (1f - t)
            OrbMotionEasing.EaseInOutCubic -> if (t < 0.5f) {
                4f * t * t * t
            } else {
                1f - (-2f * t + 2f).pow(3f) / 2f
            }
        }
    }

    private fun fillLineVertices(
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ): Int {
        val expectedFloats =
            ((counts.meshLatitudes * counts.meshLongitudes) +
                (counts.meshLongitudes * (counts.meshLatitudes - 1)) +
                (counts.visualLayers.surfaceVeilBands * counts.visualLayers.surfaceVeilSteps) +
                (counts.visualLayers.diagonalRibbonCount * counts.visualLayers.diagonalRibbonSteps) +
                (counts.visualLayers.microRibbonCount * counts.visualLayers.microRibbonSteps) +
                counts.visualLayers.shortSegmentCount +
                (counts.visualLayers.localFilamentCount * counts.visualLayers.localFilamentSteps) +
                (counts.visualLayers.primarySignalArcCount * counts.visualLayers.primarySignalArcSteps)) *
                2 *
                ORB_VERTEX_STRIDE_FLOATS
        val target = ensureBuffer(expectedFloats)
        target.clear()

        fillSurfaceVeilVertices(target, counts, config, time)
        fillDiagonalRibbonVertices(target, counts, config, time)
        fillMicroRibbonVertices(target, counts, config, time)
        fillInterruptedSegmentVertices(target, counts, config, time)
        fillLocalFilamentVertices(target, counts, config, time)
        fillPrimarySignalArcVertices(target, counts, config, time)

        for (latIndex in 1 until counts.meshLatitudes - 1) {
            val latitude = Orb3DGeometry.latitudeForIndex(latIndex, counts.meshLatitudes)
            for (lonIndex in 0 until counts.meshLongitudes) {
                val lonA = Orb3DGeometry.longitudeForIndex(lonIndex, counts.meshLongitudes)
                val lonB = Orb3DGeometry.longitudeForIndex((lonIndex + 1) % counts.meshLongitudes, counts.meshLongitudes)
                putLineSegment(target, lonA, latitude, lonB, latitude, time, config, phase = latIndex * 0.19f)
            }
        }

        for (lonIndex in 0 until counts.meshLongitudes) {
            val longitude = Orb3DGeometry.longitudeForIndex(lonIndex, counts.meshLongitudes)
            for (latIndex in 1 until counts.meshLatitudes - 1) {
                val latA = Orb3DGeometry.latitudeForIndex(latIndex, counts.meshLatitudes)
                val latB = Orb3DGeometry.latitudeForIndex(latIndex + 1, counts.meshLatitudes)
                putLineSegment(target, longitude, latA, longitude, latB, time, config, phase = lonIndex * 0.13f + 1.7f)
            }
        }

        return target.position() / ORB_VERTEX_STRIDE_FLOATS
    }

    private fun fillWireParams(counts: Orb3DRenderCounts): Int {
        val target = ensureWireBuffer(counts.lineParamFloatCount)
        target.clear()

        repeat(counts.visualLayers.surfaceVeilBands) { band ->
            val latitude = -1.18f + band / (counts.visualLayers.surfaceVeilBands - 1f) * 2.36f
            var previousLongitude: Float? = null
            repeat(counts.visualLayers.surfaceVeilSteps + 1) { step ->
                val t = step / counts.visualLayers.surfaceVeilSteps.toFloat()
                val longitude = t * ORB_TWO_PI + sin(t * ORB_TWO_PI * 2f + band * 0.37f) * 0.030f
                previousLongitude?.let {
                    putWireVertex(target, it, latitude, band * 0.41f, 0.35f)
                    putWireVertex(target, longitude, latitude, band * 0.41f, 0.35f)
                }
                previousLongitude = longitude
            }
        }

        repeat(counts.visualLayers.diagonalRibbonCount) { ribbon ->
            val phase = ribbon * 0.61f
            val tilt = -0.64f + ribbon / counts.visualLayers.diagonalRibbonCount.toFloat() * 1.28f
            var previous: Pair<Float, Float>? = null
            repeat(counts.visualLayers.diagonalRibbonSteps + 1) { step ->
                val t = step / counts.visualLayers.diagonalRibbonSteps.toFloat()
                val latitude = -1.05f + t * 2.10f
                val longitude = phase + t * (ORB_TWO_PI * 0.78f + tilt) +
                    sin(t * PI.toFloat() * 2f + phase) * 0.13f
                previous?.let {
                    putWireVertex(target, it.first, it.second, phase, 1.0f)
                    putWireVertex(target, longitude, latitude, phase, 1.0f)
                }
                previous = longitude to latitude
            }
        }

        repeat(counts.visualLayers.microRibbonCount) { ribbon ->
            val phase = ribbon * 0.47f + 0.31f
            val lane = -0.82f + ribbon / counts.visualLayers.microRibbonCount.toFloat() * 1.64f
            var previous: Pair<Float, Float>? = null
            repeat(counts.visualLayers.microRibbonSteps + 1) { step ->
                val t = step / counts.visualLayers.microRibbonSteps.toFloat()
                val latitude = ((lane * 0.42f) + sin(t * PI.toFloat() * 1.4f + phase) * 0.34f)
                    .coerceIn(-1.16f, 1.16f)
                val longitude = phase + t * ORB_TWO_PI * 0.56f +
                    sin(t * ORB_TWO_PI * 3.0f + phase) * 0.050f
                previous?.let {
                    putWireVertex(target, it.first, it.second, phase, 1.8f)
                    putWireVertex(target, longitude, latitude, phase, 1.8f)
                }
                previous = longitude to latitude
            }
        }

        repeat(counts.visualLayers.shortSegmentCount) { index ->
            val seed = index * 17.173f + 2.1f
            val longitude = normalizedNoise(seed) * ORB_TWO_PI
            val latitude = -1.12f + normalizedNoise(seed * 1.31f) * 2.24f
            val slant = if (index % 2 == 0) 1f else -1f
            val length = 0.030f + normalizedNoise(seed * 0.67f) * 0.048f
            val phase = normalizedNoise(seed * 0.43f) * ORB_TWO_PI
            putWireVertex(target, longitude, latitude, phase, 2.4f)
            putWireVertex(target, longitude + length * slant, (latitude + length * 0.54f).coerceIn(-1.18f, 1.18f), phase, 2.4f)
        }

        repeat(counts.visualLayers.localFilamentCount) { filament ->
            val seed = filament * 23.719f + 4.7f
            val centerLongitude = normalizedNoise(seed) * ORB_TWO_PI
            val centerLatitude = -0.96f + normalizedNoise(seed * 1.41f) * 1.92f
            val arc = 0.16f + normalizedNoise(seed * 0.77f) * 0.20f
            val phase = normalizedNoise(seed * 0.53f) * ORB_TWO_PI
            var previous: Pair<Float, Float>? = null
            repeat(counts.visualLayers.localFilamentSteps + 1) { step ->
                val t = step / counts.visualLayers.localFilamentSteps.toFloat()
                val offset = (t - 0.5f) * arc
                val longitude = centerLongitude + offset
                val latitude = (centerLatitude + sin(t * PI.toFloat() + phase) * 0.060f + offset * 0.36f)
                    .coerceIn(-1.18f, 1.18f)
                previous?.let {
                    putWireVertex(target, it.first, it.second, phase, 3.2f)
                    putWireVertex(target, longitude, latitude, phase, 3.2f)
                }
                previous = longitude to latitude
            }
        }

        repeat(counts.visualLayers.primarySignalArcCount) { arcIndex ->
            val seed = arcIndex * 31.719f + 6.3f
            val phase = normalizedNoise(seed) * ORB_TWO_PI
            val tilt = -0.52f + normalizedNoise(seed * 1.37f) * 1.04f
            val latitudeBias = -0.36f + normalizedNoise(seed * 1.91f) * 0.72f
            var previous: Pair<Float, Float>? = null
            repeat(counts.visualLayers.primarySignalArcSteps + 1) { step ->
                val t = step / counts.visualLayers.primarySignalArcSteps.toFloat()
                val longitude = phase + t * (ORB_TWO_PI * (0.92f + normalizedNoise(seed * 0.73f) * 0.28f)) +
                    sin(t * PI.toFloat() * 2f + phase) * 0.20f
                val latitude = (latitudeBias + (t - 0.5f) * tilt + sin(t * PI.toFloat() * 1.45f + phase) * 0.46f)
                    .coerceIn(-1.16f, 1.16f)
                previous?.let {
                    putWireVertex(target, it.first, it.second, phase, 4.6f)
                    putWireVertex(target, longitude, latitude, phase, 4.6f)
                }
                previous = longitude to latitude
            }
        }

        for (latIndex in 1 until counts.meshLatitudes - 1) {
            val latitude = Orb3DGeometry.latitudeForIndex(latIndex, counts.meshLatitudes)
            for (lonIndex in 0 until counts.meshLongitudes) {
                val lonA = Orb3DGeometry.longitudeForIndex(lonIndex, counts.meshLongitudes)
                val lonB = Orb3DGeometry.longitudeForIndex((lonIndex + 1) % counts.meshLongitudes, counts.meshLongitudes)
                putWireVertex(target, lonA, latitude, latIndex * 0.19f, 0f)
                putWireVertex(target, lonB, latitude, latIndex * 0.19f, 0f)
            }
        }

        for (lonIndex in 0 until counts.meshLongitudes) {
            val longitude = Orb3DGeometry.longitudeForIndex(lonIndex, counts.meshLongitudes)
            for (latIndex in 1 until counts.meshLatitudes - 1) {
                val latA = Orb3DGeometry.latitudeForIndex(latIndex, counts.meshLatitudes)
                val latB = Orb3DGeometry.latitudeForIndex(latIndex + 1, counts.meshLatitudes)
                putWireVertex(target, longitude, latA, lonIndex * 0.13f + 1.7f, 0f)
                putWireVertex(target, longitude, latB, lonIndex * 0.13f + 1.7f, 0f)
            }
        }

        return target.position() / ORB_WIRE_STRIDE_FLOATS
    }

    private fun putWireVertex(
        target: FloatBuffer,
        longitude: Float,
        latitude: Float,
        phase: Float,
        kind: Float,
    ) {
        target.put(longitude)
        target.put(latitude)
        target.put(phase)
        target.put(kind)
    }

    private fun fillSurfaceVeilVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.surfaceVeilBands) { band ->
            val latitude = -1.18f + band / (counts.visualLayers.surfaceVeilBands - 1f) * 2.36f
            var previous: Orb3DPoint? = null
            repeat(counts.visualLayers.surfaceVeilSteps + 1) { step ->
                val t = step / counts.visualLayers.surfaceVeilSteps.toFloat()
                val longitude = t * ORB_TWO_PI +
                    sin(t * ORB_TWO_PI * 2f + band * 0.37f) * 0.030f
                val point = Orb3DGeometry.deformedSpherePoint(
                    longitude = longitude,
                    latitude = latitude,
                    time = time,
                    phase = band * 0.41f,
                    deformationAmplitude = config.deformation * 0.72f,
                    radiusScale = config.radiusScale(time),
                    waveSpeed = config.motion.waveSpeed,
                )
                previous?.let {
                    val color = config.veilColor(point)
                    putVertex(target, it, color, config.linePointSize)
                    putVertex(target, point, color, config.linePointSize)
                }
                previous = point
            }
        }
    }

    private fun fillDiagonalRibbonVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.diagonalRibbonCount) { ribbon ->
            val phase = ribbon * 0.61f
            val tilt = -0.64f + ribbon / counts.visualLayers.diagonalRibbonCount.toFloat() * 1.28f
            var previous: Orb3DPoint? = null
            repeat(counts.visualLayers.diagonalRibbonSteps + 1) { step ->
                val t = step / counts.visualLayers.diagonalRibbonSteps.toFloat()
                val latitude = -1.05f + t * 2.10f
                val longitude = phase + t * (ORB_TWO_PI * 0.78f + tilt) +
                    sin(t * PI.toFloat() * 2f + phase) * 0.13f
                val point = Orb3DGeometry.deformedSpherePoint(
                    longitude = longitude,
                    latitude = latitude,
                    time = time,
                    phase = phase,
                    deformationAmplitude = config.deformation * 0.88f,
                    radiusScale = config.radiusScale(time),
                    waveSpeed = config.motion.waveSpeed,
                )
                val light = sin(t * PI.toFloat() * 2f - time * config.motion.lightFlowSpeed * 4f + phase) * 0.5f + 0.5f
                previous?.let {
                    val color = config.ribbonColor(point, light)
                    putVertex(target, it, color, config.linePointSize)
                    putVertex(target, point, color, config.linePointSize)
                }
                previous = point
            }
        }
    }

    private fun fillMicroRibbonVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.microRibbonCount) { ribbon ->
            val phase = ribbon * 0.47f + 0.31f
            val lane = -0.82f + ribbon / counts.visualLayers.microRibbonCount.toFloat() * 1.64f
            var previous: Orb3DPoint? = null
            repeat(counts.visualLayers.microRibbonSteps + 1) { step ->
                val t = step / counts.visualLayers.microRibbonSteps.toFloat()
                val latitude = (lane * 0.42f) + sin(t * PI.toFloat() * 1.4f + phase) * 0.34f
                val longitude = phase + t * ORB_TWO_PI * 0.56f +
                    sin(t * ORB_TWO_PI * 3.0f + phase) * 0.050f
                val point = Orb3DGeometry.deformedSpherePoint(
                    longitude = longitude,
                    latitude = latitude.coerceIn(-1.16f, 1.16f),
                    time = time,
                    phase = phase,
                    deformationAmplitude = config.deformation * 0.62f,
                    shellDepth = 1.006f,
                    radiusScale = config.radiusScale(time),
                    waveSpeed = config.motion.waveSpeed,
                )
                val light = sin(t * ORB_TWO_PI - time * config.motion.lightFlowSpeed * 5.5f + phase) * 0.5f + 0.5f
                previous?.let {
                    val color = config.microRibbonColor(point, light)
                    putVertex(target, it, color, config.linePointSize)
                    putVertex(target, point, color, config.linePointSize)
                }
                previous = point
            }
        }
    }

    private fun fillInterruptedSegmentVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.shortSegmentCount) { index ->
            val seed = index * 17.173f + 2.1f
            val longitude = normalizedNoise(seed) * ORB_TWO_PI
            val latitude = -1.12f + normalizedNoise(seed * 1.31f) * 2.24f
            val slant = if (index % 2 == 0) 1f else -1f
            val length = 0.030f + normalizedNoise(seed * 0.67f) * 0.048f
            val phase = normalizedNoise(seed * 0.43f) * ORB_TWO_PI
            val pointA = Orb3DGeometry.deformedSpherePoint(
                longitude = longitude,
                latitude = latitude,
                time = time,
                phase = phase,
                deformationAmplitude = config.deformation * 0.78f,
                shellDepth = 1.004f,
                radiusScale = config.radiusScale(time),
                waveSpeed = config.motion.waveSpeed,
            )
            val pointB = Orb3DGeometry.deformedSpherePoint(
                longitude = longitude + length * slant,
                latitude = (latitude + length * 0.54f).coerceIn(-1.18f, 1.18f),
                time = time,
                phase = phase,
                deformationAmplitude = config.deformation * 0.78f,
                shellDepth = 1.004f,
                radiusScale = config.radiusScale(time),
                waveSpeed = config.motion.waveSpeed,
            )
            val blink = sin(time * config.motion.lightFlowSpeed * 7.2f + phase) * 0.5f + 0.5f
            val color = config.shortSegmentColor(pointA, blink)
            putVertex(target, pointA, color, config.linePointSize)
            putVertex(target, pointB, color, config.linePointSize)
        }
    }

    private fun fillLocalFilamentVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.localFilamentCount) { filament ->
            val seed = filament * 23.719f + 4.7f
            val centerLongitude = normalizedNoise(seed) * ORB_TWO_PI
            val centerLatitude = -0.96f + normalizedNoise(seed * 1.41f) * 1.92f
            val arc = 0.16f + normalizedNoise(seed * 0.77f) * 0.20f
            val phase = normalizedNoise(seed * 0.53f) * ORB_TWO_PI
            var previous: Orb3DPoint? = null
            repeat(counts.visualLayers.localFilamentSteps + 1) { step ->
                val t = step / counts.visualLayers.localFilamentSteps.toFloat()
                val offset = (t - 0.5f) * arc
                val longitude = centerLongitude + offset
                val latitude = centerLatitude +
                    sin(t * PI.toFloat() + phase) * 0.060f +
                    offset * 0.36f
                val point = Orb3DGeometry.deformedSpherePoint(
                    longitude = longitude,
                    latitude = latitude.coerceIn(-1.18f, 1.18f),
                    time = time,
                    phase = phase,
                    deformationAmplitude = config.deformation * 1.05f,
                    shellDepth = 1.010f,
                    radiusScale = config.radiusScale(time),
                    waveSpeed = config.motion.waveSpeed,
                )
                val energy = sin(t * PI.toFloat() - time * config.motion.lightFlowSpeed * 5.8f + phase) * 0.5f + 0.5f
                previous?.let {
                    val color = config.filamentColor(point, energy)
                    putVertex(target, it, color, config.linePointSize)
                    putVertex(target, point, color, config.linePointSize)
                }
                previous = point
            }
        }
    }

    private fun fillPrimarySignalArcVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.primarySignalArcCount) { arcIndex ->
            val seed = arcIndex * 31.719f + 6.3f
            val phase = normalizedNoise(seed) * ORB_TWO_PI
            val tilt = -0.52f + normalizedNoise(seed * 1.37f) * 1.04f
            val latitudeBias = -0.36f + normalizedNoise(seed * 1.91f) * 0.72f
            var previous: Orb3DPoint? = null
            repeat(counts.visualLayers.primarySignalArcSteps + 1) { step ->
                val t = step / counts.visualLayers.primarySignalArcSteps.toFloat()
                val longitude = phase + t * (ORB_TWO_PI * (0.92f + normalizedNoise(seed * 0.73f) * 0.28f)) +
                    sin(t * PI.toFloat() * 2f + phase) * 0.20f
                val latitude = (latitudeBias + (t - 0.5f) * tilt + sin(t * PI.toFloat() * 1.45f + phase) * 0.46f)
                    .coerceIn(-1.16f, 1.16f)
                val point = Orb3DGeometry.deformedSpherePoint(
                    longitude = longitude,
                    latitude = latitude,
                    time = time,
                    phase = phase,
                    deformationAmplitude = config.deformation * 0.94f,
                    shellDepth = 1.016f,
                    radiusScale = config.radiusScale(time),
                    waveSpeed = config.motion.waveSpeed,
                )
                val runner = sin(t * ORB_TWO_PI - time * config.motion.lightFlowSpeed * 2.7f + phase) * 0.5f + 0.5f
                previous?.let {
                    val color = config.primarySignalArcColor(point, runner)
                    putVertex(target, it, color, config.linePointSize)
                    putVertex(target, point, color, config.linePointSize)
                }
                previous = point
            }
        }
    }

    private fun putLineSegment(
        target: FloatBuffer,
        lonA: Float,
        latA: Float,
        lonB: Float,
        latB: Float,
        time: Float,
        config: Orb3DStateConfig,
        phase: Float,
    ) {
        val lightPhase = time * config.motion.lightFlowSpeed
        val wave = sin(lonA * 2.4f + latA * 5.2f - lightPhase + phase) * 0.5f + 0.5f
        val crest = if (wave > 0.82f) 1.0f + (wave - 0.82f) * 3.8f else 1.0f
        val pointA = Orb3DGeometry.deformedSpherePoint(
            longitude = lonA,
            latitude = latA,
            time = time,
            phase = phase,
            deformationAmplitude = config.deformation,
            radiusScale = config.radiusScale(time),
            waveSpeed = config.motion.waveSpeed,
        )
        val pointB = Orb3DGeometry.deformedSpherePoint(
            longitude = lonB,
            latitude = latB,
            time = time,
            phase = phase,
            deformationAmplitude = config.deformation,
            radiusScale = config.radiusScale(time),
            waveSpeed = config.motion.waveSpeed,
        )
        putVertex(target, pointA, config.lineColor(pointA, crest), config.linePointSize)
        putVertex(target, pointB, config.lineColor(pointB, crest), config.linePointSize)
    }

    private fun fillParticleVertices(
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ): Int {
        val coreGlowCount = counts.visualLayers.coreGlowCount
        val expectedFloats = (
            counts.particleCount +
                counts.haloCount +
                coreGlowCount +
                counts.visualLayers.rimLoopCount * counts.visualLayers.rimSteps +
                counts.visualLayers.surfaceDustCount +
                counts.visualLayers.ambientDustCount +
                counts.visualLayers.intersectionPointCount
            ) * ORB_VERTEX_STRIDE_FLOATS
        val target = ensureBuffer(expectedFloats)
        target.clear()

        fillSurfaceDustVertices(target, counts, config, time)
        fillAmbientDustVertices(target, counts, config, time)
        fillIntersectionPointVertices(target, counts, config, time)

        val count = min(counts.particleCount, particleSeeds.size)
        for (i in 0 until count) {
            val seed = particleSeeds[i]
            val direction = if (i % 2 == 0) 1f else -1f
            val pathFamily = i % 4
            val lane = (i % 12) / 11f
            val laneLatitude = -0.74f + lane * 1.48f
            val flowProgress = time * config.motion.particleFlowSpeed * seed.speedBias * direction
            val longitude = when (pathFamily) {
                0 -> seed.longitude + flowProgress
                1 -> seed.longitude + flowProgress * 0.64f
                2 -> seed.longitude + flowProgress * 0.38f + sin(seed.phase) * 0.040f
                else -> seed.longitude + sin(seed.phase + time * config.motion.lightFlowSpeed) * 0.030f
            }
            val latitude = (when (pathFamily) {
                0 -> laneLatitude + sin(seed.phase + flowProgress) * 0.035f
                1 -> laneLatitude + flowProgress * 0.12f
                2 -> seed.latitude * 0.42f + laneLatitude * 0.58f + cos(seed.phase + flowProgress) * 0.028f
                else -> seed.latitude * 0.34f + sin(seed.phase + flowProgress) * 0.030f
            }).toFloat().coerceIn(-1.02f, 1.02f)
            val point = Orb3DGeometry.deformedSpherePoint(
                longitude = longitude,
                latitude = latitude,
                time = time,
                phase = seed.phase,
                deformationAmplitude = config.deformation,
                shellDepth = seed.shellDepth,
                radiusScale = config.radiusScale(time),
                waveSpeed = config.motion.waveSpeed,
            )
            val shimmer = 0.58f + sin(time * config.motion.lightFlowSpeed * 5.8f + seed.shimmerPhase) * 0.42f
            val layerGate = when {
                seed.shellDepth < 0.70f -> 0.66f
                seed.shellDepth < 0.82f -> 0.82f
                else -> 0.58f
            }
            val alpha = (
                seed.alpha *
                    config.particleAlpha *
                    config.visualIntensity.particleAlphaMultiplier *
                    (0.07f + point.front * 0.58f) *
                    layerGate *
                    point.edge +
                    shimmer * config.shimmerAlpha * 0.055f
                ).coerceIn(0f, 0.56f)
            val color = Orb3DPalette.surfaceColor(
                point,
                config.primary,
                config.secondary,
                config.visualIntensity.saturationBoost,
            )
                .brighten(if (i % 37 == 0) 0.22f else if (i % 11 == 0) 0.10f else 0f)
                .withAlpha(alpha)
            val pointSize = config.particleSize * (0.46f + seed.sizeRatio * 0.42f + point.front * 0.18f)
            putVertex(target, point, color, pointSize)
        }

        for (i in 0 until coreGlowCount) {
            val seed = i * 19.1919f
            val angle = -0.95f + i * (1.90f / (coreGlowCount - 1).coerceAtLeast(1))
            val pulse = sin(time * config.motion.breathingSpeed * (1.6f + i * 0.13f) + i * 1.37f) * 0.5f + 0.5f
            val radius = (0.16f + i * 0.038f + pulse * 0.018f) * config.radiusScale(time)
            val x = cos(angle) * radius * 0.84f
            val y = sin(angle * 1.18f) * radius * 0.54f
            val z = (0.08f + i * 0.045f) * (0.72f + pulse * 0.28f)
            val pointRadius = sqrt(x * x + y * y + z * z).coerceAtLeast(0.001f)
            val sideMix = ((x / pointRadius + 1f) * 0.5f).coerceIn(0f, 1f)
            val point = Orb3DPoint(
                x = x,
                y = y,
                z = z,
                radius = pointRadius,
                sideMix = sideMix,
                front = ((z / pointRadius + 1f) * 0.5f).coerceIn(0f, 1f),
                edge = 0.38f,
            )
            val color = mix(
                Orb3DPalette.sideColor(sideMix, config.visualIntensity.saturationBoost),
                Orb3DPalette.SurfaceGlow,
                0.34f + pulse * 0.18f,
            ).withAlpha(
                (config.coreGlowAlpha * config.visualIntensity.coreGlowMultiplier * (0.08f + pulse * 0.12f))
                    .coerceAtMost(0.16f),
            )
            val sizeScale = (min(width, height) / 960f).coerceIn(0.72f, 1.35f)
            putVertex(target, point, color, sizeScale * (6.5f + normalizedNoise(seed * 2.11f) * 6.0f))
        }

        fillRimBloomVertices(target, counts, config, time)

        for (i in 0 until counts.haloCount) {
            val seed = i * 12.9898f
            val theta = normalizedNoise(seed) * ORB_TWO_PI
            val spread = normalizedNoise(seed * 1.73f)
            val radialBreath = sin(time * config.motion.breathingSpeed * 3.2f + i * 0.47f) * 0.012f
            val radius = (0.72f + spread * 0.18f + radialBreath) * config.radiusScale(time)
            val x = cos(theta) * radius
            val y = sin(theta) * radius * 0.985f
            val z = sin(seed * 0.31f + time * config.motion.lightFlowSpeed * 1.8f) * 0.12f
            val sideMix = ((x / radius + 1f) * 0.5f).coerceIn(0f, 1f)
            val point = Orb3DPoint(
                x = x,
                y = y,
                z = z,
                radius = radius,
                sideMix = sideMix,
                front = 0.45f + normalizedNoise(seed * 0.7f) * 0.28f,
                edge = (0.52f + spread * 0.18f).coerceIn(0.2f, 0.82f),
            )
            val sparkle = sin(time * config.motion.lightFlowSpeed * 8.0f + i * 1.41f) * 0.5f + 0.5f
            val alpha = config.particleAlpha *
                config.visualIntensity.particleAlphaMultiplier *
                (0.006f + point.edge * 0.022f + sparkle * 0.010f)
            val color = Orb3DPalette.sideColor(sideMix, config.visualIntensity.saturationBoost)
                .brighten(0.10f)
                .withAlpha(alpha.coerceAtMost(0.045f))
            putVertex(target, point, color, config.haloParticleSize * (0.18f + spread * 0.12f))
        }

        return target.position() / ORB_VERTEX_STRIDE_FLOATS
    }

    private fun fillAmbientDustVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        val total = counts.visualLayers.ambientDustCount
        if (total <= 0) return

        val goldenAngle = 2.3999631f
        val sizeScale = (min(width, height) / 960f).coerceIn(0.72f, 1.35f)
        repeat(total) { i ->
            val seed = i * 17.771f + 3.0f
            val t = (i + 0.5f) / total.toFloat()
            val latitude = kotlin.math.asin(1f - 2f * t)
            val baseLongitude = i * goldenAngle + normalizedNoise(seed) * 0.28f
            val drift = sin(time * (0.08f + normalizedNoise(seed * 1.9f) * 0.045f) + seed) * 0.030f
            val attractedRadius = 0.98f + normalizedNoise(seed * 0.41f) * 0.30f - drift
            val longitude = baseLongitude + time * (0.006f + normalizedNoise(seed * 2.3f) * 0.010f)
            val x = cos(latitude) * cos(longitude) * attractedRadius
            val y = sin(latitude) * attractedRadius * 0.92f
            val z = cos(latitude) * sin(longitude) * attractedRadius * 0.62f
            val radius = sqrt(x * x + y * y + z * z).coerceAtLeast(0.001f)
            val sideMix = ((x / radius + 1f) * 0.5f).coerceIn(0f, 1f)
            val front = ((z / radius + 1f) * 0.5f).coerceIn(0f, 1f)
            val edgeAttraction = (1.0f - ((attractedRadius - 0.98f) / 0.30f).coerceIn(0f, 1f))
            val point = Orb3DPoint(
                x = x,
                y = y,
                z = z,
                radius = radius,
                sideMix = sideMix,
                front = front,
                edge = edgeAttraction,
            )
            val shimmer = sin(time * config.motion.lightFlowSpeed * 2.6f + seed * 0.37f) * 0.5f + 0.5f
            val blueGrey = OrbRgba(0.50f, 0.60f, 0.72f, 1f)
            val violetDust = OrbRgba(0.54f, 0.42f, 0.82f, 1f)
            val color = mix(blueGrey, violetDust, sideMix * 0.62f + shimmer * 0.14f)
                .withAlpha(
                    (
                        config.particleAlpha *
                            config.visualIntensity.particleAlphaMultiplier *
                            (0.010f + edgeAttraction * 0.026f + front * 0.010f) *
                            (0.55f + shimmer * 0.45f)
                        ).coerceAtMost(0.040f),
                )
            putVertex(target, point, color, sizeScale * (0.75f + normalizedNoise(seed * 4.1f) * 0.85f))
        }
    }

    private fun fillSurfaceDustVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        val total = counts.visualLayers.surfaceDustCount
        val goldenAngle = 2.3999631f
        repeat(total) { i ->
            val t = (i + 0.5f) / total.toFloat()
            val latitude = kotlin.math.asin(1f - 2f * t)
            val longitude = i * goldenAngle + sin(i * 0.17f) * 0.018f
            val phase = i * 0.217f
            val point = Orb3DGeometry.deformedSpherePoint(
                longitude = longitude,
                latitude = latitude,
                time = time,
                phase = phase,
                deformationAmplitude = config.deformation * 0.52f,
                shellDepth = 1.012f,
                radiusScale = config.radiusScale(time),
                waveSpeed = config.motion.waveSpeed,
            )
            val shimmer = sin(time * config.motion.lightFlowSpeed * 4.4f + phase) * 0.5f + 0.5f
            val alpha = config.particleAlpha *
                config.visualIntensity.particleAlphaMultiplier *
                (0.018f + point.front * 0.18f + shimmer * 0.040f) *
                point.edge
            val color = Orb3DPalette.surfaceColor(
                point,
                config.primary,
                config.secondary,
                config.visualIntensity.saturationBoost,
            )
                .brighten(0.10f + shimmer * 0.12f)
                .withAlpha(alpha.coerceIn(0f, 0.32f))
            putVertex(target, point, color, config.surfaceDustPointSize(point.front, i))
        }
    }

    private fun fillIntersectionPointVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.intersectionPointCount) { i ->
            val lonIndex = (i * 7) % counts.meshLongitudes
            val latIndex = 1 + ((i * 5) % (counts.meshLatitudes - 2))
            val longitude = Orb3DGeometry.longitudeForIndex(lonIndex, counts.meshLongitudes)
            val latitude = Orb3DGeometry.latitudeForIndex(latIndex, counts.meshLatitudes)
            val phase = i * 0.331f
            val point = Orb3DGeometry.deformedSpherePoint(
                longitude = longitude,
                latitude = latitude,
                time = time,
                phase = phase,
                deformationAmplitude = config.deformation * 0.70f,
                shellDepth = 0.88f,
                radiusScale = config.radiusScale(time),
                waveSpeed = config.motion.waveSpeed,
            )
            val spark = sin(time * config.motion.lightFlowSpeed * 8.6f + phase) * 0.5f + 0.5f
            val alpha = config.particleAlpha *
                config.visualIntensity.particleAlphaMultiplier *
                (0.020f + point.front * 0.18f + spark * 0.060f) *
                point.edge
            val color = mix(
                Orb3DPalette.surfaceColor(
                    point,
                    config.primary,
                    config.secondary,
                    config.visualIntensity.saturationBoost,
                ),
                Orb3DPalette.TitaniumGlow,
                0.16f + spark * 0.22f,
            )
                .brighten(0.14f + spark * 0.16f)
                .withAlpha(alpha.coerceAtMost(0.30f))
            putVertex(target, point, color, config.intersectionPointSize(spark) * 0.72f)
        }
    }

    private fun fillRimBloomVertices(
        target: FloatBuffer,
        counts: Orb3DRenderCounts,
        config: Orb3DStateConfig,
        time: Float,
    ) {
        repeat(counts.visualLayers.rimLoopCount) { loop ->
            val phase = loop * 0.72f
            repeat(counts.visualLayers.rimSteps) { step ->
                val theta = step / counts.visualLayers.rimSteps.toFloat() * ORB_TWO_PI
                val wave = sin(theta * 3.0f + phase + time * config.motion.waveSpeed * 0.35f) * 0.018f +
                    sin(theta * 7.0f - phase) * 0.010f
                val radius = (1.012f + loop * 0.018f + wave) * config.radiusScale(time)
                val point = Orb3DPoint(
                    x = cos(theta) * radius,
                    y = sin(theta) * radius * 0.985f,
                    z = sin(theta * 2.0f + phase) * 0.045f,
                    radius = radius,
                    sideMix = ((cos(theta) + 1f) * 0.5f).coerceIn(0f, 1f),
                    front = 0.78f,
                    edge = 1f,
                )
                val crest = sin(theta * 2.0f - time * config.motion.lightFlowSpeed * 5f + phase) * 0.5f + 0.5f
                val color = Orb3DPalette.sideColor(point.sideMix, config.visualIntensity.saturationBoost)
                    .brighten(0.28f + crest * 0.30f)
                    .withAlpha(
                        (
                            config.lineAlpha *
                                config.visualIntensity.rimBloomMultiplier *
                                (0.045f + crest * 0.12f)
                            ).coerceAtMost(0.46f),
                    )
                putVertex(target, point, color, config.rimPointSize(loop))
            }
        }
    }

    private fun putVertex(
        target: FloatBuffer,
        point: Orb3DPoint,
        color: OrbRgba,
        pointSize: Float,
    ) {
        target.put(point.x)
        target.put(point.y)
        target.put(point.z)
        target.put(color.red)
        target.put(color.green)
        target.put(color.blue)
        target.put(color.alpha)
        target.put(pointSize)
    }

    private fun uploadAndDraw(
        activeShader: Orb3DShaderProgram,
        targetVbo: Int,
        vertexCount: Int,
        mode: Int,
        pointMode: Boolean,
    ) {
        if (vertexCount <= 0) return
        uploadBuffer(targetVbo, vertexCount)
        drawBoundBuffer(activeShader, targetVbo, vertexCount, mode, pointMode)
    }

    private fun uploadBuffer(
        targetVbo: Int,
        vertexCount: Int,
    ) {
        if (vertexCount <= 0) return
        buffer.limit(vertexCount * ORB_VERTEX_STRIDE_FLOATS)
        buffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, targetVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexCount * ORB_VERTEX_STRIDE_BYTES,
            buffer,
            GLES30.GL_DYNAMIC_DRAW,
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun uploadWireBuffer(
        targetVbo: Int,
        vertexCount: Int,
    ) {
        if (vertexCount <= 0) return
        wireBuffer.limit(vertexCount * ORB_WIRE_STRIDE_FLOATS)
        wireBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, targetVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexCount * ORB_WIRE_STRIDE_BYTES,
            wireBuffer,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawWireBuffer(
        targetVbo: Int,
        vertexCount: Int,
    ) {
        if (vertexCount <= 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, targetVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, ORB_WIRE_STRIDE_BYTES, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawBoundBuffer(
        activeShader: Orb3DShaderProgram,
        targetVbo: Int,
        vertexCount: Int,
        mode: Int,
        pointMode: Boolean,
    ) {
        if (vertexCount <= 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, targetVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, ORB_VERTEX_STRIDE_BYTES, 0)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, ORB_VERTEX_STRIDE_BYTES, 3 * 4)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, ORB_VERTEX_STRIDE_BYTES, 7 * 4)
        GLES30.glUniform1i(activeShader.pointModeLocation, if (pointMode) 1 else 0)
        GLES30.glDrawArrays(mode, 0, vertexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun ensureBuffer(requiredFloats: Int): FloatBuffer {
        if (buffer.capacity() < requiredFloats) {
            buffer = allocateFloatBuffer(requiredFloats * 2)
        }
        return buffer
    }

    private fun ensureWireBuffer(requiredFloats: Int): FloatBuffer {
        if (wireBuffer.capacity() < requiredFloats) {
            wireBuffer = allocateFloatBuffer(requiredFloats * 2)
        }
        return wireBuffer
    }

    private fun SwimParticleOrbState.config(reducedMotion: Boolean): Orb3DStateConfig {
        val base = when (this) {
            SwimParticleOrbState.DISCONNECTED -> Orb3DStateConfig(
                deformation = 0.58f,
                lineAlpha = 0.24f,
                particleAlpha = 0.34f,
                shimmerAlpha = 0.12f,
                coreGlowAlpha = 0.07f,
                motion = SwimParticleOrbState.DISCONNECTED.orbMotionConfig(),
                lineWidth = 1.08f,
                particleSize = 2.0f,
                haloParticleSize = 1.8f,
                primary = Orb3DPalette.PurplePrimary,
                secondary = Orb3DPalette.PurplePrimary,
                visualIntensity = SwimParticleOrbState.DISCONNECTED.orbVisualIntensity(),
                breathing = SwimParticleOrbState.DISCONNECTED.orbBreathingProfile(),
                liquidOverlay = SwimParticleOrbState.DISCONNECTED.orbLiquidOverlayStyle(),
                touchWave = SwimParticleOrbState.DISCONNECTED.orbTouchWaveProfile(),
                surfaceTurbulence = SwimParticleOrbState.DISCONNECTED.orbSurfaceTurbulenceProfile(),
                dropletField = SwimParticleOrbState.DISCONNECTED.orbDropletFieldProfile(),
                ferrofluidMotion = SwimParticleOrbState.DISCONNECTED.orbFerrofluidMotionProfile(),
                shapeState = SwimParticleOrbState.DISCONNECTED.orbShapeStateField(),
                holographicPolish = SwimParticleOrbState.DISCONNECTED.orbHolographicPolishProfile(),
            )
            SwimParticleOrbState.CONNECTING -> Orb3DStateConfig(
                deformation = 1.18f,
                lineAlpha = 0.52f,
                particleAlpha = 0.72f,
                shimmerAlpha = 0.26f,
                coreGlowAlpha = 0.22f,
                motion = SwimParticleOrbState.CONNECTING.orbMotionConfig(),
                lineWidth = 1.35f,
                particleSize = 3.1f,
                haloParticleSize = 1.65f,
                primary = Orb3DPalette.PurpleSecondary,
                secondary = Orb3DPalette.PurpleSoftGlow,
                visualIntensity = SwimParticleOrbState.CONNECTING.orbVisualIntensity(),
                breathing = SwimParticleOrbState.CONNECTING.orbBreathingProfile(),
                liquidOverlay = SwimParticleOrbState.CONNECTING.orbLiquidOverlayStyle(),
                touchWave = SwimParticleOrbState.CONNECTING.orbTouchWaveProfile(),
                surfaceTurbulence = SwimParticleOrbState.CONNECTING.orbSurfaceTurbulenceProfile(),
                dropletField = SwimParticleOrbState.CONNECTING.orbDropletFieldProfile(),
                ferrofluidMotion = SwimParticleOrbState.CONNECTING.orbFerrofluidMotionProfile(),
                shapeState = SwimParticleOrbState.CONNECTING.orbShapeStateField(),
                holographicPolish = SwimParticleOrbState.CONNECTING.orbHolographicPolishProfile(),
            )
            SwimParticleOrbState.CONNECTED -> Orb3DStateConfig(
                deformation = 0.86f,
                lineAlpha = 0.74f,
                particleAlpha = 0.68f,
                shimmerAlpha = 0.22f,
                coreGlowAlpha = 0.16f,
                motion = SwimParticleOrbState.CONNECTED.orbMotionConfig(),
                lineWidth = 1.25f,
                particleSize = 2.8f,
                haloParticleSize = 1.55f,
                primary = Orb3DPalette.PurplePrimary,
                secondary = Orb3DPalette.PurpleSoftGlow,
                visualIntensity = SwimParticleOrbState.CONNECTED.orbVisualIntensity(),
                breathing = SwimParticleOrbState.CONNECTED.orbBreathingProfile(),
                liquidOverlay = SwimParticleOrbState.CONNECTED.orbLiquidOverlayStyle(),
                touchWave = SwimParticleOrbState.CONNECTED.orbTouchWaveProfile(),
                surfaceTurbulence = SwimParticleOrbState.CONNECTED.orbSurfaceTurbulenceProfile(),
                dropletField = SwimParticleOrbState.CONNECTED.orbDropletFieldProfile(),
                ferrofluidMotion = SwimParticleOrbState.CONNECTED.orbFerrofluidMotionProfile(),
                shapeState = SwimParticleOrbState.CONNECTED.orbShapeStateField(),
                holographicPolish = SwimParticleOrbState.CONNECTED.orbHolographicPolishProfile(),
            )
            SwimParticleOrbState.UNSTABLE -> Orb3DStateConfig(
                deformation = 1.42f,
                lineAlpha = 0.46f,
                particleAlpha = 0.66f,
                shimmerAlpha = 0.30f,
                coreGlowAlpha = 0.15f,
                motion = SwimParticleOrbState.UNSTABLE.orbMotionConfig(),
                lineWidth = 1.25f,
                particleSize = 3.0f,
                haloParticleSize = 1.6f,
                primary = Orb3DPalette.PurplePrimary,
                secondary = Orb3DPalette.PurpleSecondary,
                visualIntensity = SwimParticleOrbState.UNSTABLE.orbVisualIntensity(),
                breathing = SwimParticleOrbState.UNSTABLE.orbBreathingProfile(),
                liquidOverlay = SwimParticleOrbState.UNSTABLE.orbLiquidOverlayStyle(),
                touchWave = SwimParticleOrbState.UNSTABLE.orbTouchWaveProfile(),
                surfaceTurbulence = SwimParticleOrbState.UNSTABLE.orbSurfaceTurbulenceProfile(),
                dropletField = SwimParticleOrbState.UNSTABLE.orbDropletFieldProfile(),
                ferrofluidMotion = SwimParticleOrbState.UNSTABLE.orbFerrofluidMotionProfile(),
                shapeState = SwimParticleOrbState.UNSTABLE.orbShapeStateField(),
                holographicPolish = SwimParticleOrbState.UNSTABLE.orbHolographicPolishProfile(),
            )
            SwimParticleOrbState.FAILED -> Orb3DStateConfig(
                deformation = 0.68f,
                lineAlpha = 0.24f,
                particleAlpha = 0.40f,
                shimmerAlpha = 0.18f,
                coreGlowAlpha = 0.10f,
                motion = SwimParticleOrbState.FAILED.orbMotionConfig(),
                lineWidth = 1.15f,
                particleSize = 2.4f,
                haloParticleSize = 2.0f,
                primary = Orb3DPalette.FailureRed,
                secondary = Orb3DPalette.FailureAmber,
                visualIntensity = SwimParticleOrbState.FAILED.orbVisualIntensity(),
                breathing = SwimParticleOrbState.FAILED.orbBreathingProfile(),
                liquidOverlay = SwimParticleOrbState.FAILED.orbLiquidOverlayStyle(),
                touchWave = SwimParticleOrbState.FAILED.orbTouchWaveProfile(),
                surfaceTurbulence = SwimParticleOrbState.FAILED.orbSurfaceTurbulenceProfile(),
                dropletField = SwimParticleOrbState.FAILED.orbDropletFieldProfile(),
                ferrofluidMotion = SwimParticleOrbState.FAILED.orbFerrofluidMotionProfile(),
                shapeState = SwimParticleOrbState.FAILED.orbShapeStateField(),
                holographicPolish = SwimParticleOrbState.FAILED.orbHolographicPolishProfile(),
            )
        }
        return if (reducedMotion) {
            base.copy(
                deformation = base.deformation * 0.42f,
                lineAlpha = base.lineAlpha * 0.82f,
                particleAlpha = base.particleAlpha * 0.72f,
                shimmerAlpha = base.shimmerAlpha * 0.35f,
                coreGlowAlpha = base.coreGlowAlpha * 0.60f,
                motion = base.motion.copy(
                    breathingSpeed = base.motion.breathingSpeed * 0.45f,
                    breathingAmplitude = base.motion.breathingAmplitude * 0.45f,
                    waveSpeed = base.motion.waveSpeed * 0.45f,
                    particleFlowSpeed = base.motion.particleFlowSpeed * 0.40f,
                    lightFlowSpeed = base.motion.lightFlowSpeed * 0.45f,
                ),
                visualIntensity = base.visualIntensity,
                breathing = base.breathing.copy(
                    baseSpeed = base.breathing.baseSpeed * 0.55f,
                    baseAmplitude = base.breathing.baseAmplitude * 0.50f,
                    pulseSpeed = base.breathing.pulseSpeed * 0.55f,
                    pulseAmplitude = base.breathing.pulseAmplitude * 0.42f,
                    pulseSharpness = base.breathing.pulseSharpness * 0.75f,
                    bumpAmplitude = base.breathing.bumpAmplitude * 0.45f,
                    bumpSpeed = base.breathing.bumpSpeed * 0.55f,
                    touchBoost = base.breathing.touchBoost * 0.65f,
                    haloPulse = base.breathing.haloPulse * 0.55f,
                    flowAcceleration = base.breathing.flowAcceleration * 0.70f,
                    instabilityJitter = base.breathing.instabilityJitter * 0.35f,
                ),
                liquidOverlay = base.liquidOverlay.copy(
                    overlayAlpha = base.liquidOverlay.overlayAlpha * 0.72f,
                    flowSpeed = base.liquidOverlay.flowSpeed * 0.50f,
                    iridescence = base.liquidOverlay.iridescence * 0.70f,
                    symbioteViscosity = (base.liquidOverlay.symbioteViscosity * 1.05f).coerceAtMost(1f),
                    spikeHeight = base.liquidOverlay.spikeHeight * 0.45f,
                    magneticPulseStrength = base.liquidOverlay.magneticPulseStrength * 0.45f,
                ),
                touchWave = base.touchWave.copy(
                    waveAmplitude = base.touchWave.waveAmplitude * 0.45f,
                    edgeDisplacement = base.touchWave.edgeDisplacement * 0.45f,
                    forwardPush = base.touchWave.forwardPush * 0.55f,
                ),
                surfaceTurbulence = base.surfaceTurbulence.copy(
                    surfaceWarpAmplitude = base.surfaceTurbulence.surfaceWarpAmplitude * 0.45f,
                    edgeChaosAmplitude = base.surfaceTurbulence.edgeChaosAmplitude * 0.45f,
                    frontDepthPush = base.surfaceTurbulence.frontDepthPush * 0.55f,
                    fieldRestlessness = base.surfaceTurbulence.fieldRestlessness * 0.50f,
                ),
                dropletField = base.dropletField.copy(
                    fieldLineStrength = base.dropletField.fieldLineStrength * 0.55f,
                    clusterStrength = base.dropletField.clusterStrength * 0.55f,
                    clusterRestlessness = base.dropletField.clusterRestlessness * 0.45f,
                ),
                ferrofluidMotion = base.ferrofluidMotion.copy(
                    corePulseScale = base.ferrofluidMotion.corePulseScale * 0.55f,
                    internalDriftAmplitudePx = base.ferrofluidMotion.internalDriftAmplitudePx * 0.55f,
                    fluidDriftAmplitudePx = base.ferrofluidMotion.fluidDriftAmplitudePx * 0.55f,
                    spikeGrowthBonusPx = base.ferrofluidMotion.spikeGrowthBonusPx * 0.55f,
                    spikeActivity = base.ferrofluidMotion.spikeActivity * 0.60f,
                    microJitter = base.ferrofluidMotion.microJitter * 0.45f,
                    highlightTravelPx = base.ferrofluidMotion.highlightTravelPx * 0.55f,
                    highlightIntensityBoost = base.ferrofluidMotion.highlightIntensityBoost * 0.55f,
                    rimBreathing = base.ferrofluidMotion.rimBreathing * 0.50f,
                    irregularity = base.ferrofluidMotion.irregularity * 0.45f,
                    highlightFlicker = base.ferrofluidMotion.highlightFlicker * 0.45f,
                    edgeTickVisibility = base.ferrofluidMotion.edgeTickVisibility * 0.45f,
                ),
                shapeState = base.shapeState.copy(
                    lobeHeight = base.shapeState.lobeHeight * 0.45f,
                    dropletChance = base.shapeState.dropletChance * 0.35f,
                    restlessness = base.shapeState.restlessness * 0.45f,
                ),
                holographicPolish = base.holographicPolish.copy(
                    volumeFogAlpha = base.holographicPolish.volumeFogAlpha * 0.70f,
                    breathTextureAlpha = base.holographicPolish.breathTextureAlpha * 0.45f,
                    fiberHighlight = base.holographicPolish.fiberHighlight * 0.65f,
                    runnerIntensity = base.holographicPolish.runnerIntensity * 0.50f,
                    membraneAlpha = base.holographicPolish.membraneAlpha * 0.80f,
                    microReliefAlpha = base.holographicPolish.microReliefAlpha * 0.60f,
                    specularSheen = base.holographicPolish.specularSheen * 0.55f,
                    iridescentFilm = base.holographicPolish.iridescentFilm * 0.65f,
                    rimMaterialization = base.holographicPolish.rimMaterialization * 0.75f,
                    depthFogCenterAlpha = base.holographicPolish.depthFogCenterAlpha * 0.85f,
                    depthFogMiddleAlpha = base.holographicPolish.depthFogMiddleAlpha * 0.80f,
                    glassShellAlpha = base.holographicPolish.glassShellAlpha * 0.70f,
                    premiumGrainAlpha = base.holographicPolish.premiumGrainAlpha * 0.55f,
                    glassStreakAlpha = base.holographicPolish.glassStreakAlpha * 0.55f,
                    backlineOpacityReduction = base.holographicPolish.backlineOpacityReduction * 0.70f,
                    backlineSaturationReduction = base.holographicPolish.backlineSaturationReduction * 0.70f,
                    backlineSoftness = base.holographicPolish.backlineSoftness * 0.65f,
                    frostedGlassAlpha = base.holographicPolish.frostedGlassAlpha * 0.70f,
                    dichroicReflection = base.holographicPolish.dichroicReflection * 0.70f,
                    internalRefraction = base.holographicPolish.internalRefraction * 0.60f,
                    microPrismCoating = base.holographicPolish.microPrismCoating * 0.55f,
                    smokedGlassAlpha = base.holographicPolish.smokedGlassAlpha * 0.70f,
                    grapheneMeshSoftening = base.holographicPolish.grapheneMeshSoftening * 0.70f,
                    magneticDustAlpha = base.holographicPolish.magneticDustAlpha * 0.50f,
                    thinFresnelRim = base.holographicPolish.thinFresnelRim * 0.70f,
                    opalescentDiffusionAlpha = base.holographicPolish.opalescentDiffusionAlpha * 0.70f,
                    subsurfaceScattering = base.holographicPolish.subsurfaceScattering * 0.65f,
                    wireClarityReduction = base.holographicPolish.wireClarityReduction * 0.70f,
                    innerMilkyGlow = base.holographicPolish.innerMilkyGlow * 0.60f,
                ),
            )
        } else {
            base
        }
    }

    private fun normalizedNoise(seed: Float): Float {
        val value = sin(seed) * 43758.5453f
        return value - kotlin.math.floor(value)
    }

    private fun allocateFloatBuffer(floatCapacity: Int): FloatBuffer {
        return ByteBuffer
            .allocateDirect(floatCapacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}

private data class Orb3DStateConfig(
    val deformation: Float,
    val lineAlpha: Float,
    val particleAlpha: Float,
    val shimmerAlpha: Float,
    val coreGlowAlpha: Float,
    val motion: OrbMotionConfig,
    val lineWidth: Float,
    val particleSize: Float,
    val haloParticleSize: Float,
    val primary: OrbRgba,
    val secondary: OrbRgba,
    val visualIntensity: OrbVisualIntensity,
    val breathing: OrbBreathingProfile,
    val liquidOverlay: OrbLiquidOverlayStyle,
    val touchWave: OrbTouchWaveProfile,
    val surfaceTurbulence: OrbSurfaceTurbulenceProfile,
    val dropletField: OrbDropletFieldProfile,
    val ferrofluidMotion: OrbFerrofluidMotionProfile,
    val shapeState: OrbShapeStateField,
    val holographicPolish: OrbHolographicPolishProfile,
) {
    val linePointSize: Float = 1f

    fun radiusScale(time: Float): Float {
        val harmonic = sin(time * breathing.baseSpeed)
        val secondary = sin(time * breathing.baseSpeed * 0.47f + 1.4f) * 0.35f
        return 1f + (harmonic + secondary) * breathing.baseAmplitude + breathPulse(time) * breathing.baseAmplitude * 0.82f
    }

    fun breathPulse(time: Float): Float {
        if (breathing.pulseAmplitude <= 0f || breathing.pulseSpeed <= 0f) return 0f
        val cycle = time * breathing.pulseSpeed
        val phase = cycle - floor(cycle)
        val rise = smoothStep(0.06f, 0.24f + breathing.pulseSharpness * 0.08f, phase)
        val fall = 1f - smoothStep(0.32f + breathing.pulseSharpness * 0.10f, 0.98f, phase)
        val mainPulse = (rise * fall).coerceIn(0f, 1f)
        val echo = 0.86f + sin(time * breathing.baseSpeed * 2.7f + 0.9f) * 0.10f +
            sin(time * breathing.baseSpeed * 5.1f + 2.2f) * 0.04f
        return mainPulse * breathing.pulseAmplitude * echo.coerceIn(0.74f, 1.0f)
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lineColor(point: Orb3DPoint, crest: Float): OrbRgba {
        val depthShimmer = 0.90f + sin(point.front * 2.7f + crest * 0.35f) * 0.10f
        val alpha = lineAlpha *
            visualIntensity.lineAlphaMultiplier *
            (0.055f + point.front * 0.58f) *
            point.edge *
            crest *
            depthShimmer
        val brighten = if (crest > 1.02f) 0.34f else 0.14f
        return Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost)
            .brighten(brighten)
            .withAlpha(alpha.coerceIn(0.015f, 0.86f))
    }

    fun veilColor(point: Orb3DPoint): OrbRgba {
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.018f + point.front * 0.12f) * point.edge
        return Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost)
            .brighten(0.18f)
            .withAlpha(alpha.coerceIn(0.01f, 0.34f))
    }

    fun ribbonColor(point: Orb3DPoint, light: Float): OrbRgba {
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.045f + point.front * 0.34f) * point.edge * (0.5f + light * 0.8f)
        return Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost)
            .brighten(0.22f + light * 0.22f)
            .withAlpha(alpha.coerceIn(0.02f, 0.72f))
    }

    fun microRibbonColor(point: Orb3DPoint, light: Float): OrbRgba {
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.020f + point.front * 0.16f) * point.edge * (0.55f + light * 0.65f)
        return mix(
            Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost),
            Orb3DPalette.TitaniumGlow,
            0.08f + light * 0.18f,
        )
            .brighten(0.16f + light * 0.20f)
            .withAlpha(alpha.coerceIn(0.01f, 0.42f))
    }

    fun shortSegmentColor(point: Orb3DPoint, blink: Float): OrbRgba {
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.030f + point.front * 0.22f + blink * 0.10f) * point.edge
        return Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost)
            .brighten(0.24f + blink * 0.24f)
            .withAlpha(alpha.coerceIn(0.012f, 0.58f))
    }

    fun filamentColor(point: Orb3DPoint, energy: Float): OrbRgba {
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.040f + point.front * 0.26f) * point.edge * (0.42f + energy * 0.88f)
        return mix(
            Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost),
            Orb3DPalette.TitaniumGlow,
            0.12f + energy * 0.28f,
        )
            .brighten(0.20f + energy * 0.26f)
            .withAlpha(alpha.coerceIn(0.018f, 0.66f))
    }

    fun primarySignalArcColor(point: Orb3DPoint, runner: Float): OrbRgba {
        val frontGate = 0.18f + point.front * 0.82f
        val runnerHot = if (runner > 0.72f) (runner - 0.72f) * 3.57f else 0f
        val alpha = lineAlpha * visualIntensity.lineAlphaMultiplier *
            (0.070f + point.front * 0.46f + runnerHot * 0.18f) *
            point.edge *
            frontGate *
            (0.82f + holographicPolish.runnerIntensity * 0.42f)
        return mix(
            Orb3DPalette.surfaceColor(point, primary, secondary, visualIntensity.saturationBoost),
            Orb3DPalette.TitaniumGlow,
            0.24f + runnerHot * 0.44f,
        )
            .brighten(0.30f + runnerHot * 0.34f)
            .withAlpha(alpha.coerceIn(0.030f, 0.82f))
    }

    fun rimPointSize(loop: Int): Float {
        return (9f + loop * 3f) * visualIntensity.rimBloomMultiplier
    }

    fun surfaceDustPointSize(front: Float, index: Int): Float {
        val variance = 0.72f + (index % 11) * 0.028f
        return particleSize * (0.34f + front * 0.20f) * variance
    }

    fun intersectionPointSize(spark: Float): Float {
        return particleSize * (0.76f + spark * 0.42f)
    }

    val skinIntensity: Float
        get() = (0.38f + visualIntensity.saturationBoost * 0.08f + lineAlpha * 0.04f).coerceIn(0.34f, 0.58f)
}
