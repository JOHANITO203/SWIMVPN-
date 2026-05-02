# Access And Active Config Metadata Separation Design

## Goal
Create a single coherent UI truth for quota and expiration without conflating two different sources:
- SWIMVPN-managed access sold by SWIMVPN
- imported VPN configs brought by the user

The app must let users understand both their SWIMVPN account access and the currently active VPN config, while making the source of each metric obvious.

## Problem Statement
The current app now has two valid metadata sources:
1. backend access/profile data for SWIMVPN offers and trials
2. parser-derived metadata from imported configs, including external subscriptions

Today the UI does not clearly separate these two truths. This creates ambiguity around:
- which quota is being shown
- which expiration is being shown
- whether a value is enforced by SWIMVPN or just observed from an imported config

This ambiguity becomes more serious because SWIMVPN intentionally allows users to connect using configs purchased elsewhere.

## Product Principles
- The app must never imply that an imported external config is managed by SWIMVPN.
- The app must never merge backend subscription quota and parser-derived config quota into a single number.
- The app must always expose the source of the currently active config.
- Parser metadata is observational and import-oriented.
- Backend access metadata is account-oriented and commercial.

## Recommended Approach
Use two clearly separated cards in the profile area:
1. `SWIMVPN Access`
2. `Active Config`

This keeps one truth per card instead of one truth per field.

## Card 1: SWIMVPN Access
### Purpose
Represent what SWIMVPN knows and manages.

### Source of truth
Backend `AccessProfileResponse`

### Contents
- account/access status
- access type: trial or subscription
- commercial plan name/code when relevant
- expiration of SWIMVPN-managed access
- SWIMVPN-managed quota/progress when relevant

### Rules
- This card is always about the SWIMVPN account state.
- If the user is on a trial, this card shows the trial state.
- If the user has an active paid plan, this card shows the paid plan state.
- If the user is using an external config, this card does not pretend that the external config quota belongs to SWIMVPN.

## Card 2: Active Config
### Purpose
Represent the VPN config currently selected or imported for connection.

### Source of truth
Parser-derived normalized config metadata plus local config identity.

### Source badges
- `SWIMVPN Managed`
- `Imported Config`

### Contents
- display name
- provider name if available
- protocol
- server/host if useful
- config expiration if parsed
- config traffic used/total if parsed
- warnings if the config is partially informative

### Rules
- If parser metadata is absent, the app shows only the config identity and source badge.
- If parser metadata is present, it is displayed as config metadata only.
- The card must not claim that parser quota is enforced by SWIMVPN.

## Source Differentiation Model
Introduce a lightweight UI-facing distinction for config ownership/source:
- `SWIMVPN_MANAGED`
- `IMPORTED_CONFIG`

This distinction is not payment logic. It is display truth.

### Mapping rules
- A config delivered by SWIMVPN backend or known to be ours maps to `SWIMVPN_MANAGED`.
- A manually imported config or external subscription maps to `IMPORTED_CONFIG`.

## UI Behavior Rules
### When user has SWIMVPN access and uses SWIMVPN config
- `SWIMVPN Access` shows account quota/expiration
- `Active Config` shows source `SWIMVPN Managed`
- parser metadata may still appear, but as config metadata only

### When user has SWIMVPN access but uses imported external config
- `SWIMVPN Access` still shows account state
- `Active Config` shows source `Imported Config`
- config quota/expiration come from parser metadata if available

### When user has no paid SWIMVPN access but imports a config
- `SWIMVPN Access` can remain minimal, showing no active managed plan or only trial eligibility/account state
- `Active Config` becomes the practical connection truth for quota/expiration if parser metadata exists

## Data Flow
1. Import pipeline preserves raw config and parses normalized metadata.
2. Config storage keeps enough information to know whether the config is SWIMVPN-managed or imported.
3. Profile UI receives two separate view models:
   - account access state
   - active config state
4. Each card renders only from its own source.

## Non-Goals
- Do not merge backend and parser quotas into one progress bar.
- Do not let the subscription page become a config metadata inspector.
- Do not move parser logic into UI composables.
- Do not invent backend enforcement for imported config quotas.

## Initial Implementation Scope
### Android UI
- Refactor `ProfileScreen` into two clear cards.
- Keep subscription/checkout screen focused on offers and payment.
- Add source badge labeling for active config.

### Android data/view-state
- Add a small UI model for active config metadata.
- Map parser output into this UI model.
- Add a source enum or equivalent labelable type.

### Existing quota card coherence
- The current quota/progress logic in the profile is not discarded.
- It is narrowed to `SWIMVPN Access` only.
- Config quota/progress becomes a separate concern inside `Active Config`.

## Error Handling
- Missing parser metadata must never break the profile screen.
- If only some config metadata is known, show partial fields and omit the rest.
- If the active config source is unknown, default to conservative labeling and avoid misleading ownership language.

## Testing Strategy
- Verify profile rendering for:
  - trial + no imported config
  - active paid SWIMVPN plan + SWIMVPN-managed config
  - active paid SWIMVPN plan + imported config with metadata
  - imported config with no metadata
- Verify no screen claims backend ownership over imported config quotas.
- Verify parser-derived expiration/quota only appear in the `Active Config` card.

## Recommended Rollout Order
1. Add active config source/view-state model.
2. Refactor profile UI into `SWIMVPN Access` and `Active Config`.
3. Wire parser metadata into `Active Config`.
4. Verify trial, paid plan, and imported-config scenarios.

## Open Points Resolved
- External configs are labeled `Imported Config`.
- The subscription page remains product/checkout oriented.
- The profile page becomes the central place for quota/expiration coherence.
