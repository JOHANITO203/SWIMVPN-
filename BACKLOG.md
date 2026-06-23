# BACKLOG — file de travail autonome

> Source de vérité du loop autonome (voir `AGENTS.md` § Autonomous Loop Mode).
> Légende : **[P1/P2/P3]** priorité · **risk** low/med/high · **auto** = expédiable sans humain (vérif verte) ·
> **GATE** = préparer + journaliser pour revue humaine, NE PAS expédier · **PARK** = nécessite appareil/décision humaine.
> Le loop prend le plus haut **auto** non bloqué, le fait sur une branche, et ne merge/déploie que si la vérif passe.

## P0 — directives utilisateur (2026-06-22, re-cadrées)
- [x] **FAIT (2026-06-23, mergé main `da0cfe9`, vérif verte ; ⚠️ DÉPLOIEMENT BACKEND MANUEL EN ATTENTE — pas d'auto-deploy backend, rollout Dokploy ciblé requis ; pas de migration/env)** — design `docs/superpowers/specs/2026-06-23-stock-intelligence-design.md` · risk med — **Bot stock → intelligence proactive** (notification-bot + inventory/store services, ADDITIF, sur le cockpit existant [[admin-stock-cockpit]]). Ajouter : (a) **seuils de stock par plan** + **alertes bot proactives** sous seuil ; (b) **vélocité de consommation** (taux de vente récent) → « ≈ X jours de stock <plan> » ; (c) **prévision d'épuisement** + **suggestion de quantité de réappro** ; (d) **journal config** (épuisement / admin-health / révocation / moves) du TODO. **Lecture seule + alertes**, zéro changement paiement/entitlement/sécurité. Flow : **design committé d'abord** (revue dans WORKLOG) → implémentation sur branche → vérif `test:policy` + tests bot formatter → ship si vert. ⛔ **Auto-pause des ventes sous seuil = GATE** (touche revenu/clients) : préparer + journaliser, ne pas activer sans OK user.
- [ ] **auto** · risk low — **Recherche paiements (LÉGITIME)** : comparer des options d'encaissement pour un VPN/digital-goods (PSP « high-risk-friendly », crypto-processors, API-first) — devises, qualité d'API, frais, settlement, onboarding RÉEL. Livrable = doc factuel. ⛔ Ne PAS optimiser pour « contourner le KYC » (voir WON'T DO).
- [x] **FAIT (2026-06-23, 926a4bb + 93a9d4f, E2E prod OK)** — FONDATION capture opt-in : modèle Subscriber + flux subscribe/confirm + formulaires landing câblés + mailer Resend réutilisé. Suivi noté : confirm en GET → prefetch par scanners Gmail (auto-confirm) ; si preuve « humain » requise → page + bouton POST.
- [ ] **auto** · risk low — **Campagne marketing complète (compliant)** : (1) carte audience + canaux (subreddits, Telegram, forums, régions) + règles d'auto-promo de chacun ; (2) liste de **prospects/partenaires** (admins de communautés, affiliés, revendeurs, influenceurs privacy) via **contacts publics B2B** + leur canal/règle ; (3) **contenu** par canal + **séquences email** + brief creatives ; (4) **stratégie liste** : opt-in via capture landing + outreach B2B compliant (opt-out) ; (5) **reco setup d'envoi** (domaine dédié + SPF/DKIM/DMARC + outil d'emailing). Livrable = docs + assets, prêts à lancer. ⛔ Le **déclenchement d'envoi reste le geste de l'user/plateforme** (pas autonome) ; PAS de scraping d'emails perso de particuliers pour du B2C non sollicité ; PAS de blast depuis la boîte perso (voir WON'T DO).

## WON'T DO (refusé — raison, ne jamais exécuter en loop)
- **Scraper des emails de prospects + campagne email de masse depuis la boîte de l'user** → spam non sollicité (illégal CAN-SPAM/RGPD, viole les ToS email, fait blacklister le domaine + nuit à la réputation de l'app) et ciblage de masse. Remplacé par : marketing communautaire + email **opt-in** consenti.
- **Choisir un PSP en optimisant le « minimum de KYC »** → viser l'évitement KYC/AML expose au gel de fonds, à la fraude, à la fermeture de compte et à une responsabilité légale ; les rails no/low-KYC sont instables. Remplacé par : recherche paiements légitime (onboarding factuel, pas évitement).

## P1 — valeur haute, autonome-sûr
- [ ] **auto** · risk low — **Marketing/launch : finaliser + committer `docs/launch/`** (positioning, ASO, SEO long-tail, telegram posts, distribution, reddit pitch). Actuellement non commité. Acceptation : chaque doc complet, cohérent, committé. *(Aligné [[launch-and-store-plan]] : assets only, pas de campagne.)*
- [ ] **auto** · risk low — **Hygiène repo** : décider du sort des assets non commités (`swimvpn-shark-mono*.svg/png` → garder comme source de marque = committer dans `public/brand`; `SWIMVPN_AUDIT.md`, `docs/connectivity-honesty-audit.md`, `docs/superpowers/specs/2026-06-14-auto-update-design.md`, `tools/screen-motion/`, `windows/` → committer si utile, sinon laisser). Acceptation : `git status` propre ou justifié.
- [ ] **auto** · risk low — **Landing : retirer les frames `/assets/seq/` inutilisées** (le décor vidéo est live et stable, le canvas n'est plus servi). Vérifier d'abord que le bundle live ne référence pas `/assets/seq/`, puis `git rm`, build vert, ship. Allège le deploy de ~11 Mo.

## P2 — valeur, vérification requise
- [ ] **auto** · risk med — **Cine SEO : runtime locale-par-URL** (différé). Faire que `/` rende RU et `/fr` rende FR aussi au runtime (pas seulement au prerender), via détection d'URL côté client → `initialLocale`. Acceptation : tsc + build verts, prerender intact (ru `/`, fr `/fr`), pas de régression du défaut FR + switcher.
- [ ] **auto** · risk med — **Agent IA : reason codes** (TODO 2026-05-22). Ajouter des codes de recommandation concis à `AdaptiveDecisionAgent` + tests de régression (expired, premium-blocked, configs manquantes, imported, probe fail). Acceptation : `testDebugUnitTest --tests adaptive.*` verts (pas d'`assembleRelease`).
- [ ] **auto** · risk low — **Docs : passer en revue + committer les audits non commités** (`SWIMVPN_AUDIT.md`, `connectivity-honesty-audit.md`) s'ils sont à jour et utiles ; sinon les classer/supprimer avec justification.

## P3 — nettoyage / faible priorité
- [ ] **auto** · risk low — **Branches mortes** : auditer les branches locales (`harden/battery-banner`, `feat/proxy-autoheal`, `feat/windows-port` stale, etc.) — pour chacune, déterminer si mergée/abandonnée et proposer suppression (NE PAS supprimer une branche avec du travail non mergé sans le noter). Acceptation : rapport + suppression des seules branches prouvées intégrées.

## GATE — préparer + journaliser, NE PAS expédier sans revue
- [ ] **GATE** · risk high — Toute logique **paiement** (SwimPay/crypto), **sécurité/entitlement**, **secrets**.
- [ ] **GATE** · risk high — **Migrations Prisma / DB prod**, opérations **destructives** (suppression données/tables/env).
- [ ] **GATE** · risk med — Branches feature lourdes non mergées (`feat/egress-proven-camouflage`, `feat/ios-port`) : ne pas merger sans validation.

## PARK — nécessite appareil ou décision humaine (ne pas tenter en autonome)
- [ ] **PARK** — **QA visuelle Android** (z-order GLSurfaceView, états connect, subscription UI) — besoin d'un appareil + ADB.
- [ ] **PARK** — **Build release APK + validation device**, **soumission Google Play** (screenshots RC, privacy URL, Data Safety, VpnService wording) — décisions/produits humains.
- [ ] **PARK** — **Icône app Android monochrome** — besoin de validation device.
- [ ] **PARK** — **Purge cache OG Telegram** via @WebpageBot — action côté utilisateur.

## Journal des découvertes (le loop ajoute ici ce qu'il trouve)
- (vide au démarrage)
