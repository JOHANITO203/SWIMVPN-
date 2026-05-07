# Rapport Android VPN et Parser Fournisseur - SWIMVPN

Date: 2026-05-07  
Portee: Android VPN runtime, foreground service, reconnexion, observabilite, parser de subscriptions fournisseur  
Statut de deploiement: PARTIEL

## 1. Resume Executif

SWIMVPN disposait deja d'une base Android fonctionnelle: `VpnService`, foreground service, Xray, tun2socks, import de configs et parser multi-format. La faiblesse principale n'etait pas une absence totale de tunnel, mais une stabilite insuffisante en conditions reelles: changement reseau, fermeture UI, crash moteur, economie batterie, et manque de logs causaux.

Le travail realise renforce la stabilite sans refactor massif:

- ajout d'etats runtime plus precis;
- ajout de causes de deconnexion persistables;
- callback reseau cote service;
- reconnexion bornee cote service;
- logs exploitables et redactes;
- detection de battery optimization;
- extension du parser pour formats fournisseur supplementaires;
- tests unitaires parser/adaptive;
- build Android debug valide.

La validation locale est positive, mais la readiness production reste partielle tant qu'un test reel appareil/logcat n'a pas confirme les causes de deconnexion.

## 2. Diagnostic Stabilite VPN

### Architecture actuelle inspectee

Fichiers critiques:

- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/VpnManager.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeStateStore.kt`
- `android/app/src/main/java/com/swimvpn/app/runtime/XrayProcessBridge.kt`
- `android/app/src/main/java/com/swimvpn/app/runtime/Tun2SocksNativeBridge.kt`
- `android/app/src/main/java/com/swimvpn/app/runtime/Tun2SocksRuntimeFilePreparer.kt`
- `android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt`
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
- `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`
- `android/app/src/main/java/com/swimvpn/app/AutoConnectBootReceiver.kt`

### Constats principaux

1. Le service etait bien demarre en foreground, mais retournait `START_NOT_STICKY`.
2. Les etats runtime etaient trop limites: `IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `FAILED`.
3. La reconnexion dependait surtout du `MainViewModel`, donc de l'UI.
4. Il n'y avait pas de `ConnectivityManager.NetworkCallback`.
5. Le monitoring verifiait surtout si Xray/tun2socks etaient vivants, pas si le reseau avait change.
6. Les logs ne produisaient pas toujours une cause lisible de deconnexion.
7. Le contrat de propriete du file descriptor TUN avec tun2socks JNI reste un risque natif important.

## 3. Causes Probables des Deconnexions

Causes les plus probables, par niveau de risque:

1. Changement reseau Wi-Fi/mobile sans re-arm propre des sockets.
2. Service tue par Android/OEM, puis non restaure car `START_NOT_STICKY`.
3. Crash ou sortie de Xray/tun2socks detectee trop tard ou sans reconnect service.
4. TUN fd potentiellement mal gere entre `ParcelFileDescriptor` Android et JNI natif.
5. Battery optimization/OEM policy qui restreint le service en arriere-plan.
6. Etat UI devenu stale car `RuntimeStateStore` ne prouve pas l'etat reel du service.
7. Readiness trop optimiste: service marque `RUNNING` apres delais fixes et process alive.

## 4. Correctifs Appliques

### Etats runtime

Ajoutes:

- `RECONNECTING`
- `DEGRADED`
- `STOPPED_BY_USER`

Ces etats permettent de distinguer:

- connexion active;
- reseau degrade;
- reconnexion en cours;
- arret manuel;
- erreur systeme/moteur.

### Causes de deconnexion

Ajoutees:

- `USER_STOPPED`
- `NETWORK_LOST`
- `SERVER_UNREACHABLE`
- `DNS_FAILURE`
- `HANDSHAKE_FAILURE`
- `ENGINE_CRASH`
- `SERVICE_KILLED`
- `BATTERY_RESTRICTION`
- `CONFIG_INVALID`
- `UNKNOWN`

Ces causes sont persistables via `RuntimeStateStore`.

### Foreground service

Changements:

- `SwimVpnService` retourne maintenant `START_STICKY`.
- L'arret manuel mappe vers `STOPPED_BY_USER`.
- Le service loggue `foreground_service_started`.
- Le service conserve une session active locale pour pouvoir retenter la meme session.

Important: la reconnexion service ne recupere pas de nouveaux serveurs premium et ne contourne pas l'entitlement backend.

### Network monitoring

Ajout d'un `ConnectivityManager.NetworkCallback` dans `SwimVpnService`.

Evenements ajoutes:

- `network_lost`
- `network_available`

Comportement:

- sur perte reseau, passage en `DEGRADED`;
- sur retour reseau, planification d'une reconnexion si la session n'a pas ete arretee par l'utilisateur;
- appel a `setUnderlyingNetworks` quand possible.

