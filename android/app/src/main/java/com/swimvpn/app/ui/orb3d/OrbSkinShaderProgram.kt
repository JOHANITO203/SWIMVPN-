package com.swimvpn.app.ui.orb3d

import android.opengl.GLES30

internal class OrbSkinShaderProgram {
    var programId: Int = 0
        private set
    var timeLocation: Int = -1
        private set
    var aspectLocation: Int = -1
        private set
    var grainLocation: Int = -1
        private set
    var intensityLocation: Int = -1
        private set
    var haloLocation: Int = -1
        private set
    var haloPulseLocation: Int = -1
        private set
    var bumpCountLocation: Int = -1
        private set
    var bumpAmplitudeLocation: Int = -1
        private set
    var touchLocation: Int = -1
        private set
    var touchStrengthLocation: Int = -1
        private set
    var liquidAlphaLocation: Int = -1
        private set
    var liquidFlowSpeedLocation: Int = -1
        private set
    var liquidCausticScaleLocation: Int = -1
        private set
    var liquidIridescenceLocation: Int = -1
        private set
    var liquidCoolingLocation: Int = -1
        private set
    var mercuryReflectionLocation: Int = -1
        private set
    var symbioteViscosityLocation: Int = -1
        private set
    var coreDropletAlphaLocation: Int = -1
        private set
    var spikeDensityLocation: Int = -1
        private set
    var spikeHeightLocation: Int = -1
        private set
    var spikeSharpnessLocation: Int = -1
        private set
    var magneticPulseStrengthLocation: Int = -1
        private set
    var fieldStabilityLocation: Int = -1
        private set
    var touchAgeLocation: Int = -1
        private set
    var waveAmplitudeLocation: Int = -1
        private set
    var waveSpeedLocation: Int = -1
        private set
    var waveFrequencyLocation: Int = -1
        private set
    var waveDampingLocation: Int = -1
        private set
    var waveFalloffLocation: Int = -1
        private set
    var edgeDisplacementLocation: Int = -1
        private set
    var forwardPushLocation: Int = -1
        private set
    var surfaceWarpAmplitudeLocation: Int = -1
        private set
    var edgeChaosAmplitudeLocation: Int = -1
        private set
    var lobeVarietyLocation: Int = -1
        private set
    var frontDepthPushLocation: Int = -1
        private set
    var fieldRestlessnessLocation: Int = -1
        private set
    var fieldLineStrengthLocation: Int = -1
        private set
    var clusterStrengthLocation: Int = -1
        private set
    var dropletGlossLocation: Int = -1
        private set
    var magneticAttractionLocation: Int = -1
        private set
    var clusterRestlessnessLocation: Int = -1
        private set
    var shapeLobeCountLocation: Int = -1
        private set
    var shapeLobeHeightLocation: Int = -1
        private set
    var shapeLobeSharpnessLocation: Int = -1
        private set
    var shapeDropletChanceLocation: Int = -1
        private set
    var shapeSymmetryLocation: Int = -1
        private set
    var shapeRestlessnessLocation: Int = -1
        private set
    var shapeReabsorptionSpeedLocation: Int = -1
        private set
    var shapeEdgeContainmentLocation: Int = -1
        private set
    var volumeFogAlphaLocation: Int = -1
        private set
    var breathTextureAlphaLocation: Int = -1
        private set
    var depthSeparationLocation: Int = -1
        private set
    var rimPrismLocation: Int = -1
        private set
    var centerClarityLocation: Int = -1
        private set
    var membraneAlphaLocation: Int = -1
        private set
    var microReliefAlphaLocation: Int = -1
        private set
    var specularSheenLocation: Int = -1
        private set
    var iridescentFilmLocation: Int = -1
        private set
    var rimMaterializationLocation: Int = -1
        private set
    var depthFogCenterAlphaLocation: Int = -1
        private set
    var depthFogMiddleAlphaLocation: Int = -1
        private set
    var glassShellAlphaLocation: Int = -1
        private set
    var premiumGrainAlphaLocation: Int = -1
        private set
    var glassStreakAlphaLocation: Int = -1
        private set
    var overlayPassLocation: Int = -1
        private set
    var frostedGlassAlphaLocation: Int = -1
        private set
    var dichroicReflectionLocation: Int = -1
        private set
    var internalRefractionLocation: Int = -1
        private set
    var microPrismCoatingLocation: Int = -1
        private set
    var smokedGlassAlphaLocation: Int = -1
        private set
    var magneticDustAlphaLocation: Int = -1
        private set
    var thinFresnelRimLocation: Int = -1
        private set
    var opalescentDiffusionAlphaLocation: Int = -1
        private set
    var subsurfaceScatteringLocation: Int = -1
        private set
    var innerMilkyGlowLocation: Int = -1
        private set
    var corePulseScaleLocation: Int = -1
        private set
    var corePulseDurationLocation: Int = -1
        private set
    var internalDriftAmplitudeLocation: Int = -1
        private set
    var internalDriftDurationLocation: Int = -1
        private set
    var fluidDriftAmplitudeLocation: Int = -1
        private set
    var fluidDriftDurationLocation: Int = -1
        private set
    var contourActivityZonesLocation: Int = -1
        private set
    var spikeBaseLengthLocation: Int = -1
        private set
    var spikeGrowthBonusLocation: Int = -1
        private set
    var spikeActivityLocation: Int = -1
        private set
    var spikeCycleDurationLocation: Int = -1
        private set
    var spikeMicroJitterLocation: Int = -1
        private set
    var highlightTravelLocation: Int = -1
        private set
    var highlightIntensityBoostLocation: Int = -1
        private set
    var highlightResponseLagLocation: Int = -1
        private set
    var rimBreathingLocation: Int = -1
        private set
    var motionIrregularityLocation: Int = -1
        private set
    var highlightFlickerLocation: Int = -1
        private set
    var edgeTickVisibilityLocation: Int = -1
        private set
    var transitionProgressLocation: Int = -1
        private set
    var transitionEasedLocation: Int = -1
        private set

