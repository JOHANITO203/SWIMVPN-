# SWIMVPN — Document de revue des implémentations et risques de régression

Date: 2026-05-17  
Portée analysée: série de commits du PR précédent, de `e7d6d8b` à `0dec96d` inclus.  
Objectif: fournir un document lisible pour comparer les régressions potentielles introduites par les implémentations précédentes.

## 1. Résumé exécutif

Le PR précédent a touché trois zones principales:

1. **Android runtime Xray**
   - Ajout d'une vérification de readiness du proxy SOCKS local avant de déclarer le tunnel prêt.
   - Centralisation de la génération du JSON runtime Xray dans `TunnelRuntimeAdapter`.
   - Ajout de garde-fous explicites pour les configurations non supportées au runtime.

2. **Android réseau / latence serveur**
   - Limitation de la concurrence lors des probes de latence.
   - Ajout d'un cache mémoire court par `host:port`.
   - Ajout de tests unitaires autour de la mesure de latence.

3. **Backend parser VPN / contrats partagés**
   - Extension du parser du `vpn-config-engine-service` pour mieux conserver les métadonnées runtime.
   - Ajout de champs optionnels aux contrats partagés afin d'aligner backend et Android.
   - Ajout d'un test de parité parser pour VLESS Reality, VMess, Trojan, Shadowsocks et JSON Xray/V2Ray.

Ces changements sont fonctionnels mais sensibles, car ils modifient le chemin entre une config VPN brute importée, sa normalisation, sa conversion en runtime Xray et la validation de démarrage du tunnel.

## 2. Fichiers impactés par zone

### Android runtime et config

