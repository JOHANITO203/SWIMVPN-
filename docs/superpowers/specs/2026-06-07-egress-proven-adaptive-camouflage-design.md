# Camouflage adaptatif prouvé-par-egress — Design

> Spec validée le 2026-06-07. Agent adaptatif qui **façonne un camouflage et refuse de le croire tant qu'il ne l'a pas vu sortir** (shaping × preuve couplés, par-levier). Honnête, faisable, no-regression.

**Objectif :** garantir un tunnel dont la sortie est **prouvée** (egress réel), supprimer *structurellement* le faux positif où l'app affiche « connecté » sans vrai tunnel, et faire apprendre l'agent **uniquement sur cette preuve** — par signature réseau, sans PII, sans nouveau backend.

**Architecture :** un **oracle d'egress-vérité** (la sortie observée de l'extérieur = le serveur, ≠ l'ISP local, soutenue dans le temps) devient l'unique déclencheur de crédit d'apprentissage ET un 2ᵉ verdict de santé. Sur les liens REALITY, l'override d'empreinte uTLS est **interdit** (il casse le handshake) ; le seul levier de shaping adaptatif est la **fragmentation TLS**, *prouvée avant d'être crue*.

**Stack :** Android (Kotlin), cœur Xray (Go) + `hev-socks5-tunnel`, VLESS+REALITY. minSdk 26 / targetSdk 34.

---

## 1. Contexte & cause racine (confirmés device cette session)

