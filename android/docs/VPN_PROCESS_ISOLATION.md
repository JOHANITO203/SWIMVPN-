# Design — isoler le moteur VPN dans un process `:vpn` (anti-crash OEM)

**Statut : design / NON implémenté.** Ce changement doit être **validé on-device** (il touche le
modèle de process + l'IPC d'état) ; il n'a délibérément pas été flippé à l'aveugle.

## Problème
`SwimVpnService` tourne dans le **process principal** (pas d'`android:process` dans le manifest).
Le tun2socks est chargé **en JNI in-process** (`Tun2SocksNativeBridge.nativeStart`,
`System.loadLibrary` dans le même process). Donc un **crash natif** (SIGSEGV/SIGABRT) dans
`libhev-socks5-tunnel` **tue toute l'application** → symptôme « SWIMVPN+ s'arrête systématiquement »
observé sur Xiaomi/Redmi et certains Samsung milieu de gamme.

Un `try/catch` JVM **ne peut pas** intercepter un signal natif. Les seules vraies parades :
1. **Isoler le service VPN (+ le JNI tun2socks) dans un process `:vpn`** → un crash natif tue le
   process VPN, **pas l'UI** ; l'app survit, détecte la mort du service, et peut basculer en
   LOCAL_PROXY (cf. `TunnelFallbackPolicy`) ou re-tenter proprement.
2. Crashlytics-NDK pour **savoir** ce qui crashe (cf. `CRASHLYTICS_SETUP.md`).

> Note : Xray, lui, est déjà un **process exec'd séparé** (binaire) → son crash ne tue pas l'app.
> Le risque in-process est **uniquement** le JNI tun2socks.

## Pourquoi un simple `android:process=":vpn"` casse l'app
Inventaire de l'état **partagé** entre le service et l'UI (tous des singletons **process-local**) :

| Composant | Type | Rôle | Effet en process séparé |
|---|---|---|---|
| `VpnManager` | `object` + `MutableStateFlow` (state, runtimeStatus, bytesIn/out, error, metrics) | Le service **écrit**, le ViewModel **lit/collecte** en live | 🔴 Instances distinctes → **l'UI ne voit plus les transitions** (CONNECTING→CONNECTED, bytes, erreurs). Régression majeure de l'écran d'accueil. |
| `RuntimeStateStore` | `object` + `SharedPreferences(MODE_PRIVATE)` | Snapshot pour survie au redémarrage du service | 🔴 `MODE_PRIVATE` **non multi-process** (MODE_MULTI_PROCESS déprécié/non fiable) → pas un canal d'état fiable inter-process tel quel. |
| `ServerScoreStore` | SharedPreferences | Scores adaptatifs (écrits surtout par le ViewModel) | 🟡 Le service en lit peu ; à vérifier qui écrit quoi. |
| `PreferencesManager` / `ConfigRepository` | DataStore | Prefs / profils | 🟡 DataStore **n'est pas multi-process** par défaut (corruption possible si 2 process écrivent). |
| `AdaptiveEventLogger` | Log stateless | Logs | 🟢 OK. |

**Conclusion** : flipper `android:process` sans canal d'état inter-process **casse l'UI de connexion**.

## Options
### A. Bound service + canal d'état IPC (recommandé)
- `SwimVpnService` (en `:vpn`) expose un `Messenger`/AIDL ; l'UI (process principal) **bind** et
  reçoit un flux d'updates (status, mode, bytes, error) → alimente un `VpnManager` **côté UI**.
- Le service push les updates via le canal au lieu (ou en plus) d'écrire le `VpnManager` local.
- **Avantage** : isolation crash complète + état live correct. **Coût** : AIDL/Messenger + sérialisation des updates + gestion bind/rebind (reconnexion au service après un crash).

### B. Store d'état multi-process observable
- Remplacer le canal d'état par un **ContentProvider** (multi-process natif) OU un fichier +
  `FileObserver` OU `MultiProcessDataStore`. Les deux process lisent/écrivent ; l'UI observe.
- **Avantage** : plus simple que l'AIDL. **Coût** : latence/polling, cohérence, et il faut router
  *toutes* les sources d'état (bytes haute fréquence) — moins adapté au temps réel que A.

### C. Statu quo + fallback proxy + Crashlytics (déjà fait, partiel)
- On garde l'in-process mais : `TunnelFallbackPolicy` rattrape les échecs **catchables** ; Crashlytics
  capturera les SIGSEGV pour les corriger à la source.
- **Limite** : ne protège PAS d'un SIGSEGV in-process (l'app crashe quand même). À garder comme
  filet en attendant A.

## Recommandation
1. **Court terme (déjà livré sur cette branche)** : fallback LOCAL_PROXY + Crashlytics-NDK (groundwork) → diagnostiquer + dégrader gracieusement les cas catchables.
2. **Moyen terme** : implémenter **Option A** (service `:vpn` + Messenger/AIDL + `VpnManager` côté UI alimenté par le canal). Migrer `RuntimeStateStore` hors `MODE_PRIVATE` si on veut aussi un snapshot inter-process.

## Esquisse de migration (Option A)
1. Manifest : `android:process=":vpn"` sur `SwimVpnService`.
2. Définir le contrat IPC (AIDL `IVpnStateCallback` : onStatus, onMetrics, onError) + `Messenger` ou `IVpnControl` (connect/stop/setMode).
3. Côté service : pousser chaque update `VpnManager` (local au :vpn) vers les callbacks bindés.
4. Côté UI : un `VpnServiceConnection` qui bind, reçoit les updates, et alimente le `VpnManager` du process UI (les call sites Compose restent inchangés).
5. Gérer : rebind après crash du :vpn (le `ServiceConnection.onServiceDisconnected` → marquer FAILED/RECONNECTING + rebind), démarrage foreground depuis :vpn, et le partage DataStore (n'écrire les prefs que depuis l'UI).
6. Vérifier les autres singletons (`ServerScoreStore` : décider quel process écrit).

## Plan de test ON-DEVICE (indispensable avant merge)
- T0 : connect Tunnel → l'UI affiche bien CONNECTING→CONNECTED + bytes live (preuve que l'IPC marche).
- T1 : kill du process `:vpn` (`adb shell am kill ... :vpn` ou crash simulé) → **l'UI ne crashe pas**, passe en FAILED/RECONNECTING, rebind OK.
- T2 : reproduire un device « killer » (MIUI) → vérifier que le crash natif tue `:vpn` seul + fallback proxy.
- T3 : déconnexion/arrêt manuel propre, pas de fuite de process.
- T4 : non-régression complète des états (NO_NETWORK, UNSTABLE, revoke) déjà validés.
