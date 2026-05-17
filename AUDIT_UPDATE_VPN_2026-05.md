# Audit programme pour la mise a jour VPN SWIMVPN

Date: 2026-05-18
Portee: `main` actuel uniquement, sans reprise implicite de PR externe.
Statut: programme d'audit, pas plan d'implementation.

## 1. Objectif

Ce document sert a programmer un audit fiable avant de lancer une mise a jour VPN portant sur:

- stabilite runtime;
- vitesse et latence;
- parseurs et compatibilite fournisseur;
- camouflage / discretion reseau;
- obfuscation quand elle est reellement supportee;
- coherence backend Android.

L'audit doit d'abord proteger l'intention existante du repo. Les couches qui se ressemblent ne doivent pas etre considerees comme des duplications inutiles avant verification de leur responsabilite.

## 2. Sources de verite a lire avant toute modification

Sources obligatoires:

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/SOURCE_OF_TRUTH.md`
- `ANDROID_EXECUTION_STATUS.md`
- `docs/AUDIT_SYSTEME_INSTALLE_SWIMVPN.md`
- `docs/RAPPORT_ANDROID_VPN_PARSER_2026-05-07.md`
- `docs/PLAN_MISE_A_JOUR_SECURITE_CAMOUFLAGE.md`
- `DECISIONS.md`
- `WORKLOG.md`
- `TODO.md`

Regle d'audit: si un comportement semble duplique, chercher d'abord la decision ou le worklog qui explique pourquoi il existe.

## 3. Responsabilites connues

### PostgreSQL et Prisma

Source de verite metier pour customers, plans, orders, inventory items, assignments, delivery state et admin sessions/events.

Cette couche ne doit pas etre contournee par Android.

### Backend customer/order/store

Responsable de entitlement, trial/subscription/freemium, validation device, exposition ou non des configs premium, et tracking commande -> paiement -> assignment -> delivery.

Regle: un utilisateur expire peut entrer dans l'app shell et utiliser les configs importees, mais ne doit pas recevoir de serveurs/configs premium backend.

### Backend vpn-config-engine-service

Responsable de l'ingest backend, parse/validate/normalize/classify/preview pour inventory/admin, preparation d'un payload runtime backend quand utile, preservation du raw config fournisseur, et chiffrement/resolution `swimvpn://crypt1`.

Point important: ce service ne remplace pas le parser/runtime Android. Il est actuellement plus limite que le parser Android par design operationnel.

### Android parser/import

Responsable d'importer des configs locales ou subscriptions, extraire des groupes et nodes, preserver raw config, produire un profil canonique exploitable par l'app, et signaler les formats reconnus mais non supportes runtime.

Android est l'autorite court terme pour la preparation runtime reelle.

### Android runtime

Responsable de preparation Xray, lancement Xray, handoff SOCKS local, full tunnel via tun2socks JNI, etats runtime, monitoring process/data plane/reseau sous-jacent, et snapshot runtime persistant.

## 4. Separations qui semblent dupliquees mais sont intentionnelles

### ConfigParserEngine vs SubscriptionParser

`SubscriptionParser` traite les containers, subscriptions, groupes, payloads provider, Base64, wrappers, metadata trafic/expiration.

`ConfigParserEngine` traite un node ou document cible vers un `SwimVpnProfile` canonique.

Audit a faire: verifier que les transformations de `SubscriptionParser` repassent bien par le parser canonique et ne creent pas une deuxieme verite runtime.

### ConfigNormalizationEngine vs TunnelRuntimeAdapter

`ConfigNormalizationEngine` valide, enrichit et signale les warnings de compatibilite.

`TunnelRuntimeAdapter` prepare le document runtime Xray/tun2socks.

Audit a faire: verifier si des restes de generation runtime dans la normalisation sont encore utilises, historiques, ou dangereux. Ne pas supprimer avant preuve.

### Backend parser vs Android parser

Le backend parser sert inventory/admin/metadata. Android parser sert import/runtime.

Audit a faire: identifier les formats qui doivent etre alignes maintenant parce qu'ils affectent inventory/admin, et ceux qui peuvent rester Android-only court terme.

## 5. Lots d'audit

### Lot A - Audit documentaire

But: comprendre les decisions avant de modifier.

Questions:

- Quelle decision justifie chaque couche?
- Quels TODO sont encore actifs?
- Quels risques sont deja connus?
- Quels chemins ont ete valides sur appareil reel?

Sortie attendue:

- carte des responsabilites;
- liste des separations volontaires;
- liste des zones dont l'intention n'est pas documentee.

### Lot B - Audit parser Android

But: savoir ce que l'app sait vraiment importer et transformer.

Formats a auditer:

- VLESS;
- VLESS Reality;
- VMess;
- Trojan;
- Shadowsocks;
- JSON Xray;
- JSON V2Ray;
- Clash YAML;
- sing-box JSON;
- Happ wrappers;
- subscriptions Base64/URL-safe/multilignes;
- Hysteria/TUIC/WireGuard/SOCKS reconnus mais non connectables.

Matrice attendue:

| Format | Detecte | Parse | Normalize | Preserve raw | Runtime compatible | Warning utilisateur |
| --- | --- | --- | --- | --- | --- | --- |

Go/no-go:

- une config reconnue mais non executable doit etre refusee clairement;
- un format supporte ne doit pas perdre `sni`, `pbk`, `sid`, `flow`, `fp`, `alpn`, `host`, `path`, `serviceName`, password ou method;
- raw config intact obligatoire.

