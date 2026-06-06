package com.swimvpn.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Bespoke geometric feature glyphs, drawn in the same grammar as the dock's [DockGlyphs]
 * (24×24 grid, solid silhouettes, rounded joins, even-odd cut-outs). One unique shape per
 * feature — never reused across features — so a glyph identifies a feature by its nomenclature.
 *
 * These are plain [ImageVector]s, so call sites keep using `Icon(imageVector = …, tint = …)`:
 * the per-path fill below is a neutral placeholder that `Icon`'s tint overrides at render time.
 *
 * Source of truth for the shapes: docs/design/feature-glyphs-mock.html (validated before porting).
 */
object FeatureGlyphs {

    // — Connexion —
    /** Kill switch: shield with a severed power symbol (hard cutoff). */
    val KillSwitch: ImageVector = glyph(
        "KillSwitch",
        "M12 2.2 4.8 4.9a1.2 1.2 0 0 0-.8 1.12V11c0 4.7 3.05 8.2 7.55 10.6a1 1 0 0 0 .9 0C16.95 19.2 20 15.7 20 11V6.02a1.2 1.2 0 0 0-.8-1.12L12 2.2zM12.05 5.4a1 1 0 0 1 1 1v3.1a1 1 0 0 1-2 0V6.4a1 1 0 0 1 1-1zM8.9 7.2a1 1 0 0 1 .55 1.78A3.6 3.6 0 0 0 12 15.2a3.6 3.6 0 0 0 2.55-6.22 1 1 0 1 1 1.1-1.66A5.6 5.6 0 0 1 12 17.2a5.6 5.6 0 0 1-3.65-9.84A1 1 0 0 1 8.9 7.2z",
    )

    /** Camouflage: a mask (adaptive-fingerprint disguise). */
    val Camouflage: ImageVector = glyph(
        "Camouflage",
        "M3.4 6.6c2.6-.7 5.2-1 8.6-1s6 .3 8.6 1a1 1 0 0 1 .74.85c.2 1.9.06 3.55-.5 4.9-.9 2.2-2.9 3.3-5.2 3.05-1.18-.13-2.05-.7-2.86-1.65a1.2 1.2 0 0 0-1.76 0C10.22 14.7 9.35 15.27 8.17 15.4 5.87 15.65 3.87 14.55 2.97 12.35c-.56-1.35-.7-3-.5-4.9a1 1 0 0 1 .93-.85zM7.4 9.1a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4zm9.2 0a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4z",
        evenOdd = true,
    )

    /** Auto-connexion: power symbol with an auto arrow. */
    val AutoConnect: ImageVector = glyph(
        "AutoConnect",
        "M12 3a1.1 1.1 0 0 1 1.1 1.1v5.2a1.1 1.1 0 0 1-2.2 0V4.1A1.1 1.1 0 0 1 12 3zM7.7 6.05a1.1 1.1 0 0 1 .35 1.74 5.6 5.6 0 1 0 7.9 0 1.1 1.1 0 1 1 1.56-1.55 7.8 7.8 0 1 1-11.02 0 1.1 1.1 0 0 1 1.21-.19zM18.1 4.1a.9.9 0 0 1 .9.9v2.6a.9.9 0 0 1-.9.9h-2.6a.9.9 0 0 1-.45-1.68l.62-.36a4.7 4.7 0 0 0-.7-.5.9.9 0 1 1 .9-1.56c.4.23.77.5 1.1.8V5a.9.9 0 0 1 .9-.9z",
    )

    /** Battery optimisation: battery body with a bolt. */
    val Battery: ImageVector = glyph(
        "Battery",
        "M5 6.5h11A2.5 2.5 0 0 1 18.5 9v.5H20a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1.5v.5A2.5 2.5 0 0 1 16 18.5H5A2.5 2.5 0 0 1 2.5 16V9A2.5 2.5 0 0 1 5 6.5zm6.9 2.1a.6.6 0 0 1 1.08.46l-.45 2.34h1.62a.6.6 0 0 1 .47.97l-3.5 4.5a.6.6 0 0 1-1.07-.46l.45-2.34H8.85a.6.6 0 0 1-.47-.98l3.52-4.49z",
        evenOdd = true,
    )

    /** Adaptive agent (AI): a sparkle. */
    val AdaptiveAgent: ImageVector = glyph(
        "AdaptiveAgent",
        "M13 2.6a1 1 0 0 0-1.9 0l-1.05 4.2a3 3 0 0 1-2.18 2.18L3.65 10a1 1 0 0 0 0 1.94l4.22 1.06a3 3 0 0 1 2.18 2.18l1.06 4.22a1 1 0 0 0 1.94 0l1.06-4.22a3 3 0 0 1 2.18-2.18l4.22-1.06a1 1 0 0 0 0-1.94l-4.22-1.05a3 3 0 0 1-2.18-2.18zM19.2 16.4a.7.7 0 0 0-1.34 0l-.3 1.05a1.4 1.4 0 0 1-.96.96l-1.06.3a.7.7 0 0 0 0 1.34l1.06.3a1.4 1.4 0 0 1 .96.96l.3 1.06a.7.7 0 0 0 1.34 0l.3-1.06a1.4 1.4 0 0 1 .96-.96l1.06-.3a.7.7 0 0 0 0-1.34l-1.06-.3a1.4 1.4 0 0 1-.96-.96z",
    )

