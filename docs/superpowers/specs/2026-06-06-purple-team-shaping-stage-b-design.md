# Purple-Team Shaping — Stage B Design (closed-loop evasion autopilot)

> Brainstormed 2026-06-06. Extends the program umbrella
> `2026-06-06-purple-team-shaping-design.md` (threat model #1 / honest ceiling / "reliability not
> stealth" success criterion apply unchanged). Stage A (adaptive TLS shaping) shipped main+prod
> (merge fe4cbaf). Stage B adds a runtime health sentinel + in-session profile morphing.

## 1. What Stage B adds (and what it reuses)

Stage A made shaping a learnable axis but only chose a profile **at connect time**. Stage B closes the
loop **during a live session**: detect when the connection is unhealthy and try a different shaping
profile (then a different server) to restore it, learning per network.

Crucially, **half of this already exists.** `MainViewModel.handleAdaptiveRuntimeFailure()` +
`AdaptiveDecisionAgent.planAfterFailure()` already run a closed loop on a HARD failure (tunnel down):
cascade `RECONNECT_SAME` (×`SAME_SERVER_RETRY_LIMIT`=2) → `SWITCH_SERVER` → `GIVE_UP`, gated by the AI
toggle (`AgentDisabledFailurePolicy`), recording per `(server × network × profileId)` in the score
store + `BenchmarkCollector`. Stage B adds exactly two new pieces and reuses the rest.

## 2. New piece ① — `TunnelHealthSentinel` (detect "connected but unhealthy")

The existing loop only fires on a HARD failure; it misses **"connected but no internet"** — the exact
symptom behind the 2026-06-05 incident. The sentinel catches the soft case.

- **Probe through xray's local SOCKS.** The app opens `127.0.0.1:10808` (xray's `socks-in`). Loopback
  is not routed through the tun, so the app's own tun-exclusion does not interfere; the SOCKS5 `CONNECT`
  is tunnelled to the server and out the exit, so it measures the REAL tunnelled path. Works on release
  (no log parsing, no debuggable build needed).
- **Probe target:** a neutral, censorship-agnostic `generate_204`-style endpoint (small, expected to
  succeed through any healthy exit). Resolve the host remotely (socks5h semantics) so DNS also rides the
  tunnel.
- **Cadence:** periodic, **battery/screen-aware** (reuse the existing latency-refresh scheduling
  pattern: gate on screen-on + not-low-battery, no overlapping passes), and ONLY while
  `VpnManager.state == CONNECTED`.
- **Debounce (balanced posture):** require **2-3 consecutive failed probes (~30-45 s)** before
  declaring "degraded"; a single success resets the counter. This avoids reacting to a micro-glitch.
- **Pure core:** the debounce/decision logic is a pure function (probe outcome stream → degraded?),
  unit-testable without Android; only the probe I/O and the scheduler touch the platform.

On "degraded", the sentinel invokes the SAME path as `handleAdaptiveRuntimeFailure` (record a failure
for the active `(server, network, profileId)`, then `planAfterFailure` + execute). One pipeline handles
both hard failure and soft degradation.

## 3. New piece ② — `MORPH_PROFILE` action (try a new shaping profile before switching server)

Extend `AdaptiveDecisionAgent` with a new `DecisionActionType.MORPH_PROFILE` inserted into the cascade
**before** `SWITCH_SERVER`:

```
RECONNECT_SAME (×2, same profile)  →  MORPH_PROFILE (×SHAPING_MORPH_LIMIT≈2-3, same server, next
untried shaping profile)  →  SWITCH_SERVER  →  GIVE_UP
```

- **Rationale:** the user's value is the premium server; stay on it and try shaping variants before
  abandoning it. Bounded to **~3-4 tunnel restarts (blips) per incident**, with a grace period after
  each morph so the sentinel re-measures before deciding again.
- **Which profile:** the next **untried-this-incident** profile from
  `CamouflageProfileRepository.fallbackOrder` (AUTO → browsers → frag presets). Track the set of
  profiles tried this incident so morphs explore rather than repeat. `selectBestCamouflageProfile`
  already encodes preference order; the morph picks the best *untried* one.
- **Execution:** reuse `ACTION_RESTART` (already used for mode-change and server-switch) — same
  host/port, a new `camouflageProfileId`. No new transport machinery.
- **Learning is free:** `recordFailure`/`recordSuccess` already capture `profileId`, so a morph that
  restores health credits that profile for this network → the agent's next connect-time pick improves.

## 4. Gating, UX, honesty

- **AI toggle gates everything.** Extend `AgentDisabledFailurePolicy` so that with AI OFF, `MORPH_PROFILE`
  (like `SWITCH_SERVER`) is suppressed — the manual user's chosen profile/server is honoured; at most a
  same-server retry. AI ON → full autopilot.
- **Honest framing:** we detect a **measured degradation** (probe fails while state=CONNECTED), NOT
  "DPI". UI wording stays light and factual ("optimisation de la connexion…", reuse the existing
  `adaptive_*` toast style), never "anti-detection/stealth".
- **Assumed limits:** the probe costs a few KB periodically; each morph costs a ~1-2 s tunnel blip; the
  loop optimises **observed connectivity**, not stealth; the client only tunes client-side knobs (the
  server stays fixed).

## 5. Error handling / edge cases

- Sentinel never runs when disconnected, mid-(re)connect, or when a morph/restart is already in flight
  (no overlapping actions — reuse the existing `handlingAdaptiveFailure` guard).
- A morph that itself fails to connect falls through the existing hard-failure cascade (so a bad profile
  can't wedge the session).
- `manualStopRequested` / user disconnect cancels the sentinel immediately.
- Incident state (tried-profiles set, attempt counts) resets on a confirmed-healthy probe and on a fresh
  user-initiated connect.

## 6. Components / files (informational — the plan locks exact edits)

- `adaptive/TunnelHealthSentinel.kt` — **new**: pure debounce/decision core + a thin scheduler entry.
- `adaptive/AdaptiveDecisionAgent.kt` — **modify**: add `MORPH_PROFILE` to `DecisionActionType`,
  `SHAPING_MORPH_LIMIT`, and the cascade branch + next-untried-profile selection.
- `adaptive/AgentDisabledFailurePolicy.kt` — **modify**: suppress `MORPH_PROFILE` when AI off.
- `MainViewModel.kt` — **modify**: own the sentinel lifecycle (start on connect, stop on
  disconnect/manual-stop, battery/screen gating), route "degraded" into the failure pipeline, and
  execute `MORPH_PROFILE` via `ACTION_RESTART` with the next profile id.
- strings ×4 — a light "optimising connection" toast if a new one is needed.

## 7. Testing

- `TunnelHealthSentinelTest` — pure debounce: N consecutive failures → degraded; one success resets;
  never degraded when not CONNECTED.
- `AdaptiveDecisionAgentTest` (extend) — cascade now RECONNECT_SAME→MORPH_PROFILE×K→SWITCH_SERVER→GIVE_UP;
  morph picks the next untried profile; bounds respected; **non-regression** of the existing
  hard-failure cascade and server selection.
- `AgentDisabledFailurePolicyTest` (extend) — AI off suppresses MORPH_PROFILE to same-server retry.
- Device validation (manual, like Stage A's gate): force a degraded premium session, confirm the
  autopilot morphs (toast + a brief blip) and recovers, and that a healthy session is never morphed.

## 8. Out of scope

Stage C (cover traffic), Observer #2 (residential exit / leak prevention), any server-side control,
ALPN/pacing knobs, and parsing xray runtime logs (the SOCKS probe replaces that need).
