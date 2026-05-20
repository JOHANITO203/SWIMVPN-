# SWIMVPN Design DNA - Dark Luxury

Date: 2026-05-20

This document is the design source of truth for future SWIMVPN mobile UI implementations.

It defines the visual DNA, tokens, screen contracts, and negative rules that must guide Home, Servers, Subscription, and Settings updates.

## 0. Global Design DNA

Design a premium futuristic VPN mobile app with a dark monolithic UI.

Core visual language:

- matte black background
- cold purple ambient lighting
- monolithic pill surfaces
- 100% rounded geometry
- metaball floating dock
- hardware-inspired depth
- soft industrial luxury
- minimal cyber-futurism
- no generic SaaS layout
- no sharp rectangles
- no flat Android cards

The final interface must feel less like a classic graphical UI and more like a tactile piece of premium network hardware.

## 1. Color Tokens

| Token | Value | Usage |
| --- | --- | --- |
| Background base | `#0D0C13` | Main matte screen background |
| Background deep | `#07070B` | Deep OLED zones and bottom depth |
| Surface base | `#131317` | Default black hardware surface |
| Surface elevated | `#1A1A20` | Raised cards, dock nodes, controls |
| Surface soft highlight | `#24232B` | Subtle top light on surfaces |
| Purple primary | `#8A6AF1` | Main brand purple |
| Purple active | `#A489E7` | Active tabs, active dock node, selected state |
| Purple deep | `#443677` | Recessed purple glow and dark purple badges |
| Purple glow | `rgba(138, 106, 241, 0.35)` | Localized active glow |
| Text primary | `#F3F1F6` | Main readable text |
| Text secondary | `#A6A1B3` | Secondary information |
| Text muted | `#6E6978` | Inactive labels |
| Success green | `#35D978` | Protected and active health status |
| Divider subtle | `rgba(255,255,255,0.08)` | Minimal separators |

## 2. Background

Use a deep matte black background with a cold purple radial ambient glow coming from the right side or upper-right side.

Rules:

- The background must stay clean, soft, cinematic, and atmospheric.
- No noisy texture.
- No city.
- No HUD.
- No cyberpunk environment.
- No decorative clutter that competes with the UI.

## 3. Material Tokens

All surfaces must look like soft black hardware.

Use:

- layered gradients
- subtle top highlights
- soft inner shadows
- restrained elevation
- localized purple glow for active states only

Reference material tokens:

```css
surface-gradient:
linear-gradient(180deg, rgba(36,35,43,0.96) 0%, rgba(14,14,18,0.98) 100%);

raised-surface-shadow:
0 18px 45px rgba(0,0,0,0.45);

inner-highlight:
inset 0 1px 0 rgba(255,255,255,0.08);

inner-dark-shadow:
inset 0 -10px 24px rgba(0,0,0,0.35);

purple-active-glow:
0 0 28px rgba(138,106,241,0.38);
```

Material feeling:

- black monolithic hardware
- tactile
- soft industrial luxury
- calm and precise
- not glossy plastic
- not cheap neon

## 4. Radius Tokens

Every interactive surface must use 100% rounded geometry.

Use pill surfaces, capsule buttons, and circular icon containers.

| Element | Radius / Size |
| --- | --- |
| Pill surface | `border-radius: 9999px` |
| Large monolithic card | `42px` to `56px`, visually close to capsule geometry |
| Icon badge | circle, `52px` to `72px` diameter |
| Segmented control | `border-radius: 9999px` |
| Bottom dock | organic metaball shape, not a normal rectangle |

No sharp rectangles.

## 5. Typography

Use a modern premium sans-serif such as SF Pro, Inter, Satoshi, or Geist.

The Android implementation may keep the current app font if needed, but hierarchy must follow these roles.

| Role | Size | Weight | Color |
| --- | --- | --- | --- |
| Screen title | `34px` to `42px` | `650` to `750` | `#F3F1F6` |
| Section label | `13px` to `15px` | `700`, uppercase | `#A489E7` |
| Card title | `24px` to `28px` | `650` | `#F3F1F6` |
| Body | `16px` to `18px` | `400` to `500` | `#A6A1B3` |
| Micro text | `13px` to `15px` | `400` to `600` | `#8A6AF1` or `#A6A1B3` |

Section labels must use:

- uppercase
- `0.08em` letter spacing
- compact but legible scale

## 6. Spacing

Spacing must be compact, dense, and premium.

| Token | Value |
| --- | --- |
| Screen horizontal padding | `52px` to `64px` |
| Vertical stack gap | `16px` to `24px` |
| Section gap | `34px` to `44px` |
| Dock bottom margin | `34px` to `44px` |

The layout must not feel like a loose marketing landing page.

## 7. Bottom Dock DNA

Create a floating metaball navigation dock.

The dock must look like several black circular nodes fused together into one organic liquid shape.

