package com.swimvpn.app.ui.orb3d

data class OrbRgba(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    fun withAlpha(value: Float): OrbRgba = copy(alpha = value.coerceIn(0f, 1f))

    fun brighten(amount: Float): OrbRgba = mix(this, Orb3DPalette.Highlight, amount)
}

object Orb3DPalette {
    val Blue = rgba(0x4D, 0xA3, 0xFF)
    val Cyan = rgba(0x66, 0xE6, 0xFF)
    val PurplePrimary = rgba(0x9B, 0x5C, 0xFF)
    val PurpleSecondary = rgba(0xB8, 0x84, 0xFF)
    val PurpleSoftGlow = rgba(0xC8, 0x9B, 0xFF)
    val Magenta = rgba(0xFF, 0x4F, 0xD8)
    val Rose = rgba(0xFF, 0x7A, 0xF6)
    val FailureRed = rgba(0xFF, 0x4B, 0x72)
    val FailureAmber = rgba(0xFF, 0x91, 0x5E)
    val SurfaceGlow = rgba(0xD9, 0xB8, 0xFF)
    val TitaniumGlow = rgba(0xE8, 0xF7, 0xFF)
    val MutedPurple = rgba(0x74, 0x6C, 0x8E)
    val Highlight = rgba(0xF6, 0xE8, 0xFF)

    fun sideColor(sideMix: Float, saturationBoost: Float = 1f): OrbRgba {
        val left = mix(Blue, Cyan, 0.58f)
        val center = PurpleSecondary
        val right = mix(Magenta, Rose, 0.46f)
        val color = if (sideMix < 0.5f) {
            mix(left, center, sideMix * 2f)
        } else {
            mix(center, right, (sideMix - 0.5f) * 2f)
        }
        return boosted(color, saturationBoost)
    }

    fun surfaceColor(
        point: Orb3DPoint,
        primary: OrbRgba,
        secondary: OrbRgba,
        saturationBoost: Float = 1f,
    ): OrbRgba {
        val base = sideColor(point.sideMix, saturationBoost)
        val depthTint = mix(primary, secondary, point.front * 0.46f)
        return boosted(mix(base, depthTint, 0.28f + point.front * 0.18f), saturationBoost)
    }

    fun boosted(color: OrbRgba, saturationBoost: Float): OrbRgba {
        val average = (color.red + color.green + color.blue) / 3f
        val boost = saturationBoost.coerceIn(1f, 1.6f)
        return OrbRgba(
            red = (average + (color.red - average) * boost).coerceIn(0f, 1f),
            green = (average + (color.green - average) * boost).coerceIn(0f, 1f),
            blue = (average + (color.blue - average) * boost).coerceIn(0f, 1f),
            alpha = color.alpha,
        )
    }

    private fun rgba(red: Int, green: Int, blue: Int): OrbRgba {
        return OrbRgba(red / 255f, green / 255f, blue / 255f, 1f)
    }
}

fun mix(start: OrbRgba, end: OrbRgba, fraction: Float): OrbRgba {
    val t = fraction.coerceIn(0f, 1f)
    return OrbRgba(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}