- `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- `android/app/src/main/java/com/swimvpn/app/config/ConfigNormalizationEngine.kt`
- `android/app/src/main/java/com/swimvpn/app/config/ProtocolModels.kt`
- `android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt`
- `android/app/src/test/java/com/swimvpn/app/config/ConfigParserEngineTest.kt`
- `android/app/src/test/java/com/swimvpn/app/config/TunnelRuntimeAdapterSnapshotTest.kt`

### Android latence serveur

- `android/app/src/main/java/com/swimvpn/app/data/network/ServerLatencyEvaluator.kt`
- `android/app/src/test/java/com/swimvpn/app/data/network/ServerLatencyEvaluatorTest.kt`

### Backend parser et contrats

- `backend/apps/vpn-config-engine-service/src/vpn-config.service.ts`
- `backend/apps/vpn-config-engine-service/src/__tests__/config-parser-parity.spec.ts`
- `backend/libs/contracts/src/vpn-profile.interface.ts`
- `backend/package.json`

### Documentation / suivi

- `DECISIONS.md`
- `android/DECISIONS.md`
- `android/TODO.md`
- `android/WORKLOG.md`
- `backend/TODO.md`
- `backend/WORKLOG.md`

## 3. Détail des implémentations Android

### 3.1 Readiness probe du proxy SOCKS local Xray

#### Implémentation

`SwimVpnService.kt` attend maintenant que le port SOCKS local sur `127.0.0.1:<socksPort>` accepte une connexion TCP avant de considérer Xray prêt.

Comportement attendu:

1. Le process Xray est lancé.
2. Le service boucle pendant une fenêtre bornée.
3. À chaque tentative, il vérifie:
   - que le process Xray n'est pas déjà mort;
   - que le port SOCKS local accepte une connexion TCP.
4. Si le port répond, le runtime peut continuer.
5. Si le process meurt ou si le port ne répond pas avant timeout, le démarrage échoue avec une erreur explicite.

#### Intention

Éviter les faux positifs où Android considère le VPN prêt alors que Xray n'a pas encore bindé son listener local. Cette situation pouvait créer une connexion VPN apparemment active mais inutilisable.

#### Points de régression à comparer

- Le timeout peut être trop court sur appareils lents.
- Le port SOCKS peut être correct mais bindé après la limite configurée.
- Les restrictions réseau Android, l'économie batterie ou certains kernels peuvent ralentir le démarrage du process.
- Une configuration Xray valide mais lente à initialiser peut désormais échouer plus tôt.

#### Checklist de comparaison

- Tester sur appareil réel lent ou ancien.
- Tester premier lancement après installation fraîche.
- Tester relance après arrêt forcé de l'app.
- Tester transition réseau Wi-Fi vers mobile.
- Comparer logs avant/après autour de `xray_ready` et des erreurs de readiness.

### 3.2 Centralisation de la génération runtime Xray

#### Implémentation

`ConfigNormalizationEngine.kt` ne doit plus synthétiser le document Xray exécutable pour les formats standards. Il conserve les informations normalisées et préserve les JSON importés lorsque la source est déjà JSON Xray/V2Ray. La responsabilité de produire le document runtime final est déplacée vers `TunnelRuntimeAdapter.kt`.

#### Intention

Réduire la duplication de logique entre normalisation et runtime, et limiter les divergences entre ce qui est prévisualisé, validé et effectivement lancé.

#### Points de régression à comparer

- Les anciennes configs qui dépendaient implicitement d'un JSON généré dans `ConfigNormalizationEngine` peuvent changer de sortie runtime.
- Les snapshots ajoutés couvrent des cas importants, mais pas forcément tous les fournisseurs réels.
- Les JSON importés sont préservés, donc une erreur présente dans un JSON utilisateur peut passer plus loin dans le pipeline avant d'être rejetée par Xray.

#### Checklist de comparaison

- Comparer le JSON runtime final généré pour:
  - VLESS Reality TCP;
  - VLESS Reality gRPC;
  - VMess WebSocket TLS;
  - Trojan WebSocket TLS;
  - Shadowsocks simple;
  - JSON Xray importé;
  - JSON V2Ray importé.
- Vérifier que les raw configs ne sont pas reformattées ou perdues.
- Vérifier que les champs sensibles comme `pbk`, `sid`, `sni`, `flow`, `alpn`, `allowInsecure` restent présents lorsque fournis.

### 3.3 Garde-fous runtime pour configurations non supportées

#### Implémentation

`TunnelRuntimeAdapter.kt` bloque explicitement certains cas avant génération runtime:

- Shadowsocks avec plugin d'obfuscation non supporté par le runtime local.
- VLESS Reality incomplet, notamment si la clé publique Reality ou le SNI sont absents.

#### Intention

Éviter de générer un runtime Xray qui échouera silencieusement ou qui démarrera avec une configuration incomplète.

#### Points de régression à comparer

- Une configuration auparavant acceptée par l'UI peut être rejetée plus tôt.
- Certains fournisseurs Shadowsocks utilisent `plugin` / `plugin-opts`; ces profils peuvent maintenant être considérés incompatibles même si une autre application les accepte.
- Une config Reality partielle ne doit plus créer un faux état de succès.

#### Checklist de comparaison

- Importer Shadowsocks sans plugin: doit rester accepté.
- Importer Shadowsocks avec plugin obfs/v2ray-plugin: doit produire une erreur claire, pas un crash.
- Importer Reality sans `pbk`: doit être refusé clairement.
- Importer Reality sans `sni`: doit être refusé clairement.
- Importer Reality complet: doit rester accepté.

### 3.4 Tests Android ajoutés

#### Implémentation

Des tests unitaires et snapshots ont été ajoutés pour documenter la sortie runtime attendue et les refus de compatibilité.

#### Intention

Stabiliser le contrat runtime et détecter rapidement les régressions de génération JSON.

#### Points de régression à comparer

- Les tests snapshot peuvent verrouiller un comportement incorrect si le snapshot est basé sur une mauvaise hypothèse.
- Les tests unitaires ne remplacent pas un test appareil réel avec le binaire Xray effectivement embarqué.

## 4. Détail des implémentations Android réseau / latence

### 4.1 Probes de latence bornées et cache TTL

#### Implémentation

`ServerLatencyEvaluator.kt` limite les probes simultanés et garde temporairement les résultats par destination `host:port`.

#### Intention

Éviter de lancer trop de connexions TCP simultanées lorsque la liste de serveurs est longue, réduire le coût réseau et stabiliser l'UI.

#### Points de régression à comparer

- Les latences peuvent rester temporairement obsolètes jusqu'à expiration du cache.
- Un serveur redevenu disponible peut rester affiché comme indisponible pendant la durée du TTL.
- Un timeout trop agressif peut sous-estimer des serveurs géographiquement éloignés.

#### Checklist de comparaison

- Tester liste courte et liste longue de serveurs.
- Tester serveur down puis up dans la fenêtre TTL.
- Tester appareil en réseau lent.
- Vérifier que l'écran ne freeze pas pendant l'évaluation.

## 5. Détail des implémentations backend

### 5.1 Extension du parser `vpn-config-engine-service`

#### Implémentation

`VpnConfigService` a été étendu pour préserver davantage de métadonnées runtime:

- VLESS Reality: `pbk`, `sid`, `spiderX`, `flow`, `sni`, `fp`, transport.
- VMess: décodage du lien VMess et conservation des paramètres réseau/TLS utiles.
- Trojan: conservation du mot de passe, transport, TLS, SNI, host/path, `allowInsecure`.
- Shadowsocks: conservation de `plugin` et `pluginOptions`.
- JSON Xray/V2Ray: préservation du JSON brut et extraction d'un outbound exploitable.

#### Intention

Aligner le comportement backend avec Android: le backend ne doit pas perdre des informations nécessaires au runtime ou à la preview.

#### Points de régression à comparer

- Le parser backend est devenu plus large et donc plus exposé à des variations fournisseur.
- Les formats JSON Xray/V2Ray complexes avec plusieurs outbounds peuvent ne pas choisir l'outbound attendu.
- L'ajout de champs optionnels peut révéler des différences de sérialisation dans les consommateurs existants.
- Une validation plus permissive côté parser ne doit pas devenir une autorisation d'accès premium côté backend.

#### Checklist de comparaison

- Comparer `processPipeline()` backend et parser Android sur les mêmes exemples.
- Vérifier conservation exacte du raw JSON.
- Vérifier que le backend ne reformate pas les configs brutes.
- Vérifier que les configs expirées/premium ne sont pas exposées par erreur: cette implémentation touche le parser, pas l'entitlement, mais la frontière sécurité doit rester contrôlée ailleurs.

### 5.2 Contrats partagés

#### Implémentation

`vpn-profile.interface.ts` ajoute des champs optionnels pour transporter les métadonnées runtime sans casser les consommateurs existants.

#### Intention

Permettre aux services backend de retourner des profils plus complets et de maintenir une compatibilité ascendante via des champs optionnels.

#### Points de régression à comparer

- Les clients stricts qui ne tolèrent pas de champs supplémentaires doivent être vérifiés.
- Les champs optionnels ne doivent pas être interprétés comme obligatoires par erreur.
- Les noms JSON doivent rester cohérents entre backend, Android et tests.

## 6. Tests ajoutés et ce qu'ils couvrent

### Backend

- `config-parser-parity.spec.ts`
  - VLESS Reality.
  - VMess.
  - Trojan.
  - Shadowsocks plugin metadata.
  - JSON Xray/V2Ray avec préservation `rawJson`.

### Android

- `ConfigParserEngineTest.kt`
  - Couverture supplémentaire autour des refus runtime / métadonnées.
- `TunnelRuntimeAdapterSnapshotTest.kt`
  - Snapshots des documents runtime générés par `TunnelRuntimeAdapter`.
- `ServerLatencyEvaluatorTest.kt`
  - Concurrence bornée, cache TTL et comportement de timeout.

### Limites des tests

- Les tests unitaires ne prouvent pas que le binaire Xray embarqué démarre sur tous les appareils.
- Les tests backend ne couvrent pas tous les formats fournisseurs réels.
- Les tests ne remplacent pas une comparaison de configs réelles issues des fournisseurs cibles.
- Les tests ne valident pas automatiquement le parcours trial/subscription/freemium, car ce PR ne modifie pas directement l'entitlement.

## 7. Matrice de régression à utiliser pour comparaison

| Zone | Ce qui a changé | Risque principal | Test de comparaison recommandé |
| --- | --- | --- | --- |
| Xray startup Android | Attente explicite du port SOCKS local | Timeout sur appareil lent | Lancer 10 connexions successives sur appareil réel |
| Runtime JSON Android | Génération centralisée dans `TunnelRuntimeAdapter` | Différence de JSON final | Comparer snapshots et JSON runtime avant/après |
| JSON imports | Préservation du JSON brut | Erreur utilisateur propagée plus loin | Importer JSON réel fournisseur et vérifier lancement |
| VLESS Reality | Refus si `pbk` ou `sni` manquant | Config anciennement acceptée refusée | Tester Reality complet et incomplet |
| Shadowsocks plugin | Refus explicite des plugins non supportés | Fournisseur avec plugin non compatible bloqué | Importer SS simple et SS plugin |
| Latence serveurs | Cache + concurrence bornée | Latence affichée obsolète | Serveur down/up dans la fenêtre TTL |
| Backend parser | Métadonnées étendues | Mauvais parsing sur edge cases | Comparer Android vs backend sur corpus réel |
| Contrats partagés | Nouveaux champs optionnels | Client strict cassé | Vérifier sérialisation clients consommateurs |

## 8. Scénarios de QA manuelle prioritaires

1. **Import VLESS Reality complet**
   - Importer une config Reality complète.
   - Vérifier preview.
   - Démarrer le tunnel.
   - Confirmer que `xray_ready` apparaît et que le trafic passe.

2. **Import VLESS Reality incomplet**
   - Retirer `pbk` ou `sni`.
   - Vérifier refus clair.
   - Confirmer absence de crash et absence de faux état connecté.

3. **Import Shadowsocks simple**
   - Importer sans plugin.
   - Vérifier que le runtime est généré.
   - Tester connexion.

4. **Import Shadowsocks avec plugin**
   - Importer avec `plugin` / `plugin-opts`.
   - Vérifier erreur de compatibilité claire.

5. **Import JSON Xray/V2Ray**
   - Importer JSON complet.
   - Vérifier que le raw JSON est conservé.
   - Vérifier que le runtime ne supprime pas les champs nécessaires.

6. **Liste de serveurs longue**
   - Ouvrir l'écran serveur avec beaucoup d'entrées.
   - Vérifier que l'UI reste fluide.
   - Vérifier que les latences se remplissent progressivement.

7. **Backend parser parity**
   - Envoyer les mêmes configs au backend et à Android.
   - Comparer protocol, host, port, transport, security, SNI, Reality settings et raw config.

## 9. Ce qui n'a pas été changé par ces implémentations

- Pas de modification intentionnelle des règles d'entitlement trial/subscription/freemium.
- Pas de changement du modèle PostgreSQL ou Prisma.
- Pas de changement Docker/Dokploy/Traefik.
- Pas d'ajout de nouveau microservice.
- Pas de changement volontaire des règles de paiement ou de fulfillment.
- Pas de transformation destructive attendue des configs VPN brutes.

## 10. Points à surveiller avant de considérer le PR sans régression

- Confirmer sur appareil Android réel que la readiness SOCKS n'introduit pas de faux échec.
- Confirmer que les profils réels fournisseurs utilisés par SWIMVPN sont couverts par les snapshots ou par QA manuelle.
- Confirmer que les JSON runtime générés par `TunnelRuntimeAdapter` correspondent aux exigences du binaire Xray embarqué.
- Confirmer que le backend parser et Android produisent des métadonnées équivalentes pour le même corpus de configs.
- Confirmer que les champs optionnels ajoutés aux contrats ne cassent aucun consommateur strict.
- Confirmer que les refus de compatibilité sont affichés comme erreurs utilisateur compréhensibles, pas comme crash ou écran bloqué.

## 11. Conclusion

Les implémentations du PR précédent améliorent la robustesse du runtime et la parité parser, mais elles changent des chemins critiques. La comparaison de régression doit donc se concentrer sur:

1. le JSON runtime réellement fourni à Xray;
2. le comportement de démarrage Xray sur appareil réel;
3. la compatibilité des configs fournisseur existantes;
4. la conservation exacte des configs brutes;
5. la cohérence backend/Android;
6. l'absence de changement indirect sur entitlement, freemium et accès premium.

Aucun commentaire inline précis du diff n'était disponible dans le prompt. Ce document couvre donc les implémentations observables dans l'historique Git et les fichiers modifiés.
