package com.swimvpn.app.ui.orb3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (PI * 2.0).toFloat()

data class Orb3DPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float,
    val sideMix: Float,
    val front: Float,
    val edge: Float,
)

data class Orb3DRenderCounts(
    val meshLatitudes: Int,
    val meshLongitudes: Int,
    val particleCount: Int,
    val haloCount: Int,
    val visualLayers: Orb3DVisualLayers,
) {
    val visibleParticleCount: Int =
        particleCount +
            haloCount +
            visualLayers.coreGlowCount +
            visualLayers.rimLoopCount * visualLayers.rimSteps +
            visualLayers.surfaceDustCount +
            visualLayers.ambientDustCount +
            visualLayers.intersectionPointCount

    val lineSegmentCount: Int =
        (meshLatitudes * meshLongitudes) +
            (meshLongitudes * (meshLatitudes - 1)) +
            (visualLayers.surfaceVeilBands * visualLayers.surfaceVeilSteps) +
            (visualLayers.diagonalRibbonCount * visualLayers.diagonalRibbonSteps) +
            (visualLayers.microRibbonCount * visualLayers.microRibbonSteps) +
            visualLayers.shortSegmentCount +
            (visualLayers.localFilamentCount * visualLayers.localFilamentSteps) +
            (visualLayers.primarySignalArcCount * visualLayers.primarySignalArcSteps)

    val lineUpdateIntervalFrames: Int = Int.MAX_VALUE

    val lineVertexCount: Int = lineSegmentCount * 2

    val lineFloatCount: Int = lineVertexCount * 8

    val lineParamFloatCount: Int = lineVertexCount * 4
}

data class Orb3DVisualLayers(
    val surfaceVeilBands: Int,
    val surfaceVeilSteps: Int,
    val diagonalRibbonCount: Int,
    val diagonalRibbonSteps: Int,
    val microRibbonCount: Int,
    val microRibbonSteps: Int,
    val shortSegmentCount: Int,
    val localFilamentCount: Int,
    val localFilamentSteps: Int,
    val primarySignalArcCount: Int,
    val primarySignalArcSteps: Int,
    val rimLoopCount: Int,
    val rimSteps: Int,
    val coreGlowCount: Int,
    val surfaceDustCount: Int,
    val ambientDustCount: Int,
    val intersectionPointCount: Int,
    val proceduralSkinGrain: Float,
    val proceduralHaloRing: Float,
)

data class OrbVisualIntensity(
    val lineAlphaMultiplier: Float,
    val particleAlphaMultiplier: Float,
    val coreGlowMultiplier: Float,
    val rimBloomMultiplier: Float,
    val saturationBoost: Float,
)

data class OrbHolographicPolishProfile(
    val volumeFogAlpha: Float,
    val breathTextureAlpha: Float,
    val fiberHighlight: Float,
    val depthSeparation: Float,
    val rimPrism: Float,
    val runnerIntensity: Float,
    val centerClarity: Float,
    val membraneAlpha: Float,
    val microReliefAlpha: Float,
    val specularSheen: Float,
    val iridescentFilm: Float,
    val rimMaterialization: Float,
    val depthFogCenterAlpha: Float,
    val depthFogMiddleAlpha: Float,
    val glassShellAlpha: Float,
    val premiumGrainAlpha: Float,
    val glassStreakAlpha: Float,
    val backlineOpacityReduction: Float,
    val backlineSaturationReduction: Float,
    val backlineSoftness: Float,
    val frostedGlassAlpha: Float,
    val dichroicReflection: Float,
    val internalRefraction: Float,
    val microPrismCoating: Float,
    val smokedGlassAlpha: Float,
    val grapheneMeshSoftening: Float,
    val magneticDustAlpha: Float,
    val thinFresnelRim: Float,
    val opalescentDiffusionAlpha: Float,
    val subsurfaceScattering: Float,
    val wireClarityReduction: Float,
    val innerMilkyGlow: Float,
    val frontLineBrightnessBoost: Float = 0f,
    val opaqueShellAlpha: Float = 0f,
)

data class OrbMotionConfig(
    val breathingSpeed: Float,
    val breathingAmplitude: Float,
    val waveSpeed: Float,
    val waveAmplitudeDp: Float,
    val particleFlowSpeed: Float,
    val lightFlowSpeed: Float,
    val globalRotationEnabled: Boolean = false,
    val globalRotationDegreesPerSecond: Float = 0f,
) {
    init {
        require(!globalRotationEnabled) {
            "Global orb rotation is forbidden for SwimHolographicOrb3D."
        }
        require(globalRotationDegreesPerSecond == 0f) {
            "Global orb rotation must stay at 0 degrees per second."
        }
    }
}

