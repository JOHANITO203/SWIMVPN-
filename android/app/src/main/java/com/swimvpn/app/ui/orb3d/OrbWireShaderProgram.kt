package com.swimvpn.app.ui.orb3d

import android.opengl.GLES30

internal class OrbWireShaderProgram {
    var programId: Int = 0
        private set
    var mvpLocation: Int = -1
        private set
    var timeLocation: Int = -1
        private set
    var radiusScaleLocation: Int = -1
        private set
    var deformationLocation: Int = -1
        private set
    var waveSpeedLocation: Int = -1
        private set
    var lightFlowSpeedLocation: Int = -1
        private set
    var lineAlphaLocation: Int = -1
        private set
    var saturationLocation: Int = -1
        private set
    var bumpCountLocation: Int = -1
        private set
    var bumpAmplitudeLocation: Int = -1
        private set
    var bumpSpeedLocation: Int = -1
        private set
    var flowAccelerationLocation: Int = -1
        private set
    var instabilityJitterLocation: Int = -1
        private set
    var touchLocation: Int = -1
        private set
    var touchStrengthLocation: Int = -1
        private set
    var fiberHighlightLocation: Int = -1
        private set
    var depthSeparationLocation: Int = -1
        private set
    var runnerIntensityLocation: Int = -1
        private set
    var backlineOpacityReductionLocation: Int = -1
        private set
    var backlineSaturationReductionLocation: Int = -1
        private set
    var backlineSoftnessLocation: Int = -1
        private set
    var grapheneMeshSofteningLocation: Int = -1
        private set
    var wireClarityReductionLocation: Int = -1
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
            error("Unable to link orb wire shader program: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        mvpLocation = GLES30.glGetUniformLocation(programId, "uMvp")
        timeLocation = GLES30.glGetUniformLocation(programId, "uTime")
        radiusScaleLocation = GLES30.glGetUniformLocation(programId, "uRadiusScale")
        deformationLocation = GLES30.glGetUniformLocation(programId, "uDeformation")
        waveSpeedLocation = GLES30.glGetUniformLocation(programId, "uWaveSpeed")
        lightFlowSpeedLocation = GLES30.glGetUniformLocation(programId, "uLightFlowSpeed")
        lineAlphaLocation = GLES30.glGetUniformLocation(programId, "uLineAlpha")
        saturationLocation = GLES30.glGetUniformLocation(programId, "uSaturation")
        bumpCountLocation = GLES30.glGetUniformLocation(programId, "uBumpCount")
        bumpAmplitudeLocation = GLES30.glGetUniformLocation(programId, "uBumpAmplitude")
        bumpSpeedLocation = GLES30.glGetUniformLocation(programId, "uBumpSpeed")
        flowAccelerationLocation = GLES30.glGetUniformLocation(programId, "uFlowAcceleration")
        instabilityJitterLocation = GLES30.glGetUniformLocation(programId, "uInstabilityJitter")
        touchLocation = GLES30.glGetUniformLocation(programId, "uTouch")
        touchStrengthLocation = GLES30.glGetUniformLocation(programId, "uTouchStrength")
        fiberHighlightLocation = GLES30.glGetUniformLocation(programId, "uFiberHighlight")
        depthSeparationLocation = GLES30.glGetUniformLocation(programId, "uDepthSeparation")
        runnerIntensityLocation = GLES30.glGetUniformLocation(programId, "uRunnerIntensity")
        backlineOpacityReductionLocation = GLES30.glGetUniformLocation(programId, "uBacklineOpacityReduction")
        backlineSaturationReductionLocation = GLES30.glGetUniformLocation(programId, "uBacklineSaturationReduction")
        backlineSoftnessLocation = GLES30.glGetUniformLocation(programId, "uBacklineSoftness")
        grapheneMeshSofteningLocation = GLES30.glGetUniformLocation(programId, "uGrapheneMeshSoftening")
        wireClarityReductionLocation = GLES30.glGetUniformLocation(programId, "uWireClarityReduction")
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
            error("Unable to compile orb wire shader: $log")
        }
        return shader
    }

    private companion object {
        private const val VertexShader = """
            #version 300 es
            layout(location = 0) in vec4 aWire;

            uniform mat4 uMvp;
            uniform float uTime;
            uniform float uRadiusScale;
            uniform float uDeformation;
            uniform float uWaveSpeed;
            uniform float uLightFlowSpeed;
            uniform float uLineAlpha;
            uniform float uSaturation;
            uniform float uBumpCount;
            uniform float uBumpAmplitude;
            uniform float uBumpSpeed;
            uniform float uFlowAcceleration;
            uniform float uInstabilityJitter;
            uniform vec2 uTouch;
            uniform float uTouchStrength;
            uniform float uFiberHighlight;
            uniform float uDepthSeparation;
            uniform float uRunnerIntensity;
            uniform float uBacklineOpacityReduction;
            uniform float uBacklineSaturationReduction;
            uniform float uBacklineSoftness;
            uniform float uGrapheneMeshSoftening;
            uniform float uWireClarityReduction;

            out vec4 vColor;

            const float PI = 3.14159265359;

            vec3 boost(vec3 color, float saturation) {
                float avg = (color.r + color.g + color.b) / 3.0;
                return clamp(vec3(avg) + (color - vec3(avg)) * clamp(saturation, 1.0, 1.6), 0.0, 1.0);
            }

            float bumpField(float longitude, float latitude, float time) {
                float total = 0.0;
                for (int i = 0; i < 9; i++) {
                    float enabled = step(float(i) + 0.5, uBumpCount);
                    float seed = float(i) + 1.0;
                    float bumpLon = fract(sin(seed * 12.9898) * 43758.5453) * 6.28318530718;
                    float bumpLat = -1.05 + fract(sin(seed * 31.416) * 24634.6345) * 2.10;
                    float dLon = atan(sin(longitude - bumpLon), cos(longitude - bumpLon));
                    float dLat = latitude - bumpLat;
                    float dist2 = dLon * dLon * 0.92 + dLat * dLat * 1.35;
                    float grow = sin(time * uBumpSpeed * (0.72 + seed * 0.07) + seed * 1.73) * 0.5 + 0.5;
                    float envelope = smoothstep(0.04, 0.88, grow) * smoothstep(1.0, 0.18, grow);
                    total += enabled * exp(-dist2 * (7.0 + seed * 0.36)) * envelope;
                }
                return total;
            }

            void main() {
                float longitude = aWire.x;
                float latitude = aWire.y;
                float phase = aWire.z;
                float kind = aWire.w;
                float waveTime = uTime * uWaveSpeed * uFlowAcceleration;
                float bump = bumpField(longitude, latitude, uTime);
                float touchDistance = distance(vec2(longitude / 6.28318530718, latitude / 3.14159265359 + 0.5), uTouch);
                float touchRipple = exp(-touchDistance * touchDistance * 34.0) *
                    (0.65 + sin(uTime * 8.0 - touchDistance * 32.0) * 0.35) * uTouchStrength;
                float wave =
                    sin(longitude * 2.0 + latitude * 1.4 + waveTime + phase) +
                    sin(longitude * 5.0 - latitude * 3.2 - waveTime * 0.56 + phase) * 0.35 +
                    sin(longitude * 8.0 + latitude * 2.4 + waveTime * 0.32 + phase * 0.7) * 0.18;
                float membraneWave =
                    sin(longitude * 7.0 + latitude * 4.5 + waveTime * 0.64 + phase * 0.7) * 0.014 +
                    sin(longitude * 13.0 - latitude * 2.0 - waveTime * 0.42) * 0.008;
                float jitter = sin(longitude * 17.0 + latitude * 11.0 + uTime * 5.7 + phase) * uInstabilityJitter * 0.012;
                float localRadius = uRadiusScale * (1.0 + wave * uDeformation * 0.018 + membraneWave + bump * uBumpAmplitude + touchRipple * uBumpAmplitude * 1.6 + jitter);
                float localLatitude = latitude + sin(waveTime * 0.40 + longitude * 2.0 + phase) * 0.010;
                float cosLatitude = cos(localLatitude);
                vec3 p = vec3(
                    cosLatitude * cos(longitude) * localRadius,
                    sin(localLatitude) * localRadius * 0.985,
                    cosLatitude * sin(longitude) * localRadius
                );

                float side = clamp((p.x / max(localRadius, 0.001) + 1.0) * 0.5, 0.0, 1.0);
                float front = pow(clamp((p.z / max(localRadius, 0.001) + 1.0) * 0.5, 0.0, 1.0), 2.0);
                float rim = clamp(length(p.xy), 0.0, 1.15);
                float edge = clamp(0.52 + rim * 0.76, 0.0, 1.32);
                float light = sin(longitude * 2.4 + latitude * 5.2 - uTime * uLightFlowSpeed * uFlowAcceleration + phase) * 0.5 + 0.5;
                float movingFiber = smoothstep(0.76, 0.99, light) * uFiberHighlight;
                float runnerPhase = fract((longitude * 0.15915494 + latitude * 0.07 + phase * 0.11) + uTime * uLightFlowSpeed * 0.045);
                float runnerLane = smoothstep(0.955, 1.0, sin((longitude * 3.0 + phase * 1.7)) * 0.5 + 0.5);
                float primaryArc = smoothstep(4.20, 4.55, kind);
                float runner = exp(-pow((runnerPhase - 0.50) * 18.0, 2.0)) * runnerLane * uRunnerIntensity * primaryArc;
                float crest = 1.0 + movingFiber * 2.0 + runner * 3.2;

                vec3 cyan = vec3(0.30, 0.86, 1.0);
                vec3 violet = vec3(0.72, 0.52, 1.0);
                vec3 magenta = vec3(1.0, 0.30, 0.86);
                vec3 titanium = vec3(0.86, 0.97, 1.0);
                vec3 base = mix(mix(cyan, violet, smoothstep(0.0, 0.5, side)), magenta, smoothstep(0.5, 1.0, side));
                float baseGrid = 1.0 - smoothstep(0.18, 0.86, kind);
                float veilLine = smoothstep(0.20, 0.48, kind) * (1.0 - smoothstep(0.62, 0.98, kind));
                float accentLine = smoothstep(0.95, 1.65, kind) * (1.0 - primaryArc);
                float titaniumMix = clamp(baseGrid * 0.04 + light * 0.06 + movingFiber * 0.16 + runner * 0.32, 0.0, 0.42);
                vec3 color = boost(mix(base, titanium, titaniumMix), uSaturation);
                float back = 1.0 - front;
                float backTreatment = back * smoothstep(0.08, 0.78, uBacklineSoftness);
                float luminance = dot(color, vec3(0.299, 0.587, 0.114));
                vec3 desaturated = mix(color, vec3(luminance) * vec3(0.74, 0.76, 0.86), uBacklineSaturationReduction);
                vec3 grapheneTint = vec3(0.20, 0.23, 0.32);
                vec3 cooledBack = mix(desaturated, grapheneTint, uBacklineSoftness * 0.22 + uGrapheneMeshSoftening * 0.10);
                color = mix(color, cooledBack, backTreatment);

                float kindAlpha = clamp(baseGrid * 1.00 + veilLine * 0.38 + accentLine * 0.16 + primaryArc * 0.22, 0.08, 1.0);
                float depthGate = mix(0.48, 1.0, front);
                float depthContrast = mix(1.0, depthGate, uDepthSeparation);
                float centerClear = smoothstep(0.38, 0.10, length(p.xy)) * (1.0 - primaryArc) * 0.42;
                float backOpacity = 1.0 - back * uBacklineOpacityReduction;
                float backSoftDimming = 1.0 - backTreatment * 0.16;
                float diffusionEmbed = 1.0 - uWireClarityReduction * (0.42 + (1.0 - primaryArc) * 0.34 + back * 0.24);
                diffusionEmbed = clamp(diffusionEmbed, 0.18, 1.0);
                float grapheneCalm = 1.0 - uGrapheneMeshSoftening * (0.06 + (1.0 - primaryArc) * 0.10);
                float centerBurial = 1.0 - smoothstep(0.86, 0.18, length(p.xy)) * (1.0 - primaryArc) * 0.36;
                float frontOnlyGate = mix(0.44, 1.0, front) * mix(0.92, 0.52, primaryArc);
                float alpha = uLineAlpha * (0.020 + front * 0.260 + bump * 0.014 + touchRipple * 0.020 + movingFiber * 0.024 + runner * 0.045) * edge * crest * kindAlpha * depthContrast * (1.0 - centerClear) * centerBurial * backOpacity * backSoftDimming * grapheneCalm * diffusionEmbed * frontOnlyGate;
                vColor = vec4(color, clamp(alpha, 0.0, 0.145));
                gl_Position = uMvp * vec4(p, 1.0);
            }
        """

        private const val FragmentShader = """
            #version 300 es
            precision mediump float;

            in vec4 vColor;
            out vec4 fragColor;

            void main() {
                fragColor = vColor;
            }
        """
    }
}