    /** Geo-bypass: globe with a deviating arrow. */
    val GeoBypass: ImageVector = glyph(
        "GeoBypass",
        "M11 2.06A9 9 0 0 0 11.6 20a9 9 0 0 0 1.62-.15 1 1 0 0 1-.34-1.46l.3-.42a7 7 0 0 1-1.18.1 7 7 0 0 1-.5-13.98V6.5a1 1 0 0 0 0 2h1.4a7 7 0 0 1 1.3 2.5 1 1 0 0 0 1.94-.5 9 9 0 0 0-5.04-6.43 9 9 0 0 0-1.1-.05zM7.1 5.7a7 7 0 0 0-1.3 9.07 1 1 0 0 0 .9.55h1.9a1 1 0 0 0 1-1v-2a1 1 0 0 0-.6-.92l-1.7-.74a1 1 0 0 1-.4-1.55l.9-1.1a1 1 0 0 0-.6-1.6zM17.7 13.3a1 1 0 0 0-1.42 1.4l.8.8h-2.83a1 1 0 0 0 0 2h2.83l-.8.8a1 1 0 0 0 1.42 1.4l2.5-2.5a1 1 0 0 0 0-1.4z",
        evenOdd = true,
    )

    /** Geo-bypass list: rows with a pin accent. */
    val GeoBypassList: ImageVector = glyph(
        "GeoBypassList",
        "M3.2 5.4a1.2 1.2 0 0 1 1.2-1.2h7.6a1.2 1.2 0 0 1 0 2.4H4.4a1.2 1.2 0 0 1-1.2-1.2zM3.2 10.9a1.2 1.2 0 0 1 1.2-1.2h6.6a1.2 1.2 0 0 1 0 2.4H4.4a1.2 1.2 0 0 1-1.2-1.2zM3.2 16.4a1.2 1.2 0 0 1 1.2-1.2h5.6a1.2 1.2 0 0 1 0 2.4H4.4a1.2 1.2 0 0 1-1.2-1.2zM18.4 8.4c-2.1 0-3.8 1.66-3.8 3.7 0 2.55 3.06 5.62 3.4 5.96a.55.55 0 0 0 .8 0c.34-.34 3.4-3.41 3.4-5.96 0-2.04-1.7-3.7-3.8-3.7zm0 2.5a1.25 1.25 0 1 1 0 2.5 1.25 1.25 0 0 1 0-2.5z",
    )

    /** Routing (split tunnel): a fork in a route. */
    val Routing: ImageVector = glyph(
        "Routing",
        "M9 2.4a3 3 0 0 0-1 5.83v3.02c0 .9.4 1.45 1.1 2.05l3.1 2.66c.3.26.5.5.5.94v.74a3 3 0 1 0 2.2 0v-.74c0-1.2-.55-2.06-1.4-2.78l-3.1-2.66a.9.9 0 0 1-.4-.7V8.23A3 3 0 0 0 9 2.4zm9 0a3 3 0 0 0-1 5.83v8.54a3 3 0 1 0 2.2 0V8.23A3 3 0 0 0 18 2.4z",
    )

    // — Application —
    /** Language: a speech bubble holding two script marks (translate). */
    val Language: ImageVector = glyph(
        "Language",
        "M5 3.6h14A2.6 2.6 0 0 1 21.6 6.2v8.4A2.6 2.6 0 0 1 19 17.2H9.7l-3.5 3.06A1 1 0 0 1 4.5 19.5v-2.3H5A2.6 2.6 0 0 1 2.4 14.6V6.2A2.6 2.6 0 0 1 5 3.6zm3 3.3a.85.85 0 0 0-.8.56l-2 5.5a.85.85 0 0 0 1.6.58l.28-.78h1.84l.28.78a.85.85 0 0 0 1.6-.58l-2-5.5A.85.85 0 0 0 8 6.9zm0 2.95.46 1.28h-.92zM14 6.9a.9.9 0 0 0-.9.9v.2h-1a.85.85 0 0 0 0 1.7h.16c.2.66.55 1.27 1.02 1.8-.38.24-.8.42-1.26.52a.85.85 0 0 0 .36 1.66 5.3 5.3 0 0 0 2.02-.92 5.3 5.3 0 0 0 1.3.72.85.85 0 0 0 .6-1.6 3.6 3.6 0 0 1-.5-.24c.46-.53.82-1.14 1.02-1.94h.28a.85.85 0 0 0 0-1.7h-2.2v-.2A.9.9 0 0 0 14 6.9zm0 2.8h.86c-.13.34-.3.65-.5.9-.2-.25-.36-.56-.36-.9z",
        evenOdd = true,
    )

    /** Theme: a disc split light/dark. */
    val Theme: ImageVector = glyph(
        "Theme",
        "M12 2.6a9.4 9.4 0 1 0 0 18.8 9.4 9.4 0 0 0 0-18.8zm0 2.2v14.4a7.2 7.2 0 0 0 0-14.4z",
        evenOdd = true,
    )