data class OrbBreathingProfile(
    val baseSpeed: Float,
    val baseAmplitude: Float,
    val pulseSpeed: Float,
    val pulseAmplitude: Float,
    val pulseSharpness: Float,
    val bumpCount: Int,
    val bumpAmplitude: Float,
    val bumpSpeed: Float,
    val touchBoost: Float,
    val haloPulse: Float,
    val flowAcceleration: Float,
    val instabilityJitter: Float,
    val failureCooling: Float,
)

enum class OrbLiquidTextureMode {
    OPAQUE_ENERGY_SHELL,
    SYMBIOTE_MERCURY_SHELL,
    MAGNETIC_FERROFLUID_HYBRID,
}

data class OrbLiquidOverlayStyle(
    val mode: OrbLiquidTextureMode,
    val overlayAlpha: Float,
    val flowSpeed: Float,
    val causticScale: Float,
    val iridescence: Float,
    val wireVisibilityUnderSkin: Float,
    val particleVisibility: Float,
    val mercuryReflection: Float,
    val symbioteViscosity: Float,
    val coreDropletAlpha: Float,
    val spikeDensity: Float,
    val spikeHeight: Float,
    val spikeSharpness: Float,
    val magneticPulseStrength: Float,
    val fieldStability: Float,
    val cooling: Float,
)

data class OrbTouchWaveProfile(
    val waveAmplitude: Float,
    val waveSpeed: Float,
    val waveFrequency: Float,
    val waveDamping: Float,
    val waveFalloff: Float,
    val edgeDisplacement: Float,
    val forwardPush: Float,
)

data class OrbSurfaceTurbulenceProfile(
    val surfaceWarpAmplitude: Float,
    val edgeChaosAmplitude: Float,
    val lobeVariety: Float,
    val frontDepthPush: Float,
    val fieldRestlessness: Float,
)

data class OrbDropletFieldProfile(
    val fieldLineStrength: Float,
    val clusterStrength: Float,
    val dropletGloss: Float,
    val magneticAttraction: Float,
    val clusterRestlessness: Float,
)

data class OrbFerrofluidMotionProfile(
    val corePulseScale: Float,
    val corePulseDurationSeconds: Float,
    val internalDriftAmplitudePx: Float,
    val internalDriftDurationSeconds: Float,
    val fluidDriftAmplitudePx: Float,
    val fluidDriftDurationSeconds: Float,
    val contourActivityZones: Int,
    val spikeBaseLengthPx: Float,
    val spikeGrowthBonusPx: Float,
    val spikeActivity: Float,
    val spikeCycleDurationSeconds: Float,
    val microJitter: Float,
    val highlightTravelPx: Float,
    val highlightIntensityBoost: Float,
    val highlightResponseLagSeconds: Float,
    val rimBreathing: Float,
    val irregularity: Float,
    val highlightFlicker: Float,
    val edgeTickVisibility: Float,
)

enum class OrbMotionEasing {
    Linear,
    EaseOutCubic,
    EaseInOutCubic,
}

data class OrbFerrofluidTransitionProfile(
    val durationMillis: Int,
    val easing: OrbMotionEasing,
)

data class OrbShapeStateField(
    val lobeCount: Int,
    val lobeHeight: Float,
    val lobeSharpness: Float,
    val dropletChance: Float,
    val symmetry: Float,
    val restlessness: Float,
    val reabsorptionSpeed: Float,
    val edgeContainment: Float,
)

fun SwimParticleOrbState.orbShapeStateField(): OrbShapeStateField {
    return OrbShapeStateField(
        lobeCount = 0,
        lobeHeight = 0f,
        lobeSharpness = 0f,
        dropletChance = 0f,
        symmetry = 1f,
        restlessness = 0f,
        reabsorptionSpeed = 1f,
        edgeContainment = 1f,
    )
}

