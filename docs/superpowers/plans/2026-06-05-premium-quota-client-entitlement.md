# Premium Quota — Droit & Coupure Client — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire du quota/expiration **vendus** la seule vérité affichée + appliquée côté client (barre live + coupure au quota), en corrigeant des flux existants et en réactivant un câblage mort. Pas de Spec 2 (resupply) ici.

**Architecture:** Backend = source de vérité (Plan.`quota_gb` numérique ; `subscriptionExpiresAt` renvoie l'expiration vendue déjà calculée). Android = consomme le vendu (carte sold-only + barre live `consumedPercentage`) et coupe au quota via le moniteur de vie de `SwimVpnService` en réactivant les `EXTRA_DATA_*`.

**Tech Stack:** NestJS + Prisma (Postgres) ; Android Kotlin/Compose ; tests ts-node (`test:policy`) + JUnit (`:app:testDebugUnitTest`).

**Spec source:** `docs/superpowers/specs/2026-06-05-premium-quota-client-entitlement-design.md` (commit c8172c0).

**Branche:** `feat/premium-quota-entitlement`. Séquencement : Backend (Tâches 1-3) → Android (Tâches 4-10) → vérif/merge (Tâche 11). Le merge backend déclenche le redeploy Dokploy.

---

### Task 0: Préparation

- [ ] **Step 1: Créer la branche**

```bash
cd /c/Users/Lenovo/StudioProjects/SWIMVPN-
git checkout main && git checkout -b feat/premium-quota-entitlement
```

---

### Task 1: Backend — colonne `quota_gb` sur Plan + backfill

**Files:**
- Modify: `backend/prisma/schema.prisma` (modèle `Plan`, ~lignes 113-125)
- Create: `backend/prisma/migrations/<timestamp>_add_plan_quota_gb/migration.sql`

- [ ] **Step 1: Ajouter le champ au modèle Plan**

Dans `schema.prisma`, modèle `Plan`, ajouter sous `quota_label` :

```prisma
  quota_label    String
  quota_gb       Int?          // Quota VENDU en GB (null/0 = illimité). Source numérique du dataLimitGB exposé au client.
```

- [ ] **Step 2: Créer la migration + backfill**

Run:
```bash
cd backend
npx prisma migrate dev --name add_plan_quota_gb --create-only
```

Éditer le `migration.sql` généré pour ajouter le backfill APRÈS l'`ALTER TABLE` :

```sql
-- (généré) ajoute la colonne
ALTER TABLE "Plan" ADD COLUMN "quota_gb" INTEGER;

-- backfill depuis le label "50 GB" -> 50 ; "UNLIMITED"/sans nombre -> NULL (illimité)
UPDATE "Plan"
SET "quota_gb" = CAST(substring("quota_label" FROM '\d+') AS INTEGER)
WHERE "quota_gb" IS NULL AND "quota_label" ~ '\d';
```

- [ ] **Step 3: Appliquer + régénérer le client**

Run:
```bash
cd backend && npx prisma migrate dev --name add_plan_quota_gb && npx prisma generate
```
Expected: migration appliquée, client Prisma régénéré (le type `Plan` a maintenant `quota_gb: number | null`).

- [ ] **Step 4: Vérifier que /store/plans expose quota_gb**

`getActivePlans` ([store.service.ts:141](backend/apps/store-engine-service/src/store.service.ts)) fait un `findMany` sans `select` → `quota_gb` est inclus automatiquement. Aucun code à changer. Vérifier par lecture que `getActivePlans` ne projette pas un sous-ensemble de colonnes.

- [ ] **Step 5: Commit**

```bash
git add backend/prisma/schema.prisma backend/prisma/migrations
git commit -m "feat(backend): add numeric Plan.quota_gb (sold quota, GB) + backfill from quota_label"
```

---

### Task 2: Backend — helpers purs (quota vendu + expiration vendue) + tests

**Files:**
- Create: `backend/apps/customer-order-service/src/entitlement-policy.ts`
- Create: `backend/apps/customer-order-service/src/__tests__/entitlement-policy.spec.ts`

- [ ] **Step 1: Écrire le test (TDD)**

`entitlement-policy.spec.ts` (convention ts-node + `assert`, comme les specs existantes) :

```typescript
import { strict as assert } from 'assert';
import { resolveSoldQuotaGb, soldExpiryIso, PLAN_DURATION_MS } from '../entitlement-policy';

function main() {
  // quota vendu : depuis quota_gb du plan ; trial = 0 ; illimité (null) = 0
  assert.equal(resolveSoldQuotaGb(50, false), 50);
  assert.equal(resolveSoldQuotaGb(null, false), 0);
  assert.equal(resolveSoldQuotaGb(150, true), 0, 'trial has no measured quota');

  // expiration vendue = fulfilled_at + durée du plan
  const fulfilled = new Date('2026-06-01T00:00:00.000Z');
  assert.equal(
    soldExpiryIso(fulfilled, 'WEEK', false),
    new Date(fulfilled.getTime() + PLAN_DURATION_MS.WEEK).toISOString(),
  );
  assert.equal(soldExpiryIso(null, 'MONTH', false), null, 'no fulfilled_at -> null');

  console.log('entitlement-policy.spec.ts passed');
}
main();
```

- [ ] **Step 2: Lancer le test (échoue)**

Run: `cd backend && npx ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/entitlement-policy.spec.ts`
Expected: FAIL (`Cannot find module '../entitlement-policy'`).

- [ ] **Step 3: Implémenter les helpers**

`entitlement-policy.ts` :

```typescript
export const TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;
export const PLAN_DURATION_MS: Record<string, number> = {
  WEEK: 7 * 24 * 60 * 60 * 1000,
  MONTH: 30 * 24 * 60 * 60 * 1000,
  QUARTER: 90 * 24 * 60 * 60 * 1000,
};

/** Quota VENDU en GB. Trial ou plan illimité (quota_gb null/0) -> 0 (= illimité/non mesuré côté client). */
export function resolveSoldQuotaGb(quotaGb: number | null | undefined, isTrial: boolean): number {
  if (isTrial) return 0;
  return quotaGb && quotaGb > 0 ? quotaGb : 0;
}

/** Expiration VENDUE = fulfilled_at + durée du forfait (ISO). null si pas encore fulfilled. */
export function soldExpiryIso(fulfilledAt: Date | null, planCode: string, isTrial: boolean): string | null {
  if (!fulfilledAt) return null;
  const durationMs = isTrial ? TRIAL_DURATION_MS : (PLAN_DURATION_MS[planCode] ?? 0);
  return new Date(fulfilledAt.getTime() + durationMs).toISOString();
}
```

- [ ] **Step 4: Lancer le test (passe)**

Run: `cd backend && npx ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/entitlement-policy.spec.ts`
Expected: `entitlement-policy.spec.ts passed`.

- [ ] **Step 5: Brancher le spec dans test:policy**

Modify `backend/package.json` script `test:policy` : ajouter en fin de chaîne `&& ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/entitlement-policy.spec.ts`.

- [ ] **Step 6: Commit**

```bash
git add backend/apps/customer-order-service/src/entitlement-policy.ts backend/apps/customer-order-service/src/__tests__/entitlement-policy.spec.ts backend/package.json
git commit -m "feat(backend): pure entitlement helpers (sold quota_gb + sold expiry) + policy test"
```

---

### Task 3: Backend — getProfile utilise quota_gb + expiration vendue

**Files:**
- Modify: `backend/apps/customer-order-service/src/customer.service.ts` (getProfile, ~lignes 889-899 et 941-944)

- [ ] **Step 1: Importer les helpers**

En haut de `customer.service.ts`, ajouter :
```typescript
import { resolveSoldQuotaGb, soldExpiryIso } from './entitlement-policy';
```

- [ ] **Step 2: Sourcer le quota depuis quota_gb**

Remplacer le calcul `measuredDataLimitGb` (~ligne 896) :
```typescript
// AVANT : parseQuotaLabelToGb(latestOrder.plan.quota_label || '')
const measuredDataLimitGb = resolveSoldQuotaGb(
  latestOrder && !isTrialOrder ? latestOrder.plan.quota_gb : 0,
  isTrialOrder,
);
```
(`parseQuotaLabelToGb` peut rester pour d'éventuels autres appelants ; si plus aucun appelant, le supprimer dans un commit séparé — hors scope ici.)

- [ ] **Step 3: Corriger subscriptionExpiresAt = VENDUE**

Remplacer le bloc `providerExpiresAt`/`subscriptionExpiresAt` (~889-894) :
```typescript
const providerExpiresAt =
  assignment?.expires_at?.toISOString() ||
  inventoryItem?.supplier_expires_at?.toISOString() ||
  null;
// CLIENT-FACING = expiration VENDUE (fulfilled_at + durée plan). Trial garde sa fenêtre.
const subscriptionExpiresAt = isTrialOrder
  ? (orderExpiresAt || providerExpiresAt)
  : soldExpiryIso(latestOrder?.fulfilled_at ?? null, latestOrder?.plan.code ?? '', false);
```
`supplierExpiresAt` dans l'objet réponse (~944) reste **inchangé** (`inventoryItem?.supplier_expires_at?.toISOString() || null`).

- [ ] **Step 4: Vérifier le build + typecheck**

Run: `cd backend && npm run lint && npx nest build customer-order-service`
Expected: 0 erreur (le type `plan.quota_gb` existe après Task 1 ; `latestOrder.plan.code` déjà utilisé ailleurs).

- [ ] **Step 5: Lancer la suite policy**

Run: `cd backend && npm run test:policy`
Expected: tous les specs passent (dont entitlement-policy).

- [ ] **Step 6: Commit**

```bash
git add backend/apps/customer-order-service/src/customer.service.ts
git commit -m "fix(backend): profile sources sold quota from quota_gb + returns SOLD expiry (supplierExpiresAt stays separate)"
```

---

### Task 4: Android — `DisconnectCause.QUOTA_EXHAUSTED` + politique de coupure pure (TDD)

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt` (enum `DisconnectCause`, ~lignes 26-45)
- Create: `android/app/src/main/java/com/swimvpn/app/vpn/QuotaCutoffPolicy.kt`
- Create: `android/app/src/test/java/com/swimvpn/app/vpn/QuotaCutoffPolicyTest.kt`

- [ ] **Step 1: Écrire le test**

`QuotaCutoffPolicyTest.kt` :
```kotlin
package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaCutoffPolicyTest {
    @Test fun `no cutoff when limit unset`() {
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = 0L, baselineBytes = 0L, sessionBytes = 999L))
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = -1L, baselineBytes = 10L, sessionBytes = 10L))
    }
    @Test fun `cutoff when baseline plus session reaches limit`() {
        assertTrue(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 60L, sessionBytes = 40L))
        assertTrue(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 0L, sessionBytes = 150L))
    }
    @Test fun `no cutoff below limit`() {
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 60L, sessionBytes = 39L))
    }
}
```

- [ ] **Step 2: Lancer (échoue)**

Run (depuis `android/`): `./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.vpn.QuotaCutoffPolicyTest"`
Expected: FAIL (unresolved `QuotaCutoffPolicy`).