### Reconnexion bornee

Backoff ajoute:

- 1s
- 3s
- 5s
- 10s
- 30s

Limite:

- 5 tentatives maximum.

Regles:

- si `STOPPED_BY_USER`, pas de reconnexion;
- si pas de session active, pas de reconnexion;
- si trop d'echecs, passage en failure;
- la reconnexion redemarre uniquement le payload runtime deja actif.

### Observabilite

Logs ajoutes ou renforces:

- `vpn_connect_requested`
- `vpn_service_started`
- `foreground_service_started`
- `engine_started`
- `tunnel_started`
- `network_lost`
- `network_available`
- `reconnect_scheduled`
- `reconnect_started`
- `reconnect_failed`
- `engine_crashed`
- `service_destroyed`
- `stopped_by_user`
- `stopped_by_system`
- `battery_optimization_detected`

Les logs passent par une redaction simple:

- UUID masques partiellement;
- valeurs `password`, `token`, `uuid`, `id` en query masquees.

## 5. Diagnostic Parser Actuel

### Formats deja supportes avant extension

Le parser Android supportait deja:

- `vless://`
- VLESS Reality
- VLESS TLS
- VMess base64 JSON
- `trojan://`
- `ss://`
- JSON Xray/V2Ray outbounds pour protocoles supportes
- HTTP/HTTPS subscription URL
- Base64 subscription
- Base64 nested
- Base64 URL-safe
- URL encoded payload
- texte brut multi-lignes
- emojis
- russe / UTF-8
- metadata trafic
- expiration texte
- Happ `add` wrapper

### Manques identifies

Manques ou limites:

- Clash YAML non extrait en premiere classe;
- sing-box JSON non converti vers profils supportes;
- champs normalises incomplets (`alpn`, `hostHeader`, `serviceName`, etc.);
- Hysteria/Hysteria2/TUIC reconnus mais non importables;
- pas de fonction explicite `parseUnknownProviderSubscription`;
- nouveaux liens fournisseur sans sample exact non validables completement.

## 6. Correctifs Parser Appliques

### Champs normalises ajoutes

Dans `ParsedVpnProfile`:

- `encryption`
- `alpn`
- `hostHeader`
- `serviceName`
- `spiderX`
- `allowInsecure`

### Extraction Clash YAML

Ajout d'une extraction simple des `proxies:` Clash pour protocoles deja supportes:

- VLESS
- Trojan
- Shadowsocks

Les entrees sont converties vers des liens supportes puis repassent par le parser canonique.

### Extraction sing-box JSON

Ajout d'une conversion pour outbounds sing-box supportes:

- `type: vless`
- `type: trojan`
- `type: shadowsocks`

Les details TLS/Reality, SNI, fingerprint, public key et short ID sont preserves quand presents.

### Unknown provider

Ajout:

```kotlin
SubscriptionParser.parseUnknownProviderSubscription(raw: String)
```

Comportement:

- tente le pipeline existant: URL decode, Base64, JSON, lignes, protocoles;
- retourne les profils valides si trouves;
- sinon retourne warnings avec `Unsupported subscription format`;
- indique le scheme detecte si possible;
- ne crash pas l'app.

### Formats modernes

Hysteria/Hysteria2/TUIC restent volontairement non importables.

Raison: parser un lien ne suffit pas. Il faut un moteur runtime capable de lancer ces protocoles. Les marquer importables maintenant serait dangereux et trompeur.

## 7. Tests Ajoutes ou Renforces

### Parser

Tests ajoutes:

1. Base64 subscription avec padding manquant.
2. Clash YAML avec VLESS WS TLS et Trojan TLS.
3. sing-box JSON avec VLESS Reality.
4. Unknown provider / Hysteria2 retourne warnings utiles sans crash.

Tests existants deja couverts:

- VLESS Reality;
- VLESS TLS WS;
- VMess base64 JSON;
- Trojan TLS;
- Shadowsocks;
- subscription Base64 multi-lignes;
- emojis;
- russe UTF-8;
- metadata trafic;
- expiration date texte;
- JSON array de VLESS outbounds;
- payloads invalides partiellement importables.

### Adaptive reconnect

Backoff aligne sur:

- 1s
- 3s
- 5s
- 10s
- 30s

## 8. Verification Executee