It must not look like:

- a simple rectangular bar
- a classic capsule
- a default Android bottom navigation

Dock properties:

- organic liquid silhouette
- concave transitions between nodes
- adaptive contour around icons
- matte black material
- soft hardware depth
- subtle purple edge glow
- active node enlarged
- inactive nodes integrated into the same fused body

Active node:

- circular purple glowing node
- integrated into the metaball body
- soft localized glow
- icon in white
- label optional depending on screen
- `ACTIVE` label optional depending on density
- small purple dot below active label

Inactive nodes:

- dark circular depressions
- white outline icons
- low contrast labels
- no strong glow

Dock lighting:

- purple bloom only around active node
- no glow across the whole dock
- no neon overdose

Navigation nodes:

1. Home
2. Servers
3. Subscription
4. Settings

## 8. Screen Contract - Home

Screen state: Home / Connected State.

Purpose:

Show VPN connection status, selected location, basic runtime stats, and primary navigation.

Layout:

- top left: only system time, no logo, no text
- top right: circular black user icon container with white user icon
- center: large living VPN power orb
- bottom center: floating metaball dock with Home active

Center orb:

- large centered circular power button in matte black
- outer dark circular ring
- inner recessed black circle
- central glowing purple power icon
- subtle inner shadow
- hardware-like material
- no flat blue button

Orbital mesh:

- purple procedural particle mesh
- semi-transparent
- circular organic shape
- soft wave deformation
- subtle holographic depth
- clean edges
- elegant premium glow
- not chaotic
- not audio visualizer
- not a globe

Orb feeling:

- alive
- intelligent
- fluid
- protective
- premium

Connected text:

- title: `Connected`
- subtitle: `Your connection is secure`
- status indicator: green dot + `Protected`

Server pill:

- compact monolithic pill surface below status
- left circular purple globe icon badge
- title: country, for example `France`
- subtitle: city and mode, for example `Paris · Auto select`
- right circular dark chevron container
- 100% pill radius
- matte black material
- subtle hardware reflections
- soft inner shadow
- no rectangular card

Stats card:

- one large monolithic rounded card
- three columns:
  - purple download icon + `Downloaded`
  - purple upload icon + `Uploaded`
  - purple connection icon + `Connected`
- minimal vertical dividers
- no overload
- no charts

Bottom dock:

- Home active
- active Home node is enlarged, purple, circular, glowing
- inactive nodes: Servers, Subscription, Settings

Home must feel calm, secure, premium, and alive.

Do not add:

- extra server cards
- technical panels
- charts
- noisy telemetry

## 9. Screen Contract - Servers

Purpose:

Show two server sources:

1. Imported Servers
2. Premium Servers

Important rule:

Only one section expresses itself at a time.

- When Imported Servers is active, Premium Servers stays collapsed/inactive.
- When Premium Servers is active, Imported Servers stays collapsed/inactive.

Top:

- top left: system time only
- top right: circular black user icon container with white user icon
- no big page title
- compact top layout

Segmented source selector:

- centered monolithic segmented pill near the top
- two tabs: Imported Servers and Premium Servers
- active tab uses purple gradient
- inactive tab uses muted gray
- full pill geometry
- matte black hardware material
- subtle inner shadows
- no hard border

AI status card:

- one large monolithic card with very high radius
- left circular AI orb badge
- purple mesh / AI core inside badge
- label: `AI active`
- center title: `AI active`
- center subtitle: `Finding the best servers for your connection`
- right: small green active dot

Configuration quota surface:

- embedded horizontal pill inside the AI card
- quota and expiration belong to the decrypted configuration, not individual server nodes
- left:
  - `Total quota`
  - `842 GB`
  - `Unlimited`
- right:
  - `Expires on`
  - `Dec 12, 2025`
  - `In 45 days`
- embedded pill must feel fused into the parent card
- 100% rounded geometry

Action buttons:

- two separate pill buttons side by side
- `Import Access` / `Add your configs`
- `Subscribe` / `Get premium access`
- each has icon inside circular badge
- black monolithic pill surfaces
- compact height
- subtle depth
- no aggressive glow

Server list:

- compact vertical list of decrypted server nodes as selectable locations
- each row is a full-width pill surface
- flag on the left
- country name
- latency below in small purple text
- selection circle on the right

Visible example nodes:

- France selected
- Germany
- United States
- United Kingdom
- Canada
- Japan
- Singapore

Selected row:

- subtle purple outline
- purple filled check circle on the right
- no heavy glow

Unselected rows:

- dark empty circle selector
- subtle surface elevation

Do not show per-server quota or expiration.

Do not overload rows with protocol, traffic, or extra metadata.

Bottom dock:

- Servers active
- inactive nodes: Home, Subscription, Settings

## 10. Screen Contract - Subscription

