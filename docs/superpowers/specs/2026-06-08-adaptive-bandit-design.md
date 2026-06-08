# Adaptive bandit — design (non-disruptive server + shaping selection)

**Goal:** replace the current unbounded-greedy heuristic with a principled, uncertainty-aware online
learner (Thompson sampling on a time-decayed Beta success-rate per arm) that improves *which server and
shaping the agent picks AT DECISION POINTS* — without ever disturbing a healthy live session, and with
no regression.

**Status:** design. Builds on the honest reward signal already shipped (`SustainedHealthGate`, merge
`fb8f3b8`). Pre-req n°1 done; this is the bandit itself.

---

## 1. Hard invariants (the contract the code MUST respect)

These are non-negotiable. The device battery verifies them before any merge.

- **I1 — Never act on a healthy, traffic-carrying session.** The agent only influences a choice that is
  ALREADY happening: (a) user connect, (b) user server-switch, (c) genuine failure-recovery (sentinel
  DEGRADED / no traffic). It never proactively switches / morphs / probes a healthy session. (This is
  already today's behavior — the cascade fires only on failure. The bandit must preserve it.)
- **I2 — Exploration is passive + safe, never a hijack.** Learn continuously; ACT only at decision
  points. "Exploration" = giving an under-sampled arm a fair chance WHEN a selection is already needed,
  and only among candidates that pass the hard pre-filters. Exploration DATA comes from the existing
  background latency pings + the user's organic connects/switches — never from routing live traffic
  through a test server.
- **I3 — No new in-session network probe.** (Egress-proof lesson: an in-session probe destabilizes the
  tunnel.) Reward reuses the EXISTING sentinel (sustained health) + handshake reachability + failures.
  The bandit is pure local computation (instant); pings stay background.
- **I4 — AUTO + the link's validated fingerprint is the safe floor.** The uTLS fingerprint is
  DETERMINISTIC from the server signature (REALITY/XTLS → never override), NOT a learned axis. The only
  learnable shaping axis is the TLS fragmentation level.
- **I5 — IA OFF = current behavior intact.** The bandit runs only when the agent toggle is ON; OFF keeps
  today's manual/fixed path untouched (the fallback).
- **I6 — Hard guards stay PRE-FILTERS, not learning.** Avoidance (2 consecutive failures → 10 min),
  probe-failed, global-outage, premium-blocked, quota: these filter the candidate set BEFORE sampling.
  The learner only ranks the survivors.

---

## 2. Current state (audit) + precision gaps

Today it is a **greedy two-stage heuristic**, 0% exploration:
- Server: `score = ping + Σ penalties − Σ rewards`; pick min; deterministic tie-breaks.
- Profile: `margin = successes − failures` per `NETWORK|profile`; pick max; ties → fallback order.

Precision gaps (ranked):
1. **Unbounded counters + binary 30-min decay → score saturates.** `successReward = successCount×10`,
   penalties uncapped; only failures reset (binary at 30 min), successes never forgotten → a long-lived
   server becomes "sticky" even as it degrades. **The #1 precision killer.**
2. **Zero exploration** → no discovery; cold-start decided by ping luck; an evicted server stays evicted.
3. **No uncertainty** → 1 success treated like 100; thin history = false certainty.
4. **Profile learned per-network, not per (server×network)** → misaligned (and fp should be deterministic
   anyway, per I4).
5. **Additive non-independent signals** → one large term can swamp the rest.

---

## 3. Design

### Arms
- **Server arm** = `serverId` (context: concrete network type).
- **Shaping arm** = `(serverId × fragmentationLevel)` where level ∈ `{none, light, aggressive}`.
  **NOT the fingerprint** (deterministic from signature, I4).

### Per-arm estimate: time-decayed Beta
Each arm keeps a decayed success/failure mass:
- `α = decayedSuccesses + 1`, `β = decayedFailures + 1`.
- Decay = exponential with half-life `H` (proposed default **48 h**, tunable): on each update, scale the
  stored masses by `0.5^(Δt / H)` before adding the new outcome. Recent outcomes dominate; old ones fade
  continuously (no binary cliff).

### Selection: Thompson sampling
For each surviving (pre-filtered) candidate, sample `θ_a ~ Beta(α_a, β_a)`; pick `argmax θ_a`.
- Under-sampled arm → wide Beta → occasionally sampled high → **natural, bounded exploration**.
- Well-sampled recent arm → narrow Beta → **converges to exploit**.
- Pure local math, instant. Randomness via an **injectable `Random`** → deterministic unit tests.

### Cold-start prior
The server arm's prior is nudged by the **existing background ping** (fast+reachable → mild favorable
prior; probe-failed → pre-filtered out by I6). So a brand-new server is neither blindly trusted nor
permanently ignored.

