# BACKLOG — file de travail autonome

> Source de vérité du loop autonome (voir `AGENTS.md` § Autonomous Loop Mode).
> Légende : **[P1/P2/P3]** priorité · **risk** low/med/high · **auto** = expédiable sans humain (vérif verte) ·
> **GATE** = préparer + journaliser pour revue humaine, NE PAS expédier · **PARK** = nécessite appareil/décision humaine.
> Le loop prend le plus haut **auto** non bloqué, le fait sur une branche, et ne merge/déploie que si la vérif passe.

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