- **Faux oracle de crédit** : `recordSuccess(profileId)` est déclenché sur `RuntimeStatus.RUNNING` nu ([MainViewModel.kt:237-242](../../../android/app/src/main/java/com/swimvpn/app/MainViewModel.kt#L237)) — c.-à-d. « le moteur tourne / le serveur est joignable », **pas** « le trafic sort vraiment ». L'agent apprend donc sur des faux positifs.
- **Levier nuisible identifié** : sur un lien **REALITY**, forcer une empreinte uTLS (chrome/firefox/…) **écrase l'empreinte validée du fournisseur** et **casse le handshake**. Confirmé par test manuel sur Samsung (One UI) : **Chrome → egress KO ; fragmentation légère & agressive → egress OK ; AUTO → OK**. C'est **niveau fil/serveur → casse sur n'importe quel device**. Le symptôme « Redmi-only » venait de ce que **l'agent du Redmi avait appris/pioché un override fp**, pas le Samsung (sélection différente, pas tolérance device différente). Concorde avec le précédent connu (« forcer chrome sur REALITY fp=random → cassé »).
- **Egress mesurable, prouvé** : à travers le SOCKS, l'IP vue de l'extérieur = `85.93.1.121` (Finlande, AS202147 **VPSUS LTD** = serveur), ≠ l'ISP maison. La preuve d'egress est donc **réelle et lisible** côté client.
- **Éliminés** : IPv6 (pas d'IPv6 globale sur le WiFi, `::/0 unreachable` honoré par MIUI — fix v1.0.9 intact) et DNS MIUI.

## 2. Objectifs / Non-objectifs

**Objectifs :**
1. Ne **jamais** créditer/apprendre un profil qui n'a pas **prouvé sa sortie** (egress soutenu).
2. Sur REALITY, **ne jamais overrider** l'empreinte uTLS du lien (plancher AUTO).
3. Rendre la **fragmentation** le levier de shaping **adaptatif et prouvé**.
4. **Auto-guérison** : un réglage qui rate la preuve sur réseau sain → repli AUTO automatique (« switch boom » automatisé, fondé sur preuve).
5. Cold-start sûr (appareil neuf, zéro historique user) : AUTO d'abord, apprentissage amorcé par faits d'egress.

**Non-objectifs (frontière d'honnêteté, no-theater) :**
- On **ne mesure pas** la furtivité/détectabilité/invisibilité (aucun oracle DPI client-side). Aucun « score d'invisibilité ».
- On **ne mime pas** le timing/taille de paquets (« façon YouTube ») — Xray ne l'expose pas = théâtre interdit.
- On **ne prouve pas** l'indétectabilité — seulement que le trafic **sort par le serveur et y reste 20-30 s** (egress + durabilité, fait passé observé).
- On **ne touche pas** le transport/cover-domain (server-side), ni le data-plane IPv6 v1.0.9.
- Pas de comparaison d'ASN (l'endpoint ne renvoie que `{ip, country}`).
- Cross-user priors / agrégation = **hors scope (phase B future)**.

## 3. Composants & points de code

### 3.1 EgressTruthProbe (nouveau, pur/testable)
Factorisé depuis `probeTunnelHealthy` ([MainViewModel.kt:266-275](../../../android/app/src/main/java/com/swimvpn/app/MainViewModel.kt#L266)). Responsabilité : produire un verdict d'egress à travers le SOCKS local.
- **Cible** : `https://api.swimvpn.pro/api/v1/status/caller-ip` (déjà livré, renvoie `{ip, country}`, geoip server-side, zéro PII, zéro tiers — réutiliser le parsing de [ResidentialProxyProbe.kt:48-62](../../../android/app/src/main/java/com/swimvpn/app/data/network/ResidentialProxyProbe.kt#L48)). **Secours** : `generate_204` (gstatic) si l'echo timeout (anti-SPOF).
- **Port SOCKS RÉEL** : `runtime.ports.socksPort` (et **non** le `10808` hardcodé l.269).
- **Verdict `EGRESS_PROVEN`** = l'IP echo via SOCKS **≠** la baseline-ISP-IP **ET** soutenue (≥2 echos cohérents sur ~20-30 s, pour survivre au sondage-actif-différé).
- **Baseline-ISP-IP** : capturée par 1 GET **hors-tunnel** au démarrage (l'app est exclue du tun via `addDisallowedApplication(packageName)` [SwimVpnService.kt:630](../../../android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt#L630) → un GET direct sort toujours par l'ISP, un GET via SOCKS toujours par le serveur).
- **CGNAT** (baseline == sortie) : dégrader honnêtement vers « egress joignable, IP-distincte indisponible » — **ne pas inventer** de différence, **ne pas** lever de faux `NO_TRAFFIC`.

### 3.2 Recâblage du crédit (LE fix)
- **Retirer** `serverScoreStore.recordSuccess` de `onAdaptiveRuntimeRunning` ([MainViewModel.kt:237-242](../../../android/app/src/main/java/com/swimvpn/app/MainViewModel.kt#L237)).
- Créditer `recordSuccess(profileId)` **uniquement** sur `EGRESS_PROVEN` soutenu.
- « Serveur joignable mais egress KO » sur réseau **VALIDATED** ⇒ `recordFailure(profileId)` (le vrai « connecté sans tunnel »).
- « Serveur KO » ou réseau **non-VALIDATED** (captif) ⇒ signal **NEUTRE** (réutiliser `DisconnectCause.NETWORK_NOT_VALIDATED`, [MainViewModel.kt:1236]) — **ne jamais blacklister AUTO** derrière un portail captif.
- Echo timeout / erreur tierce ⇒ **NEUTRE** (réutiliser `ProbeFailureReason.TIMEOUT/UNKNOWN`).

### 3.3 Interdiction de l'override fp sur REALITY (corrige directement le bug)
- Dans la résolution de profil ([MainViewModel.kt:727-736](../../../android/app/src/main/java/com/swimvpn/app/MainViewModel.kt#L727) `resolveCamouflageProfile`) **et** dans la sélection agent ([AdaptiveDecisionAgent.kt:560-577](../../../android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt#L560) `selectBestCamouflageProfile`) : quand `securityMode == REALITY`, **filtrer la `fallbackOrder`** pour ne garder que les profils **sans override d'empreinte** (`fingerprint` blank) — c.-à-d. **AUTO + fragmentation** (`frag_light`, `frag_aggressive`). Les profils fp-override (chrome/firefox/safari/ios/randomized) sont **exclus** sur REALITY.
- Conserver `strict-greater` + AUTO en tête ⇒ AUTO gagne à égalité = plancher préservé.
- (Les profils fp-override restent éligibles pour d'éventuels liens TLS-pur non-REALITY où le serveur ne fige pas d'empreinte ; à confirmer selon le catalogue.)

### 3.4 Levier adaptatif = fragmentation (Couche B)
- Clé d'apprentissage **inchangée** `"NETWORK|profileId"` ([AdaptiveDecisionAgent.kt:46-48](../../../android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt#L46)) — on change **la nature du signal** (alimenté par `EGRESS_PROVEN`), pas le schéma. Sur REALITY, les seuls `profileId` appris/choisis sont `auto / frag_light / frag_aggressive`.
- **Durabilité** : un profil n'est promu pour `selectBestCamouflageProfile` qu'après survie **≥2 sondes consécutives** du sentinel existant (`launchHealthSentinel`, intervalle 8 s, gaté `screenOnAndNotPowerSave()`). Un réglage qui passe t=0 puis meurt n'est jamais promu.
- L'application de la fragmentation est déjà en place : `applyShaping` ([TunnelRuntimeAdapter.kt:293](../../../android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt#L293), `sockopt.fragment`). On n'ajoute pas de nouveau primitive.

### 3.5 Auto-guérison bornée
- Si le profil appliqué rate la preuve soutenue **alors que le réseau est sain** : restart immédiat en **AUTO** via `restartActiveServerWithProfile(server, current, "auto")` ([MainViewModel.kt:314-331](../../../android/app/src/main/java/com/swimvpn/app/MainViewModel.kt#L314)) puis re-preuve ; si AUTO prouve ⇒ `recordFailure(profil-cassant)` + `recordSuccess(auto)`.
- Réutiliser le backoff `[1,3,5,10,30s]` et `incidentTriedProfiles` ; **caper** les re-preuves pour ne jamais boucler avec la cascade du sentinel (`planAfterFailure`).

### 3.6 RUNNING inchangé (no-regression)
- `awaitStartupHealthProof` ([SwimVpnService.kt:1182-1248](../../../android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt#L1182)) et `canMarkRunning` **inchangés** : `RUNNING` reste publié vite (time-to-connected + filet IPv4-only v1.0.9 intacts). `EGRESS_PROVEN` est un **2ᵉ verdict post-RUNNING asynchrone**, jamais un durcissement du gate de RUNNING (un blocage de la cible de sonde ne doit jamais transformer une connexion en faux échec).

### 3.7 Migration du store
- `ServerScoreStore` **v6 → v7** ([ServerScoreStore.kt:87]) avec **purge sélective** des seules maps `profileSuccesses/profileFailures` (garder l'historique serveur sain : ping/recommend). Sinon les marges polluées par les anciens faux positifs survivent et le bug persiste le temps de la dilution. Codec additif/index-défensif ⇒ migration non destructive.

## 4. Flux de données (connexion, IA-ON, lien REALITY)

```
toggleVpn(server) -> ACTION_START (profil = AUTO ou meilleur frag prouvé pour la signature réseau)
   -> RUNNING publié vite (connect-serveur OK)  [inchangé]
   -> EgressTruthProbe (async, post-RUNNING): caller-ip via SOCKS, ≥2 echos / 20-30s
        IP == serveur (≠ baseline ISP)  -> EGRESS_PROVEN -> recordSuccess("WIFI|frag_light")
        IP == ISP / timeout (réseau sain) -> recordFailure(profil) -> restart AUTO -> re-preuve
        captif / serveur KO / echo KO -> NEUTRE (rien)
```

## 5. Cold-start
Zéro `ServerQualityScore` ⇒ `selectBestCamouflageProfile` retourne `DEFAULT = AUTO` ([CamouflageProfile.kt:40,59], `fingerprint=""`) = l'empreinte validée du fournisseur, identique à IA-OFF, **corrige d'emblée le profil cassant**. L'appareil n'a besoin que de ce qu'il possède : type de réseau (`currentNetworkType`), type de config (REALITY porté par le lien), baseline-ISP-IP (1 GET). Le 1er établissement n'est crédité qu'après `EGRESS_PROVEN`. Aucune donnée user, aucun prior inventé.

## 6. Honnêteté / UI
- UI optionnelle : carte **« Vérifié — sortie via *pays* »** qui n'apparaît **que** sur attestation réelle (post-RUNNING, async), wording au **passé** (« a porté du trafic réel »), **jamais** « invisible/indétectable ». CGNAT/secours 204 ⇒ « egress prouvé, géo indisponible ».
- Logger via `AdaptiveEventLogger`/`BenchmarkCollector` (déjà PII-free, [MainViewModel.kt:243-250]) les événements `egress_proven`/`early_death` avec exit-IP/country + âge — apprentissage **inspectable**.

## 7. Tests / Vérification
- **Unitaire bloquant** : un candidat qui PASSE le connect-serveur mais RATE l'egress soutenu ⇒ `recordFailure` asserté, **jamais** `recordSuccess`.
- `AdaptiveDecisionAgentTest` (étendre) : sur REALITY, `selectBestCamouflageProfile` n'émet **jamais** un profil fp-override ; fallback AUTO ; promotion seulement après survie ≥2 sondes.
- `ServerScoreCodecTest` : round-trip v7 + purge sélective des maps profil + coexistence vieilles clés.
- `EgressTruthProbe` (pur) : differential serveur≠ISP ; CGNAT ⇒ dégradation honnête ; echo timeout ⇒ NEUTRE.
- Re-run complet `npm run test:policy` (si touché) + suites adaptive/config Android (`testDebugUnitTest --tests "com.swimvpn.app.adaptive.*" --tests "com.swimvpn.app.config.*"`, `--rerun-tasks`).
- **Device (recette)** : Redmi (MIUI) IA-ON, même WiFi : profil cassant ⇒ `recordFailure` + auto-heal AUTO < 30 s + apps restreintes OK ; reconnexions suivantes ⇒ AUTO/frag seul ; Samsung ⇒ AUTO direct ; 4G ⇒ fix IPv6 v1.0.9 tient. Preuve : logs `egress_proven` + exit-IP/country.

## 8. Questions ouvertes (à trancher en plan)
- Fenêtre soutenue 20-30 s / ≥2 echos : bon compromis durabilité↔batterie↔latence-de-crédit ? (wall-clock pendant observable, pas garantie-par-T ; écran éteint = jugement reporté).
- Quota de `caller-ip` à l'échelle (2-3 echos/session/device) → sinon throttle, secours 204 par défaut.
- Liens **non-REALITY** dans le catalogue : l'override fp y est-il jamais utile, ou on retire les profils fp-override partout ?
- Geo-bypass user : garantir que `api.swimvpn.pro` n'est pas en directList (sortie echo en direct fausserait la preuve) — garde par défaut `geoip:private` (sûr).

## 9. Hors scope (phase B)
- Agrégation cross-user des priors (« le Redmi représente des millions d'users ») : exige backend/télémétrie anonymisée — chantier séparé.
- Signature réseau enrichie (transport × IPv4-only/v6 × validated) : seulement **après** validation device de la Couche A (risque de fragmentation des buckets en mono-user).