    fun create() {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VertexShader)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FragmentShader)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val status = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            error("Unable to link orb skin shader program: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        timeLocation = GLES30.glGetUniformLocation(programId, "uTime")
        aspectLocation = GLES30.glGetUniformLocation(programId, "uAspect")
        grainLocation = GLES30.glGetUniformLocation(programId, "uGrain")
        intensityLocation = GLES30.glGetUniformLocation(programId, "uIntensity")
        haloLocation = GLES30.glGetUniformLocation(programId, "uHalo")
        haloPulseLocation = GLES30.glGetUniformLocation(programId, "uHaloPulse")
        bumpCountLocation = GLES30.glGetUniformLocation(programId, "uBumpCount")
        bumpAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uBumpAmplitude")
        touchLocation = GLES30.glGetUniformLocation(programId, "uTouch")
        touchStrengthLocation = GLES30.glGetUniformLocation(programId, "uTouchStrength")
        liquidAlphaLocation = GLES30.glGetUniformLocation(programId, "uLiquidAlpha")
        liquidFlowSpeedLocation = GLES30.glGetUniformLocation(programId, "uLiquidFlowSpeed")
        liquidCausticScaleLocation = GLES30.glGetUniformLocation(programId, "uLiquidCausticScale")
        liquidIridescenceLocation = GLES30.glGetUniformLocation(programId, "uLiquidIridescence")
        liquidCoolingLocation = GLES30.glGetUniformLocation(programId, "uLiquidCooling")
        mercuryReflectionLocation = GLES30.glGetUniformLocation(programId, "uMercuryReflection")
        symbioteViscosityLocation = GLES30.glGetUniformLocation(programId, "uSymbioteViscosity")
        coreDropletAlphaLocation = GLES30.glGetUniformLocation(programId, "uCoreDropletAlpha")
        spikeDensityLocation = GLES30.glGetUniformLocation(programId, "uSpikeDensity")
        spikeHeightLocation = GLES30.glGetUniformLocation(programId, "uSpikeHeight")
        spikeSharpnessLocation = GLES30.glGetUniformLocation(programId, "uSpikeSharpness")
        magneticPulseStrengthLocation = GLES30.glGetUniformLocation(programId, "uMagneticPulseStrength")
        fieldStabilityLocation = GLES30.glGetUniformLocation(programId, "uFieldStability")
        touchAgeLocation = GLES30.glGetUniformLocation(programId, "uTouchAge")
        waveAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uWaveAmplitude")
        waveSpeedLocation = GLES30.glGetUniformLocation(programId, "uWaveSpeed")
        waveFrequencyLocation = GLES30.glGetUniformLocation(programId, "uWaveFrequency")
        waveDampingLocation = GLES30.glGetUniformLocation(programId, "uWaveDamping")
        waveFalloffLocation = GLES30.glGetUniformLocation(programId, "uWaveFalloff")
        edgeDisplacementLocation = GLES30.glGetUniformLocation(programId, "uEdgeDisplacement")
        forwardPushLocation = GLES30.glGetUniformLocation(programId, "uForwardPush")
        surfaceWarpAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uSurfaceWarpAmplitude")
        edgeChaosAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uEdgeChaosAmplitude")
        lobeVarietyLocation = GLES30.glGetUniformLocation(programId, "uLobeVariety")
        frontDepthPushLocation = GLES30.glGetUniformLocation(programId, "uFrontDepthPush")
        fieldRestlessnessLocation = GLES30.glGetUniformLocation(programId, "uFieldRestlessness")
        fieldLineStrengthLocation = GLES30.glGetUniformLocation(programId, "uFieldLineStrength")
        clusterStrengthLocation = GLES30.glGetUniformLocation(programId, "uClusterStrength")
        dropletGlossLocation = GLES30.glGetUniformLocation(programId, "uDropletGloss")
        magneticAttractionLocation = GLES30.glGetUniformLocation(programId, "uMagneticAttraction")
        clusterRestlessnessLocation = GLES30.glGetUniformLocation(programId, "uClusterRestlessness")
        shapeLobeCountLocation = GLES30.glGetUniformLocation(programId, "uShapeLobeCount")
        shapeLobeHeightLocation = GLES30.glGetUniformLocation(programId, "uShapeLobeHeight")
        shapeLobeSharpnessLocation = GLES30.glGetUniformLocation(programId, "uShapeLobeSharpness")
        shapeDropletChanceLocation = GLES30.glGetUniformLocation(programId, "uShapeDropletChance")
        shapeSymmetryLocation = GLES30.glGetUniformLocation(programId, "uShapeSymmetry")
        shapeRestlessnessLocation = GLES30.glGetUniformLocation(programId, "uShapeRestlessness")
        shapeReabsorptionSpeedLocation = GLES30.glGetUniformLocation(programId, "uShapeReabsorptionSpeed")
        shapeEdgeContainmentLocation = GLES30.glGetUniformLocation(programId, "uShapeEdgeContainment")
        volumeFogAlphaLocation = GLES30.glGetUniformLocation(programId, "uVolumeFogAlpha")
        breathTextureAlphaLocation = GLES30.glGetUniformLocation(programId, "uBreathTextureAlpha")
        depthSeparationLocation = GLES30.glGetUniformLocation(programId, "uDepthSeparation")
        rimPrismLocation = GLES30.glGetUniformLocation(programId, "uRimPrism")
        centerClarityLocation = GLES30.glGetUniformLocation(programId, "uCenterClarity")
        membraneAlphaLocation = GLES30.glGetUniformLocation(programId, "uMembraneAlpha")
        microReliefAlphaLocation = GLES30.glGetUniformLocation(programId, "uMicroReliefAlpha")
        specularSheenLocation = GLES30.glGetUniformLocation(programId, "uSpecularSheen")
        iridescentFilmLocation = GLES30.glGetUniformLocation(programId, "uIridescentFilm")
        rimMaterializationLocation = GLES30.glGetUniformLocation(programId, "uRimMaterialization")
        depthFogCenterAlphaLocation = GLES30.glGetUniformLocation(programId, "uDepthFogCenterAlpha")
        depthFogMiddleAlphaLocation = GLES30.glGetUniformLocation(programId, "uDepthFogMiddleAlpha")
        glassShellAlphaLocation = GLES30.glGetUniformLocation(programId, "uGlassShellAlpha")
        premiumGrainAlphaLocation = GLES30.glGetUniformLocation(programId, "uPremiumGrainAlpha")
        glassStreakAlphaLocation = GLES30.glGetUniformLocation(programId, "uGlassStreakAlpha")
        overlayPassLocation = GLES30.glGetUniformLocation(programId, "uOverlayPass")
        frostedGlassAlphaLocation = GLES30.glGetUniformLocation(programId, "uFrostedGlassAlpha")
        dichroicReflectionLocation = GLES30.glGetUniformLocation(programId, "uDichroicReflection")
        internalRefractionLocation = GLES30.glGetUniformLocation(programId, "uInternalRefraction")
        microPrismCoatingLocation = GLES30.glGetUniformLocation(programId, "uMicroPrismCoating")
        smokedGlassAlphaLocation = GLES30.glGetUniformLocation(programId, "uSmokedGlassAlpha")
        magneticDustAlphaLocation = GLES30.glGetUniformLocation(programId, "uMagneticDustAlpha")
        thinFresnelRimLocation = GLES30.glGetUniformLocation(programId, "uThinFresnelRim")
        opalescentDiffusionAlphaLocation = GLES30.glGetUniformLocation(programId, "uOpalescentDiffusionAlpha")
        subsurfaceScatteringLocation = GLES30.glGetUniformLocation(programId, "uSubsurfaceScattering")
        innerMilkyGlowLocation = GLES30.glGetUniformLocation(programId, "uInnerMilkyGlow")
        corePulseScaleLocation = GLES30.glGetUniformLocation(programId, "uCorePulseScale")
        corePulseDurationLocation = GLES30.glGetUniformLocation(programId, "uCorePulseDuration")
        internalDriftAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uInternalDriftAmplitude")
        internalDriftDurationLocation = GLES30.glGetUniformLocation(programId, "uInternalDriftDuration")
        fluidDriftAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uFluidDriftAmplitude")
        fluidDriftDurationLocation = GLES30.glGetUniformLocation(programId, "uFluidDriftDuration")
        contourActivityZonesLocation = GLES30.glGetUniformLocation(programId, "uContourActivityZones")
        spikeBaseLengthLocation = GLES30.glGetUniformLocation(programId, "uSpikeBaseLength")
        spikeGrowthBonusLocation = GLES30.glGetUniformLocation(programId, "uSpikeGrowthBonus")
        spikeActivityLocation = GLES30.glGetUniformLocation(programId, "uSpikeActivity")
        spikeCycleDurationLocation = GLES30.glGetUniformLocation(programId, "uSpikeCycleDuration")
        spikeMicroJitterLocation = GLES30.glGetUniformLocation(programId, "uSpikeMicroJitter")
        highlightTravelLocation = GLES30.glGetUniformLocation(programId, "uHighlightTravel")
        highlightIntensityBoostLocation = GLES30.glGetUniformLocation(programId, "uHighlightIntensityBoost")
        highlightResponseLagLocation = GLES30.glGetUniformLocation(programId, "uHighlightResponseLag")
        rimBreathingLocation = GLES30.glGetUniformLocation(programId, "uRimBreathing")
        motionIrregularityLocation = GLES30.glGetUniformLocation(programId, "uMotionIrregularity")
        highlightFlickerLocation = GLES30.glGetUniformLocation(programId, "uHighlightFlicker")
        edgeTickVisibilityLocation = GLES30.glGetUniformLocation(programId, "uEdgeTickVisibility")
        transitionProgressLocation = GLES30.glGetUniformLocation(programId, "uTransitionProgress")
        transitionEasedLocation = GLES30.glGetUniformLocation(programId, "uTransitionEased")
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Unable to compile orb skin shader: $log")
        }
        return shader
    }

    private companion object {
        private const val VertexShader = """
            #version 300 es
            layout(location = 0) in vec2 aPosition;

            out vec2 vUv;

            void main() {
                vUv = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FragmentShader = """
            #version 300 es
            precision mediump float;

            in vec2 vUv;
            uniform float uTime;
            uniform float uAspect;
            uniform float uGrain;
            uniform float uIntensity;
            uniform float uHalo;
            uniform float uHaloPulse;
            uniform float uBumpCount;
            uniform float uBumpAmplitude;
            uniform vec2 uTouch;
            uniform float uTouchStrength;
            uniform float uLiquidAlpha;
            uniform float uLiquidFlowSpeed;
            uniform float uLiquidCausticScale;
            uniform float uLiquidIridescence;
            uniform float uLiquidCooling;
            uniform float uMercuryReflection;
            uniform float uSymbioteViscosity;
            uniform float uCoreDropletAlpha;
            uniform float uSpikeDensity;
            uniform float uSpikeHeight;
            uniform float uSpikeSharpness;
            uniform float uMagneticPulseStrength;
            uniform float uFieldStability;
            uniform float uTouchAge;
            uniform float uWaveAmplitude;
            uniform float uWaveSpeed;
            uniform float uWaveFrequency;
            uniform float uWaveDamping;
            uniform float uWaveFalloff;
            uniform float uEdgeDisplacement;
            uniform float uForwardPush;
            uniform float uSurfaceWarpAmplitude;
            uniform float uEdgeChaosAmplitude;
            uniform float uLobeVariety;
            uniform float uFrontDepthPush;
            uniform float uFieldRestlessness;
            uniform float uFieldLineStrength;
            uniform float uClusterStrength;
            uniform float uDropletGloss;
            uniform float uMagneticAttraction;
            uniform float uClusterRestlessness;
            uniform float uShapeLobeCount;
            uniform float uShapeLobeHeight;
            uniform float uShapeLobeSharpness;
            uniform float uShapeDropletChance;
            uniform float uShapeSymmetry;
            uniform float uShapeRestlessness;
            uniform float uShapeReabsorptionSpeed;
            uniform float uShapeEdgeContainment;
            uniform float uVolumeFogAlpha;
            uniform float uBreathTextureAlpha;
            uniform float uDepthSeparation;
            uniform float uRimPrism;
            uniform float uCenterClarity;
            uniform float uMembraneAlpha;
            uniform float uMicroReliefAlpha;
            uniform float uSpecularSheen;
            uniform float uIridescentFilm;
            uniform float uRimMaterialization;
            uniform float uDepthFogCenterAlpha;
            uniform float uDepthFogMiddleAlpha;
            uniform float uGlassShellAlpha;
            uniform float uPremiumGrainAlpha;
            uniform float uGlassStreakAlpha;
            uniform float uOverlayPass;
            uniform float uFrostedGlassAlpha;
            uniform float uDichroicReflection;
            uniform float uInternalRefraction;
            uniform float uMicroPrismCoating;
            uniform float uSmokedGlassAlpha;
            uniform float uMagneticDustAlpha;
            uniform float uThinFresnelRim;
            uniform float uOpalescentDiffusionAlpha;
            uniform float uSubsurfaceScattering;
            uniform float uInnerMilkyGlow;
            uniform float uCorePulseScale;
            uniform float uCorePulseDuration;
            uniform float uInternalDriftAmplitude;
            uniform float uInternalDriftDuration;
            uniform float uFluidDriftAmplitude;
            uniform float uFluidDriftDuration;
            uniform float uContourActivityZones;
            uniform float uSpikeBaseLength;
            uniform float uSpikeGrowthBonus;
            uniform float uSpikeActivity;
            uniform float uSpikeCycleDuration;
            uniform float uSpikeMicroJitter;
            uniform float uHighlightTravel;
            uniform float uHighlightIntensityBoost;
            uniform float uHighlightResponseLag;
            uniform float uRimBreathing;
            uniform float uMotionIrregularity;
            uniform float uHighlightFlicker;
            uniform float uEdgeTickVisibility;
            uniform float uTransitionProgress;
            uniform float uTransitionEased;
            out vec4 fragColor;

            float easeInOutSine(float x) {
                return -(cos(3.14159265 * clamp(x, 0.0, 1.0)) - 1.0) * 0.5;
            }

            float hash(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float bumpField(vec2 p) {
                float total = 0.0;
                for (int i = 0; i < 9; i++) {
                    float enabled = step(float(i) + 0.5, uBumpCount);
                    float seed = float(i) + 1.0;
                    vec2 c = vec2(
                        -0.62 + fract(sin(seed * 14.13) * 37123.31) * 1.24,
                        -0.62 + fract(sin(seed * 27.71) * 17321.17) * 1.24
                    );
                    float d = distance(p, c);
                    float grow = sin(uTime * (0.35 + seed * 0.035) + seed * 2.1) * 0.5 + 0.5;
                    float envelope = smoothstep(0.05, 0.90, grow) * smoothstep(1.0, 0.20, grow);
                    total += enabled * exp(-d * d * (15.0 + seed)) * envelope;
                }
                return total;
            }

            float symbioteLobes(vec2 p, float time) {
                float total = 0.0;
                for (int i = 0; i < 7; i++) {
                    float seed = float(i) + 1.0;
                    float angle = seed * 2.399963 + sin(time * (0.10 + seed * 0.012) + seed) * 0.16;
                    float orbit = 0.16 + fract(sin(seed * 19.71) * 9123.17) * 0.50;
                    vec2 c = vec2(cos(angle), sin(angle)) * orbit;
                    c += vec2(
                        sin(time * (0.15 + seed * 0.010) + seed * 2.4),
                        cos(time * (0.12 + seed * 0.014) + seed * 1.7)
                    ) * 0.055;
                    float d = distance(p, c);
                    float pulse = sin(time * (0.32 + seed * 0.035) + seed * 1.9) * 0.5 + 0.5;
                    float radius = mix(0.18, 0.42, pulse) * mix(0.78, 1.12, uSymbioteViscosity);
                    total += exp(-(d * d) / max(radius * radius, 0.001)) * (0.55 + pulse * 0.45);
                }
                return clamp(total / 2.85, 0.0, 1.0);
            }

            float ferroSpikes(vec2 p, float time) {
                float total = 0.0;
                for (int i = 0; i < 11; i++) {
                    float seed = float(i) + 1.0;
                    float row = floor(float(i) / 4.0);
                    float column = mod(float(i), 4.0);
                    vec2 base = vec2(
                        -0.48 + column * 0.32 + fract(sin(seed * 18.17) * 231.13) * 0.08,
                        -0.42 + row * 0.34 + fract(sin(seed * 31.41) * 917.61) * 0.10
                    );
                    float anchor = smoothstep(0.92, 0.16, length(base));
                    float d = distance(p, base);
                    float pulse = sin(time * (0.22 + uMagneticPulseStrength * 0.36) + seed * 1.71) * 0.5 + 0.5;
                    float emergence = mix(0.55, 1.0, pulse) * uSpikeHeight;
                    float width = mix(0.18, 0.055, uSpikeSharpness) * mix(1.18, 0.86, uSpikeDensity);
                    float spike = exp(-(d * d) / max(width * width, 0.001));
                    total += spike * emergence * anchor;
                }
                return clamp(total * (0.45 + uSpikeDensity * 0.85), 0.0, 1.35);
            }

            float capillaryWave(vec2 uv01, float radius) {
                float d = distance(uv01, uTouch);
                float age = max(uTouchAge, 0.0);
                float ring = sin(d * uWaveFrequency - age * uWaveSpeed * 6.2831853);
                float envelope = exp(-age * uWaveDamping) * exp(-d * uWaveFalloff);
                float frontBias = smoothstep(0.10, 0.92, 1.0 - radius);
                return ring * envelope * uWaveAmplitude * uTouchStrength * frontBias;
            }

            float edgeMold(float angle, float time, float wave) {
                float slowLobe =
                    sin(angle * 3.0 + time * (0.23 + uFieldRestlessness * 0.16)) * 0.52 +
                    sin(angle * 5.0 - time * (0.17 + uFieldRestlessness * 0.21) + 1.8) * 0.36 +
                    sin(angle * 7.0 + time * (0.11 + uFieldRestlessness * 0.18) + 0.7) * 0.24 +
                    sin(angle * 11.0 - time * (0.07 + uFieldRestlessness * 0.12) + 2.3) * 0.15;
                float magneticTension = mix(1.0, 0.42, uFieldStability);
                float normalizedWave = wave / max(uWaveAmplitude, 0.001);
                return (slowLobe * uEdgeChaosAmplitude * magneticTension) + normalizedWave * uEdgeDisplacement;
            }

            float shapeStateField(vec2 p, float angle, float time) {
                float total = 0.0;
                for (int i = 0; i < 7; i++) {
                    float enabled = step(float(i) + 0.5, uShapeLobeCount);
                    float seed = float(i) + 1.0;
                    float baseAngle = seed * 2.399963;
                    float mirrored = mix(fract(sin(seed * 17.2) * 8.1), 1.0 - fract(sin(seed * 17.2) * 8.1), uShapeSymmetry * 0.45);
                    float lobeAngle = baseAngle + (mirrored - 0.5) * (1.2 - uShapeSymmetry * 0.7);
                    float phase = sin(time * (0.25 + uShapeRestlessness * 0.45) + seed * 1.73) * 0.5 + 0.5;
                    float life = smoothstep(0.04, 0.50, phase) * smoothstep(1.0, 0.18 + uShapeReabsorptionSpeed * 0.16, phase);
                    float dAngle = atan(sin(angle - lobeAngle), cos(angle - lobeAngle));
                    float angularShape = exp(-(dAngle * dAngle) / mix(0.20, 0.035, uShapeLobeSharpness));
                    total += enabled * angularShape * life;
                }
                return clamp(total, 0.0, 1.25);
            }

            float attachedDropletField(vec2 p, float angle, float effectiveRadius, float time) {
                float total = 0.0;
                for (int i = 0; i < 3; i++) {
                    float seed = float(i) + 1.0;
                    float enabled = step(float(i) * 0.22 + 0.10, uShapeDropletChance);
                    float dropletAngle = seed * 2.399963 + sin(seed * 4.7) * 0.35;
                    float pulse = sin(time * (0.20 + uShapeRestlessness * 0.35) + seed * 2.0) * 0.5 + 0.5;
                    float life = smoothstep(0.18, 0.62, pulse) * smoothstep(1.0, 0.38, pulse);
                    vec2 c = vec2(cos(dropletAngle), sin(dropletAngle)) * (0.78 + uShapeLobeHeight * 0.45);
                    float d = distance(p, c);
                    float size = 0.055 + uShapeLobeHeight * 0.26;
                    float attached = smoothstep(1.0, 0.62, abs(effectiveRadius - 0.86));
                    total += enabled * exp(-(d * d) / max(size * size, 0.001)) * life * attached;
                }
                return clamp(total, 0.0, 1.0);
            }

            vec4 dropletContribution(vec2 p, vec2 c, float radius, float strength) {
                vec2 d = p - c;
                float dist = length(d);
                float mask = smoothstep(radius, radius * 0.42, dist) * strength;
                float inner = sqrt(max(0.0, 1.0 - (dist / max(radius, 0.001)) * (dist / max(radius, 0.001))));
                vec2 lightDir = normalize(vec2(-0.45, 0.78));
                float shine = pow(max(0.0, dot(normalize(d + vec2(0.001)), lightDir)) * 0.5 + inner * 0.72, 10.0);
                return vec4(vec3(mask, shine, inner), mask);
            }

            vec4 ferroDropletField(vec2 p, float time) {
                vec3 accum = vec3(0.0);
                float alpha = 0.0;
                for (int r = 0; r < 8; r++) {
                    float seed = float(r) + 1.0;
                    float timing = time * (0.045 + seed * 0.004 + uClusterRestlessness * 0.060) + seed * 1.71;
                    float easedPulse = smoothstep(0.12, 0.88, sin(timing) * 0.5 + 0.5);
                    float angle = seed * 2.399963 + sin(seed * 4.1) * 0.18 + sin(timing * 0.37) * 0.045 * uClusterRestlessness;
                    float enabled = step(0.18, uFieldLineStrength + fract(sin(seed * 8.31) * 13.13) * 0.24);
                    for (int j = 0; j < 2; j++) {
                        float fj = float(j);
                        float radial = 1.02 + fj * 0.17 + fract(sin(seed * 11.7 + fj) * 41.7) * 0.06;
                        float wobble = sin(time * (0.070 + uClusterRestlessness * 0.11) + seed * 1.7 + fj * 2.3) * 0.014 * uClusterRestlessness;
                        vec2 c = vec2(cos(angle), sin(angle)) * (radial - uMagneticAttraction * (0.090 + easedPulse * 0.030) + wobble);
                        float size = (0.024 + fract(sin(seed * 19.9 + fj * 3.4) * 17.0) * 0.030) * (0.74 + uFieldLineStrength * 0.45) * (0.86 + easedPulse * 0.18);
                        vec4 drop = dropletContribution(p, c, size, enabled * uFieldLineStrength * (0.76 + easedPulse * 0.34));
                        alpha = max(alpha, drop.a);
                        accum += drop.rgb * drop.a;
                    }
                }
                for (int i = 0; i < 3; i++) {
                    float seed = float(i) + 1.0;
                    float side = seed < 3.0 ? -1.0 : 1.0;
                    vec2 clusterCenter = vec2(side * (1.02 + fract(sin(seed * 6.7) * 9.1) * 0.16), -0.18 + fract(sin(seed * 8.3) * 11.8) * 0.58);
                    float clusterPulse = smoothstep(0.10, 0.92, sin(time * (0.050 + uClusterRestlessness * 0.080) + seed * 1.37) * 0.5 + 0.5);
                    clusterCenter += vec2(
                        sin(time * (0.055 + uClusterRestlessness * 0.090) + seed),
                        cos(time * (0.045 + uClusterRestlessness * 0.075) + seed * 1.6)
                    ) * 0.035 * uClusterRestlessness;
                    for (int k = 0; k < 4; k++) {
                        float fk = float(k);
                        float a = fk * 2.399963 + seed;
                        float rr = 0.045 + fract(sin(seed * 9.0 + fk) * 7.0) * 0.15;
                        vec2 c = clusterCenter + vec2(cos(a), sin(a)) * rr;
                        float size = (0.044 + fract(sin(seed * 21.0 + fk * 2.1) * 19.0) * 0.044) * (0.88 + clusterPulse * 0.18);
                        vec4 drop = dropletContribution(p, c, size, uClusterStrength * (0.80 + clusterPulse * 0.30));
                        alpha = max(alpha, drop.a);
                        accum += drop.rgb * drop.a;
                    }
                }
                vec3 color = alpha > 0.001 ? accum / max(alpha * 2.2, 0.001) : vec3(0.0);
                return vec4(color, clamp(alpha, 0.0, 1.0));
            }

            float magneticContourZones(float angle, float time) {
                float total = 0.0;
                for (int i = 0; i < 5; i++) {
                    float enabled = step(float(i) + 0.5, uContourActivityZones);
                    float seed = float(i) + 1.0;
                    float anchor = -2.65 + seed * 1.08 + sin(seed * 3.17) * 0.20;
                    float phase = time * (0.20 + seed * 0.018 + uMotionIrregularity * 0.08) + seed * 1.73;
                    float creep = sin(phase) * (0.045 + uSpikeMicroJitter * 0.22);
                    float d = atan(sin(angle - anchor - creep), cos(angle - anchor - creep));
                    float pulse = easeInOutSine(sin(phase * 0.72) * 0.5 + 0.5);
                    float width = mix(0.075, 0.145, pulse);
                    total += enabled * exp(-(d * d) / max(width * width, 0.001)) * (0.58 + pulse * 0.42);
                }
                return clamp(total, 0.0, 1.0);
            }

            void main() {
                vec2 p = vUv;
                p.x *= uAspect;
                float radius = length(p);
                vec2 uv01 = vec2((vUv.x + 1.0) * 0.5, (vUv.y + 1.0) * 0.5);
                float angle = atan(p.y, p.x);
                float pulsePhase = fract(uTime / max(uCorePulseDuration, 0.1));
                float corePulse = easeInOutSine(pulsePhase) * uCorePulseScale;
                float driftPhase = uTime * 6.2831853 / max(uFluidDriftDuration, 0.1);
                float internalPhase = uTime * 6.2831853 / max(uInternalDriftDuration, 0.1);
                vec2 internalDrift = vec2(
                    sin(internalPhase + 0.6) * uInternalDriftAmplitude * 0.0015,
                    cos(internalPhase * 0.73 + 1.4) * uInternalDriftAmplitude * 0.0015
                );
                float driftPx = uFluidDriftAmplitude * 0.00135;
                float localDrift =
                    sin(angle * 3.0 + driftPhase) * 0.52 +
                    sin(angle * 5.0 - driftPhase * 0.73 + 1.7) * 0.34 +
                    sin(angle * 9.0 + driftPhase * 0.41 + 0.9) * 0.14;
                float physicalWave = capillaryWave(uv01, radius);
                float shapeField = shapeStateField(p, angle, uTime);
                float edgeOffset = edgeMold(angle, uTime, physicalWave) +
                    localDrift * driftPx +
                    corePulse * 0.42 +
                    shapeField * uShapeLobeHeight * uShapeEdgeContainment;
                float effectiveRadius = radius - edgeOffset;
                float attachedDrops = attachedDropletField(p, angle, effectiveRadius, uTime);
                if (effectiveRadius > 1.16) {
                    discard;
                }

                float sphere = sqrt(max(0.0, 1.0 - effectiveRadius * effectiveRadius));
                float edge = smoothstep(0.18, 0.82, effectiveRadius);
                float fresnel = pow(edge, 1.85);
                float side = clamp((vUv.x + 0.82) / 1.64, 0.0, 1.0);
                float basePass = 1.0 - step(0.5, uOverlayPass);
                float bump = basePass > 0.5 ? bumpField(p) : 0.0;
                float touchDistance = distance(uv01, uTouch);
                float touchRipple = exp(-touchDistance * touchDistance * 30.0) *
                    (0.70 + sin(uTime * 7.5 - touchDistance * 30.0) * 0.30) * uTouchStrength;

                float liquidTime = uTime * uLiquidFlowSpeed;
                float lobes = 0.0;
                float spikes = 0.0;
                float tendon = 0.0;
                float surfaceRelief = 0.0;
                if (basePass > 0.5) {
                    lobes = symbioteLobes(p, liquidTime);
                    spikes = ferroSpikes(p, liquidTime);
                    float tendonA = sin((p.x * 3.2 + p.y * 4.6) * uLiquidCausticScale + liquidTime * 0.84);
                    float tendonB = sin((p.x * -4.8 + p.y * 2.7) * uLiquidCausticScale - liquidTime * 0.62);
                    tendon = smoothstep(0.24, 0.92, tendonA * 0.35 + tendonB * 0.22 + lobes * 0.50 + spikes * 0.70);
                    float surfaceWarp =
                        sin(p.x * 5.4 + p.y * 2.2 + liquidTime * (0.32 + uFieldRestlessness * 0.25)) * 0.35 +
                        sin(p.x * -2.6 + p.y * 6.1 - liquidTime * (0.24 + uFieldRestlessness * 0.22)) * 0.28 +
                        lobes * (0.30 + uLobeVariety * 0.22) +
                        shapeField * (0.62 + uShapeLobeHeight * 0.80) +
                        attachedDrops * (0.45 + uShapeDropletChance * 0.35) +
                        spikes * 0.52 +
                        physicalWave * 2.2;
                    surfaceRelief = surfaceWarp * uSurfaceWarpAmplitude;
                }
                float liquidBody = smoothstep(0.99, 0.10, effectiveRadius) * (0.70 + sphere * 0.30);
                float frontPressure = pow(max(0.0, sphere), 1.45);
                float normalizedWavePressure = abs(physicalWave) / max(uWaveAmplitude, 0.001);
                float radialPush = (spikes * uSpikeHeight + normalizedWavePressure * uForwardPush * 0.28 + max(surfaceRelief, 0.0) * uFrontDepthPush) * frontPressure;
                float mercurySpecular = pow(max(0.0, sphere + radialPush * 0.58), 2.0) * (0.42 + fresnel * 0.88 + spikes * 0.55 + abs(surfaceRelief) * 1.20);
                float hotReflection = smoothstep(0.50, 0.98, tendon + spikes * 0.38) * mercurySpecular;

                vec3 cyan = vec3(0.24, 0.86, 1.0);
                vec3 violet = vec3(0.62, 0.36, 1.0);
                vec3 magenta = vec3(1.0, 0.22, 0.86);
                vec3 titanium = vec3(0.86, 0.97, 1.0);
                vec3 chrome = vec3(0.70, 0.78, 0.86);
                vec3 failure = vec3(1.0, 0.28, 0.36);
                vec3 color = mix(mix(cyan, violet, smoothstep(0.0, 0.55, side)), magenta, smoothstep(0.46, 1.0, side));
                float internalFog = smoothstep(0.95, 0.10, effectiveRadius) * (0.40 + sphere * 0.60);
                vec2 innerP = p + internalDrift * smoothstep(0.92, 0.08, effectiveRadius);
                float breathTexture = (
                    sin(innerP.x * 7.0 + innerP.y * 3.5 + internalPhase * 0.34) * 0.38 +
                    sin(innerP.x * -4.0 + innerP.y * 8.0 - internalPhase * 0.25) * 0.28 +
                    sin((innerP.x + innerP.y) * 11.0 + internalPhase * 0.16) * 0.16
                ) * 0.5 + 0.5;
                float depthMist = internalFog * (0.34 + sphere * 0.46) * uVolumeFogAlpha;
                float breathingMist = internalFog * breathTexture * uBreathTextureAlpha * (0.86 + corePulse / max(uCorePulseScale, 0.001) * 0.14);
                float centerWindow = smoothstep(0.52, 0.0, effectiveRadius) * uCenterClarity;
                depthMist *= (1.0 - centerWindow * 0.42);
                breathingMist *= (1.0 - centerWindow * 0.55);
                float frontMembrane = smoothstep(0.98, 0.18, effectiveRadius) * pow(max(0.0, sphere), 0.52);
                float highlightOnly = smoothstep(0.18, 0.92, fresnel + sphere * 0.46);
                float microA = hash(p * 182.0 + vec2(0.0, floor(uTime * 0.18)));
                float microB = hash(p * 317.0 + vec2(17.0, 9.0));
                float microWeave = sin((p.x * 116.0 + p.y * 71.0) + uTime * 0.09) *
                    sin((p.x * -83.0 + p.y * 127.0) - uTime * 0.07);
                float microRelief = (microA * 0.46 + microB * 0.28 + microWeave * 0.13 + 0.13);
                microRelief = smoothstep(0.54, 0.96, microRelief) * highlightOnly * frontMembrane;
                float sweepAxis = p.x * 0.48 - p.y * 0.16 + sphere * 0.28 + sin(uTime * 0.21) * 0.18;
                float softSweep = exp(-pow((sweepAxis - 0.22) * 2.2, 2.0)) * smoothstep(0.06, 0.98, sphere);
                float secondarySweep = exp(-pow((sweepAxis + 0.34) * 3.4, 2.0)) * 0.34;
                float specularFilm = (softSweep + secondarySweep) * frontMembrane * (0.72 + fresnel * 0.28);
                vec3 filmCyan = vec3(0.35, 0.92, 1.0);
                vec3 filmViolet = vec3(0.74, 0.46, 1.0);
                vec3 filmPink = vec3(1.0, 0.34, 0.88);
                vec3 filmColor = mix(mix(filmCyan, filmViolet, smoothstep(-0.34, 0.42, sweepAxis)), filmPink, smoothstep(0.34, 0.92, side));
                float iridescenceMask = (specularFilm * 0.54 + fresnel * frontMembrane * 0.30 + microRelief * 0.22) * uIridescentFilm;
                float membraneAlpha = frontMembrane * (0.48 + sphere * 0.30 + fresnel * 0.22) * uMembraneAlpha;
                float microAlpha = microRelief * uMicroReliefAlpha;
                float sheenAlpha = specularFilm * uSpecularSheen * 0.18;
                vec3 fogColor = mix(cyan, magenta, side);
                color = mix(color, chrome, uMercuryReflection * (0.36 + sphere * 0.20 + spikes * 0.16 + abs(surfaceRelief) * 0.55));
                color = mix(color, titanium, (hotReflection * 0.64 + fresnel * 0.20 + lobes * 0.08 + spikes * 0.24 + max(surfaceRelief, 0.0) * 0.38) * uLiquidIridescence);
                color = mix(color, fogColor, clamp((depthMist + breathingMist) * 1.45, 0.0, 0.34));
                color = mix(color, filmColor, clamp(iridescenceMask, 0.0, 0.22));
                color = mix(color, titanium, clamp((specularFilm * uSpecularSheen + microRelief * uMicroReliefAlpha) * 0.20, 0.0, 0.16));
                color = mix(color, violet * 0.88 + titanium * 0.12, centerWindow * 0.16);
                color = mix(color, failure, uLiquidCooling * (0.42 + tendon * 0.16));

                float coreAlpha = smoothstep(0.92, 0.18, effectiveRadius) * uCoreDropletAlpha;
                float spikeAlpha = spikes * (0.18 + uSpikeHeight * 0.34) * frontPressure;
                float waveAlpha = normalizedWavePressure * uWaveAmplitude * (0.34 + uForwardPush * 0.24);
                float warpAlpha = abs(surfaceRelief) * (0.34 + uFrontDepthPush * 0.28);
                float shapeAlpha = shapeField * (0.20 + uShapeLobeHeight * 0.36) + attachedDrops * (0.22 + uShapeDropletChance * 0.28);
                float liquidAlpha = (0.42 + coreAlpha * 0.28 + lobes * 0.08 + tendon * 0.06 + fresnel * 0.16 + spikeAlpha + waveAlpha + warpAlpha + shapeAlpha + bump * 0.06 + touchRipple * 0.12) *
                    uLiquidAlpha * liquidBody;
                float bodyAlpha = liquidAlpha;

                float outerFade = smoothstep(0.930, 0.790, effectiveRadius);
                float ringCore = exp(-pow((effectiveRadius - 0.858) * 38.0, 2.0));
                float ringHot = exp(-pow((effectiveRadius - 0.862) * 74.0, 2.0));
                float ringFeather = exp(-pow((effectiveRadius - 0.852) * 28.0, 2.0));
                float ringPulse = 0.94 + sin(uTime * 6.2831853 / max(uCorePulseDuration, 0.1) + effectiveRadius * 6.0) * uRimBreathing;
                float ringStriation = 0.97 + sin(atan(p.y, p.x) * 14.0 + driftPhase * 0.16) * 0.03;
                float shellBoundary = exp(-pow((effectiveRadius - 0.872) * 72.0, 2.0)) *
                    (0.78 + microRelief * 0.22 + specularFilm * 0.18);
                float ringAlpha = (ringCore * 0.12 + ringHot * 0.10 + ringFeather * 0.025 + touchRipple * 0.045 + bump * 0.018) *
                    uHalo * uIntensity * ringPulse * ringStriation * outerFade * (1.0 + uRimPrism * 0.14 + uRimMaterialization * 0.10);
                ringAlpha += shellBoundary * uRimMaterialization * 0.025 * outerFade;
                vec3 ringColor = mix(color, titanium, 0.62 + ringHot * 0.20 + uRimPrism * 0.12);
                vec4 ferroMatter = ferroDropletField(p, liquidTime);
                float magneticZones = magneticContourZones(angle, driftPhase);
                float contourWindow = smoothstep(0.70, 0.88, effectiveRadius) * smoothstep(1.02, 0.86, effectiveRadius);
                float lowerLeftZone = smoothstep(-0.12, -0.58, p.x) * smoothstep(0.06, -0.48, p.y);
                float rightEdgeZone = smoothstep(0.34, 0.76, p.x) * smoothstep(0.62, 0.18, abs(p.y));
                float upperRimZone = smoothstep(0.30, 0.62, p.y) * smoothstep(0.72, 0.24, abs(p.x));
                float creepingBand = smoothstep(0.90, 1.0, sin(angle * 5.0 + p.y * 3.6 + driftPhase * 0.18) * 0.5 + 0.5) *
                    smoothstep(0.72, 0.94, effectiveRadius);
                float patchA = smoothstep(0.70, 1.0, sin(angle * 3.0 + 0.7 + driftPhase * 0.18) * 0.5 + 0.5);
                float patchB = smoothstep(0.80, 1.0, sin(angle * -4.0 + 2.1 - driftPhase * 0.14) * 0.5 + 0.5) * 0.58;
                float zoneCoverage = max(max(lowerLeftZone, rightEdgeZone), max(upperRimZone * 0.72, creepingBand * 0.64));
                float partialCoverage = clamp(max(zoneCoverage * 0.58, max(patchA, patchB) * 0.14) * contourWindow + magneticZones * contourWindow * 0.86, 0.0, 1.0);
                float spikeLane = floor((angle + 3.14159265) * 0.7957747);
                float spikeSeed = hash(vec2(spikeLane, 17.0));
                float spikeGate = magneticZones * (0.62 + uSpikeActivity * 1.35);
                float spikePhase = uTime * 6.2831853 / max(uSpikeCycleDuration, 0.1) + spikeSeed * 6.2831853;
                float spikeGrow = easeInOutSine(sin(spikePhase) * 0.5 + 0.5);
                float spikeJitter = sin(spikePhase * (2.7 + spikeSeed) + spikeSeed * 4.1) * uSpikeMicroJitter;
                float spikeLength = (uSpikeBaseLength + uSpikeGrowthBonus * spikeGrow) * 0.00125;
                float needleField = smoothstep(0.990, 1.0, sin(angle * 18.0 - spikePhase * (0.16 + uMotionIrregularity * 0.12) + spikeJitter) * 0.5 + 0.5) *
                    smoothstep(0.90, 1.02 + spikeLength, effectiveRadius) * smoothstep(1.06 + spikeLength, 1.00, effectiveRadius) *
                    spikeGate * uEdgeTickVisibility;
                float surfaceInk = smoothstep(0.76, 0.90, effectiveRadius) * smoothstep(1.00, 0.91, effectiveRadius) *
                    (0.76 + sin(angle * 7.0 + driftPhase * 0.14) * 0.12) * uFieldLineStrength * (0.72 + magneticZones * 0.36);
                float magneticInk = magneticZones * contourWindow * smoothstep(0.74, 0.94, effectiveRadius) * smoothstep(1.01, 0.88, effectiveRadius);
                float ferroAlpha = clamp((ferroMatter.a * 0.72 + surfaceInk * 1.02 + needleField * spikeGrow * uFieldLineStrength * 0.20 + magneticInk * (0.28 + uFieldLineStrength * 0.46)) * partialCoverage, 0.0, 0.72);
                vec3 ferroReflection = mix(mix(cyan, violet, smoothstep(0.0, 0.55, side)), magenta, smoothstep(0.52, 1.0, side));
                vec3 ferroBlack = vec3(0.0007, 0.0009, 0.0016);
                float ridgeHighlight = pow(clamp(ferroMatter.g * 0.54 + needleField * spikeGrow * 0.64 + surfaceInk * 0.44 + magneticInk * 0.55, 0.0, 1.0), 3.0);
                float highlightLagPhase = max(0.0, uTime - uHighlightResponseLag) * 6.2831853 / max(uSpikeCycleDuration, 0.1);
                float highlightTravel = uHighlightTravel * 0.0014;
                float localFlicker = 1.0 + sin(highlightLagPhase * 2.3 + spikeSeed * 5.7) * uHighlightFlicker + magneticZones * uHighlightIntensityBoost;
                float ridgeTrace = smoothstep(0.965, 1.0, sin(angle * 18.0 + p.x * (4.0 + highlightTravel * 800.0) - highlightLagPhase * 0.10) * 0.5 + 0.5) *
                    partialCoverage * magneticZones * smoothstep(0.72, 0.94, effectiveRadius);
                vec3 ferroColor = mix(
                    ferroBlack,
                    ferroReflection,
                    clamp(ridgeHighlight * uDropletGloss * 0.46 + needleField * spikeGrow * 0.14, 0.0, 0.34)
                );
                ferroColor = mix(ferroColor, ferroReflection, clamp(ridgeHighlight * uDropletGloss * 0.28, 0.0, 0.24));
                ferroColor = mix(ferroColor, titanium, clamp(ridgeHighlight * uDropletGloss * 0.18, 0.0, 0.14));
                vec3 ferroSpecular = mix(ferroReflection, titanium, 0.32);

                if (uOverlayPass > 0.5) {
                    if (effectiveRadius > 0.865) {
                        discard;
                    }
                    float internalClip = smoothstep(0.855, 0.785, effectiveRadius);
                    float diffusionClip = smoothstep(0.760, 0.600, effectiveRadius);
                    float shellClip = smoothstep(0.865, 0.815, effectiveRadius);
                    float fogContainment = smoothstep(0.680, 0.500, effectiveRadius) * diffusionClip;
                    float centerMask = smoothstep(0.62, 0.0, effectiveRadius);
                    float middleMask = smoothstep(0.74, 0.18, effectiveRadius) * (1.0 - centerMask * 0.48);
                    float fogAlpha = centerMask * uDepthFogCenterAlpha + middleMask * uDepthFogMiddleAlpha;
                    float refractionVeil = (sin(p.x * 3.4 + sphere * 2.1 + uTime * 0.10) * 0.5 + 0.5) *
                        (sin(p.y * -2.8 + p.x * 1.2 - uTime * 0.08) * 0.5 + 0.5);
                    fogAlpha *= fogContainment * (1.0 + refractionVeil * uInternalRefraction * 0.18);
                    vec3 centerFog = vec3(0.031, 0.024, 0.086);
                    vec3 middleFog = vec3(0.078, 0.039, 0.165);
                    vec3 overlayColor = mix(middleFog, centerFog, centerMask);
                    float volumeBody = smoothstep(0.78, 0.08, effectiveRadius) * (0.42 + sphere * 0.58) * diffusionClip;
                    float opalCloud =
                        sin(p.x * 4.2 + p.y * 2.1 + sphere * 2.8 + uTime * 0.10) * 0.24 +
                        sin(p.x * -2.8 + p.y * 5.1 - sphere * 1.9 - uTime * 0.08) * 0.18 +
                        hash(p * 96.0 + vec2(floor(uTime * 0.12), 5.0)) * 0.18;
                    opalCloud = clamp(opalCloud + 0.50, 0.0, 1.0);
                    float opalescentAlpha = volumeBody * (0.70 + opalCloud * 0.30) * uOpalescentDiffusionAlpha;
                    vec3 opalColor = mix(
                        vec3(0.58, 0.86, 1.0),
                        mix(vec3(0.74, 0.58, 1.0), vec3(1.0, 0.48, 0.88), side),
                        0.48 + opalCloud * 0.22
                    );
                    vec3 milkyScatter = mix(opalColor, vec3(0.88, 0.92, 1.0), 0.34 + sphere * 0.22);
                    float smokedBody = smoothstep(0.76, 0.10, effectiveRadius) * (0.52 + sphere * 0.48) * diffusionClip;
                    vec3 smokedColor = vec3(0.020, 0.017, 0.048);

                    float shellEdge = smoothstep(0.58, 0.835, effectiveRadius) * shellClip * internalClip;
                    float shellAlpha = shellEdge * (0.64 + fresnel * 0.36) * uGlassShellAlpha * 0.20;
                    float frostedShell = smoothstep(0.46, 0.810, effectiveRadius) * smoothstep(0.855, 0.760, effectiveRadius) * internalClip;
                    float frostNoise = hash(p * 260.0 + vec2(floor(uTime * 0.08), 11.0)) * 0.55 +
                        hash(p * 431.0 + vec2(7.0, 19.0)) * 0.45;
                    float frostAlpha = frostedShell * (0.56 + frostNoise * 0.44) * uFrostedGlassAlpha * 0.18;
                    vec3 shellColor = mix(mix(cyan, violet, smoothstep(0.0, 0.55, side)), magenta, smoothstep(0.50, 1.0, side));
                    float dichroicShift = sin(angle * 2.0 + sphere * 2.6 + uTime * 0.09) * 0.5 + 0.5;
                    vec3 dichroicColor = mix(mix(cyan, violet, dichroicShift), magenta, smoothstep(0.44, 1.0, side));

                    float rimShell = exp(-pow((effectiveRadius - 0.842) * 120.0, 2.0));
                    float rimAlpha = rimShell * 0.060 * uRimMaterialization * outerFade * internalClip * (1.0 + uThinFresnelRim * 0.12);
                    vec3 rimColor = mix(mix(cyan, violet, smoothstep(0.15, 0.60, abs(p.y) + 0.20)), magenta, smoothstep(0.48, 1.0, side));

                    float curvedCoordinate =
                        p.y * (7.0 + effectiveRadius * 2.0) +
                        sin(p.x * 2.2 + uTime * 0.06) * 0.55 +
                        sphere * 1.4;
                    float sparseBands =
                        smoothstep(0.972, 1.0, sin(curvedCoordinate + 1.2) * 0.5 + 0.5) +
                        smoothstep(0.986, 1.0, sin(curvedCoordinate * 1.61 - 0.7) * 0.5 + 0.5) * 0.55;
                    float streakWindow = shellEdge * smoothstep(0.12, 0.84, sphere) * smoothstep(0.18, 0.82, effectiveRadius);
                    float streakAlpha = sparseBands * streakWindow * uGlassStreakAlpha;

                    float fineGrain = hash((p + vec2(uTime * 0.003, -uTime * 0.002)) * 510.0);
                    float grainAlpha = (fineGrain * 0.5 + 0.5) * uPremiumGrainAlpha * smoothstep(0.82, 0.12, effectiveRadius) * diffusionClip;
                    float magneticDust = smoothstep(0.992, 1.0, hash(p * 150.0 + vec2(floor(uTime * 0.35), 3.0))) *
                        smoothstep(0.78, 0.28, effectiveRadius) * diffusionClip * uMagneticDustAlpha;
                    float prismA = smoothstep(0.986, 1.0, sin(angle * 17.0 + p.x * 5.0 + sphere * 3.0) * 0.5 + 0.5);
                    float prismB = smoothstep(0.990, 1.0, sin(angle * -23.0 + p.y * 4.0 - sphere * 2.0) * 0.5 + 0.5);
                    float prismAlpha = (prismA + prismB * 0.55) * shellEdge * uMicroPrismCoating;

                    overlayColor = mix(overlayColor, milkyScatter, clamp(opalescentAlpha * (0.78 + uSubsurfaceScattering * 0.60), 0.0, 0.42));
                    overlayColor = mix(overlayColor, smokedColor, clamp(smokedBody * uSmokedGlassAlpha, 0.0, 0.22));
                    overlayColor = mix(overlayColor, shellColor, clamp(shellAlpha * 2.4 + frostAlpha * 1.6 + iridescenceMask * 0.55, 0.0, 0.64));
                    overlayColor = mix(overlayColor, dichroicColor, clamp((fresnel * shellEdge + prismAlpha) * uDichroicReflection, 0.0, 0.24));
                    overlayColor = mix(overlayColor, titanium, clamp((specularFilm * uSpecularSheen + streakAlpha) * 0.38, 0.0, 0.18));
                    overlayColor = mix(overlayColor, rimColor, clamp(rimAlpha * 8.0, 0.0, 0.82));
                    float lowerLeftShadow = smoothstep(0.58, 0.86, effectiveRadius) *
                        smoothstep(-0.08, -0.58, p.x) *
                        smoothstep(0.22, -0.54, p.y);
                    overlayColor = mix(overlayColor, vec3(0.0, 0.001, 0.006), clamp(lowerLeftShadow * 0.34, 0.0, 0.34));
                    overlayColor = mix(overlayColor, ferroColor, clamp(ferroAlpha * 2.45, 0.0, 0.98));
                    overlayColor = mix(overlayColor, ferroSpecular, clamp((ridgeTrace * 0.46 + ridgeHighlight * ferroAlpha * 0.26) * uDropletGloss * localFlicker, 0.0, 0.42));

                    float innerGlowAlpha = volumeBody * uInnerMilkyGlow * (0.46 + sphere * 0.42 + opalCloud * 0.12);
                    float overlayAlpha = clamp(
                        fogAlpha * 0.92 +
                            opalescentAlpha * 0.62 +
                            innerGlowAlpha +
                            smokedBody * uSmokedGlassAlpha * 0.42 +
                            shellAlpha +
                            frostAlpha +
                            rimAlpha +
                            streakAlpha +
                            grainAlpha +
                            prismAlpha * 0.45 +
                            magneticDust +
                            lowerLeftShadow * 0.16 +
                            ferroAlpha * 1.05 +
                            ridgeTrace * uDropletGloss * 0.18 * localFlicker,
                        0.0,
                        0.64
                    );
                    fragColor = vec4(overlayColor, overlayAlpha);
                    return;
                }

                color = mix(color, ringColor, clamp(ringAlpha * 2.2, 0.0, 1.0));
                color = mix(color, ferroColor, clamp(ferroAlpha * (1.28 + uDropletGloss), 0.0, 0.86));

                float alpha = bodyAlpha + ringAlpha + depthMist + breathingMist + membraneAlpha + microAlpha + sheenAlpha + centerWindow * 0.018;
                alpha = max(alpha, ferroAlpha);
                vec3 attachedColor = mix(color, titanium, attachedDrops * uDropletGloss * 0.55);
                color = mix(color, attachedColor, attachedDrops * 0.70);
                alpha = max(alpha, attachedDrops * uLiquidAlpha * 0.92);
                fragColor = vec4(color, clamp(alpha, 0.0, max(0.34, uLiquidAlpha + uVolumeFogAlpha + uBreathTextureAlpha + uMembraneAlpha)));
            }
        """
    }
}
