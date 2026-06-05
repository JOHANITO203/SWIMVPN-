# Spec 1 — Droit & quota client (affichage + coupure) — v3 (audité, quota en GB)

> Statut : design validé (brainstorming) + audité sur le code réel. Prêt pour plan d'implémentation.
> Sous-projet 1/2. Le 2 = « Auto-resupply fournisseur » (backend), spec séparée.

## Context (corrigé par l'audit)

Le quota **vendu** circule **déjà** de bout en bout en **GB** : le backend le parse depuis `plan.quota_label`
("50 GB"→50) dans `getProfile` ([customer.service.ts:896](../../../backend/apps/customer-order-service/src/customer.service.ts)) et l'expose en `dataLimitGB` ;
Android calcule déjà `dataLimitBytes` (`dataLimitGB×1024³`), `totalConsumedBytes`, `remainingBytes`,
`consumedPercentage` (Models.kt). Les vrais défauts à corriger (pas à réinventer) :

1. **La carte préfère le FOURNISSEUR au VENDU** : `server.trafficTotalBytes ?? dataLimitBytes`,
   `server.expiresAt ?? effectiveExpiryAt` (ServersScreen `toPremiumAccessSummaryUi`).
2. **`subscriptionExpiresAt` renvoie l'expiration FOURNISSEUR** (`assignment.expires_at ?? supplier_expires_at`)
   alors que l'expiration **vendue** est déjà calculée (`calculateSubscriptionExpiresAt = fulfilled_at + durée plan`,
   [:1972](../../../backend/apps/customer-order-service/src/customer.service.ts)) mais non renvoyée. = bug d'expiration.
3. **Aucune coupure client** : `EXTRA_DATA_LIMIT/USED` sont posés (= déjà le quota vendu en octets) mais
   **jamais lus** par `SwimVpnService`.
4. **Barre statique** : la carte n'utilise pas `consumedPercentage(bytesIn,bytesOut)` (qui existe déjà, live).

Objectif : faire du **droit acheté** la seule vérité visible/appliquée, barre live honnête, coupure client
alignée au vendu. La réalité fournisseur (quota réel, expiration réelle) reste invisible côté client (=
déclencheur de resupply, **Spec 2**).

## Décisions verrouillées

- **Quota en GB** (maille des forfaits 50/150/500). On **conserve le pipeline existant**
  `dataLimitGB → dataLimitBytes` ; **Android inchangé** sur ce point. L'**usage** s'affiche en Mo/Go via
  `formatBytes` (base 1024).
- **Source = champ numérique** `quota_gb` sur Plan (remplace le parsing fragile de `quota_label`).
  `quota_gb` null/0 = plan **réellement illimité**. `dataLimitGB` du profil est dérivé de `quota_gb`.
- **Vérité visible/appliquée = droit vendu** : quota = `quota_gb` du plan acheté ; expiration =
  `subscriptionExpiresAt` **corrigé** pour renvoyer l'expiration **vendue** (`calculateSubscriptionExpiresAt`).
  `supplierExpiresAt` reste exposé séparément (ops), jamais affiché au client.
- **used = mesuré par-droit** : `OrderAssignment.measured_used_bytes` (déjà, via `report_usage`) →
  `parsedDataUsedBytes` (baseline) **+ session live** (`bytesIn + bytesOut`).
- **Coupure = client/service au quota vendu** : on **réactive** les `EXTRA_DATA_*` (déjà posés) dans le
  moniteur de vie ; stop dès `baseline + bytesIn + bytesOut ≥ limite`, cause `QUOTA_EXHAUSTED`, sans auto-reconnect.
- **Carte = vendu uniquement** (on retire la préférence `server.*`), **barre live** (`consumedPercentage`).

## Périmètre & changements (précis)

### Backend
- **Plan** (prisma) : ajouter `quota_gb Int?` (null/0 = illimité). Migration de données : backfill depuis
  `quota_label` existant. `store.service.getActivePlans` : exposer `quota_gb`.
- **`getProfile`** : source du quota = `plan.quota_gb` (au lieu de `parseQuotaLabelToGb`) → `dataLimitGB`.
  **`subscriptionExpiresAt` = `calculateSubscriptionExpiresAt(...)` (VENDUE)** ; `supplierExpiresAt` inchangé.
- `dataUsedBytes` inchangé (= `OrderAssignment.measured_used_bytes`, par-droit).

### Android — modèle (Models.kt)
- **Inchangé** : `dataLimitGB`, `dataLimitBytes = dataLimitGB×1024³`, `hasMeasuredLimit = dataLimitGB > 0`,
  `totalConsumedBytes/remainingBytes/consumedPercentage`. (50/150/500 entiers ⇒ octets exacts.)