- [ ] **Step 3: Implémenter**

`QuotaCutoffPolicy.kt` :
```kotlin
package com.swimvpn.app.vpn

/** Pure quota-cutoff rule (sold quota). limitBytes <= 0 = unmetered/unlimited => never cut (fail-open). */
object QuotaCutoffPolicy {
    fun isExhausted(limitBytes: Long, baselineBytes: Long, sessionBytes: Long): Boolean {
        if (limitBytes <= 0L) return false
        return baselineBytes + sessionBytes >= limitBytes
    }
}
```

- [ ] **Step 4: Ajouter la cause**

Dans `RuntimeModels.kt`, enum `DisconnectCause`, ajouter avant `UNKNOWN` :
```kotlin
    QUOTA_EXHAUSTED,
    UNKNOWN,
```

- [ ] **Step 5: Lancer (passe)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.vpn.QuotaCutoffPolicyTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/vpn/QuotaCutoffPolicy.kt android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt android/app/src/test/java/com/swimvpn/app/vpn/QuotaCutoffPolicyTest.kt
git commit -m "feat(android): QuotaCutoffPolicy (pure) + DisconnectCause.QUOTA_EXHAUSTED"
```

---

### Task 5: Android — coupure quota dans le moniteur de vie de SwimVpnService

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt` (ActiveSession ~161 ; EXTRA + onStartCommand ~138/200 ; startVpn ~388 ; moniteur de vie ~1319-1388)

