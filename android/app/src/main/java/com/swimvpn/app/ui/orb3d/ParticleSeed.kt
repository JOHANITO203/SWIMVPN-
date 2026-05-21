package com.swimvpn.app.ui.orb3d

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sin

data class ParticleSeed(
    val longitude: Float,
    val latitude: Float,
    val shellDepth: Float,
    val phase: Float,
    val shimmerPhase: Float,
    val sizeRatio: Float,
    val alpha: Float,
    val speedBias: Float,
    val depthBias: Float,
)

fun buildParticleSeeds(count: Int): List<ParticleSeed> {
    return List(count) { index ->
        val n = index + 1f
        val goldenAngle = 2.3999631f
        val z = 1f - (2f * n - 1f) / count
        val latitude = acos(z.coerceIn(-1f, 1f)) - (PI.toFloat() / 2f)
        val shimmer = seeded(index, 11)
        ParticleSeed(
            longitude = (n * goldenAngle) % ((PI * 2.0).toFloat()),
            latitude = latitude,
            shellDepth = 0.60f + seeded(index, 3) * 0.30f,
            phase = seeded(index, 5) * ((PI * 2.0).toFloat()),
            shimmerPhase = shimmer * ((PI * 2.0).toFloat()),
            sizeRatio = 0.24f + seeded(index, 7) * 0.54f,
            alpha = 0.16f + abs(sin(n * 0.71f)) * 0.42f,
            speedBias = 0.82f + seeded(index, 13) * 0.36f,
            depthBias = 0.68f + seeded(index, 17) * 0.32f,
        )
    }
}

private fun seeded(index: Int, salt: Int): Float {
    val x = kotlin.math.sin((index + 1) * (12.9898f + salt) + salt * 78.233f) * 43758.5453f
    return x - kotlin.math.floor(x)
}
