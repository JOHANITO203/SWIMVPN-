# Audit du systeme installe SWIMVPN

Date: 2026-05-07

Update note: after reviewing the worklogs, the current code and recent worklogs are treated as the operational truth. Items previously described as "contradictions" should be read as documentation drift unless the code itself fails a real installed flow.

## 1. Conclusion courte

Le systeme installe est coherent dans son intention et reflete des ajustements terrain documentes dans les worklogs. La source de verite metier est bien PostgreSQL via Prisma, le backend expose un gateway public, Android garde la responsabilite du runtime VPN et des imports locaux, et l'inventaire backend distribue des configs fournisseur preservees en brut.

La principale lecon de l'audit est celle-ci: certains endpoints semblent vulnerables si on les regarde seuls, mais ils sont aussi utilises par des flows Android de bootstrap, freemium, reprise d'etat, supervision premium et auto-connect. Les durcissements doivent donc etre faits par modes de reponse, migrations progressives ou nouveaux contrats, pas par verrouillage brutal.

Etat global:

- Backend entitlement: structure saine, mais quelques endpoints publics restent trop bavards.
- Android VPN runtime: nettement ameliore, avec foreground service, logs, reconnect borne et monitoring NOT_VPN.
- Parser Android: plus avance que le parser backend.
- Deploiement prod: plutot sain sur les ports internes si le root `docker-compose.yml` est bien celui utilise en prod.
- Confidentialite device: documentation/legal copy a realigner sur le code actuel ou a traiter plus tard par migration.
- Donnees locales Android: configs et auto-connect payload sont app-private mais non chiffres.

## 2. Sources de verite reelles

### PostgreSQL / Prisma

PostgreSQL est la source de verite pour:

- customers
- plans
- orders
- inventory_items
- order_assignments
- deliveries
- admins
- admin_sessions
- admin_events
- accounting_entries

Le schema actuel est dans `backend/prisma/schema.prisma`. Les services partagent la meme base via Prisma. Les frontends ne doivent pas inventer l'etat d'abonnement.

### Backend

Le backend decide:

- l'eligibilite trial
- l'etat entitlement
- l'exposition ou non des serveurs premium
- l'affectation de config fournisseur
- la revocation/cancel
- la verification device pour les actions sensibles
- la resolution `swimvpn://crypt1/...`

### Android

Android decide:

- l'experience app shell/freemium
- l'import local de configs
- la selection de serveur actif
- le runtime Xray/tun2socks
- l'auto-connect local apres revalidation
- la supervision runtime et usage reporting

Android ne doit pas accorder un acces premium backend par lui-meme.

### Raw VPN config

La config fournisseur brute est un asset critique. Elle doit rester preservee intacte. Les etapes valides sont:

- ingest
- parse
- validate
- normalize
- classify
- preview
- prepare runtime payload

Il ne faut pas muter silencieusement `raw_config`.

## 3. Architecture installee

### Gateway public

`gateway-service` est l'entree HTTP publique:

- `POST /api/v1/access/bootstrap`
- `POST /api/v1/access/trial`
- `POST /api/v1/access/trial/activate`
- `POST /api/v1/access/profile/complete`
- `GET /api/v1/access/:userNumber`
- `GET /api/v1/servers`
- `GET /api/v1/store/plans`
- `POST /api/v1/orders/checkout`
- `POST /api/v1/payments/crypto/webhook`
- `POST /api/v1/admin/login`
- routes admin protegees par `AdminGuard`

Le gateway ne doit pas acceder directement a la DB. Il route vers les microservices TCP.

Patch deja applique dans le worktree:

- rate limit local cible sur `admin/login`, `access/:userNumber`, `subscription/resolve-crypt`
- Swagger desactive par defaut en production sauf `GATEWAY_SWAGGER_ENABLED=true`
- CORS configurable par `GATEWAY_CORS_ORIGINS`

### Customer/order service

`customer-order-service` est le coeur metier:

- cree ou retrouve le customer par device
- gere trial
- gere checkout
- gere payment webhook Crypto Pay
- gere manual card approval/reject
- calcule `getProfile()`
- expose l'etat entitlement
- supervise usage reporting
- resolve crypt import avec verification customer/device/assignment

Point important: `getProfile()` est une fonction centrale appelee par beaucoup de flows internes. Elle ne doit pas etre verrouillee globalement sans option, sinon bootstrap, freemium ou refresh peuvent casser.