fun SwimParticleOrbState.orbHolographicPolishProfile(): OrbHolographicPolishProfile {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbHolographicPolishProfile(
            volumeFogAlpha = 0.045f,
            breathTextureAlpha = 0.022f,
            fiberHighlight = 0.36f,
            depthSeparation = 0.58f,
            rimPrism = 0.30f,
            runnerIntensity = 0.22f,
            centerClarity = 0.62f,
            membraneAlpha = 0.026f,
            microReliefAlpha = 0.015f,
            specularSheen = 0.12f,
            iridescentFilm = 0.06f,
            rimMaterialization = 0.18f,
            depthFogCenterAlpha = 0.22f,
            depthFogMiddleAlpha = 0.10f,
            glassShellAlpha = 0.080f,
            premiumGrainAlpha = 0.030f,
            glassStreakAlpha = 0.060f,
            backlineOpacityReduction = 0.28f,
            backlineSaturationReduction = 0.14f,
            backlineSoftness = 0.30f,
            frostedGlassAlpha = 0.040f,
            dichroicReflection = 0.12f,
            internalRefraction = 0.070f,
            microPrismCoating = 0.028f,
            smokedGlassAlpha = 0.065f,
            grapheneMeshSoftening = 0.12f,
            magneticDustAlpha = 0.008f,
            thinFresnelRim = 0.38f,
            opalescentDiffusionAlpha = 0.12f,
            subsurfaceScattering = 0.10f,
            wireClarityReduction = 0.58f,
            innerMilkyGlow = 0.070f,
        )
        SwimParticleOrbState.CONNECTING -> OrbHolographicPolishProfile(
            volumeFogAlpha = 0.14f,
            breathTextureAlpha = 0.105f,
            fiberHighlight = 0.76f,
            depthSeparation = 0.88f,
            rimPrism = 0.62f,
            runnerIntensity = 0.72f,
            centerClarity = 0.72f,
            membraneAlpha = 0.086f,
            microReliefAlpha = 0.048f,
            specularSheen = 0.30f,
            iridescentFilm = 0.18f,
            rimMaterialization = 0.34f,
            depthFogCenterAlpha = 0.32f,
            depthFogMiddleAlpha = 0.16f,
            glassShellAlpha = 0.16f,
            premiumGrainAlpha = 0.052f,
            glassStreakAlpha = 0.130f,
            backlineOpacityReduction = 0.38f,
            backlineSaturationReduction = 0.24f,
            backlineSoftness = 0.58f,
            frostedGlassAlpha = 0.095f,
            dichroicReflection = 0.30f,
            internalRefraction = 0.20f,
            microPrismCoating = 0.072f,
            smokedGlassAlpha = 0.150f,
            grapheneMeshSoftening = 0.28f,
            magneticDustAlpha = 0.022f,
            thinFresnelRim = 0.68f,
            opalescentDiffusionAlpha = 0.34f,
            subsurfaceScattering = 0.30f,
            wireClarityReduction = 0.86f,
            innerMilkyGlow = 0.22f,
        )
        SwimParticleOrbState.CONNECTED -> OrbHolographicPolishProfile(
            volumeFogAlpha = 0.115f,
            breathTextureAlpha = 0.070f,
            fiberHighlight = 0.62f,
            depthSeparation = 0.84f,
            rimPrism = 0.52f,
            runnerIntensity = 0.24f,
            centerClarity = 0.80f,
            membraneAlpha = 0.068f,
            microReliefAlpha = 0.034f,
            specularSheen = 0.24f,
            iridescentFilm = 0.14f,
            rimMaterialization = 0.30f,
            depthFogCenterAlpha = 0.32f,
            depthFogMiddleAlpha = 0.16f,
            glassShellAlpha = 0.14f,
            premiumGrainAlpha = 0.044f,
            glassStreakAlpha = 0.110f,
            backlineOpacityReduction = 0.35f,
            backlineSaturationReduction = 0.20f,
            backlineSoftness = 0.54f,
            frostedGlassAlpha = 0.085f,
            dichroicReflection = 0.24f,
            internalRefraction = 0.16f,
            microPrismCoating = 0.058f,
            smokedGlassAlpha = 0.140f,
            grapheneMeshSoftening = 0.22f,
            magneticDustAlpha = 0.018f,
            thinFresnelRim = 0.60f,
            opalescentDiffusionAlpha = 0.22f,
            subsurfaceScattering = 0.20f,
            wireClarityReduction = 0.62f,
            innerMilkyGlow = 0.18f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbHolographicPolishProfile(
            volumeFogAlpha = 0.135f,
            breathTextureAlpha = 0.090f,
            fiberHighlight = 0.70f,
            depthSeparation = 0.92f,
            rimPrism = 0.58f,
            runnerIntensity = 0.58f,
            centerClarity = 0.68f,
            membraneAlpha = 0.078f,
            microReliefAlpha = 0.056f,
            specularSheen = 0.27f,
            iridescentFilm = 0.16f,
            rimMaterialization = 0.34f,
            depthFogCenterAlpha = 0.34f,
            depthFogMiddleAlpha = 0.18f,
            glassShellAlpha = 0.16f,
            premiumGrainAlpha = 0.056f,
            glassStreakAlpha = 0.125f,
            backlineOpacityReduction = 0.40f,
            backlineSaturationReduction = 0.25f,
            backlineSoftness = 0.60f,
            frostedGlassAlpha = 0.095f,
            dichroicReflection = 0.28f,
            internalRefraction = 0.18f,
            microPrismCoating = 0.080f,
            smokedGlassAlpha = 0.155f,
            grapheneMeshSoftening = 0.30f,
            magneticDustAlpha = 0.032f,
            thinFresnelRim = 0.72f,
            opalescentDiffusionAlpha = 0.36f,
            subsurfaceScattering = 0.32f,
            wireClarityReduction = 0.93f,
            innerMilkyGlow = 0.24f,
        )
        SwimParticleOrbState.FAILED -> OrbHolographicPolishProfile(
            volumeFogAlpha = 0.040f,
            breathTextureAlpha = 0.026f,
            fiberHighlight = 0.42f,
            depthSeparation = 0.62f,
            rimPrism = 0.32f,
            runnerIntensity = 0.16f,
            centerClarity = 0.70f,
            membraneAlpha = 0.022f,
            microReliefAlpha = 0.018f,
            specularSheen = 0.10f,
            iridescentFilm = 0.07f,
            rimMaterialization = 0.20f,
            depthFogCenterAlpha = 0.26f,
            depthFogMiddleAlpha = 0.12f,
            glassShellAlpha = 0.090f,
            premiumGrainAlpha = 0.035f,
            glassStreakAlpha = 0.075f,
            backlineOpacityReduction = 0.32f,
            backlineSaturationReduction = 0.18f,
            backlineSoftness = 0.36f,
            frostedGlassAlpha = 0.048f,
            dichroicReflection = 0.13f,
            internalRefraction = 0.080f,
            microPrismCoating = 0.035f,
            smokedGlassAlpha = 0.075f,
            grapheneMeshSoftening = 0.16f,
            magneticDustAlpha = 0.012f,
            thinFresnelRim = 0.46f,
            opalescentDiffusionAlpha = 0.16f,
            subsurfaceScattering = 0.12f,
            wireClarityReduction = 0.62f,
            innerMilkyGlow = 0.090f,
        )
    }
}

