# Adaptive bandit — implementation plan (Phase 2 wiring)

> Executes spec `docs/superpowers/specs/2026-06-08-adaptive-bandit-design.md` on branch
> `feat/adaptive-bandit`. Phase 1 (pure `BanditPolicy`, UCB1 + decay, gentle c=0.2) is DONE + green.
> Honors the 6 hard invariants (esp. I1 no action on a healthy session, I6 hard guards as pre-filters,
> I5 IA-OFF intact). Every behavior-changing sub-phase is device-gated before merge.

## Surface audit (what Phase 2 touches, grounded)
- **`ServerQualityScore`** (AdaptiveDecisionAgent.kt:24-53) — data class; today learns via UNBOUNDED
  cumulative counters (`successCount`/`failureCount`, per-network/profile maps) with a binary 30-min reset.
- **`recordSuccess`** (211-268) / **`recordFailure`** (140-177) — increment those counters (+ avoidance,
  per-network recovery). Called from MainViewModel: `creditSustainedHealth` (honest success),
  `handleAdaptiveRuntimeFailure` (failure), `onAdaptiveRuntimeRunning` (reachability, profileId=null).
- **`recommendServer`** (384-410) → filters then `minWithOrNull` on **`recommendationFor`** (414-458):
  `score = ping + qualityPenalty + load + availability + historyPenalty + networkPenalty + hourlyNudge
  − pinned − manualReward − networkSuccessReward`. 100% greedy. `historyPenalty` (579-589) is the
  unbounded/saturating term (`consecutive×250 + failures×25 − success×10`).
- **Hard pre-filters** (399-403): not-current, has-config & !premiumBlocked, !avoided (unless outage),
  !FRESH_PROBE_FAILED. **These stay (I6).**
- **`ServerScoreCodec`** (ServerScoreStore.kt) — v6 (16 fields), index-tolerant decode → easy v7 add.

**Integration strategy (low-risk):** keep the hard pre-filters AND the real-time signals (ping, load,
availability) untouched. Replace ONLY the **unbounded learned-history signals** (`historyPenalty`,
`networkFailurePenalty`, `networkSuccessReward`) with a single **bandit term** computed from decayed
masses via `BanditPolicy`. Ping/load/availability fold into the bandit's **prior**; the bandit term
becomes the primary rank among survivors; ping→id stay as deterministic tie-breaks. The additive scorer
is NOT ripped out wholesale — only its three saturating history terms are superseded.

---

## Phase 2a — data + reward plumbing (NO ranking change → ~zero behavior risk)
Add decayed masses; record them; do NOT yet use them to rank. App behavior byte-identical.

- [ ] **2a.1 RED** — extend ServerScoreCodecTest: round-trip of new fields `successMass`,`failureMass`,
  `massUpdatedAtMs`; v6→v7 migration seeds masses from legacy counts (`successMass=successCount.toDouble()`
  etc.) so no learning is lost; idempotent.
- [ ] **2a.2 GREEN** — `ServerQualityScore` += `successMass: Double = 0.0`, `failureMass: Double = 0.0`,
  `massUpdatedAtMs: Long = 0L`. `ServerScoreCodec` VERSION `v6`→`v7`, append 3 fields, decode index-tolerant
  (v2..v6 → seed masses from counts in a pure `migrateRowsToCurrentVersion`). Pure → unit-tested.
- [ ] **2a.3** — in `recordSuccess`/`recordFailure`: before mutating, `decay` both masses to `nowMs`
  (`BanditPolicy.decay`, half-life const), set `massUpdatedAtMs=nowMs`, then `successMass+=1.0` (success)
  / `failureMass+=1.0` (failure). Keep ALL existing counter logic untouched (parallel, not replaced).
  Extend AdaptiveDecisionAgentTest: a success bumps `successMass` and decays a stale `failureMass`.
- [ ] **2a.4 VERIFY** — full suite green; **device smoke**: connect/disconnect, confirm scores persist,
  **no behavior change** (ranking still the old additive one). Commit.

## Phase 2b — bandit ranking (THE behavior change → device-gated)
- [ ] **2b.1 RED** — extend AdaptiveDecisionAgentTest: with masses present, `recommendServer` prefers the
  higher decayed-success-rate survivor; a never-tried survivor with a good ping gets a fair chance (gentle
  c); a pre-filtered (avoided/probe-failed/blocked) server is STILL never returned (I6 lock); IA-OFF path
  (manual server) unaffected.
- [ ] **2b.2 GREEN** — in `recommendationFor`: replace `historyPenalty + networkPenalty − networkSuccessReward`
  with `− banditTerm`, where `banditTerm = SCALE × BanditPolicy.ucbScore(decayedSuccess, decayedFailure,
  totalDecayedMassOverCandidates, priorMean, c)`. `priorMean` from ping/load/availability normalized to
  [0,1] (fast+healthy → higher prior). Keep ping/load/availability as the prior + as deterministic
  tie-breaks; keep `pinned`/`manualReward`/`hourlyNudge` as small nudges. Delete the 3 superseded helpers
  (or keep private + unused → remove in cleanup). Adjust the now-obsolete additive-history tests to assert
  the bandit behavior (NOT weaken — re-express the intent).
- [ ] **2b.3 VERIFY** — full suite green. **Device battery (gate):** connect / disconnect / user-switch /
  wifi-cut; sustained-credit fires; **0 spurious switch on a healthy session over ~5 min (I1)**; the
  recommended server is sane; 0 crash. Commit only if all green.

## Phase 2c — fragmentation arm (server × fragmentation)
- [ ] **2c.1 RED** — test: `selectShaping(server, network, scores)` (new pure fn) returns the
  fragmentation level (`none`/`light`/`aggressive`) with the best decayed rate for (server×network), gentle
  exploration, AUTO/none floor; fp is NOT chosen here (deterministic per signature, I4).
- [ ] **2c.2 GREEN** — add the (server × fragmentation) arm to the masses (key `"serverId|frag_level"`),
  credited by the same honest reward path. `resolveCamouflageProfile` (when agent ON) picks fragmentation
  via the bandit, fp stays the link's validated one (AUTO). MORPH cascade tries fragmentation levels, not
  browser fps.
- [ ] **2c.3 VERIFY** — full suite + device: fragmentation adapts on a flaky network, fp never overridden
  on REALITY, no regression. Commit.

---

## Risk + rollback
- **IA-OFF is the hard fallback (I5):** OFF → manual server + no bandit, behavior unchanged. Verified each phase.
- **2a is reversible/no-op** (masses recorded, unused). **2b is the real change** → isolated + device-gated;
  if device shows any regression, 2b is one revert.
- **Hard guards stay pre-filters (I6):** a unit test locks "never returns a pre-filtered server", run every phase.
- **No new network I/O (I3):** the bandit is pure; reward stays the existing sentinel.
- **Migration safety:** v7 seeds masses from legacy counts (no learning lost); old rows decode; idempotent
  (re-uses the v6→v7 pure-migration pattern already shipped).

## Verification commands (RAM-constrained)
`./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.*" --tests "com.swimvpn.app.config.*"
--rerun-tasks --max-workers=1 --no-parallel --no-daemon` per phase; full `:app:testDebugUnitTest` before
each commit; device battery before any merge to main.

## Open calls (confirm at 2b)
- `SCALE` (bandit term → additive-score units): pick so the bandit dominates history but not the hard
  ping/probe filters (e.g. map UCB [0,1+] to a 0..~200 band, below MISSING_PING=300).
- `priorMean` mapping from ping/load/availability (proposed: fresh+fast+available ≈ 0.6, missing/slow ≈ 0.3).
