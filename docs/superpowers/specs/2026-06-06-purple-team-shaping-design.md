# Purple-Team Adaptive Shaping — Design (Stage A)

> Brainstormed 2026-06-06. This document specifies **Stage A** in full and sketches Stages B/C
> (their own specs later). The goal of the whole program is an honest client-side anti-detection
> layer that **completes** the supplier's REALITY fingerprint without overriding it.

## 1. Threat model (the honesty gate)

Two different observers can "detect a VPN", and they see opposite things:

- **Observer #1 — the censor / network DPI** (between the device and the server, e.g. the Russian
  TSPU). It watches the connection **to the server** and tries to classify it as proxy/VPN to block
  it. This is what uTLS/REALITY fingerprinting addresses, and what a **client-side shaping layer can
  genuinely strengthen** (TLS fragmentation, packet-size/timing signature, pre-handshake noise).
- **Observer #2 — the destination app/site** (ChatGPT, Netflix, banks). It sees traffic exiting the
  server. It never sees xray's fingerprint — the app's own TLS is tunnelled opaquely and replayed at
  the exit, so the site sees a normal browser TLS. Its dominant "VPN" signal is **exit-IP reputation**
  (datacenter ASN). **No client fingerprint fixes this** — the lever is a residential exit IP (the
  existing BYO "Mon proxy" mode) plus DNS/WebRTC/timezone leak prevention.

**This spec targets Observer #1 only.** Observer #2 (residential exit + leak prevention) is a separate
program with its own spec.

## 2. Honest ceiling (client-only)

The strongest anti-DPI levers are **server-side and fixed by the supplier**, because the user buys
REALITY links and does not control those nodes:

- active-probing resistance = REALITY server behaviour (already handled, not client-tunable);
- cover-domain / SNI = server-chosen (we keep the link's `serverName`);
- TLS-in-TLS defeat = the link's `xtls-rprx-vision` flow (already present).

A **client** layer can only tune the client-tunable knobs **around** the supplier's fingerprint. This
program completes that fingerprint; it never overrides the supplier's fp/SNI/flow. (Full control would
require running our own nodes — out of scope.)

## 3. Success criterion (no stealth promise)

Stealth is not measurable from the client, so we do **not** claim or display "undetectable". The
measurable win condition is **connection health under DPI pressure**: handshake success rate,
RST / dial-cancel frequency, throughput stability — the exact signals observable in the xray/tun2socks
runtime logs. The adaptive agent learns which profile keeps the connection **healthy/connectable** per
network. Same honest limit as Phase 3: we learn **reliability**, not stealth.

## 4. Staged architecture (A → B → C)

Three layers on the existing adaptive agent; each is independently shippable and testable.

- **Stage A — Adaptive shaping profiles (THIS SPEC).** Add the client-tunable anti-DPI knobs
  (TLS fragmentation, optional pre-handshake noise) as an axis the agent learns, bundled with the
  existing uTLS-fp axis. Default = AUTO = no shaping = byte-identical to today.
- **Stage B — Closed-loop evasion autopilot (future spec).** A runtime *sentinel* reads the live
  health signature; a *controller* detects DPI pressure and re-selects the profile in-session (via a
  fast reconfigure+restart — xray does not hot-reload), learning per network which morph escapes the
  throttle.
- **Stage C — Budget-governed cover-shaping (future spec).** A sub-module of B's controller. Default
  ≈ keepalive-cadence mimicry (near-zero cost); escalates under detected pressure within a **moderate
  adaptive data/battery budget**; **target profile = the supplier's REALITY cover-domain** (so cover
  amplifies the supplier's cover instead of contradicting it); reversible and self-disabling when it
  does not measurably help. Hard problem to solve in C: the app is excluded from its own tun
  (`addDisallowedApplication`), so injecting cover that traverses the tunnel needs a dedicated
  non-excluded sender or tun-layer injection.