fun SwimParticleOrbState.orbDropletFieldProfile(): OrbDropletFieldProfile {
    return OrbDropletFieldProfile(
        fieldLineStrength = 0f,
        clusterStrength = 0f,
        dropletGloss = 0f,
        magneticAttraction = 0f,
        clusterRestlessness = 0f,
    )
}

fun SwimParticleOrbState.orbFerrofluidMotionProfile(): OrbFerrofluidMotionProfile {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbFerrofluidMotionProfile(
            corePulseScale = 0.008f,
            corePulseDurationSeconds = 4.8f,
            internalDriftAmplitudePx = 1.5f,
            internalDriftDurationSeconds = 11.5f,
            fluidDriftAmplitudePx = 2.0f,
            fluidDriftDurationSeconds = 8.0f,
            contourActivityZones = 0,
            spikeBaseLengthPx = 0f,
            spikeGrowthBonusPx = 0f,
            spikeActivity = 0f,
            spikeCycleDurationSeconds = 3.8f,
            microJitter = 0f,
            highlightTravelPx = 0f,
            highlightIntensityBoost = 0f,
            highlightResponseLagSeconds = 0.18f,
            rimBreathing = 0.04f,
            irregularity = 0f,
            highlightFlicker = 0f,
            edgeTickVisibility = 0f,
        )
        SwimParticleOrbState.CONNECTING -> OrbFerrofluidMotionProfile(
            corePulseScale = 0.030f,
            corePulseDurationSeconds = 2.1f,
            internalDriftAmplitudePx = 5.0f,
            internalDriftDurationSeconds = 7.2f,
            fluidDriftAmplitudePx = 6.0f,
            fluidDriftDurationSeconds = 4.5f,
            contourActivityZones = 0,
            spikeBaseLengthPx = 0f,
            spikeGrowthBonusPx = 0f,
            spikeActivity = 0f,
            spikeCycleDurationSeconds = 2.2f,
            microJitter = 0f,
            highlightTravelPx = 0f,
            highlightIntensityBoost = 0f,
            highlightResponseLagSeconds = 0.08f,
            rimBreathing = 0.12f,
            irregularity = 0f,
            highlightFlicker = 0f,
            edgeTickVisibility = 0f,
        )
        SwimParticleOrbState.CONNECTED -> OrbFerrofluidMotionProfile(
            corePulseScale = 0.015f,
            corePulseDurationSeconds = 4.1f,
            internalDriftAmplitudePx = 3.5f,
            internalDriftDurationSeconds = 9.2f,
            fluidDriftAmplitudePx = 1.5f,
            fluidDriftDurationSeconds = 8.2f,
            contourActivityZones = 0,
            spikeBaseLengthPx = 0f,
            spikeGrowthBonusPx = 0f,
            spikeActivity = 0f,
            spikeCycleDurationSeconds = 3.4f,
            microJitter = 0f,
            highlightTravelPx = 0f,
            highlightIntensityBoost = 0f,
            highlightResponseLagSeconds = 0.13f,
            rimBreathing = 0.045f,
            irregularity = 0f,
            highlightFlicker = 0f,
            edgeTickVisibility = 0f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbFerrofluidMotionProfile(
            corePulseScale = 0.020f,
            corePulseDurationSeconds = 2.8f,
            internalDriftAmplitudePx = 5.0f,
            internalDriftDurationSeconds = 6.8f,
            fluidDriftAmplitudePx = 5.0f,
            fluidDriftDurationSeconds = 4.8f,
            contourActivityZones = 0,
            spikeBaseLengthPx = 0f,
            spikeGrowthBonusPx = 0f,
            spikeActivity = 0f,
            spikeCycleDurationSeconds = 2.0f,
            microJitter = 0f,
            highlightTravelPx = 0f,
            highlightIntensityBoost = 0f,
            highlightResponseLagSeconds = 0.08f,
            rimBreathing = 0.11f,
            irregularity = 0f,
            highlightFlicker = 0f,
            edgeTickVisibility = 0f,
        )
        SwimParticleOrbState.FAILED -> OrbFerrofluidMotionProfile(
            corePulseScale = 0.006f,
            corePulseDurationSeconds = 5.2f,
            internalDriftAmplitudePx = 1.2f,
            internalDriftDurationSeconds = 12.0f,
            fluidDriftAmplitudePx = 1.5f,
            fluidDriftDurationSeconds = 8.6f,
            contourActivityZones = 0,
            spikeBaseLengthPx = 0f,
            spikeGrowthBonusPx = 0f,
            spikeActivity = 0f,
            spikeCycleDurationSeconds = 4.2f,
            microJitter = 0f,
            highlightTravelPx = 0f,
            highlightIntensityBoost = 0f,
            highlightResponseLagSeconds = 0.18f,
            rimBreathing = 0.03f,
            irregularity = 0f,
            highlightFlicker = 0f,
            edgeTickVisibility = 0f,
        )
    }
}

