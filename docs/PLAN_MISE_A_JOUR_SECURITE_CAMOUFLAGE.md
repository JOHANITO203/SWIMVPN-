# Plan de mise a jour securite et camouflage reseau

Date: 2026-05-07

## Objectif

La prochaine mise a jour doit etre separee en deux rails de travail pour eviter de melanger les correctifs de securite urgents avec la nouvelle feature de camouflage reseau.

Le principe est simple:

- sortir maintenant une version stable, securisee et defendable en production;
- travailler en parallele, en cloud, sur la future feature de camouflage, discretion, vie privee et vitesse.

## Point important immediat

Le review de securite realise hier doit etre transmis maintenant.

Ce review servira de base pour prioriser les corrections avant la sortie de la version stable. Les points seront classes par gravite:

- critique;
- haute;
- moyenne;
- basse.

Les corrections prioritaires seront celles qui peuvent exposer:

- les configs VPN;
- les secrets;
- le backend;
- les utilisateurs;
- la stabilite du tunnel VPN;
- les donnees de production.

## Rail A: version stable et securisee maintenant

### Objectif

Sortir une version actuelle plus fiable, plus stable et plus defendable en production.

### Etapes

1. Traiter le review de securite
   - Lire les resultats du review.
   - Identifier les fichiers et flux touches.
   - Classer les risques par gravite.
   - Corriger en priorite les failles exploitables ou les expositions de secrets.

2. Durcir la version actuelle
   - Verifier les logs sensibles.
   - Verifier les configs brutes exposees.
   - Verifier le stockage local critique.
   - Verifier les permissions Android.
   - Verifier le release build, ProGuard, R8 et minify.
   - Verifier API HTTPS, cleartext traffic et logs debug.
   - Verifier la stabilite VPN deja patchee.

3. Verifier la stabilite Android VPN
   - Tester la connexion VPN.
   - Tester notification foreground persistante.
   - Tester ecran eteint.
   - Tester changement Wi-Fi vers mobile et mobile vers Wi-Fi.
   - Verifier les logs `network_lost`, `reconnect_scheduled`, `reconnect_success`, `service_destroyed`, `engine_crashed`.

4. Sortir une version stable
   - Executer les tests unitaires Android.
   - Executer `assembleDebug`.
   - Executer `assembleRelease` si la configuration de signature est disponible.
   - Faire un test ADB de connexion.
   - Produire un rapport final.
   - Committer les corrections de securite/stabilite.

### Resultat attendu

Une version stable et securisee peut etre mise en ligne sans attendre la feature camouflage.

## Rail B: feature cloud en parallele

### Objectif

Construire une mise a jour produit autour du camouflage reseau, de la vie privee, de la securite et de la vitesse.

Nom interne propose: **Adaptive Network Privacy Engine**.

Cette feature doit exploiter les informations deja extraites par le parser pour classer, selectionner et preparer les configs VPN de facon plus intelligente.

### Principe technique

L'app ne peut pas inventer un protocole que le serveur ne supporte pas.

Elle peut cependant:

- detecter le niveau de discretion d'une config;
- classer automatiquement les nodes;
- privilegier les profils les plus discrets et les plus stables;
- eviter les profils faibles si de meilleurs profils existent;
- normaliser le runtime Xray quand les parametres sont deja presents;
- ameliorer le fallback et la selection automatique.

Elle ne doit pas:

- inventer une cle Reality;
- inventer un `shortId`;
- inventer un UUID ou password;
- transformer un serveur TCP nu en Reality si le serveur ne le supporte pas;
- modifier ou corrompre la config brute.

## Architecture feature proposee

### 1. NetworkPrivacyAnalyzer

Analyse un profil parse et produit des signaux internes.

Champs utiles:

- protocol;
- security;
- transport;
- sni;
- alpn;
- fingerprint;
- flow;
- publicKey;
- shortId;
- spiderX;
- path;
- hostHeader;
- serviceName;
- allowInsecure;
- raw input;
- warnings.

### 2. ProfileQualityScore

Produit plusieurs scores internes:

- score discretion;
- score securite;
- score vitesse probable;
- score stabilite;
- score risque DPI.

Bonus possibles:

- VLESS Reality;
- TLS propre;
- uTLS fingerprint;
- `flow=xtls-rprx-vision`;
- transports `grpc`, `xhttp`, `httpupgrade`, `ws` selon contexte;
- SNI coherent;
- ALPN present.

Penalites possibles:

- TCP nu;
- `security=none`;
- `allowInsecure=true`;
- SNI absent;
- transport inconnu;
- protocole faible;
- metadata incomplete;
- historique de reconnects ou d'echecs.

### 3. AdaptiveProfileSelector

Classe les nodes automatiquement et choisit le meilleur profil disponible.

Criteres:

- discretion;
- latence mesuree;
- stabilite historique;
- disponibilite;
- pays ou region si necessaire;
- compatibilite runtime.

Le classement doit rester invisible par defaut pour l'utilisateur.

### 4. RuntimeTransformPolicy

Prepare la config runtime de facon sure.

Transformations autorisees:

- normaliser les parametres Xray deja presents;
- conserver `sni`;
- conserver `fp`;
- conserver `flow`;
- mapper `hostHeader`, `path`, `serviceName`;
- preserver Reality/TLS/uTLS;
- refuser les transformations dangereuses.

Transformations interdites:

- inventer des secrets;
- remplacer arbitrairement le transport;
- changer une config en un protocole non supporte par le serveur;
- supprimer le raw config.

## Tests requis pour la feature

Tests unitaires minimum:

1. VLESS Reality fort.
2. VLESS TCP faible.
3. VLESS TLS WebSocket moyen/bon.
4. VLESS gRPC.
5. VLESS xhttp ou httpupgrade.
6. Trojan TLS.
7. Shadowsocks avec encryption.
8. `allowInsecure=true`.
9. SNI absent.
10. Node invalide.
11. Subscription multi-nodes.
12. Selection du meilleur node.
13. Fallback apres echec.
14. Aucun secret expose dans logs.
15. Runtime transform preserve le raw config.

## Ordre recommande

### Maintenant

1. Recevoir les resultats du review de securite.
2. Faire un audit securite cible du repo.
3. Corriger uniquement les failles prioritaires.
4. Verifier Android, build, logs et stabilite.
5. Sortir la version stable et securisee.

### Ensuite, en parallele cloud

1. Rediger la spec de la feature camouflage.
2. Implementer le moteur de scoring.
3. Implementer le selector.
4. Implementer la policy de transformation runtime.
5. Tester sur subscriptions reelles.
6. Preparer la prochaine mise a jour produit.

## Conclusion

La bonne strategie est:

- securite maintenant;
- release stable en ligne;
- feature camouflage en chantier separe;
- aucune refonte massive aveugle;
- aucune transformation reseau incompatible serveur.

Le prochain input attendu est le review de securite realise hier.