### Lot C - Audit runtime Android

But: savoir si un profil parse devient vraiment une connexion utilisable.

Chemins a verifier:

- `LOCAL_PROXY`;
- `FULL_TUNNEL`;
- Xray process alive;
- readiness du SOCKS local;
- tun2socks JNI;
- fd TUN;
- DNS;
- reconnect borne;
- changement Wi-Fi/mobile;
- screen-off/battery optimization;
- stop utilisateur vs crash moteur;
- snapshot persistant vs etat UI.

Matrice attendue:

| Etape | Fichier | Signal actuel | Risque faux succes | Verification |
| --- | --- | --- | --- | --- |

Go/no-go:

- ne pas afficher RUNNING si Xray est vivant mais SOCKS inutilisable;
- ne pas relancer une config premium apres expiration sans nouveau check backend;
- ne pas masquer une erreur runtime sous un etat connecte.

### Lot D - Audit backend VPN / inventory

But: verifier la frontiere backend sans exiger qu'elle remplace Android.

Points a auditer:

- `vpn-config-engine-service` parse/preview;
- crypt1 generate/resolve;
- `store-engine-service` exposition serveurs;
- entitlement active/expired;
- device binding;
- preservation raw config;
- SSRF healthcheck;
- parser metadata utile admin/inventory.

Go/no-go:

- expired users ne recoivent pas de raw premium backend;
- imported configs restent locales/freemium;
- backend ne doit pas inventer d'etat ACTIVE;
- backend parser doit rester suffisant pour les decisions inventory/admin qu'il prend.

### Lot E - Audit camouflage / obfuscation

But: separer analyse, selection et vrai support runtime.

Categories:

1. Scoring camouflage: analyser une config existante.
2. Selection automatique: choisir le meilleur node compatible.
3. Transformation runtime sure: mapper les parametres deja presents.
4. Obfuscation reelle: seulement si serveur et runtime supportent le mecanisme.

Interdits:

- inventer `pbk`, `sid`, UUID, password;
- transformer TCP nu en Reality;
- modifier le raw config;
- promettre Shadowsocks plugin si runtime ne l'execute pas;
- traiter Hysteria/TUIC/WireGuard comme connectables sans moteur.

Signaux a scorer:

- Reality complet;
- TLS coherent;
- SNI present;
- uTLS fingerprint;
- `flow=xtls-rprx-vision`;
- transport `grpc`, `ws`, `http2`, `xhttp`, `httpupgrade`;
- `allowInsecure=true`;
- metadata incomplete;
- historique d'echec runtime.

### Lot F - Audit performance

But: optimiser avec preuves.

Mesures a collecter:

- latence TCP serveur;
- temps de parsing/import;
- temps generation runtime;
- temps lancement Xray;
- temps readiness SOCKS;
- temps lancement tun2socks;
- debit `LOCAL_PROXY` vs `FULL_TUNNEL`;
- DNS failures;
- reconnect count;
- crash Xray/tun2socks;
- usage bytes.

Go/no-go:

- ne pas tuner avant de savoir si le probleme vient du serveur, du chemin reseau, de Xray, de tun2socks ou du parser;
- ne pas confondre ping TCP et sante VPN complete.

### Lot G - QA appareil reel

Scenarios minimum:

1. Import VLESS Reality complet.
2. Import VLESS Reality incomplet.
3. Import VMess gRPC.
4. Import Trojan TLS.
5. Import Shadowsocks simple.
6. Import Shadowsocks plugin.
7. Import JSON Xray.
8. Import subscription multi-nodes.
9. Selection node importe puis connexion.
10. Connexion premium backend puis expiration.
11. Freemium avec config importee.
12. Wi-Fi vers mobile pendant tunnel.
13. Screen-off long.
14. Stop utilisateur.
15. Crash ou sortie Xray/tun2socks si reproductible.

## 6. Livrables de l'audit

Livrable principal:

- `AUDIT_UPDATE_VPN_2026-05.md`

Livrables possibles apres execution de l'audit:

- `VPN_CONFIG_CORPUS_NOTES.md` si un corpus anonyme est constitue;
- `ANDROID_RUNTIME_QA_YYYY-MM-DD.md` apres test appareil;
- `DECISIONS.md` seulement si une decision architecturale est prise;
- `TODO.md` pour actions concretes restantes.

## 7. Ordre recommande apres audit

Ordre cible, a confirmer par preuves:

1. Parser coverage et verite import.
2. Compatibilite runtime et erreurs claires.
3. Readiness/liveness runtime.
4. QA appareil reel.
5. Performance avec mesures.
6. Backend parser alignment uniquement la ou inventory/admin en a besoin.
7. Camouflage/scoring/selection automatique.

## 8. Criteres de sortie

L'audit est considere exploitable quand:

- les responsabilites des couches sont documentees;
- les separations volontaires sont distinguees des vraies incoherences;
- une matrice parser/runtime existe;
- une matrice backend/Android existe;
- les risques freemium/entitlement sont explicitement couverts;
- les tests et builds de baseline sont notes;
- les prochains lots d'implementation sont classes par risque.

## 9. Decision actuelle

Aucune implementation runtime ne doit commencer avant execution du Lot A et production au minimum des matrices Lot B, Lot C et Lot D.

La prochaine mise a jour doit etre programmee comme une suite de petits lots verifies, pas comme un refactor global du VPN.
