# SWIMVPN+ — Réalité technique opérationnelle

> **Portée** : analyse statique exhaustive de la branche `main` (état déployé, `7fbebd5`).
> **Règle** : ce document décrit **uniquement ce que l'app fait aujourd'hui** — pas de roadmap, pas
> d'intentions, pas de suppositions. Toute affirmation est sourcée `fichier:ligne`. Les features
> présentes uniquement sur des branches non mergées (ex. l'auto-heal proxy `feat/proxy-autoheal`)
> sont **exclues**. Plateforme : **Android uniquement**.
>
> **Mise à jour — Phase 1 sécurité (`fb0eb84`)** : depuis cette analyse, deux constats du §16 sont
> **RÉSOLUS** — **#1 stockage des credentials** (désormais chiffré au repos en Keystore AES-GCM via
> `SecureCrypto`, sur `imported_profiles` + `last_runtime_config`) et **#7 fuite de l'IP de sortie**
> (la sonde proxy passe par notre endpoint HTTPS `api.swimvpn.pro/api/v1/status/caller-ip`, plus
> aucun appel à ip-api en HTTP). Le corps ci-dessous décrit la **baseline `7fbebd5`** (pré-Phase 1).

---

## 1. Architecture & couches

Application Android Kotlin/Compose. Package racine `com.swimvpn.app`.

| Package | Rôle réel |
|---|---|
| *(racine)* | `SwimVpnService` (le `VpnService` + orchestrateur runtime), `MainActivity`, `MainViewModel`, `AutoConnectBootReceiver` |
| `vpn/` | machine d'état + **politiques pures** : `RuntimeModels` (enums Mode/Status/Cause), `VpnManager` (StateFlows en mémoire), `RuntimeStateStore` (snapshot persisté), + policies (`TunnelFallbackPolicy`, `StickyReconnectPolicy`, `RuntimeRecoveryPolicy`, `NetworkHandoffPolicy`, `NetworkClassifier`…) |
| `runtime/` | lancement des moteurs natifs : `XrayProcessBridge` (Xray en **process enfant**), `Tun2SocksNativeBridge` (**JNI**, chemin câblé), préparateurs d'assets |
| `config/` | pipeline config : `ConfigParserEngine`, `ConfigNormalizationEngine`, `TunnelRuntimeAdapter` (profil → JSON Xray), `ConfigRepository`, `SubscriptionFetcher`, `subscriptionparser/`, `ProtocolModels` |
| `data/` | `network/` (Retrofit `ApiService`, `ResidentialProxyProbe`, `ServerLatencyEvaluator`), `local/` (`PreferencesManager` DataStore, `DeviceIdentityProvider`) |
| `adaptive/` | `AdaptiveDecisionAgent`, `ServerScoreStore`, `AgentDisabledFailurePolicy`, `AdaptiveEventLogger` (hors du chemin paquets) |
| `diagnostics/` | `CrashReporter` (stub logcat) |
| `ui/`, `cpp/` | écrans Compose + thème ; `tun2socks_jni.c` (shim JNI) |

**Build** (`android/app/build.gradle`) : `compileSdk 34`, `minSdk 26`, `targetSdk 34`, `versionCode 7` / `versionName "1.0.6"`, NDK `27.0.12077973`, ABIs `arm64-v8a` + `x86_64`. Java/Kotlin 17 + desugaring. Compose BOM 2024.12.01, Retrofit 2.11.0, OkHttp 4.12.0, DataStore 1.2.1, `play-services-code-scanner` 16.1.0. Release : R8 + shrinkResources ; signature lue hors-repo (`~/.swimvpn-signing/keystore.properties`, sinon UNSIGNED).

---

## 2. Chemin de données VPN (data plane) — FULL_TUNNEL

Câblé entièrement dans `SwimVpnService.startTunnelInterface()` (`SwimVpnService.kt:541-721`) :