fun SwimParticleOrbState.ferrofluidTransitionFrom(previous: SwimParticleOrbState): OrbFerrofluidTransitionProfile {
    return when {
        previous == SwimParticleOrbState.DISCONNECTED && this == SwimParticleOrbState.CONNECTING ->
            OrbFerrofluidTransitionProfile(1050, OrbMotionEasing.EaseOutCubic)
        previous == SwimParticleOrbState.CONNECTING && this == SwimParticleOrbState.CONNECTED ->
            OrbFerrofluidTransitionProfile(760, OrbMotionEasing.EaseInOutCubic)
        previous == SwimParticleOrbState.CONNECTED && this == SwimParticleOrbState.UNSTABLE ->
            OrbFerrofluidTransitionProfile(560, OrbMotionEasing.EaseInOutCubic)
        previous == SwimParticleOrbState.UNSTABLE && this == SwimParticleOrbState.CONNECTED ->
            OrbFerrofluidTransitionProfile(960, OrbMotionEasing.EaseInOutCubic)
        previous == SwimParticleOrbState.CONNECTED && this == SwimParticleOrbState.DISCONNECTED ->
            OrbFerrofluidTransitionProfile(860, OrbMotionEasing.EaseInOutCubic)
        else ->
            OrbFerrofluidTransitionProfile(720, OrbMotionEasing.EaseInOutCubic)
    }
}

fun SwimParticleOrbState.orbSurfaceTurbulenceProfile(): OrbSurfaceTurbulenceProfile {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbSurfaceTurbulenceProfile(
            surfaceWarpAmplitude = 0.010f,
            edgeChaosAmplitude = 0.006f,
            lobeVariety = 0.12f,
            frontDepthPush = 0.04f,
            fieldRestlessness = 0.08f,
        )
        SwimParticleOrbState.CONNECTING -> OrbSurfaceTurbulenceProfile(
            surfaceWarpAmplitude = 0.038f,
            edgeChaosAmplitude = 0.024f,
            lobeVariety = 0.32f,
            frontDepthPush = 0.16f,
            fieldRestlessness = 0.34f,
        )
        SwimParticleOrbState.CONNECTED -> OrbSurfaceTurbulenceProfile(
            surfaceWarpAmplitude = 0.026f,
            edgeChaosAmplitude = 0.012f,
            lobeVariety = 0.24f,
            frontDepthPush = 0.10f,
            fieldRestlessness = 0.18f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbSurfaceTurbulenceProfile(
            surfaceWarpAmplitude = 0.052f,
            edgeChaosAmplitude = 0.028f,
            lobeVariety = 0.40f,
            frontDepthPush = 0.18f,
            fieldRestlessness = 0.46f,
        )
        SwimParticleOrbState.FAILED -> OrbSurfaceTurbulenceProfile(
            surfaceWarpAmplitude = 0.008f,
            edgeChaosAmplitude = 0.004f,
            lobeVariety = 0.08f,
            frontDepthPush = 0.03f,
            fieldRestlessness = 0.05f,
        )
    }
}