Purpose:

Show three plan options:

- Basic
- Premium
- Platinum

Top:

- top left: system time only
- top right: circular black user icon container with white user icon
- centered title: `Subscription`
- subtitle: `Choose the plan that fits your needs`

Plan card structure:

- three vertically stacked monolithic cards
- physical black hardware block feeling
- very high radius corners, almost capsule-like
- no square corners

Card style:

- matte black gradient
- subtle top highlight
- soft inner shadow
- soft ambient elevation
- no cheap SaaS pricing look

Basic:

- circular icon badge on left
- title: `Basic`
- subtitle: `Essential protection`
- price: `$4.99 / month`
- features:
  - Secure VPN connection
  - Auto select server
  - No logs policy
- bottom CTA pill: `Select Basic`
- calm dark visual
- CTA is black monolithic pill

Premium:

- highlighted plan
- circular shield/diamond badge on left
- title: `Premium`
- subtitle: `Advanced protection`
- price: `$8.99 / month`
- top-right small pill badge: `Most popular`
- features:
  - Everything in Basic
  - Access to premium servers
  - Kill switch
  - Split tunneling
  - Priority support
- bottom CTA pill: `Select Premium`
- subtle purple outline
- soft purple ambient glow
- purple CTA button
- localized glow only

Platinum:

- circular crown badge
- title: `Platinum`
- subtitle: `Ultimate security`
- price: `$14.99 / month`
- features:
  - Everything in Premium
  - Dedicated IP
  - Multi-hop connection
  - Advanced threat protection
  - 24/7 VIP support
- bottom CTA pill: `Select Platinum`
- obsidian luxury
- no gold
- matte black and white/chrome feel
- restrained premium

Guarantee row:

- purple shield icon badge
- `30-day money-back guarantee`
- `Cancel anytime, no questions asked.`

Bottom dock:

- Subscription active
- inactive nodes: Home, Servers, Settings

## 11. Screen Contract - Settings / Parameters

Purpose:

Allow the user to manage app and connectivity preferences.

Application:

- Theme: Light / Dark
- Language: Russian / English / French

Connectivity:

- Routing: Tunnel / Proxy
- Auto-connect
- AI Switch node
- Kill Switch

Top:

- top left: system time only
- top right: circular black user icon container with white user icon
- centered title: `Settings`

Background:

- deep matte black
- cold purple ambient glow on the right
- calm and premium atmosphere

Section labels:

- `APPLICATION`
- `CONNECTIVITY`
- small
- spaced letters
- purple
- aligned left with content column

Application card:

- one large monolithic card with very high radius

Theme row:

- left circular icon badge
- title: `Theme`
- subtitle: `Choose your preferred theme`
- segmented pill selector: Light / Dark
- Dark selected with purple active pill

Language row:

- left circular globe icon badge
- title: `Language`
- subtitle: `Select your language`
- language options:
  - `Русский`
  - `English`
  - `Français`
- selected state uses a small purple check circle on the right
- no heavy highlight

Connectivity card:

- one large monolithic card with very high radius

Routing row:

- circular routing icon badge
- title: `Routing`
- subtitle: `Choose how your connection is routed`
- segmented pill selector: Tunnel / Proxy
- Tunnel selected with purple active pill

Toggle rows:

- Auto-connect
- AI Switch node
- Kill Switch

Toggle style:

- right-side `ON` label
- purple toggle switch when active
- white knob
- soft hardware shadow
- pill radius 100%

Rows:

- every row must be a monolithic pill surface or live inside a large monolithic card
- high radius corners
- no square separators
- no normal Android settings list

Bottom dock:

- Settings active
- inactive nodes: Home, Servers, Subscription

## 12. Reusable Dock Token

Use this token on every main screen.

Create a floating bottom navigation dock with an organic metaball shape.

Geometry:

- 4 circular node zones
- concave valleys between nodes
- adaptive contour around each icon
- active node larger than others
- active node integrated into the body, not detached
- 100% organic rounded geometry

Material:

- matte black
- soft hardware-like surface
- subtle top highlight
- inner dark shadow
- ambient purple edge glow
- soft drop shadow

Active node:

- purple circular glowing surface
- localized glow
- icon in white
- label below or inside node
- tiny purple dot below active label

Inactive nodes:

- dark recessed circular zones
- white outline icons
- muted labels
- no strong glow

Dock feeling:

- tactile
- liquid
- premium
- hardware-inspired
- organic
- futuristic

## 13. Central Non-Breaking Rule

Every screen must feel derived from the same object.

The dock, cards, rows, buttons, toggles, and selectors must all share:

- 100% rounded geometry
- monolithic pill surfaces
- black hardware material
- cold purple active state
- localized glow
- dense premium spacing

The UI must never become:

- rectangular
- flat
- generic
- SaaS-like
- Android default
- overly neon