- [ ] **Step 1: Porter limit/baseline sur ActiveSession**

Dans `data class ActiveSession` (~161), ajouter :
```kotlin
        val camouflageFingerprint: String? = null,
        val quotaLimitBytes: Long = -1L,
        val quotaBaselineBytes: Long = 0L,
    )
```

- [ ] **Step 2: Lire les extras (ACTION_START et ACTION_RESTART)**

Dans `onStartCommand`, brancher les extras (déjà définis `EXTRA_DATA_LIMIT`/`EXTRA_DATA_USED`) vers `startVpn`/`restartVpn` via deux params. Ajouter aux signatures `startVpn` et `restartVpn` :
```kotlin
        quotaLimitBytes: Long = -1L,
        quotaBaselineBytes: Long = 0L,
```
ACTION_START → `startVpn(..., quotaLimitBytes = intent.getLongExtra(EXTRA_DATA_LIMIT, -1L), quotaBaselineBytes = intent.getLongExtra(EXTRA_DATA_USED, 0L))`.
ACTION_RESTART → idem avec fallback `activeSession` : `intent.getLongExtra(EXTRA_DATA_LIMIT, activeSession?.quotaLimitBytes ?: -1L)` et `getLongExtra(EXTRA_DATA_USED, activeSession?.quotaBaselineBytes ?: 0L)`. `restartVpn` relaie à `startVpn`.