### Inventory/delivery service

`inventory-delivery-service`:

- importe les configs via `vpn-config-engine-service`
- cree `InventoryItem`
- choisit un item disponible/assignable avec transaction et `FOR UPDATE SKIP LOCKED`
- cree ou active `OrderAssignment`
- gere slots de revente, quotas fournisseur, statut health
- notifie admin et livraison post-achat
- recoit usage reporting via inventory pour mettre a jour usage et expirations

### Store service

`store-engine-service`:

- expose les plans actifs payants
- expose les serveurs backend uniquement si:
  - customer existe
  - `x-device-id` matche `Customer.device_id`
  - assignment active existe
  - config non expiree
  - quota non depasse
  - health non expired/disabled

C'est une frontiere securite importante: les hosts premium ne sont pas retournes si device ou entitlement ne match pas.

### VPN config engine backend

`vpn-config-engine-service`:

- parser backend plus limite que parser Android
- support principal: VLESS, Shadowsocks, URL subscription HTTP/HTTPS comme ressource fournisseur
- crypt1 AES-256-GCM
- healthcheck TCP

Patch deja applique dans le worktree:

- healthcheck bloque localhost/private/reserved/multicast/unresolvable avant socket

Limite: le parser backend n'a pas le meme niveau de support que le parser Android. C'est acceptable seulement si le backend gere surtout inventory metadata, pas le runtime final Android.

### Admin service

`admin-control-service`:

- login bcrypt
- JWT admin
- sessions admin en DB
- admin events
- import inventory
- revoke/move/retry fulfillment
- Telegram admin/support bots

Patch deja applique dans le worktree:

- `AdminSession.refresh_token_hash` stocke un fingerprint SHA-256 du JWT, plus le JWT en clair.

Consequence: les admins existants devront probablement se reconnecter apres deploy.

## 4. Flow Android installe

### Cold start / bootstrap

1. Android lit `Settings.Secure.ANDROID_ID` via `DeviceIdentityProvider`.
2. `MainViewModel.initApp()` appelle `POST /access/bootstrap` avec `deviceId`.
3. Backend `findOrCreateCustomerByDevice()` cree ou retrouve `Customer`.
4. Backend retourne:
   - `userNumber`
   - email/phone
   - trialEligible
   - profileCompletionRequired
   - hasActiveAccess
   - profile
5. Android sauvegarde `userNumber`.
6. Si profile complete, Android construit `Success`; sinon `TrialSetup`.

Implication: `bootstrapAccess` est le vrai endpoint d'entree securise par device. Il ne faut pas le remplacer par `GET /access/:userNumber`.

### Trial activation

1. Android envoie `userNumber`, `deviceId`, `email`, `phone`.
2. Backend verifie que `customer.device_id === deviceId`.
3. Backend verifie trial eligible par customer, device, email, phone.
4. Backend cree un order trial `TRIAL-*`, payment_ref `TRIAL:3D`.
5. Inventory tente fulfillment.
6. Si pas de stock: order peut devenir `PENDING_FULFILLMENT`, pas un echec utilisateur definitif.

### Freemium

Freemium est un mode legitime:

- app shell autorisee
- imports locaux autorises
- premium backend refuse
- pas de lockout total si trial/subscription expire

Android `buildSuccessState()` supporte ce modele: si pas premium, `getServers()` peut echouer ou retourner vide, mais l'app continue avec imported configs et plans.

### Premium backend

1. Android charge les serveurs via `GET /servers` avec `x-user-number` + `x-device-id`.
2. Store service verifie device + assignment + quotas + expiration.
3. Android selectionne un `ServerNode`.
4. Runtime config vient de `server.rawConfig` ou `profile.subscriptionUrl`.
5. Si config est remote subscription, Android fetch et parse.
6. `SwimVpnService` demarre Xray + tun2socks.

### Usage reporting

Pendant une connexion backend premium:

- Android mesure bytes via runtime/tun2socks.
- Envoie `POST /subscription/usage` avec `userNumber`, `deviceId`, `measuredUsedBytes`.
- Backend verifie device.
- Inventory met a jour usage monotone.
- Si quota/expiry termine, Android stoppe backend premium et revient standard.

### Auto-connect

Auto-connect est local mais revalide:

- Payload local stocke host/port/protocol/runtimeConfig/runtimeMode.
- Boot receiver ne demarre pas directement le VPN.
- Il attend que l'app bootstrap revalide l'acces.

C'est sain: pas de bypass entitlement au boot.

## 5. Android VPN runtime

`SwimVpnService` est declare:

- `android.permission.BIND_VPN_SERVICE`
- `android:exported=false`
- foreground service type special use
- notification foreground
- `START_STICKY`

Le service:

- appelle `startForeground` rapidement
- prepare Xray runtime
- etablit TUN en full tunnel
- demarre tun2socks JNI si disponible
- exclut l'app du tunnel via `addDisallowedApplication(packageName)`
- monitore Xray et tun2socks
- monitore le reseau sous-jacent avec `NET_CAPABILITY_NOT_VPN`
- reconnecte avec backoff 1s, 3s, 5s, 10s, 30s max 5 tentatives
- distingue stop user et stop systeme
- loggue battery optimization

Point deja corrige: le monitoring ne doit pas observer le VPN comme reseau par defaut, mais les underlays NOT_VPN.

Risques restants:

- donnees locales non chiffrees dans DataStore
- auto-connect payload contient runtime config brute
- besoin de test long screen-off et Wi-Fi/mobile handoff
- notification POST_NOTIFICATIONS peut manquer sur Android 13+, mais foreground existe quand meme; UX peut etre degradee

## 6. Parser / configs

Android a le parser le plus avance:

- VLESS
- VMess
- Trojan
- Shadowsocks
- subscriptions base64
- URL decode/base64 padding
- Clash YAML
- sing-box JSON
- JSON Xray/V2Ray
- metadata trafic/expiration
- provider cookies en memoire pour certains fournisseurs

Backend parser est plus restreint:

- VLESS
- Shadowsocks
- HTTP/HTTPS subscription comme ressource
- extraction primary config candidate
- metadata fournisseur

Documentation a retenir: Android prepare le runtime final; backend inventory fait surtout validation/metadata. Mais a terme, les deux parseurs devraient partager un modele ou au moins des tests de compatibilite.

## 7. Deploiement

Root `docker-compose.yml`:

- pas de `ports:` host
- seuls `landing-service` et `gateway-service` sont sur `dokploy-network`
- services internes + DB sur `swimvpn-private`
- Traefik labels uniquement gateway/landing

Ce modele est sain si c'est bien celui deploye en prod.

`backend/docker-compose.yml`:

- publie `5432:5432`
- publie `3000:3000`

Ce fichier est dangereux si utilise tel quel sur VPS public. Il doit etre dev-only ou binder sur `127.0.0.1`.

Patch futur sur compose prod:

- ajouter `traefik.enable=false` explicitement aux services prives
- ajouter `GATEWAY_CORS_ORIGINS` au gateway env

## 8. Documentation drift et points a realigner

### D1 - Identifiant device actuel a documenter et proteger

Decision produit actuelle: conserver le raw `Settings.Secure.ANDROID_ID` normalise comme identifiant operationnel device.

Realite:

- Android envoie raw `Settings.Secure.ANDROID_ID`
- backend stocke raw dans `Customer.device_id`
- cet identifiant sert a la continuite customer, l'anti-abus trial, les actions sensibles, l'exposition serveur premium, crypt1, cancellation et usage reporting

Risque: privacy/compliance seulement si la page publique promet autre chose que le code actuel, ou si l'identifiant est loggue/expose/fuite via DB.

Ne pas corriger cote Android vers un hash. Ce comportement est voulu pour coller aux besoins terrain actuels.

Correction documentaire immediate: decrire l'identifiant device actuel factuellement et renforcer les protections autour:

- ne pas exposer `device_id` en API publique
- ne pas logger le raw device id
- garder les checks device cote backend pour les actions sensibles
- proteger DB/backups/secrets/admin access
- maintenir la privacy copy alignee avec le code

### D2 - `GET /access/:userNumber` public plus large que necessaire

Realite:

- retourne profile sans runtime config
- peut retourner email/phone et metadata entitlement

Risque: enumeration PII par public id. Le code actuel fonctionne ainsi; la correction doit suivre les flows installes au lieu de verrouiller `getProfile()` globalement.

Piege: ne pas verrouiller `getProfile()` globalement, car c'est une fonction centrale interne.