1. `registerNetworkCallback()` **avant** d'établir le tun.
2. **Xray démarré d'abord** (`startValidatedXrayRuntime`, `:1085-1118`) : `prepare()` → `start()` → `delay(600)` → check de vie.
3. **tun construit** : `addAddress("10.0.0.2", 24)`, `addRoute("0.0.0.0", 0)` (full tunnel), `setMtu(1280)`, DNS `1.1.1.1 / 1.0.0.1 / 8.8.8.8 / 8.8.4.4`, et `addDisallowedApplication(packageName)` (anti-boucle : l'app s'exclut elle-même). `establish()` → fd tun.
4. **tun2socks lancé en JNI** : `socks5://127.0.0.1:10808`, `tunFd`, `mtu 1280`, interface `swim0` ; `Tun2SocksNativeBridge.start(...)` bloque dans la boucle native.
5. **Flux paquets** : apps device → tun `swim0` (10.0.0.2/24, route 0.0.0.0/0) → hev-socks5-tunnel lit le fd → SOCKS local `127.0.0.1:10808` → inbound socks de Xray → outbound `proxy` Xray (selon le profil) → réseau.
6. **Preuve de démarrage active** (`awaitStartupHealthProof`, `:1120-1186`) : après `delay(1000)`, vérifie Xray vivant + tun2socks actif + **sonde TCP réelle à travers le SOCKS** vers le serveur. `RUNNING` n'est publié **qu'après** preuve de trafic — pas d'état « connecté » sans trafic prouvé.