- [ ] **Step 3: Stocker sur ActiveSession au connect**

Là où `activeSession = ActiveSession(...)` est construit dans `startVpn` (le point unique, à côté de `effectiveCamouflageFingerprint`), ajouter :
```kotlin
        activeSession = ActiveSession(host, port, requestedMode, rawConfig, isByoProxy, effectiveCamouflageFingerprint, quotaLimitBytes, quotaBaselineBytes)
```

- [ ] **Step 4: Coupure dans le moniteur de vie**

Dans `startRuntimeLivenessMonitor` (boucle `while (RUNNING)`, ~1324), AVANT le watchdog de stall, insérer :
```kotlin
                val qLimit = activeSession?.quotaLimitBytes ?: -1L
                if (QuotaCutoffPolicy.isExhausted(qLimit, activeSession?.quotaBaselineBytes ?: 0L,
                        VpnManager.bytesIn.value + VpnManager.bytesOut.value)) {
                    logRuntimeEvent("quota_exhausted", mapOf("limit" to qLimit))
                    stoppedByUser = true // empêche l'auto-reconnect
                    setRuntimeError(
                        localizedContextFor(notificationLanguage).getString(R.string.vpn_err_quota_exhausted),
                        DisconnectCause.QUOTA_EXHAUSTED,
                    )
                    stopVpn(clearRuntimeState = false, reason = "quota_exhausted", cause = DisconnectCause.QUOTA_EXHAUSTED)
                    return@launch
                }
```
Import en haut : `import com.swimvpn.app.vpn.QuotaCutoffPolicy` (même package `vpn` → pas d'import si SwimVpnService importe déjà le package ; sinon ajouter). Note : `stoppedByUser=true` réutilise le garde anti-reconnect existant (vérifier le nom exact du flag dans le fichier ; à défaut, utiliser `manualStopRequested`/le mécanisme qui court-circuite `scheduleReconnect`).

- [ ] **Step 5: Vérifier le build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (la string `vpn_err_quota_exhausted` est ajoutée en Task 9 ; si compile avant Task 9, faire Task 9 d'abord — voir note de séquencement ci-dessous).

> **Séquencement intra-Android :** faire **Task 9 (strings) avant** les steps qui référencent `R.string.vpn_err_quota_exhausted` / les nouvelles clés, pour que `compileDebugKotlin` passe. Recommandation : exécuter Task 9 juste avant Task 5/Task 7.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt
git commit -m "feat(android/vpn): client quota cutoff in liveness monitor (QUOTA_EXHAUSTED, no auto-reconnect)"
```

---

### Task 6: Android — VM passe la baseline + surface l'état quota-épuisé

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt` (putExtra ~1494-1498 ; observe runtime cause)

- [ ] **Step 1: EXTRA_DATA_USED = baseline seule**

Remplacer (~1494-1498) :
```kotlin
                    val limitBytes = if (profile.hasMeasuredLimit) profile.dataLimitBytes else -1L
                    putExtra(SwimVpnService.EXTRA_DATA_LIMIT, limitBytes)
                    putExtra(SwimVpnService.EXTRA_DATA_USED, profile.parsedDataUsedBytes) // baseline cumulée (pas la session)
```
(retire `val usedBytes = profile.totalConsumedBytes()` s'il n'est plus utilisé).

- [ ] **Step 2: Surface l'état quota-épuisé**

Dans l'observateur runtime (`observeAdaptiveRuntime` / là où `runtimeStatus`/cause est collecté), quand `VpnManager` rapporte la cause `QUOTA_EXHAUSTED`, émettre un toast + état CTA :
```kotlin
                if (cause == DisconnectCause.QUOTA_EXHAUSTED) {
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.quota_exhausted_toast)))
                }
```
(Réutiliser le canal de diagnostics existant qui expose la cause ; si la cause n'est pas déjà observée dans le VM, lire `VpnManager.runtimeStatus`/diagnostics et mapper. Le CTA « Renouveler » est porté par la carte en Task 8.)

- [ ] **Step 3: Vérifier le build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/MainViewModel.kt
git commit -m "feat(android): pass quota baseline to service + surface quota-exhausted toast"
```

---

### Task 7: Android — carte premium = vendu uniquement (drop server.*) (TDD sur le mapping)

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/ServersScreen.kt` (`toPremiumAccessSummaryUi` ~1174-1227 ; `PremiumAccessSummaryUi` ~105-115)
- Create: `android/app/src/test/java/com/swimvpn/app/ui/PremiumQuotaMappingTest.kt`

- [ ] **Step 1: Étendre PremiumAccessSummaryUi (champs pour la barre)**

Ajouter à `data class PremiumAccessSummaryUi` (~105) :
```kotlin
    val limitBytes: Long = 0L,        // quota vendu (octets) ; 0 = illimité
    val usedBaselineBytes: Long = 0L, // baseline backend (hors session live)
    val isUnlimited: Boolean = false,
```

- [ ] **Step 2: Écrire le test du mapping (pure-ish)**

Extraire la résolution dans une fonction pure testable. Créer dans ServersScreen (top-level, hors @Composable) :
```kotlin
internal data class PremiumQuotaNumbers(val limitBytes: Long, val usedBaselineBytes: Long, val isUnlimited: Boolean)

internal fun premiumQuotaNumbers(profile: AccessProfileResponse): PremiumQuotaNumbers =
    PremiumQuotaNumbers(
        limitBytes = profile.dataLimitBytes,                 // VENDU only (plus de server.trafficTotalBytes)
        usedBaselineBytes = profile.parsedDataUsedBytes,     // VENDU only (plus de server.trafficUsedBytes)
        isUnlimited = profile.isPremiumAllowed && !profile.hasMeasuredLimit,
    )
```
Test `PremiumQuotaMappingTest.kt` :
```kotlin
package com.swimvpn.app.ui

import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.screens.premiumQuotaNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumQuotaMappingTest {
    private fun profile(limitGb: Double, usedBytes: String, state: String = "ACTIVE_SUBSCRIPTION") =
        AccessProfileResponse(/* … minimal requis … */).copy(dataLimitGB = limitGb, dataUsedBytes = usedBytes, entitlementState = state)

    @Test fun `metered plan exposes sold limit and baseline`() {
        val n = premiumQuotaNumbers(profile(50.0, "1073741824"))
        assertEquals(50L * 1024 * 1024 * 1024, n.limitBytes)
        assertEquals(1073741824L, n.usedBaselineBytes)
        assertTrue(!n.isUnlimited)
    }
    @Test fun `unlimited plan flagged when no measured limit`() {
        val n = premiumQuotaNumbers(profile(0.0, "0"))
        assertTrue(n.isUnlimited)
    }
}
```
> Note d'exécution : adapter le constructeur `AccessProfileResponse` au vrai (champs requis) ; utiliser un helper `copy(...)` minimal. Le test valide UNIQUEMENT que le mapping ignore `server.*` et lit le vendu.

- [ ] **Step 3: Lancer (échoue)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.ui.PremiumQuotaMappingTest"`
Expected: FAIL (fonction absente).

- [ ] **Step 4: Brancher dans toPremiumAccessSummaryUi**

Dans `toPremiumAccessSummaryUi` (~1183), **retirer** la préférence `server.*` :
```kotlin
    val nums = premiumQuotaNumbers(this)
    val expiry = effectiveExpiryAt            // = subscriptionExpiresAt (désormais VENDUE) ; plus de server.expiresAt
```
et remplir `PremiumAccessSummaryUi(..., limitBytes = nums.limitBytes, usedBaselineBytes = nums.usedBaselineBytes, isUnlimited = nums.isUnlimited, ...)`. Remplacer l'ancien calcul `totalBytes/usedBytes` basé sur `premiumServers.firstNotNullOfOrNull { it.trafficTotalBytes ... }`. Garder `quotaValue`/`quotaCaption` (fallback texte) mais alimentés par `nums` (`formatBytes(nums.limitBytes)` / used).

- [ ] **Step 5: Lancer (passe)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.ui.PremiumQuotaMappingTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/ui/screens/ServersScreen.kt android/app/src/test/java/com/swimvpn/app/ui/PremiumQuotaMappingTest.kt
git commit -m "feat(android/ui): premium card uses SOLD quota/expiry only (drop supplier server.*)"
```

---

### Task 8: Android — barre de quota LIVE + état épuisé + CTA

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/ServersScreen.kt` (`PremiumSummaryPill` ~675-702 ; appelant `PremiumAccessCard` ~560)

- [ ] **Step 1: Rendre la barre live dans PremiumSummaryPill**

Remplacer la 1ʳᵉ `QuotaColumn` (quota) par une colonne avec barre live. En tête de `PremiumSummaryPill`, collecter le live et calculer la fraction :
```kotlin
    val bytesIn by VpnManager.bytesIn.collectAsState()
    val bytesOut by VpnManager.bytesOut.collectAsState()
    val usedNow = (access.usedBaselineBytes + bytesIn + bytesOut)
    val fraction = if (access.limitBytes > 0L)
        (usedNow.toFloat() / access.limitBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val exhausted = access.limitBytes > 0L && usedNow >= access.limitBytes
```
Remplacer la colonne quota par :
```kotlin
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.servers_quota_premium), /* style label existant */)
            if (access.isUnlimited) {
                Text(stringResource(R.string.servers_unlimited) /* style value */)
            } else {
                // barre
                Box(Modifier.fillMaxWidth().height(6.dp).clip(SwimDesignTokens.Shape.Pill)
                    .background(SwimDesignTokens.Color.DividerSubtle)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(SwimDesignTokens.Shape.Pill)
                        .background(if (exhausted) SwimDesignTokens.Color.DangerRed else SwimDesignTokens.Highlight.PurpleEdge))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (exhausted) stringResource(R.string.quota_exhausted_label)
                    else stringResource(R.string.servers_quota_used_remaining,
                        formatBytes(usedNow), formatBytes(access.limitBytes), formatBytes((access.limitBytes - usedNow).coerceAtLeast(0L))),
                    /* style caption */
                )
            }
        }
```
(Adapter les styles `Text` aux signatures `QuotaColumn` existantes : couleurs/tailles de `SwimDesignTokens`. Vérifier le nom exact `DangerRed`/équivalent dans les tokens ; sinon utiliser une couleur rouge des tokens existants.)

- [ ] **Step 2: CTA « Renouveler » quand épuisé**

Dans `PremiumAccessCard` (~560), quand `access.limitBytes>0 && (access.usedBaselineBytes ≥ access.limitBytes)` (état persistant épuisé hors session), afficher sous la pill un bouton « Renouveler » qui appelle le callback de navigation abonnement déjà présent (`onSubscribeClick`). Réutiliser `ServerActionPill`/le style bouton existant.

- [ ] **Step 3: Vérifier le build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/ui/screens/ServersScreen.kt
git commit -m "feat(android/ui): live decrementing premium quota bar + exhausted state + Renew CTA"
```

---

### Task 9: Android — i18n (FR/EN/RU)

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml` + `values-fr` + `values-en` + `values-ru`

- [ ] **Step 1: Ajouter les clés (×4 fichiers)**

`values/` et `values-fr/` (FR) :
```xml
    <string name="servers_quota_used_remaining">%1$s utilisés / %2$s · %3$s restants</string>
    <string name="quota_exhausted_label">Quota épuisé</string>
    <string name="quota_exhausted_toast">Quota atteint — renouvelle pour continuer.</string>
    <string name="vpn_err_quota_exhausted">Quota de données atteint. Renouvelle ton forfait.</string>
    <string name="servers_renew">Renouveler</string>
```
`values-en/` (EN) :
```xml
    <string name="servers_quota_used_remaining">%1$s used / %2$s · %3$s left</string>
    <string name="quota_exhausted_label">Quota reached</string>
    <string name="quota_exhausted_toast">Data quota reached — renew to keep going.</string>
    <string name="vpn_err_quota_exhausted">Data quota reached. Renew your plan.</string>
    <string name="servers_renew">Renew</string>
```
`values-ru/` (RU) :
```xml
    <string name="servers_quota_used_remaining">%1$s использовано / %2$s · осталось %3$s</string>
    <string name="quota_exhausted_label">Квота исчерпана</string>
    <string name="quota_exhausted_toast">Квота трафика исчерпана — продлите, чтобы продолжить.</string>
    <string name="vpn_err_quota_exhausted">Квота трафика исчерпана. Продлите тариф.</string>
    <string name="servers_renew">Продлить</string>
```

- [ ] **Step 2: Vérifier le build (résolution R.string)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (toutes les `R.string.*` des Tasks 5/6/8 résolvent).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-fr/strings.xml android/app/src/main/res/values-en/strings.xml android/app/src/main/res/values-ru/strings.xml
git commit -m "i18n: premium quota bar + quota-exhausted + renew (FR/EN/RU)"
```

---

### Task 10: Vérification globale Android (tests unitaires)

- [ ] **Step 1: Lancer la suite unitaire ciblée**

Run (depuis `android/`): `./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.vpn.*" --tests "com.swimvpn.app.ui.PremiumQuotaMappingTest" --tests "com.swimvpn.app.config.*"`
Expected: BUILD SUCCESSFUL, tous verts (QuotaCutoffPolicy + mapping + non-régression config). **Ne PAS** lancer `assembleRelease` ici (machine RAM-contrainte).

---

### Task 11: Build release + device + merge

- [ ] **Step 1: Build release (workaround mémoire)**

Baisser temporairement le heap dans `android/gradle.properties` (`-Xmx2560m -XX:MaxMetaspaceSize=512m`), tuer les daemons java, puis :
Run: `./gradlew :app:assembleRelease --max-workers=1 --no-parallel --no-daemon -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease --console=plain`
Puis `git checkout -- android/gradle.properties` (revert heap).

- [ ] **Step 2: Installer sur device + valider**

Installer l'APK (`adb -s <serial> install -r app-release.apk`). Valider : plan mesuré → barre live se remplit ; atteinte du vendu → coupure + « Quota épuisé » + CTA ; plan illimité → « Illimité », pas de coupure ; expiration affichée = vendue (≠ fournisseur). **Backend doit être déployé d'abord** (champs `quota_gb`/expiration).

- [ ] **Step 3: Merge (après validation device)**

```bash
git checkout main && git merge --no-ff feat/premium-quota-entitlement -m "merge: premium quota client entitlement (sold quota bar + client cutoff)"
git push origin main
git checkout production && git merge --ff-only main && git push origin production
git checkout main && git branch -d feat/premium-quota-entitlement
```

---

## Notes de séquencement
- **Backend d'abord et déployé** (Tasks 1-3 mergées → redeploy Dokploy) AVANT de valider l'Android sur device (sinon `quota_gb`/expiration vendue absents → fail-open partout).
- **Au sein d'Android : Task 9 (strings) avant Tasks 5/6/8** pour que `compileDebugKotlin` résolve les `R.string.*`.
- Vérifier les noms exacts au moment de l'édition : flag anti-reconnect dans `SwimVpnService` (`stoppedByUser` vs `manualStopRequested`), token couleur rouge dans `SwimDesignTokens`, constructeur réel de `AccessProfileResponse` pour le test de mapping.
