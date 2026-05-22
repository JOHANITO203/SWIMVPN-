# Agent IA Current Role and PR Plan - 2026-05-22

## Executive Summary

The current SWIMVPN "Agent IA" is not an LLM and does not autonomously decide for the user. It is a deterministic adaptive recommendation agent that scores server candidates and provides guidance based on runtime evidence.

This matches the product direction if the UI describes it as an agent that gives live indices and recommendations, while the user remains in control.

## Current Implementation Files

- `android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt`
- `android/app/src/main/java/com/swimvpn/app/adaptive/ServerScoreStore.kt`
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
- `android/app/src/main/java/com/swimvpn/app/ui/screens/ServersScreen.kt`
- `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`

## Current Inputs

`AdaptiveDecisionAgent` receives `ServerDecisionCandidate` items with:

- `serverId`
- `pingMs`
- `isPinned`
- `hasRuntimeConfig`
- `premiumBlocked`
- `latencyMeasuredAtMs`
- `latencyProbeFailed`
- optional `load`
- optional `availabilityStatus`

It also receives persisted local quality scores:

- success count,
- failure count,
- consecutive failures,
- last success/failure time,
- avoid-until timestamp.

## Current Decisions

The agent currently supports:

- recommending the best available server,
- retrying the same server briefly after failure,
- switching to a safer fallback after repeated failure,
- giving up when no safe fallback exists.

It filters out:

- the current server,
- servers without runtime config,
- premium-blocked servers,
- temporarily avoided servers,
- fresh probe failures.

## Scoring Model

The score is lower-is-better and combines:

- normalized ping,
- latency quality penalty,
- load penalty,
- congested availability penalty,
- local failure/success history,
- small pinned-server reward.

Fresh latency is considered valid for two minutes. Missing ping, stale ping, and fresh probe failures are penalized differently.

## Persistence

`ServerScoreStore` stores local adaptive quality scores in Android `SharedPreferences` under:

- prefs name: `swimvpn_adaptive_scores`
- key: `server_scores`

This is local client-side adaptation, not backend authority.

## UI Exposure

### Servers Screen

`ServersScreen` marks the Agent as active only when:

- `recommendedServerId != null`
- `isRecommendedServerValidated == true`

### Home Screen

`HomeScreen` shows the AI badge when:

- the active server matches `recommendedServerId`
- the recommendation is validated.

## Product Language Boundary

Safe wording:

- Agent active
- live indices
- helps identify stronger nodes
- guidance based on ping/runtime signals
- the user stays in control

Avoid wording:

- autonomous AI chooses for you,
- guaranteed best server,
- neural/LLM intelligence,
- always fastest,
- provider-level optimization.

## Gaps and Risks

- The agent depends on the freshness and reliability of latency probes.
- It has no backend global health feed yet.
- It does not know supplier-side capacity beyond exposed metadata.
- It does not currently explain recommendation reasons to the user.
- It is deterministic scoring, so marketing must not oversell it as generative AI.

## PR Plan for Agent Optimization

### PR 1 - Explainability

- Add a small reason model: `LOW_LATENCY`, `STABLE_HISTORY`, `FRESH_PROBE`, `AVOIDING_FAILURE`, `PINNED_PREFERENCE`.
- Surface one concise reason in server rows or detail sheets.
- Keep supplier/private metadata hidden.

### PR 2 - Runtime Probe Freshness

- Add screen-scoped periodic ping refresh for visible server candidates.
- Keep refresh bounded and battery-aware.
- Do not probe indefinitely in background.

### PR 3 - Backend Health Signal

- If backend exposes safe aggregate health, add it as a non-sensitive input.
- Never expose supplier internals, hostnames, UUIDs, or raw premium configs.

### PR 4 - Recommendation Safety

- Add tests for expired users, imported configs, premium-blocked nodes, missing runtime configs, fresh probe failures, and avoided servers.
- Ensure expired users never receive premium backend nodes/configs through recommendation paths.

### PR 5 - UX Copy

- Align UI language across Home, Servers, Subscription, and landing:
  - Agent as guidance,
  - user retains choice,
  - no overpromising.

## Notes for Future Agents

- Do not convert this into a cloud LLM unless explicitly requested and privacy-reviewed.
- Do not send raw VPN configs to an AI service.
- Do not use supplier-private metadata for public labels.
- Keep PostgreSQL/backend entitlement as source of truth; the Android agent is only a local recommendation layer.