> Le port SOCKS local (10808) est **codé en dur** des deux côtés (tun2socks cible 10808 ; l'inbound Xray écoute 10808). Couplage par convention, pas par dérivation depuis une source unique.

---

## 3. Dépendances natives & runtime

| Binaire | Quoi | Version | Packaging | Lancement |
|---|---|---|---|---|
| **Xray-core** = `libxray.so` | moteur proxy | `v26.3.27` | téléchargé par ABI depuis XTLS releases, renommé en `.so` ; `geoip.dat`/`geosite.dat` en assets | **process OS enfant** : `ProcessBuilder("xray","run","-config",…)` (`XrayProcessBridge.kt:80-92`) |
| **tun2socks** = hev-socks5-tunnel (`libhev-socks5-tunnel.so`) | pompe tun↔SOCKS | tag `2.14.4` | build `ndk-build` depuis source si non fourni | **in-process JNI** via shim `libswimvpn_tun2socks_jni.so` → `hev_socks5_tunnel_main_from_file(config, tunFd)` (bloquant) ; **single-instance** |

**Limites natives** : si le shim JNI ne charge pas → `FAILED` (« Full tunnel data plane is unavailable », `:665-668`) — **aucun fallback exécutable câblé**. La détection « tun2socks a démarré » repose sur un `delay(200)` + job actif (`:649-651`) ; un crash plus tardif n'est rattrapé que par le moniteur de vie. Un **SIGSEGV/SIGABRT natif ne peut pas être attrapé** par le `runCatching` Kotlin.

---

## 4. Modes de runtime (`RuntimeMode`)

- **FULL_TUNNEL** — le **seul** vrai data plane (§2). Câblé.
- **LOCAL_PROXY** — **retiré/mort** : `startVpn` route FULL_TUNNEL **et** LOCAL_PROXY vers `startTunnelInterface` (`:466-467`) ; `startLocalProxy()` (`:723-770`) **sans aucun appelant**.
- **SPLIT_TUNNEL** — non implémenté : `throw IllegalStateException("Split tunnel is not available yet")` (`:468-470`).
- **Fallback proxy** — **mort** : `TunnelFallbackPolicy.PROXY_RESCUABLE_CAUSES = emptySet()` → tout le branchement `pendingProxyFallback` (`:503-536`) est inatteignable (neutralisation B1/B2 délibérée pour éviter un état « connecté mais qui fuit »).

---

## 5. Protocoles (parsing vs exécutable)

Enum `Protocol` : VLESS, VMESS, TROJAN, SHADOWSOCKS, SOCKS5, HTTP, UNKNOWN (`ProtocolModels.kt:98-107`).

**Détail critique de précédence** : `generateXrayRuntimeDocument` privilégie le `normalizedRuntimeConfig` (string JSON construit par `ConfigNormalizationEngine`) quand il existe ; les builders riches `createXxxOutbound` de `TunnelRuntimeAdapter` ne sont qu'un **fallback**.

| Protocole | Parsé | Exécutable | Réalité |
|---|---|---|---|
| **VLESS** | `vless://…` + JSON | ✅ | runtime via `generateVlessRuntimeConfig` → transports **TCP/WS uniquement** ; gRPC/HTTP2/KCP/QUIC droppés ; **reality dégradé** (publicKey+shortId seuls) |
| **VMESS** | `vmess://base64` + JSON | ✅ | `alterId:0`, `security:"auto"` ; mêmes limites transport/reality que VLESS |
| **TROJAN** | `trojan://…` + JSON | ✅ | le `normalizedRuntimeConfig` émet un **schéma trojan-client (mort, ignoré par Xray)** → fallback `createTrojanOutbound` (vrai outbound Xray + streamSettings) |
| **SHADOWSOCKS** | `ss://…` + JSON | ✅ | idem (schéma ss-natif mort → fallback `createShadowsocksOutbound`) ; **plugins obfs/v2ray-plugin parse-only** ; pas de streamSettings |
| **SOCKS5** | `socks5://`, `socks5h://`, `socks://`, `host:port[:user:pass]` | ✅ | chemin **BYO proxy résidentiel** : `createProxyOutbound` → outbound `socks` Xray |
| **HTTP** | — | ✅ (câblé) **mais inatteignable** | aucun parser ne produit `Protocol.HTTP` (`parseProxyEndpoint` force SOCKS5, `ConfigParserEngine.kt:458`) → support HTTP mort en pratique |
| **UNKNOWN** | — | ❌ | rejeté partout |

**Transports émis** (`buildStreamSettings`, chemin fallback) : TCP, WS, gRPC, HTTP2, TLS, Reality ; **KCP/QUIC → settings vides (non fonctionnels)** ; **XTLS → string sans bloc settings**. Comme VLESS/VMESS passent par le chemin `normalize`, leurs transports **effectifs** se limitent à **TCP/WS**.

**Schémas reconnus mais REJETÉS** : `hy2://`/`hysteria2://`/`hysteria://`, `tuic://`, `wg://`/`wireguard://` (« recognized but not supported », `ConfigParserEngine.kt:1298-1335`).

---

## 6. Config Xray réellement générée (`TunnelRuntimeAdapter`)

- **log** `warning` · **dns** `1.1.1.1,1.0.0.1,8.8.8.8,8.8.4.4` + `queryStrategy UseIPv4`
- **inbounds** : `socks-in` `127.0.0.1:10808` (udp+noauth) + `http-in` `127.0.0.1:10809` (ports **codés en dur**)
- **policy** niveau 0 : `handshake 4, connIdle 300, uplinkOnly 2, downlinkOnly 5`
- **outbounds** : `proxy` (primaire) + `direct` (freedom) + `block` (blackhole)
- **routing** : `domainStrategy` défini mais **`rules` TOUJOURS vide** → **aucun split/bypass geo**
- **sniffing** : `false` sur le chemin `wrapOutboundIntoRuntime`

---

## 7. Formats d'entrée acceptés

`vless:// vmess:// trojan:// ss://` · `socks5://`/`socks5h://`/`socks://` + `host:port[:user:pass]` · JSON Xray/V2Ray (single ou `outbounds[]`) · **subscription URL** `http(s)://` (fetch multi-User-Agent, 4 passes de décodage, Clash YAML + sing-box convertis) · **`swimvpn://crypt1/`** (résolu côté backend via userNumber+deviceId, **impossible offline**) · **`happ://add/…`** (deep link) · `happ://crypt3|4|5/` **non supporté** · **QR** (texte ré-injecté dans `importConfig`, pas de parser QR dédié).

---

## 8. L'« agent IA » (décisionnel embarqué)

> **Cadrage impartial** : malgré le nom « agent IA », c'est un **scoreur heuristique déterministe à base de règles** avec compteurs persistés. **Aucun modèle, aucune inférence, aucun apprentissage** au-delà de compteurs succès/échec/sélection-manuelle bornés. C'est le **seul** composant décisionnel embarqué de `main` (aucun `ProxyDecisionAgent`/auto-heal dans main — vérifié par grep).

`AdaptiveDecisionAgent` (`adaptive/AdaptiveDecisionAgent.kt`) — `object` pur, sans I/O ; persistance dans `ServerScoreStore` ; câblage/effets dans `MainViewModel`.

**Quand il agit** :
- **Échec de connexion** (`RuntimeStatus.FAILED`) → `planAfterFailure`. DEGRADED/NO_NETWORK/RECONNECTING = no-op.
- **Succès** (RUNNING) → `recordSuccess` + reset compteur.
- **Refresh de recommandation** → `recommendServer` — **événementiel uniquement, aucun ticker périodique** (déclenché par bootstrap, refresh serveurs, import/select profil, pin, foreground).
- **Sélection manuelle** → écrit un compteur (n'auto-agit jamais).
- **Changement réseau** → **aucun** callback réseau ne relance l'agent ; le `networkType` est échantillonné à la demande.

**Ce qu'il décide** :
- `planAfterFailure` → `RECONNECT_SAME` / `SWITCH_SERVER` / `GIVE_UP`.
- `recommendServer`/`selectBestServer` → meilleur serveur (score minimal).

**Signaux de score** (somme, plus bas = mieux) : ping normalisé · pénalité d'âge de latence graduée (0→200) · MISSING_PING 300 · probe-failed transient 150 vs serveur-side 1000 (ce dernier filtré) · historique (`consecutiveFailures*250 + failureCount*25 − successCount*10`, décroît après 30 min) · pénalité par-réseau (≤250) · « works-here » reward (≤120) · nudge heure-du-jour (±30) · reward manuel (≤40, décroît 30 j) · load (0–50) · availability congested 50 · pinned −5 · **détection d'outage global** (≥2 candidats, ratio ≥0.6 → suppression des pénalités + relâche des filtres). **Avoid-window** : 10 min après 2 échecs consécutifs.

**Contraintes/garanties** : ne ressuscite pas un serveur évité/probe-failed (sauf outage global) · max **5** tentatives de reconnexion · backoff `[1,3,5,10,30]s` · **2** retries même-serveur avant fallback · rewards bornés < pénalités.

**Consommation (ne lui obéit PAS toujours)** : passé par `AgentDisabledFailurePolicy` — **toggle IA OFF ⇒ tout `SWITCH_SERVER` est rétrogradé en `RECONNECT_SAME`** (l'agent ne change de serveur **que si l'utilisateur a activé l'IA**). Le **badge « recommandation validée »** n'apparaît que si toggle ON **et** `qualityState == FRESH` (ping < 2 min).

**Latence (`ServerLatencyEvaluator`)** : connect TCP brut `host:port`, timeout 1500 ms, 10 sondes concurrentes max, **hors-VPN** (socket factory sur réseau `NOT_VPN`), **événementiel** (pas de timer). Raisons d'échec : TIMEOUT / DNS_FAILURE / CONNECTION_REFUSED / NETWORK_UNREACHABLE / UNKNOWN.

**Persistance** : `SharedPreferences "swimvpn_adaptive_scores"` (codec versionné v5). `AdaptiveEventLogger` = `Log.i` seulement, **aucune télémétrie réseau**.

---

## 9. Cycle de vie & récupération d'erreurs

**Entrée** (`onStartCommand`, `START_STICKY`) : ACTION_START / RESTART / STOP / null(sticky). `isByoProxy` dérivé du protocole (SOCKS5/HTTP).

**Moniteur de vie** (`startRuntimeLivenessMonitor`, poll 500 ms) : mort moteur (Xray/tun2socks) → `ENGINE_CRASH` + reconnect. **Watchdog de stall passif** : `bytesOut>0 && bytesIn==0` au-delà de **15 s** (BYO **8 s**) → `DEGRADED(NO_TRAFFIC)` + reconnect (une session 0/0 idle n'est jamais demotée).

**Reconnexion** (`scheduleReconnect`) : max **5** (BYO **2**) ; backoff `[1,3,5,10,30]s` ; à l'abandon BYO → message `proxy_session_down`. Court-circuité par stop utilisateur / `PERMISSION_REVOKED`.

**DisconnectCause** : 13 valeurs ; assignées explicitement ou par classification **par mots-clés** (fragile/locale-dépendante, hors `StartupHealthException`). **Valeurs jamais produites** : `SERVER_UNREACHABLE`, `DNS_FAILURE`, `HANDSHAKE_FAILURE`, `BATTERY_RESTRICTION` (mortes).

**Récupération mort-de-process / sticky** : `RuntimeStateStore` (SharedPreferences) + `StickyReconnectPolicy` (fenêtre 120 s) + `RuntimeRecoveryPolicy` + `AutoConnectPayload` (host/port/protocol/runtimeConfig/mode). `isFresh()` empêche un faux « Connecté » (statuts actifs valides ≤6 s, heartbeat 2 s).

**Boot** : `AutoConnectBootReceiver` est un **no-op intentionnel** — ne démarre **jamais** le service au boot ; l'auto-connect au boot est effectivement inopérant.

**Changement réseau** : callback (INTERNET + NOT_VPN) → `setUnderlyingNetworks` ; perte du réseau actif → grâce **4 s** puis reconnect (debounce, annulé si le réseau revient).

**Surface UI** : `VpnManager` mappe `RuntimeStatus` → `VpnState` (StateFlows : state, runtimeStatus, errorMessage, metrics, bytesIn/out).

---

## 10. Souscriptions, entitlements & backend

**Modèle** : `AccessProfileResponse` (`data/network/Models.kt`) = source de vérité. `isPremiumAllowed = isActiveTrial || isActiveSubscription`.

**Gate de connexion** (`toggleVpn`) : refus si `!isPremiumAllowed && server.source=="backend"` → `err_subscription_expired`. **L'expiration est appliquée via `isPremiumAllowed`** (renvoyé par le backend), pas par un check de date local.

> **Limites de quota envoyées au service mais JAMAIS appliquées** : `EXTRA_DATA_LIMIT`/`EXTRA_DATA_USED` sont déclarés + peuplés mais **jamais lus** dans `SwimVpnService` (aucun kill-on-quota client). L'enforcement de quota/expiration mi-session passe **uniquement** par le poll d'usage (`startPremiumUsageReporting`) qui re-fetch le profil et, si plus premium, `handlePremiumAccessEnded` coupe le VPN — **piloté backend, avec délai du poll**.

**Trial** : `activateTrial(email, phone)` → `api/v1/access/trial/activate`. Freemium via `completeProfile`. Éligibilité 100 % backend.

**Plans/paiement** (`SubscriptionScreen`) : plans WEEK/MONTH/QUARTER (prix RUB) ; méthodes **SwimPay + Crypto seulement** (pas de carte). **L'app ne traite aucun paiement** : `createCheckout` → `api/v1/orders/checkout` → **deep-link sortant** `redirectUrl` ; re-bootstrap au retour (fenêtre 10 min). Compteur d'appareils affiché **codé en dur 1/2/3** (pas depuis le backend). Crypto checkout sort avec `cryptoAsset=null` (asset choisi sur la page externe).

**API** (`RetrofitClient`) : base `https://api.swimvpn.pro/`, Retrofit+Gson, timeouts 15 s. **Aucun token/bearer** — identité = `userNumber` (device-bound) + `deviceId` (headers `x-user-number`/`x-device-id` sur `/servers`, dans le body ailleurs). Endpoints réels : bootstrap, trial/activate, profile/complete, subscription/cancel-current, access/{userNumber}, subscription/resolve-crypt, subscription/usage, servers, store/plans, orders/checkout.

**Usage** : `reportMeasuredUsage` POST bytes mesurés → backend (autorité du quota). **Code mort** : `data/api/ApiService.kt` (doublon non câblé) ; endpoints déclarés non appelés (`startTrial`, `importSubscription`, `activateCode`, `createOrder`).

---

## 11. Sécurité du stockage local & secrets

> **Constat le plus matériel : TOUTES les données sensibles sont stockées en CLAIR.** Aucun `EncryptedSharedPreferences`, aucun Android Keystore (dépendance `security-crypto` absente du build). Seule protection au repos : `android:allowBackup="false"` (exclut backup cloud/adb, **ne chiffre pas**).

Trois stores, **tous non chiffrés** :
- **`ConfigRepository` (DataStore `vpn_configs`)** : `imported_profiles` = tableau JSON Gson de `SwimVpnProfile` **en clair**, incluant `rawConfig`, `userId` (UUID), `password` (Trojan/SS/proxy), Reality keys, SNI, `normalizedRuntimeConfig`. + backup du blob corrompu en clair + URLs de souscription en clair.
- **`PreferencesManager` (DataStore `swimvpn_prefs`)** : `user_number`, prefs, et **`last_runtime_config`** = config runtime complète **en clair** (credentials embarqués) pour rejeu auto-connect.
- **`RuntimeStateStore` (SharedPreferences)** : état runtime + chemins de logs (pas de credentials).

**Secrets build/runtime** : aucun secret dans `BuildConfig` (seulement métadonnées d'assets non-secrètes) ; aucun token/clé API en dur ; keystore de signature = build-time hors-repo. Logs **rédacteurs** (`redactForLog` masque UUID + `password/token/uuid/id=`).

**Risque** : sur device rooté ou extraction locale, les credentials VLESS/Trojan/SS, le user/pass du proxy BYO et les URLs de souscription sont **directement lisibles**.

---

## 12. BYO proxy résidentiel (« Mon proxy »)

- **Parsing/stockage** : texte multi-ligne → `ConfigParserEngine` par ligne → si SOCKS5/HTTP, `importConfig` → persisté **comme tout profil (JSON clair)**. Le « pool » = sous-ensemble SOCKS5/HTTP des profils importés (état UI en mémoire, pas de store dédié).
- **Sonde « works-here »** (`ResidentialProxyProbe`) : hors-VPN, ouvre une connexion **à travers le proxy** vers `http://ip-api.com/json/…` → renvoie pays de sortie, IP publique, latence. **Fuite l'IP de sortie à un tiers (ip-api.com en HTTP, pas HTTPS)** avant tout tunnel.
- **Activation** : sondes **séquentielles** (le `Authenticator` JVM global empêche le parallèle), classées par latence, la meilleure devient l'outbound Xray (`createProxyOutbound` → `socks`/`http`) routé via FULL_TUNNEL. Switch manuel « Basculer » ; « Réessayer » re-sonde.
- **Limites** : sondage strictement série (lent sur gros pool) ; cible HTTP non-HTTPS ; credentials en clair.

---

## 13. Compatibilité OEM

- **Battery optimization** : détection (`logBatteryOptimizationState`) + demande (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` depuis Réglages techniques, fallback écran réglages). Permission déclarée.
- **Foreground service** : type **`specialUse`** (sous-type `vpn_core_logic_tunneling`), **pas** le type `vpn` (bien que `FOREGROUND_SERVICE_VPN` soit déclaré).
- **Autostart/boot** : receiver `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` mais **no-op** ; aucun handling vendor-spécifique (Xiaomi autostart, etc.).
- **Crash tun2socks Xiaomi/Redmi** : **aucun traitement OEM-spécifique dans main.** tun2socks tourne **in-process (JNI)** ; seules mitigations génériques (garde fd tun, gate réseau pré-vol, `runCatching` qui ne peut PAS attraper un SIGSEGV natif). `CrashReporter` = **stub logcat** (Crashlytics-NDK non câblé). Pas de process `:vpn` séparé. → L'isolation de process / fallback LOCAL_PROXY n'existe **que sur la branche non poussée**, pas dans main.

---

## 14. Permissions système

**Déclarées** (`AndroidManifest.xml`) : `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_VPN`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `allowBackup=false`. Service `BIND_VPN_SERVICE`, `exported=false`. Receiver **`exported=true`**. Pas de `<queries>`.

**Demandées au runtime** : consentement VPN (`VpnService.prepare()`, gated FULL_TUNNEL) ; `POST_NOTIFICATIONS` (Tiramisu+) ; battery optimization (initié par l'utilisateur).

---

## 15. Valeurs hardcodées / mesurées

MTU 1280 · backoff reconnect `[1,3,5,10,30]s` (max 5 ; BYO 2) · preuve santé `delay 1000ms` · settle Xray 600ms / tun2socks 200ms · poll vie 500ms · poll trafic 1000ms · heartbeat state 2000ms · sonde trafic timeout 1200ms ×2 · stall 15000ms (BYO 8000ms) · ports SOCKS 10808 / HTTP 10809 · DNS Cloudflare+Google · tun2socks tuning (`tcp-buffer 65536`, `udp-recv 524288`, `connect-timeout 10000`, etc.).

---

## 16. Synthèse impartiale — limites & code mort

**Capacités réelles** : VPN full-tunnel Android via Xray-core (process) + tun2socks (JNI) ; protocoles **VLESS/VMESS/Trojan/Shadowsocks** (TCP/WS effectifs) + **SOCKS5 BYO proxy résidentiel** ; import multi-format + souscriptions ; sélection serveur **heuristique** (opt-in) ; reconnexion/sticky/handoff réseau robustes avec preuve de trafic ; entitlements backend-pilotés.

**Limites avérées** :
1. **Stockage credentials en clair** (aucun chiffrement) — risque principal.
2. **Transports réels limités à TCP/WS** pour VLESS/VMESS (gRPC/HTTP2/Reality-complet/KCP/QUIC/XTLS non émis ou dégradés).
3. **Routing rules vide** → pas de split/bypass geo.
4. **Quota non appliqué côté client** (seulement via poll backend, différé).
5. **Pas d'auth token backend** (identité = userNumber+deviceId).
6. **Aucune isolation OEM** ; tun2socks in-process → crash natif non rattrapable (Xiaomi/Redmi).
7. **Sonde proxy fuite l'IP de sortie** à ip-api.com en HTTP.
8. **« IA »** = heuristique déterministe, opt-in, ne change de serveur que si activée.
9. **HTTP-proxy** câblé mais inatteignable.

**Code mort / inerte** : mode LOCAL_PROXY (`startLocalProxy` sans appelant) · fallback proxy (`PROXY_RESCUABLE_CAUSES` vide) · `Tun2SocksProcessBridge` (exécutable, non câblé) · schémas runtime Trojan/SS « natifs » (ignorés, fallback Xray) · `data/api/ApiService.kt` doublon · endpoints `startTrial`/`importSubscription`/`activateCode`/`createOrder` · 4 `DisconnectCause` jamais produits · boot auto-connect no-op.

*Non vérifié ici (analyse statique only)* : comportement réel du backend, perfs réseau mesurées en conditions réelles, rendu device des animations.