### Reward (honest, no new probe — I3)
- **Success** = `SustainedHealthGate` credit (sustained sentinel health) → updates the (server) and
  (server×fragmentation) arms.
- **Failure** = `recordFailure` on DEGRADED / no-traffic → updates the same arms.
- **Handshake** = server REACHABILITY only (server arm, not shaping) — already wired (`profileId=null`).

### Hard guards = pre-filter (I6)
Avoidance / probe-failed / global-outage / premium-blocked / quota remove candidates BEFORE sampling.
Ping/quality penalties that are CORRECTNESS (missing ping, fresh-probe-failed) stay as hard filters or
priors, not as unbounded learned penalties.

---

## 4. Decision points (where the bandit acts — and where it does NOT)
- **Connect (user-initiated)** → pick the server arm by Thompson among survivors; shaping by the rule.
- **User server-switch** → same.
- **Failure-recovery cascade** (`planAfterFailure`) → when a switch/morph is genuinely needed, pick the
  fallback arm by Thompson among survivors.
- **NEVER on a healthy session** (I1).

Default exploration posture (safest): **exploit-for-the-user at every actual selection**; exploration
emerges from (a) Thompson's uncertainty giving under-sampled survivors a chance at those moments, and
(b) background-ping data — NOT from forcing the user onto a test server. (An optional small ε at connect,
bounded to safe candidates, can be added later if data is too sparse — but off by default.)

---

## 5. Out of scope (explicit — no theater)
- **Seamless / zero-interruption server handover.** The tun restart causes a brief blip (Android
  VpnService limit). The bandit *reduces unnecessary switches* but does NOT make a switch zero-blip —
  that is a separate, harder chantier.
- **Cross-user ML / population priors** (backend telemetry + anonymization) — later, once there are users.
- **Fingerprint as a learned axis** — fp is deterministic from the server signature (I4).
- **Active in-session exploration that reroutes a healthy user** — forbidden (I1/I2).

---

## 6. Verification plan
- **Pure-core TDD:** a `BanditPolicy` (decayed-Beta update + Thompson select) as pure functions, with an
  injected seeded `Random` → deterministic tests (convergence, exploration of under-sampled arms, decay,
  cold-start prior, pre-filter respected).
- **Invariant unit tests:** never selects a pre-filtered arm; IA-OFF path unchanged; the selection is a
  pure function of (candidates, scores, ping, rng) with no side effects on connection state.
- **Device battery (the proven playbook):** connect / disconnect / user-switch / wifi-cut, sustained
  credit fires, **0 crash**, and — critically — **no spurious switch on a healthy session over several
  minutes** (I1 verified on device).
- **Branch `feat/adaptive-bandit` + device-gate before any merge.** IA-OFF + the v1.0.9 stability + the
  status-honesty + the honest-reward must all stay green.

---

## 7. Open parameters (to confirm before/while shaping)
- Half-life `H`: 48 h proposed (24 h = faster forgetting / more reactive; 72 h = steadier).
- Thompson vs epsilon-greedy vs UCB: **Thompson recommended** (cold-start + uncertainty in one mechanism,
  seeded-RNG testable).
- Fragmentation arm granularity: `{none, light, aggressive}` (matches existing presets).
- Active connect-time ε: **off by default** (exploit-for-user); revisit only if data is too sparse.