fun SwimParticleOrbState.orbTouchWaveProfile(): OrbTouchWaveProfile {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbTouchWaveProfile(
            waveAmplitude = 0.030f,
            waveSpeed = 2.6f,
            waveFrequency = 18f,
            waveDamping = 3.6f,
            waveFalloff = 2.8f,
            edgeDisplacement = 0.020f,
            forwardPush = 0.18f,
        )
        SwimParticleOrbState.CONNECTING -> OrbTouchWaveProfile(
            waveAmplitude = 0.120f,
            waveSpeed = 4.4f,
            waveFrequency = 30f,
            waveDamping = 2.8f,
            waveFalloff = 2.1f,
            edgeDisplacement = 0.115f,
            forwardPush = 0.78f,
        )
        SwimParticleOrbState.CONNECTED -> OrbTouchWaveProfile(
            waveAmplitude = 0.085f,
            waveSpeed = 3.6f,
            waveFrequency = 24f,
            waveDamping = 3.0f,
            waveFalloff = 2.4f,
            edgeDisplacement = 0.088f,
            forwardPush = 0.60f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbTouchWaveProfile(
            waveAmplitude = 0.112f,
            waveSpeed = 4.8f,
            waveFrequency = 32f,
            waveDamping = 2.4f,
            waveFalloff = 1.9f,
            edgeDisplacement = 0.105f,
            forwardPush = 0.72f,
        )
        SwimParticleOrbState.FAILED -> OrbTouchWaveProfile(
            waveAmplitude = 0.024f,
            waveSpeed = 2.2f,
            waveFrequency = 16f,
            waveDamping = 4.6f,
            waveFalloff = 3.2f,
            edgeDisplacement = 0.014f,
            forwardPush = 0.12f,
        )
    }
}

fun SwimParticleOrbState.orbLiquidOverlayStyle(): OrbLiquidOverlayStyle {
    return OrbLiquidOverlayStyle(
        mode = OrbLiquidTextureMode.OPAQUE_ENERGY_SHELL,
        overlayAlpha = 0f,
        flowSpeed = 0f,
        causticScale = 1f,
        iridescence = 0f,
        wireVisibilityUnderSkin = 1f,
        particleVisibility = 1f,
        mercuryReflection = 0f,
        symbioteViscosity = 0f,
        coreDropletAlpha = 0f,
        spikeDensity = 0f,
        spikeHeight = 0f,
        spikeSharpness = 0f,
        magneticPulseStrength = 0f,
        fieldStability = 1f,
        cooling = 0f,
    )
}

fun SwimParticleOrbState.orbBreathingProfile(): OrbBreathingProfile {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbBreathingProfile(
            baseSpeed = 0.10f,
            baseAmplitude = 0.006f,
            pulseSpeed = 0.10f,
            pulseAmplitude = 0.10f,
            pulseSharpness = 0.42f,
            bumpCount = 2,
            bumpAmplitude = 0.010f,
            bumpSpeed = 0.20f,
            touchBoost = 0.60f,
            haloPulse = 0.24f,
            flowAcceleration = 0.45f,
            instabilityJitter = 0.02f,
            failureCooling = 0.52f,
        )
        SwimParticleOrbState.CONNECTING -> OrbBreathingProfile(
            baseSpeed = 0.30f,
            baseAmplitude = 0.023f,
            pulseSpeed = 0.52f,
            pulseAmplitude = 0.92f,
            pulseSharpness = 0.76f,
            bumpCount = 7,
            bumpAmplitude = 0.038f,
            bumpSpeed = 0.78f,
            touchBoost = 1.85f,
            haloPulse = 0.95f,
            flowAcceleration = 1.85f,
            instabilityJitter = 0.10f,
            failureCooling = 0.0f,
        )
        SwimParticleOrbState.CONNECTED -> OrbBreathingProfile(
            baseSpeed = 0.18f,
            baseAmplitude = 0.015f,
            pulseSpeed = 0.24f,
            pulseAmplitude = 0.38f,
            pulseSharpness = 0.58f,
            bumpCount = 5,
            bumpAmplitude = 0.024f,
            bumpSpeed = 0.42f,
            touchBoost = 1.0f,
            haloPulse = 0.52f,
            flowAcceleration = 1.0f,
            instabilityJitter = 0.04f,
            failureCooling = 0.0f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbBreathingProfile(
            baseSpeed = 0.34f,
            baseAmplitude = 0.020f,
            pulseSpeed = 0.62f,
            pulseAmplitude = 0.74f,
            pulseSharpness = 0.82f,
            bumpCount = 9,
            bumpAmplitude = 0.045f,
            bumpSpeed = 0.92f,
            touchBoost = 1.35f,
            haloPulse = 0.72f,
            flowAcceleration = 1.28f,
            instabilityJitter = 0.34f,
            failureCooling = 0.0f,
        )
        SwimParticleOrbState.FAILED -> OrbBreathingProfile(
            baseSpeed = 0.08f,
            baseAmplitude = 0.004f,
            pulseSpeed = 0.16f,
            pulseAmplitude = 0.22f,
            pulseSharpness = 0.64f,
            bumpCount = 3,
            bumpAmplitude = 0.018f,
            bumpSpeed = 0.34f,
            touchBoost = 0.72f,
            haloPulse = 0.18f,
            flowAcceleration = 0.28f,
            instabilityJitter = 0.22f,
            failureCooling = 0.86f,
        )
    }
}