Commandes executees:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionParserTest --tests com.swimvpn.app.adaptive.AdaptiveDecisionAgentTest
```

Resultat: PASS apres correction d'une erreur Kotlin nullable.

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

Resultat: PASS.

```powershell
cd android
.\gradlew.bat assembleDebug
```

Resultat: PASS.

Warnings observes:

- warnings Kotlin existants (`currentState` non utilise, shadowing `context`, parametre `url` non utilise);
- warning Java target/source 8 avec JDK 21;
- warning CMake SDK XML version;
- warnings Gradle deprecated features.

Ces warnings ne bloquent pas le build.

## 9. Fichiers Modifies

Code Android:

- `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeStateStore.kt`
- `android/app/src/main/java/com/swimvpn/app/vpn/VpnManager.kt`
- `android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt`

Parser:

- `android/app/src/main/java/com/swimvpn/app/config/subscriptionparser/SubscriptionParser.kt`
- `android/app/src/main/java/com/swimvpn/app/config/subscriptionparser/SubscriptionParserModels.kt`
- `android/app/src/main/java/com/swimvpn/app/config/subscriptionparser/SubscriptionPayloadDecoder.kt`

Tests:

- `android/app/src/test/java/com/swimvpn/app/config/SubscriptionParserTest.kt`
- `android/app/src/test/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgentTest.kt`

Notes:

- `WORKLOG.md`
- `DECISIONS.md`
- `TODO.md`
- `android/WORKLOG.md`
- `android/TODO.md`

## 10. Plan QA Android Reel

Tests a faire sur appareil reel:

1. Demarrer VPN en mode Tunnel.
2. Verifier notification persistante.
3. Verrouiller l'ecran 10 a 30 minutes.
4. Changer Wi-Fi vers 4G/5G.
5. Changer 4G/5G vers Wi-Fi.
6. Couper internet temporairement.
7. Verifier `network_lost`, `network_available`, `reconnect_scheduled`, `reconnect_started`.
8. Verifier que l'arret manuel produit `stopped_by_user` et aucune reconnexion.
9. Forcer crash Xray/tun2socks si possible et verifier `engine_crashed`.
10. Tester sur OEM agressif: Xiaomi, Huawei, Oppo, Vivo, Realme, Samsung.
11. Verifier que l'app n'affiche pas de secrets complets.
12. Importer une subscription Clash YAML.
13. Importer une subscription sing-box JSON.
14. Importer un lien Hysteria2/TUIC et verifier warning propre, pas crash.
15. Tester APK debug genere par `assembleDebug`.

## 11. Plan Debug Nouveau Fournisseur

Quand le nouveau lien exact est disponible:

1. Coller le lien dans l'import manuel.
2. Capturer le rapport warnings parser.
3. Identifier le scheme: `vless`, `vmess`, `trojan`, `ss`, `hysteria2`, `tuic`, `http`, `https`, JSON, YAML.
4. Verifier s'il s'agit d'un carrier:
   - URL encoded;
   - Base64 standard;
   - Base64 URL-safe;
   - Base64 sans padding;
   - JSON;
   - YAML;
   - texte multi-lignes.
5. Verifier si au moins un node supporte un runtime actuel.
6. Si le lien est Hysteria/TUIC:
   - ne pas l'importer comme runnable;
   - creer un parser dedie seulement apres choix du moteur runtime.
7. Si le lien est un format custom chiffre:
   - ne pas tenter de "dechiffrer" sans spec/cle fournisseur;
   - retourner `Unsupported subscription format`.

## 12. Risques Restants

Risques techniques:

- propriete du fd TUN entre Android et tun2socks JNI non prouvee;
- readiness encore basee partiellement sur process alive et delais courts;
- pas de probe data-plane complet;
- reconnexion service non testee sur appareil reel;
- battery optimization UI non ajoutee;
- boot restore toujours a revalider selon entitlement;
- Hysteria/TUIC non supportes runtime.

Risques produit:

- certains fournisseurs peuvent fournir des subscriptions chiffrees proprietaires;
- Clash YAML supporte ici une forme simple, pas tout le standard Clash;
- sing-box JSON supporte les outbounds courants supportes, pas toute la spec;
- les warnings doivent etre visibles en UI pour accelerer le support.

## 13. Readiness

Build readiness locale: OUI  
Unit tests Android: OUI  
Debug APK build: OUI  
Production deploy readiness: PARTIEL

Raison du statut PARTIEL:

- la compilation et les tests passent;
- la logique de reconnexion est plus robuste;
- le parser couvre plus de formats;
- mais la stabilite VPN doit etre confirmee avec logcat reel, changement reseau reel, ecran eteint, et comportement OEM batterie.

## 14. Prochaines Actions Recommandees

Priorite 1:

- capturer logcat sur deconnexions reelles;
- confirmer fd ownership tun2socks JNI;
- tester handoff Wi-Fi/mobile;
- tester ecran eteint + battery optimization.

Priorite 2:

- ajouter ecran interne debug VPN;
- afficher cause derniere deconnexion;
- afficher compte de reconnects;
- bouton copier rapport logs redacte.

Priorite 3:

- ajouter UI "Keep SWIMVPN running";
- ouvrir settings battery optimization;
- guides OEM apres validation.

Priorite 4:

- concevoir support runtime Hysteria/TUIC si ces formats deviennent obligatoires.