### Android — carte premium (ServersScreen `toPremiumAccessSummaryUi`)
- `totalBytes = dataLimitBytes` (vendu) — **retirer** `server.trafficTotalBytes`.
- `usedBytes = parsedDataUsedBytes (+ live)` — **retirer** `server.trafficUsedBytes`.
- `expiry = effectiveExpiryAt` (= `subscriptionExpiresAt` désormais vendue) — **retirer** `server.expiresAt`.
- **Barre live** : la carte collecte `VpnManager.bytesIn/bytesOut` et affiche
  `consumedPercentage(bytesIn,bytesOut)` ; libellés « X utilisés / Y » + « Z restants », live quand connecté.
- État épuisé (`used ≥ dataLimitBytes`) : barre pleine + « Quota épuisé » + CTA « Renouveler ».
- « Illimité » conservé **uniquement** quand `dataLimitGB == 0` (plan réellement illimité).

### Android — enforcement (service)
- VM au connect : `EXTRA_DATA_LIMIT = dataLimitBytes` (vendu), `EXTRA_DATA_USED = parsedDataUsedBytes`
  (**baseline seule**, pas `totalConsumedBytes()` qui inclut une session). `dataLimitGB==0 ⇒ limit = -1` (fail-open).
- `SwimVpnService` : porter `limit`/`baseline` sur `ActiveSession` (comme le fingerprint camouflage) ; dans le
  **moniteur de vie** (boucle 500 ms qui lit déjà `bytesIn/bytesOut`) : si `limit > 0` et
  `baseline + bytesIn + bytesOut ≥ limit` → stop, **`DisconnectCause.QUOTA_EXHAUSTED`** (nouvelle valeur),
  message i18n, **pas d'auto-reconnect**, état terminal.
- À l'état `QUOTA_EXHAUSTED`, le VM surface l'UI « quota épuisé » + CTA rachat (parallèle à
  `handlePremiumAccessEnded`, motif quota).
- i18n : nouvelles clés FR/EN/RU (utilisés / restants / quota épuisé / renouveler / message coupure quota).

## Invariants anti-contradiction
- **I1** Coupure client = quota **vendu** (ou expiration vendue, pilotée backend via `isPremiumAllowed`).
  La réalité fournisseur ne coupe pas le client (continuité = Spec 2).
- **I2** Même chiffre live pour barre **et** coupure ⇒ barre 100% ⟺ coupure.
- **I3** used plafonné au vendu ; « Illimité » **seulement** si `dataLimitGB==0` (plan vraiment illimité).
- **I4** `dataLimitGB` absent/0 sur un plan censé mesuré ⇒ fail-open (pas de fausse barre, pas de coupure).
- **I5** Affichage expiration = `subscriptionExpiresAt` (désormais vendue) ; `supplierExpiresAt` jamais montré.

## Dépendance & résidu honnête (avant le Spec 2)
I1 n'est **pleinement vrai qu'avec le Spec 2** (resupply). **Avant** : si un accès fournisseur meurt avant
épuisement du droit, le client est coupé tôt (échec de connexion / `handlePremiumAccessEnded`). La carte doit
alors montrer un **état « accès interrompu — renouveler/synchroniser »**, jamais une barre partielle figée.
Spec 1 ferme : contradiction *quota* (barre=coupure), *« Illimité »* trompeur, *expiration fournisseur affichée*.

## Hors périmètre (YAGNI / autres specs)
Cycle de vie fournisseur (détection expiration/quota réel, auto-resupply, alerte stock) → **Spec 2** ;
affichage du quota réel fournisseur dans l'app ; split par appareil ; enforcement backend-autoritaire (futur) ;
changement de convention 1024 ; passage de l'unité en MB (abandonné : GB suffit pour le quota).

## Forward-compat
`quota_gb`/expiration vendue venant du backend ⇒ la coupure dure pourra migrer côté backend (flag) sans
changer l'UI ni le contrat ; le client deviendra un fast-path confirmé par le backend.

## Vérification
- **Backend** : `npm run lint` (tsc) + `nest build` ; specs ts-node : `/store/plans` renvoie `quota_gb` ;
  `getProfile` renvoie `dataLimitGB` (dérivé de `quota_gb`) + `subscriptionExpiresAt == calculateSubscriptionExpiresAt`
  (vendue) et `supplierExpiresAt` distinct.
- **Android unit** (`:app:testDebugUnitTest`, pas `assembleRelease`) : `consumedPercentage`/`remainingBytes`
  plafonnés ; fail-open quand `dataLimitGB==0` ; logique de coupure (`baseline+session ≥ limit ⇒ QUOTA_EXHAUSTED`)
  extraite en fonction pure testable.
- **Device** : plan mesuré → barre se remplit **en live** ; atteinte du vendu ⇒ coupure + « Quota épuisé » +
  CTA ; plan illimité ⇒ « Illimité », pas de coupure ; expiration affichée = **vendue** (≠ fournisseur).

## Séquencement
Backend (`quota_gb` + fix `subscriptionExpiresAt`) → Android (carte vendu/live + coupure service + i18n) →
tests → build release → validation device → merge main+prod (le merge backend déclenche le redeploy Dokploy).