    // — Mon proxy —
    /** Proxy / hub: a central node with satellites. */
    val ProxyHub: ImageVector = glyph(
        "ProxyHub",
        "M12 8.7a3.3 3.3 0 1 0 0 6.6 3.3 3.3 0 0 0 0-6.6zM12 2.3a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8zM12 16.9a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8zM4.9 5.4a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8zM19.1 5.4a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8z",
    )

    /** Latency / speed: a speedometer dial. */
    val Latency: ImageVector = glyph(
        "Latency",
        "M12 4a9 9 0 0 0-7.8 13.5 1.4 1.4 0 0 0 1.22.7h13.16a1.4 1.4 0 0 0 1.22-.7A9 9 0 0 0 12 4zm0 2.2a1 1 0 0 1 1 1 1 1 0 0 1-2 0 1 1 0 0 1 1-1zM6.7 8.5a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm10.6 0a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm-1.1 2.95a1.2 1.2 0 0 1 .26 1.84l-2.5 2.6a2 2 0 1 1-2.2-2.86l3.27-1.64a1.2 1.2 0 0 1 1.17.06z",
        evenOdd = true,
    )

    /** Leak protection / secure DNS: a shield with a droplet. */
    val LeakProtection: ImageVector = glyph(
        "LeakProtection",
        "M12 2.4 5.2 4.95a1.2 1.2 0 0 0-.8 1.12V11c0 4.55 2.95 7.95 7.25 10.35a1.1 1.1 0 0 0 1.06 0C16.95 18.95 19.6 15.55 19.6 11V6.07a1.2 1.2 0 0 0-.8-1.12L12 2.4zm0 5.1c.32 0 .6.16.78.42l1.6 2.36a2.95 2.95 0 1 1-4.86.07l1.7-2.43A.95.95 0 0 1 12 7.5zm0 3.05-.66.94a1.2 1.2 0 1 0 1.34-.05z",
        evenOdd = true,
    )

    // — Compte —
    /** Offers / promo: a price tag. */
    val Offers: ImageVector = glyph(
        "Offers",
        "M11.2 3.1a2.4 2.4 0 0 0-1.7.7L3.4 9.9a2.4 2.4 0 0 0 0 3.4l7.3 7.3a2.4 2.4 0 0 0 3.4 0l6.1-6.1a2.4 2.4 0 0 0 .7-1.7V5.5a2.4 2.4 0 0 0-2.4-2.4h-7.3zm5.7 2.6a1.7 1.7 0 1 1 0 3.4 1.7 1.7 0 0 1 0-3.4z",
        evenOdd = true,
    )

    /** Payment: a card with a chip. */
    val Payment: ImageVector = glyph(
        "Payment",
        "M4.4 4.8h15.2A2.4 2.4 0 0 1 22 7.2v9.6a2.4 2.4 0 0 1-2.4 2.4H4.4A2.4 2.4 0 0 1 2 16.8V7.2a2.4 2.4 0 0 1 2.4-2.4zm.6 2.6a.8.8 0 0 0-.8.8v2.1c0 .44.36.8.8.8h3.2a.8.8 0 0 0 .8-.8V8.2a.8.8 0 0 0-.8-.8H5zm9.3 7.4a1 1 0 0 0 0 2h4a1 1 0 0 0 0-2h-4z",
        evenOdd = true,
    )

    /** Help / support: a life buoy. */
    val Help: ImageVector = glyph(
        "Help",
        "M12 2.4a9.6 9.6 0 1 0 0 19.2 9.6 9.6 0 0 0 0-19.2zm0 3.1c.62 0 1.22.08 1.78.24l-.96 2.96a3.6 3.6 0 0 0-1.64 0L10.22 5.74A6.5 6.5 0 0 1 12 5.5zm-3.62 1.1.96 2.96a3.6 3.6 0 0 0-1.02 1.3L5.36 8.94A6.55 6.55 0 0 1 8.38 6.6zm7.24 0a6.55 6.55 0 0 1 3.02 2.34l-2.96.96a3.6 3.6 0 0 0-1.02-1.3l.96-2.96zM12 9.6a2.4 2.4 0 1 1 0 4.8 2.4 2.4 0 0 1 0-4.8zm-6.5.36 2.96.96a3.6 3.6 0 0 0 0 2.16L5.5 14.04a6.5 6.5 0 0 1 0-4.08zm13 0a6.5 6.5 0 0 1 0 4.08l-2.96-.96a3.6 3.6 0 0 0 0-2.16zm-9.14 5.48a3.6 3.6 0 0 0 1.02 1.3l-.96 2.96A6.55 6.55 0 0 1 5.36 17.4zm7.28 0 2.96.96a6.55 6.55 0 0 1-3.02 2.34l-.96-2.96a3.6 3.6 0 0 0 1.02-1.3zm-4.46 1.92a3.6 3.6 0 0 0 1.64 0l.96 2.96a6.5 6.5 0 0 1-3.56 0z",
        evenOdd = true,
    )

    private fun glyph(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
        }.build()
}