enum class OrbRenderQuality(val counts: Orb3DRenderCounts) {
    Low(
        Orb3DRenderCounts(
            meshLatitudes = 22,
            meshLongitudes = 16,
            particleCount = 360,
            haloCount = 0,
            visualLayers = Orb3DVisualLayers(
                surfaceVeilBands = 12,
                surfaceVeilSteps = 80,
                diagonalRibbonCount = 5,
                diagonalRibbonSteps = 92,
                microRibbonCount = 5,
                microRibbonSteps = 54,
                shortSegmentCount = 48,
                localFilamentCount = 8,
                localFilamentSteps = 18,
                primarySignalArcCount = 4,
                primarySignalArcSteps = 130,
                rimLoopCount = 0,
                rimSteps = 150,
                coreGlowCount = 3,
                surfaceDustCount = 0,
                ambientDustCount = 32,
                intersectionPointCount = 40,
                proceduralSkinGrain = 0.35f,
                proceduralHaloRing = 0.58f,
            ),
        ),
    ),
    Medium(
        Orb3DRenderCounts(
            meshLatitudes = 62,
            meshLongitudes = 52,
            particleCount = 300,
            haloCount = 0,
            visualLayers = Orb3DVisualLayers(
                surfaceVeilBands = 38,
                surfaceVeilSteps = 150,
                diagonalRibbonCount = 2,
                diagonalRibbonSteps = 100,
                microRibbonCount = 6,
                microRibbonSteps = 60,
                shortSegmentCount = 16,
                localFilamentCount = 2,
                localFilamentSteps = 18,
                primarySignalArcCount = 2,
                primarySignalArcSteps = 150,
                rimLoopCount = 0,
                rimSteps = 42,
                coreGlowCount = 4,
                surfaceDustCount = 0,
                ambientDustCount = 48,
                intersectionPointCount = 42,
                proceduralSkinGrain = 0.80f,
                proceduralHaloRing = 0.72f,
            ),
        ),
    ),
    High(
        Orb3DRenderCounts(
            meshLatitudes = 86,
            meshLongitudes = 70,
            particleCount = 440,
            haloCount = 0,
            visualLayers = Orb3DVisualLayers(
                surfaceVeilBands = 60,
                surfaceVeilSteps = 180,
                diagonalRibbonCount = 2,
                diagonalRibbonSteps = 120,
                microRibbonCount = 8,
                microRibbonSteps = 70,
                shortSegmentCount = 24,
                localFilamentCount = 4,
                localFilamentSteps = 20,
                primarySignalArcCount = 2,
                primarySignalArcSteps = 170,
                rimLoopCount = 0,
                rimSteps = 44,
                coreGlowCount = 5,
                surfaceDustCount = 0,
                ambientDustCount = 72,
                intersectionPointCount = 56,
                proceduralSkinGrain = 1.0f,
                proceduralHaloRing = 0.86f,
            ),
        ),
    ),
    Auto(Medium.counts);

    fun resolve(reducedMotion: Boolean = false): Orb3DRenderCounts {
        return when {
            this == Auto && reducedMotion -> Low.counts
            this == Auto -> Medium.counts
            reducedMotion && this == High -> Medium.counts
            else -> counts
        }
    }
}