## 5. Stage A — components

Greft onto existing types; reuse the v6 score-store learning (keyed `NETWORK|profileId`) — **no codec
change needed**, because new profile IDs learn through the existing maps.

1. **`CamouflageProfile` (extended).** From `{id, displayNameRes, fingerprint}` to
   `{id, displayNameRes, fingerprint, fragment?, noises?}` — all shaping fields nullable. `fragment` =
   `{packets, length, interval}` (xray `sockopt.fragment`); `noises` = list of `{type, packet, delay}`
   (xray `sockopt.noises`). Presets: `AUTO` (all null ⇒ respect link, no shaping — today's behaviour),
   `FRAG_LIGHT`, `FRAG_AGGRESSIVE` (and `NOISE_*` only after device validation). `DEFAULT = AUTO`.
2. **`TunnelRuntimeAdapter` — `applyShaping(document, profile)`.** A post-process mirroring
   `applyFingerprintOverride`/`resolveOutboundServerAddresses`: when the profile carries `fragment` /
   `noises`, inject them into each outbound's `streamSettings.sockopt`. Untouched when null ⇒ AUTO
   produces a byte-identical document (regression guard). Runs alongside the existing fp override
   (which already respects the link via AUTO).
3. **`AdaptiveDecisionAgent` (extended).** The shaping profile joins the per-network success/failure
   learning already used for the camouflage fp (`selectBestCamouflageProfile` generalised to the new
   presets). No score-store schema change.
4. **UI.** The manual profile picker (shown when the AI toggle is OFF) lists the shaping profiles with
   honest wording ("Compatibilité réseau" / profile names). Never "furtif/invisible". strings ×4.
5. **Tests** (existing ts-node-style JUnit patterns under `app/src/test/.../config` & `/adaptive`):
   repository presets present; adapter injects `fragment`/`noises` correctly **and AUTO yields output
   identical to today** (the regression guard); agent selects best shaping per network with
   non-regression of server selection; round-trip through the v6 codec with the new profile IDs.

## 6. Data flow

connect → `resolveCamouflageProfile(serverId, agentEnabled)` (agent if AI on, else manual / AUTO) →
`EXTRA_CAMOUFLAGE_FP`-style carry (extended to carry the whole profile id) → `SwimVpnService` →
`TunnelRuntimeAdapter` applies fp (AUTO-respecting) + `applyShaping` → xray. Session success/failure →
recorded per `(shapingProfileId × networkType)` in the existing store.

## 7. Device-validation gate (operationalises the hard constraint)

TLS `fragment`/`noises` must be proven to **compose with REALITY + xtls-rprx-vision without breaking
the handshake** — this stack is fragile (a forced chrome fp alone broke premium on 2026-06-05). So:

- A ships with `DEFAULT = AUTO` ⇒ no shaping ⇒ zero regression, guaranteed by the byte-identical test.
- `FRAG_*` / `NOISE_*` presets are enabled for manual/agent selection **only after** on-device
  validation (debug build): connect a REALITY+vision supplier node with the profile, confirm the xray
  log shows a successful tunnelled request (not a dial-cancel storm) and real traffic flows.
- Any preset that fails validation is dropped, not shipped.

## 8. Out of scope (Stage A)

ALPN override (handshake-fragile, like fp — respect the link); connection pacing (xray exposes no fine
client control); the closed-loop autopilot (Stage B); cover traffic (Stage C); Observer #2 / residential
exit / leak prevention (separate program).

## 9. Assumed limits (must be reflected in UI + docs)

The layer learns **connection reliability** per profile/network, **not** stealth (unmeasurable
client-side). The win is **bounded**: fragmentation mainly defeats SNI-based DPI and shifts the
packet-size signature; it is not a cloak. The UI informs about compatibility/profile and promises no
undetectability. This honesty is a feature, not a hedge: a false "invisible" badge is dangerous for an
anti-censorship tool.