Correctif recommande:

- deprecier ce GET dans Android si possible
- ou ajouter un mode public safe qui masque email/phone sans device proof
- garder bootstrap comme route principale

### D3 - Gateway DTOs faibles

Gateway utilise souvent `any`, donc `ValidationPipe` global est moins utile.

Correctif: typer progressivement les DTOs gateway avec les contrats existants. Ne pas tout convertir d'un coup.

### D4 - Backend parser moins capable qu'Android parser

Le backend peut rejeter ou mal classifier des ressources que Android sait utiliser.

Correctif: ne pas refactorer massivement; ajouter des tests fournisseur et aligner par formats prioritaires.

### D5 - Donnees locales Android non chiffrees

DataStore contient imported profiles et auto-connect payload.

Risque local: rooted device, debug/forensics, malware local.

Correctif: chiffrage progressif local configs + migration safe. A ne pas confondre avec obfuscation reseau.

## 9. Patchs vraiment surs maintenant

Ces patchs ont peu de risque metier:

1. Ajouter `traefik.enable=false` aux services prives du root compose.
2. Binder `backend/docker-compose.yml` sur `127.0.0.1` pour Postgres/gateway.
3. Ajouter `GATEWAY_CORS_ORIGINS` dans compose gateway env.
4. Masquer les details internes du public `/health` ou creer un `/health/details` admin.
5. Garder Swagger prod disabled par defaut.
6. Garder rate limit gateway.

## 10. Patchs a ne pas faire brutalement

1. Remplacer raw Android ID par hash uniquement cote Android.
2. Verrouiller `getProfile()` globalement par device.
3. Supprimer freemium ou forcer paywall global.
4. Modifier raw VPN config en DB.
5. Changer les bind TCP internes Nest de `0.0.0.0` a `127.0.0.1` dans Docker.
6. Activer minify/proguard agressif sans tester Xray/tun2socks/parser.

## 11. Ordre recommande

### Lot A - Release stable securisee

1. Compose hardening sans logique metier.
2. Public health minimal.
3. Swagger/rate-limit/CORS env verifies.
4. Android long-run QA: screen off, Wi-Fi/mobile handoff, logcat causes.
5. Release build signed.

### Lot B - Device identity protection

1. Documenter raw Android ID comme identifiant operationnel device.
2. Verifier qu'il n'est jamais expose dans les API publiques.
3. Verifier qu'il n'est jamais loggue en clair.
4. Verifier les backups/secrets/admin access autour de la DB.
5. Garder les checks device backend sur les actions sensibles.
6. Garder la privacy copy alignee sur ce modele.

### Lot C - Access profile privacy

1. Auditer usages Android de `GET /access/:userNumber`.
2. Remplacer les refreshs premium par endpoint device-bound ou bootstrap refresh.
3. Introduire `publicSafeProfile`.
4. Masquer email/phone sans proof.

### Lot D - Parser alignment

1. Garder Android parser comme runtime authority court terme.
2. Ajouter tests fournisseur communs.
3. Etendre backend parser seulement pour formats inventory prioritaires.

### Lot E - Local data encryption

1. Chiffrer configs locales et auto-connect payload.
2. Migration safe des anciennes prefs.
3. Tests de migration.

## 12. Reponse a la question "le code est securise ?"

Non, pas encore au sens production complete.

Mais il est beaucoup plus defendable qu'au depart:

- entitlement backend existe
- premium servers sont device-bound
- admin sessions ne doivent plus etre stockees en clair dans le worktree actuel
- healthcheck SSRF est bloque dans le worktree actuel
- foreground VPN/reconnect est bien plus robuste
- Docker prod isole correctement les services internes si root compose est utilise

Les risques ou dettes restantes pour une release plus defensive sont:

- protection/documentation du raw Android device identifier
- public profile lookup trop bavard
- CORS prod a configurer
- local configs non chiffrees
- QA Android longue duree encore necessaire

## 13. Checklist avant prochain patch

- Ne pas toucher `getProfile()` sans mode/option explicite.
- Ne pas changer `DeviceIdentityProvider` sans migration backend prealable.
- Ne pas changer Docker internal bind hosts.
- Ne pas supprimer le freemium.
- Ne pas exposer raw configs aux expired users.
- Toujours tester Android + backend quand le contrat API change.
