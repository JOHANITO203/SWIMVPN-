# SWIMVPN+ Android — Audit de fragilité pré-release (Phase 1)

> Audit statique lecture-seule (2026-06-03), branche `feat/oem-hardening` (candidate release).
> 4 surfaces auditées en parallèle : VPN lifecycle/tunnel · pipeline de config/stockage ·
> moteur adaptatif/réseau · UI/state/boot. Findings classés par sévérité avec `fichier:ligne`,
> cause racine, impact, correction proposée.
> ⚠️ Trouvé par audit de code — chaque correction sera validée à la racine (CORRECTION + RE-REVIEW)
> avant d'être considérée résolue.

---

## 🔴 RELEASE-BLOCKERS (à corriger avant release)

### B1 — LOCAL_PROXY affiche CONNECTÉ sans router aucun trafic (fuite / faux-protégé)
`SwimVpnService.kt:696-743` (`startLocalProxy`) · `TunnelRuntimeAdapter.kt:502-517`
`startLocalProxy` démarre Xray (SOCKS/HTTP sur 127.0.0.1), passe la sonde santé, puis marque
`RUNNING`. Aucun tun, aucun `setHttpProxy`/`ProxyInfo` (confirmé : absents du codebase). L'OS ignore
le proxy → **le trafic réel sort en clair**. La sonde ne prouve que Xray↔serveur via loopback.
**Impact :** UI « Connecté » alors que l'appareil n'est PAS protégé. Faille de confidentialité.
**Fix :** ne jamais mapper LOCAL_PROXY sur `CONNECTED` (statut dédié `RUNNING_PROXY_ONLY` + UI/notif
explicite « proxy prêt, pas system-wide »), ou le retirer (cf. `LOCAL_PROXY_ANALYSIS.md`).

### B2 — Le fallback FULL_TUNNEL→LOCAL_PROXY dégrade en faux-protégé et annonce le succès
`SwimVpnService.kt:478-516` · `vpn/TunnelFallbackPolicy.kt:23-37`
Sur échec data-plane (`ENGINE_CRASH`/`UNKNOWN`), bascule en LOCAL_PROXY → atteint `CONNECTED` (cf. B1).
`PROXY_RESCUABLE_CAUSES` inclut `UNKNOWN` → tout échec non classé descend en faux-protégé, sur les
Xiaomi/Redmi visés.
**Fix :** subordonner le fallback au fix B1 (état visiblement non-protégé) + retirer `UNKNOWN` des
causes « rescuable » (ne tomber que sur une cause infra prouvée, sinon FAILED).

