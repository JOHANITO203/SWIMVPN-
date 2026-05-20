# SWIMVPN Server Screen Tokens - 2026-05

## Scope

This document freezes the Android Servers screen design contract for the Dark
Luxury pass. The screen is UI-only and must not change VPN runtime, config
parsing, backend entitlement, trial, subscription, or fulfillment rules.

## Data Contract

```kotlin
enum class ServerSourceTab {
    IMPORTED,
    PREMIUM,
}

data class ImportedConfigSummaryUi(
    val totalQuotaGb: String,
    val quotaCaption: String,
    val expiresOn: String,
    val expiresCaption: String,
)

data class ServerNodeUi(
    val id: String,
    val countryName: String,
    val flagEmoji: String,
    val latencyLabel: String,
    val isSelected: Boolean,
)

data class ServerScreenUiState(
    val selectedTab: ServerSourceTab,
    val aiActive: Boolean,
    val importedConfig: ImportedConfigSummaryUi?,
    val importedNodes: List<ServerNodeUi>,
    val premiumNodes: List<ServerNodeUi>,
    val selectedNodeId: String?,
)
```

Quota and expiration belong to the active imported/managed configuration
summary, never to individual server rows.

## Shared Visual Source

The screen derives from `SwimDesignTokens`:

```text
SwimDesignTokens.Color
SwimDesignTokens.Material
SwimDesignTokens.Highlight
SwimDesignTokens.Shadow
SwimDesignTokens.Shape
SwimDesignTokens.Motion
SwimDesignTokens.Servers
SwimDesignTokens.Dock
SwimDesignTokens.UserButton
```

The dock, Start button, User button, AI card, segmented selector, action pills,
quota pill, and server rows must all use the same black molded hardware logic:
outer shell, recessed bowl/pill, controlled purple active state, soft rim
highlight, and no noise or texture.

## Layout Contract

```text
Root: SwimDarkLuxuryBackground
Top-right: SwimCircularIconButton profile action
Top center: Imported/Premium segmented pill
Main: AI active card with embedded quota pill
Actions: Import Access / Subscribe pill buttons
List: compact selectable server node pills
Bottom: SwimMetaballDock active on Servers
```

## Motion Contract

```text
screenEnter: 320ms
curve: cubic-bezier(0.22, 1, 0.36, 1)
selectorDelay: 40ms
aiCardDelay: 80ms
actionsDelay: 130ms
rowStagger: 30ms, capped under 250ms
dockTransition: existing MetaballNavDock active-center animation
```

## Negative Rules

Do not show both Imported and Premium sections fully at the same time.
Do not show quota, expiration, host, port, protocol, load, pin, or provider
metadata inside a server row.
Do not create a second dock.
Do not invent premium access or fake backend server exposure.
