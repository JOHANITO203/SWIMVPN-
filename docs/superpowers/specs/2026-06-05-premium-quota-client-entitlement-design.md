# Spec 1 — Droit & quota client (affichage + coupure)

> Statut : design validé (brainstorming), prêt pour plan d'implémentation.
> Sous-projet 1/2. Le 2 = « Auto-resupply fournisseur » (backend), spec séparée.

## Context

L'UI du quota premium est aujourd'hui un **miroir lecture-seule** de ce que le backend renvoie
(`ServerNode.trafficTotalBytes/trafficUsedBytes` ou `AccessProfileResponse.dataLimitBytes/parsedDataUsedBytes`),
sans enforcement client, avec « Illimité » affiché quand aucun plafond n'est reçu, et l'expiration
tirée de `server.expiresAt` (**fournisseur**). Problèmes : (a) le chiffre affiché peut diverger de la
coupure réelle ; (b) « Illimité » masque un inconnu ; (c) afficher l'expiration **fournisseur** alors
qu'on **revend** des accès (l'accès fournisseur meurt souvent avant l'échéance vendue) crée une
contradiction directe.

Objectif : faire de **ce que le client a acheté** (le *droit*) la **seule vérité visible et appliquée**,
avec une barre de quota décrémentale honnête, et une coupure client alignée au pixel sur le quota vendu.
La réalité fournisseur (quota réel, expiration réelle) devient invisible côté client et n'est qu'un
déclencheur de réapprovisionnement — traité dans le **Spec 2** (hors périmètre ici).

## Décisions verrouillées