object Orb3DGeometry {
    fun deformedSpherePoint(
        longitude: Float,
        latitude: Float,
        time: Float,
        phase: Float,
        deformationAmplitude: Float,
        shellDepth: Float = 1f,
        radiusScale: Float = 1f,
        waveSpeed: Float = 0.45f,
    ): Orb3DPoint {
        val waveTime = time * waveSpeed
        val wave =
            sin(longitude * 2.0f + latitude * 1.4f + waveTime + phase) +
                sin(longitude * 5.0f - latitude * 3.2f - waveTime * 0.56f + phase) * 0.35f +
                sin(longitude * 8.0f + latitude * 2.4f + waveTime * 0.32f + phase * 0.7f) * 0.18f
        val membraneWave =
            sin(longitude * 7.0f + latitude * 4.5f + waveTime * 0.64f + phase * 0.7f) * 0.014f +
                sin(longitude * 13.0f - latitude * 2.0f - waveTime * 0.42f) * 0.008f
        val localRadius = shellDepth * radiusScale * (1f + wave * deformationAmplitude * 0.018f + membraneWave)
        val localLongitude = longitude
        val localLatitude = latitude + sin(waveTime * 0.40f + longitude * 2.0f + phase) * 0.010f

        val cosLatitude = cos(localLatitude)
        val x = cosLatitude * cos(localLongitude) * localRadius
        val y = sin(localLatitude) * localRadius * 0.985f
        val z = cosLatitude * sin(localLongitude) * localRadius
        val planarRim = sqrt((x * x + y * y).coerceAtLeast(0f)).coerceIn(0f, 1.15f)
        val depth = ((z / localRadius.coerceAtLeast(0.001f) + 1f) * 0.5f).coerceIn(0f, 1f)

        return Orb3DPoint(
            x = x,
            y = y,
            z = z,
            radius = sqrt(x * x + y * y + z * z),
            sideMix = ((x / localRadius.coerceAtLeast(0.001f) + 1f) * 0.5f).coerceIn(0f, 1f),
            front = (depth * depth).coerceIn(0f, 1f),
            edge = (0.52f + planarRim * 0.76f).coerceIn(0f, 1.32f),
        )
    }

    fun latitudeForIndex(index: Int, count: Int): Float {
        return -PI.toFloat() / 2f + index / (count - 1f) * PI.toFloat()
    }

    fun longitudeForIndex(index: Int, count: Int): Float {
        return index / count.toFloat() * TWO_PI
    }
}

fun SwimParticleOrbState.orbMotionConfig(): OrbMotionConfig {
    return when (this) {
        SwimParticleOrbState.CONNECTED -> OrbMotionConfig(
            breathingSpeed = 0.18f,
            breathingAmplitude = 0.015f,
            waveSpeed = 0.45f,
            waveAmplitudeDp = 3f,
            particleFlowSpeed = 0.045f,
            lightFlowSpeed = 0.055f,
        )
        SwimParticleOrbState.CONNECTING -> OrbMotionConfig(
            breathingSpeed = 0.28f,
            breathingAmplitude = 0.022f,
            waveSpeed = 0.65f,
            waveAmplitudeDp = 5f,
            particleFlowSpeed = 0.075f,
            lightFlowSpeed = 0.085f,
        )
        SwimParticleOrbState.DISCONNECTED -> OrbMotionConfig(
            breathingSpeed = 0.10f,
            breathingAmplitude = 0.006f,
            waveSpeed = 0.22f,
            waveAmplitudeDp = 1.5f,
            particleFlowSpeed = 0.018f,
            lightFlowSpeed = 0.025f,
        )
        SwimParticleOrbState.UNSTABLE -> OrbMotionConfig(
            breathingSpeed = 0.32f,
            breathingAmplitude = 0.018f,
            waveSpeed = 0.85f,
            waveAmplitudeDp = 6f,
            particleFlowSpeed = 0.060f,
            lightFlowSpeed = 0.095f,
        )
        SwimParticleOrbState.FAILED -> OrbMotionConfig(
            breathingSpeed = 0.08f,
            breathingAmplitude = 0.004f,
            waveSpeed = 0.30f,
            waveAmplitudeDp = 2.4f,
            particleFlowSpeed = 0.014f,
            lightFlowSpeed = 0.020f,
        )
    }
}

fun SwimParticleOrbState.orbVisualIntensity(): OrbVisualIntensity {
    return when (this) {
        SwimParticleOrbState.DISCONNECTED -> OrbVisualIntensity(0.90f, 0.82f, 0.88f, 0.82f, 0.96f)
        SwimParticleOrbState.CONNECTING -> OrbVisualIntensity(1.42f, 1.02f, 1.58f, 1.45f, 1.25f)
        SwimParticleOrbState.CONNECTED -> OrbVisualIntensity(1.32f, 0.98f, 1.40f, 1.28f, 1.22f)
        SwimParticleOrbState.UNSTABLE -> OrbVisualIntensity(1.25f, 0.96f, 1.30f, 1.38f, 1.20f)
        SwimParticleOrbState.FAILED -> OrbVisualIntensity(0.98f, 0.78f, 0.72f, 0.96f, 1.08f)
    }
}