### B3 — tun2socks toujours en JNI in-process → la classe de crash OEM (SIGSEGV) n'est PAS corrigée
`SwimVpnService.kt:599-625` · `Tun2SocksNativeBridge.kt:49-52` · `cpp/tun2socks_jni.c:195-229`
`main_from_file(...)` bloque tout le cycle de vie en natif. Un `SIGSEGV`/`SIGABRT` dans
hev-socks5-tunnel (cause racine OEM documentée) tue le process — aucun `runCatching` Kotlin ne peut
l'attraper. Le retrait de l'orbe 3D a aidé, mais **le vecteur de crash principal demeure**.
**Impact :** crash process sur OEM low-memory (le bug d'origine).
**Fix réel :** isoler tun2socks dans le process `:vpn` (cf. `android/docs/VPN_PROCESS_ISOLATION.md`)
pour qu'un crash natif ne tue que ce process (+ `START_STICKY` récupère). Non corrigeable depuis Kotlin.

### B4 — Un JSON de profils corrompu efface TOUS les configs importés au prochain lancement
`ConfigRepository.kt:288-302` (`getAllProfiles`)
Sur JSON corrompu/partiel/dérive de schéma, `getAllProfiles` catch → `emptyList()`. Le `saveProfiles`
suivant **écrase** le blob récupérable. Forte probabilité sur kill OEM en cours d'écriture.
**Fix :** ne pas traiter l'échec de parse comme « vide-et-écrasable » : sauvegarder le blob brut
avant tout save, ou lever un sentinel qui annule le save.

### B5 — Dérive d'enum dans le JSON stocké → exception → perte totale (aggrave B4)
`ProtocolModels.kt:72-128` + `ConfigRepository.kt:295-297`
Gson lève sur une constante d'enum inconnue → liste perdue. Tout renommage/suppression d'enum dans
une future build = wipe garanti des profils à la mise à jour.
**Fix :** `TypeAdapter` d'enum null-safe (constante inconnue → `UNKNOWN`) + versionner le blob.

### B6 — Probes TCP parallèles non bornées → épuisement de FD / starvation de threads (crash)
`data/network/ServerLatencyEvaluator.kt:27-44`
`enrichWithLatency` lance **une coroutine par serveur sans cap**, chacune un `Socket.connect()`
bloquant sur `Dispatchers.IO` (64 threads). Une grosse liste → burst de sockets + saturation IO
(affame aussi les bridges VPN) → `Too many open files`/OOM.
**Fix :** `Semaphore(8-12)` autour du fan-out (changement local).

### B7 — Reduced-motion du Halo Pulse = code mort → 5 animations infinies + blur 30dp/frame en continu
`ui/screens/HomeScreen.kt:236-241` (n'envoie jamais `isReducedMotionEnabled`) · `HomeVpnCoreStage.kt:54-61`
Le flag existe mais n'est jamais alimenté (aucun `ANIMATOR_DURATION_SCALE`/`isPowerSaveMode`). Les
chemins « freeze » sont inatteignables. 5 animations infinies + un `.blur(30.dp)` plein-canvas par
frame tournent même en DISCONNECTED, **en ignorant le mode économie d'énergie**. C'est la classe de
coût GPU qui avait fait retirer l'orbe.
**Fix (1 ligne) :** calculer `isReducedMotionEnabled` depuis l'OS dans `HomeScreen` et le passer
(geler aussi drift/spin en DISCONNECTED).

---

## 🟠 HIGH (à corriger avant release si possible)

| # | Sujet | Fichier | Cause / Impact | Fix |
|---|---|---|---|---|
| H1 | tun fd use-after-close (SIGSEGV au stop/reconnect) | `SwimVpnService.kt:568-576,796-827` | `quit()` async, fd fermé pendant que le natif l'utilise | `join()` borné le job natif avant `close()`, ou `dup()` le fd |
| H2 | start/stop non sérialisés → double-start, Xray orphelins | `SwimVpnService.kt:371-375,507-515` | guard ne bloque pas `RECONNECTING`; pas de `join()` | mutex/actor; guard = `STARTING|RUNNING|RECONNECTING` + join |
| H3 | JNI singleton global → collisions sessions concurrentes | `cpp/tun2socks_jni.c:25-26,231-255` | `g_state` jamais reset; `quit()` peut viser la mauvaise session | reset `g_state` au stop; `quit()` idempotent |
| H4 | Classification d'échec par mots-clés de message | `vpn/RuntimeStartupFailurePolicy.kt:19-30` | « permission » dans un message tunnel → mauvaise reprise | exceptions typées portant `DisconnectCause` |
| H5 | Reconnect storms sans backoff cross-process | `SwimVpnService.kt:1561-1617,965-999` | budget 5 essais ré-armé à chaque kill OEM | persister tentatives + cooldown dans `RuntimeStateStore` |
| H6 | Health-proof FULL_TUNNEL ne valide pas le tun | `SwimVpnService.kt:644-653,1093-1199` | sonde via loopback → faux-connecté si tun2socks black-hole | router un paquet via le tun, ou exiger rx/tx tun2socks > 0 |
| H7 | Parsing lourd sur main thread + pas de cap de taille | `ConfigImportScreen.kt:218,250` · `ConfigRepository.kt:396` | `canAttemptImport`/`previewConfig` non-suspend sur l'UI | off-main (`Dispatchers.Default`) + cap longueur (1M comme le fetch) |
| H8 | Port overflow tronqué + preview/import incohérents | `ConfigParserEngine.kt:154,1111` | port JSON `Number.toInt()` overflow silencieux | valider `port in 1..65535` au parse (rejeter) |
| H9 | Toggle IA sans debounce + `loadScores()` sur main | `MainViewModel.kt:526-538,1443-1481` · `ServerScoreStore.kt:9-13` | recompute storm + I/O SharedPreferences sur l'UI | collapse le job + `withContext(IO)` |
| H10 | Sweep de probes non annulable + guard qui se bloque | `MainViewModel.kt:1059-1088` | probes continuent après navigate-away/flap; flag peut rester `true` → IA gelée | retenir/annuler le `Job`; `Mutex.tryLock()` |
| H11 | GIVE_UP prématuré sur comptes à 1-2 serveurs | `AdaptiveDecisionAgent.kt:318-331` | exclut `currentServerId` → 0 candidat → abandon | fallback `RECONNECT_SAME` si set filtré vide |
| H12 | Boucle reconcile 1Hz infinie sur Home (même idle) | `HomeScreen.kt:103-115` | read SharedPreferences + recompute chaque seconde | one-shot au cold-start + collecter les StateFlow |
| H13 | Auto-reconnect adaptatif récursif → churn/batterie | `MainViewModel.kt:209-292,158-184` | pas de plafond indépendant côté VM | vérifier cap dur + plancher `delayMs` (incl. agent off) |

---

## 🟡 MEDIUM / LOW (à traiter par lot, post-blockers)

- **Config JSON runtime construit par interpolation de strings** (`ConfigNormalizationEngine.kt:308-544`) — un `"`/`\`/newline dans un password/SNI/path produit du JSON invalide pour le moteur. (Aussi pertinent sécurité.) → construire avec `JsonObject`/Gson. **[à traiter, frontière fragilité/sécurité]**
- Préservation raw lossy pour Clash/sing-box (vmess droppé) (`SubscriptionPayloadDecoder.buildSingBoxLink:142-205`) → porter l'objet source original.
- Dedup faible (host+port+protocol+userId) drope des serveurs Trojan/SS distincts (`ConfigRepository.kt:506-514`) → inclure password/SNI/transport.
- Croissance non bornée des scores persistés + counts non décayés (`ServerScoreStore.kt`, `AdaptiveDecisionAgent.kt:464-474`) → TTL prune + cap/decay.
- Stats tun2socks via JNI bloquant sans timeout (`SwimVpnService.kt:1201-1225`) → `withTimeout`.
- `serviceScope.cancel()` race le `stopVpn` synchrone (`SwimVpnService.kt:990-998`) → teardown synchrone avant cancel.
- Side-effect flow sans buffer → toasts d'erreur perdus en background (`MainViewModel.kt:120-121`) → `extraBufferCapacity`.
- Auto-connect ne se relance qu'au bootstrap, pas au resume + no-op silencieux si consent VPN révoqué (`MainActivity.kt:187-199`, `MainViewModel.kt:1096`) → relancer au resume + feedback.
- Permission notifications refusée = log seul → FGS sans notif (risqué MIUI) (`HomeScreen.kt:117-129`) → snackbar d'explication.
- `decodeBase64Flexible` mojibake au lieu d'échouer (`ConfigParserEngine.kt:1070-1095`) ; recursion `extractEntries` sans borne de profondeur (`SubscriptionPayloadDecoder.kt:62-124`) ; `URLDecoder` qui rejette tout le config sur un `%` malformé (`ConfigParserEngine.kt:966-973`).
- Strings FR hardcodées (i18n) ; `ERROR`→`UNSTABLE` (visuel) ; classification `ConnectException` par substring localisé (`ServerLatencyEvaluator.kt:67-76`).

---

## Constat stratégique (recadrage)
1. **L'orbe retiré n'a pas corrigé le crash OEM principal** : tun2socks reste in-process (B3). Le vrai
   fix = isolation process `:vpn` (déjà conçue, pas implémentée).
2. **LOCAL_PROXY est un faux-protégé** (B1) et le « fallback » OEM y mène silencieusement (B2) — donc
   le filet de sécurité actuel est une fuite, pas une protection.
3. Le reste = robustesse low-end (data-loss B4/B5, FD B6, GPU B7) + un cluster de crash natif (H1-H3).
4. La base est saine par ailleurs : math de scoring défensive, recovery process-death correcte,
   no-op du BootReceiver justifié (sécurité), protections faux-connecté correctes côté UI.
</content>