- **Unité canonique = MégaOctets (MB)** (devise déjà utilisée). Champs en MB ; affichage Mo/Go.
- **Droit client (entitlement) = seule vérité visible/appliquée** : `soldQuotaMb` + `soldExpiry`
  (date d'achat + durée forfait). Quota vendu par forfait : WEEK / MONTH / QUARTER (valeurs en MB
  fournies par le backend, pas codées en dur dans l'app).
- **Source = champ numérique backend** : `quota_mb` (entier) sur chaque plan `/store/plans` ;
  `soldQuotaMb` (entier) + `soldExpiry` (ISO-8601) sur `AccessProfileResponse`. Aucun parsing de label,
  aucune dérivation côté app.
- **`used` affiché = used MESURÉ** = baseline backend (`parsedDataUsedBytes`, converti/comparé en MB) +
  session live (`bytesIn + bytesOut`). Un seul `used`, plafonné à `soldQuotaMb` à l'affichage.
- **Coupure = client/service au quota vendu** (approche A) : le `SwimVpnService` coupe quand
  `baseline + bytesIn + bytesOut ≥ soldQuotaMb`. Pilotée par le **compteur live**.
- **Expiration vendue** : la carte affiche et raisonne sur `soldExpiry`, **jamais** `server.expiresAt`
  (fournisseur). L'enforcement d'expiration reste backend (via `isPremiumAllowed`/`handlePremiumAccessEnded`).
- **Réalité fournisseur (quota réel / expiration réelle)** : ni affichée, ni utilisée comme coupure
  client. = déclencheur de resupply → **Spec 2**.

## Périmètre

### Backend (prérequis, customer-order-service + contracts)
- Ajouter `quota_mb: Int` au modèle Plan + l'exposer dans `GET /store/plans`.
- Ajouter `soldQuotaMb: Long` et `soldExpiry: String?` (ISO-8601) à `AccessProfileResponse`
  (= quota/expiration du forfait acheté ; `soldExpiry` = date d'achat + durée du plan). 
- `parsedDataUsedBytes` reste le used cumulé mesuré (déjà alimenté par `reportMeasuredUsage`).
- Aucune logique de resupply ici (Spec 2).

### Android — modèle/état
- `AccessProfileResponse` (Models.kt) : champs `soldQuotaMb`, `soldExpiry`. Helpers :
  `soldQuotaBytes = soldQuotaMb * 1024 * 1024` ; `usedBytesLive(bytesIn,bytesOut) = parsedDataUsedBytes + bytesIn + bytesOut` ;
  `quotaFraction = (used / soldQuotaBytes).coerceIn(0f,1f)` ; `remainingBytes = max(0, soldQuotaBytes − used)`.
  (Remplace l'usage de `dataLimitBytes`/`hasMeasuredLimit` pour la carte premium.)
- La carte premium (`toPremiumAccessSummaryUi`, ServersScreen) : **n'utilise plus**
  `server.trafficTotalBytes/trafficUsedBytes` ni `server.expiresAt` ; consomme `soldQuotaMb` + used + `soldExpiry`.

### Android — UI (carte premium = barre décrémentale)
- En-tête : nom de plan (Basic/Premium/Platinum) + quota **vendu** (« Premium · 150 Go »).
- **Barre** : remplissage = `quotaFraction`, dégradé premium ; vire ambre puis rouge en approche de 100%.
- Libellés : « **X,X Go utilisés / 150 Go** » + « **Y,Y Go restants** », **rafraîchis en live** quand connecté.
- Expiration : « Expire le {soldExpiry} » + caption (réutilise `formatServerExpiryCaption`, sur `soldExpiry`).
- État épuisé (`used ≥ soldQuotaMb`) : barre pleine + « Quota épuisé » + CTA « Renouveler » → abonnement.
- i18n : nouvelles clés (FR/EN/RU) « utilisés / restants / quota épuisé / renouveler ».

### Android — enforcement (approche A, service)
- VM au connect : `EXTRA_DATA_LIMIT = soldQuotaBytes`, `EXTRA_DATA_USED = parsedDataUsedBytes` (réactive
  ces extras aujourd'hui morts ; remplace la dérivation `hasMeasuredLimit→dataLimitBytes`).
- `SwimVpnService` (moniteur de vie, qui compte déjà les octets) : si `limit > 0` et
  `usedBaseline + bytesIn + bytesOut ≥ limit` → stop tunnel, nouvelle cause **`DisconnectCause.QUOTA_EXHAUSTED`**,
  message i18n, **pas d'auto-reconnect**, état terminal.
- `startPremiumUsageReporting` continue de reporter le used ; à l'épuisement, surface l'état UI (CTA rachat).
- `soldQuotaBytes` absent/0 (legacy/plan inconnu) ⇒ `limit = -1` ⇒ **fail-open** (pas de coupure quota).

## Invariants anti-contradiction (cœur)
- **I1** Coupure client = `soldQuotaMb` ; la réalité fournisseur ne coupe jamais le client (Spec 2 resupply
  garantit la couverture du droit). Seule coupure « anticipée » légitime côté client = `soldExpiry`.
- **I2** Même chiffre live pour la barre **et** la coupure ⇒ barre 100% ⟺ coupure réelle.
- **I3** `used` affiché plafonné à `soldQuotaMb` ; **plus jamais « Illimité »** pour un forfait mesuré.
- **I4** `soldQuotaMb` absent ⇒ fail-open : « Plan actif » + expiration, **pas** de barre inventée, **pas**
  de coupure quota client.
- **I5** Affichage = `soldExpiry` uniquement (jamais l'expiration fournisseur).

## Dépendance & résidu honnête (avant le Spec 2)
I1 (« la réalité fournisseur ne coupe jamais le client ») n'est **pleinement vrai qu'une fois le Spec 2
livré** (resupply transparent). **Avant le Spec 2**, si un accès fournisseur **meurt avant** que le droit
vendu soit consommé (expiration ou quota réel fournisseur), le client est coupé tôt par le chemin existant
(`handlePremiumAccessEnded` / échec de connexion + réconciliation). Dans ce cas, la carte doit afficher un
**état « accès interrompu — renouveler/synchroniser »**, **jamais** une barre partielle figée qui laisserait
croire qu'il reste du quota. Spec 1 ferme donc : la contradiction *quota* (barre = coupure), le *« Illimité »*
trompeur, et l'*expiration fournisseur affichée*. Il **délègue au Spec 2** la continuité face à la mort d'un
accès fournisseur.

## Hors périmètre (YAGNI / autres specs)
- Tout le cycle de vie fournisseur : détection expiration/épuisement réel, auto-resupply, alerte stock
  bas → **Spec 2**.
- Affichage du quota réel fournisseur où que ce soit dans l'app.
- Split de quota par appareil ; changement de convention Go binaire/décimal (on garde ×1024²/×1024³).
- Enforcement backend-autoritaire (chemin futur « vers C ») — l'app reste compatible sans changement d'UI.

## Forward-compat
`soldQuotaMb`/`soldExpiry` venant du backend : quand la prod sera contrôlée en interne, la coupure dure
pourra migrer côté backend (flag) ; l'UI (barre vendu) et le contrat ne changent pas — le client devient
un fast-path que le backend confirme.

## Vérification
- **Backend** : `tsc --noEmit` + `nest build` ; specs : `/store/plans` renvoie `quota_mb` ;
  `AccessProfileResponse` renvoie `soldQuotaMb`/`soldExpiry`.
- **Android unit** (machine RAM-contrainte ⇒ `:app:testDebugUnitTest`, pas `assembleRelease`) :
  helpers purs (`quotaFraction`, `remainingBytes`, plafonnement à sold, fail-open quand soldQuotaMb=0),
  + un test du calcul de coupure (used ≥ limit ⇒ QUOTA_EXHAUSTED) sur la logique extractible.
- **Device** : forfait avec quota vendu → barre se remplit en live ; atteinte du vendu ⇒ coupure +
  « Quota épuisé » + CTA ; profil sans soldQuotaMb ⇒ fail-open (pas de coupure, pas de fausse barre) ;
  expiration affichée = vendue.

## Séquencement
Backend (champs) → Android (modèle + helpers + UI barre + enforcement service + i18n) → tests → build
release → validation device → merge main+prod. (Le merge backend déclenche le redeploy Dokploy.)
