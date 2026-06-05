# Spec — Cockpit stock admin (continuité par réallocation + réorg du bot) — v1

> Statut : design validé (brainstorming). Prêt pour plan d'implémentation.
> C'est le « Spec 2 » différé du chantier premium-quota, recadré sur la réalité du système.

## Context (audité sur le code réel)

Le bot admin (Telegraf, [admin-bot.service.ts](../../../backend/apps/admin-control-service/src/admin-bot.service.ts), ~1500 lignes ; templates [admin-bot.formatter.ts](../../../backend/apps/admin-control-service/src/admin-bot.formatter.ts)) expose **~27 commandes à plat**. Quelques claviers inline existent (détail stock, dashboard finance, retry commande, wizard d'import), mais **pas de hub persistant** et beaucoup d'actions exigent un **ID brut** en argument.

Côté stock, deux schedulers tournent déjà dans [inventory-delivery-service](../../../backend/apps/inventory-delivery-service) :
- **healthcheck** (~30 min) : marque chaque `InventoryItem` `EXPIRED` quand `supplier_expires_at <= now`, `DEGRADED` si le contrôle de vie échoue.
- **retry fulfillment** (~5 min) : balaie les commandes `PENDING_FULFILLMENT` et les sert dès que de la capacité (stock importé) apparaît.

Un event `low_stock_alert` est émis quand le nombre d'items sains allocatables passe sous un seuil (`checkStockAndNotify`), **mais il n'est pas surfacé dans le bot**. Le **resupply est aujourd'hui manuel** (l'admin importe les configs).

**Dépendance Spec 1** : l'invariant I1 (« la réalité fournisseur ne coupe pas le client ; continuité = Spec 2 ») n'est pleinement vrai qu'avec **ce** spec.

## Décisions verrouillées (brainstorming)

- **« Auto-resupply » = réallocation INTERNE, pas du procurement.** Quand le config **assigné** à un user va mourir (date `supplier_expires_at` OU quota source) **avant la promesse VENDUE** (expiration vendue / quota vendu), on **réassigne** ce user à un **autre config déjà en stock** ayant assez de marge. Le code ne crée jamais de nœud.
- **Le procurement reste manuel = l'admin.** Le bot l'**informe** (stock restant par forfait, alertes) pour qu'il refille à temps et ne soit jamais à court.
- **Pas de théâtre** : si aucun stock sain ne peut couvrir la promesse, on n'invente rien → **alerte urgente** + résidu honnête (le user reste sur son config jusqu'à sa mort, comme acté au Spec 1).
- **Un seul spec cohérent** (« cockpit »), livré en **3 phases** : moteur (A) → alertes (B) → réorg UX (C).
- **Réorg UX = navigation par boutons** (fini les IDs bruts), **hub** unique, **import unifié** ; les commandes texte restent en **raccourcis power-user**.

## Périmètre & composants

### A. Moteur — continuité par réallocation

**A1. `EntitlementContinuityPolicy` (fonctions pures, testables).**
- `isAssignmentAtRisk(input): RiskVerdict` — entrée : `soldExpiryMs`, `soldQuotaBytes` (0 = illimité), `configSupplierExpiresAtMs|null`, `configSourceQuotaRemainingBytes|null`, `clientConsumedBytes`, `nowMs`. Sortie : `{ atRisk: boolean, reasons: ('DATE'|'QUOTA')[] }`.
  - Risque **DATE** : `configSupplierExpiresAtMs != null && configSupplierExpiresAtMs < soldExpiryMs`.
  - Risque **QUOTA** : `soldQuotaBytes > 0 && configSourceQuotaRemainingBytes != null && configSourceQuotaRemainingBytes < (soldQuotaBytes - clientConsumedBytes)` (le reste de droit vendu ne tient pas dans ce que le config peut encore fournir).
  - `supplier_expires_at null` ⇒ pas de risque DATE (config sans expiration connue) ; quota source null ⇒ pas de risque QUOTA. Fail-safe : aucune donnée ⇒ pas à risque (on ne déplace pas sans raison).
- `selectReallocationCandidate(atRiskAssignment, candidates, nowMs): InventoryItemRef | null` — choisit, parmi les `InventoryItem` du **même `category`**, `health_status === HEALTHY`, `used_resale_slots < max_resale_slots`, **et couvrant la promesse** (`supplier_expires_at >= soldExpiry` quand connu ; quota source restant `>= reste de droit vendu`), celui au **meilleur `sale_priority_score`** (ou à la plus longue marge). `null` si aucun.

**A2. `ResupplyOrchestrator` (service, greffé sur le scheduler healthcheck existant).**
Après le passage healthcheck, pour chaque `OrderAssignment` `ACTIVE` :
1. Calcule la promesse vendue : `soldExpiry = calculateSubscriptionExpiresAt(order)` ([customer.service.ts](../../../backend/apps/customer-order-service/src/customer.service.ts)) ; `soldQuotaBytes = plan.quota_gb * 1024³` (0/null = illimité).
2. `isAssignmentAtRisk(...)`. Si à risque :
   - `candidate = selectReallocationCandidate(...)`.
   - **Candidat trouvé** : transaction atomique — créer/repointer l'`OrderAssignment` vers le candidat (`inventory_item_id`, `expires_at = candidate.supplier_expires_at`, conserver `measured_used_bytes` cumulé), `used_resale_slots++` sur le candidat, **libérer/`REVOKED`** l'ancien (`used_resale_slots--`), log `AdminEvent('ASSIGNMENT_REALLOCATED', { from, to, reasons })`. Le client reçoit le nouveau nœud au prochain refresh profil (mécanisme `getProfile`/store-engine existant — **pas de push spécial requis**).
   - **Aucun candidat** : log `AdminEvent('REALLOCATION_FAILED_NO_STOCK', ...)` + émet l'event d'alerte (cf. B). **On ne touche pas** l'assignment (continuité best-effort : le user garde son config jusqu'à sa mort réelle, où le flux Spec 1 `handlePremiumAccessEnded` prend le relais).
- **Idempotence** : un assignment déjà réalloué vers un config sain n'est pas re-déplacé (le verdict redevient `atRisk=false`). Limite de réallocations par passage (anti-emballement) configurable.

### B. Surveillance & alertes surfacées dans le bot

**B1. `InventoryStockMonitor` (étend l'existant).** Réutilise `checkStockAndNotify` + ajoute, par forfait : nb allocatables sains, **vie moyenne restante**, **nb d'assignments à risque**, **nb de réallocations échouées (out-of-stock)**.

**B2. Events → push bot.** Le `admin-control-service` s'abonne aux events (déjà : `low_stock_alert` ; nouveaux : `assignment_reallocated` résumé, `reallocation_failed_no_stock`). Chaque event **déclenche un message bot** vers le(s) chat(s) admin autorisé(s), formaté design-aligné, ex. :
> ⚠️ *Stock Basic critique* — 0 allocatable · **2 users à risque non couverts**. Refill Basic maintenant. `[📥 Importer Basic]`
Throttling anti-spam (1 alerte/type/forfait par fenêtre).

### C. Réorg UX — le cockpit (bot)

**C1. Hub d'accueil** (`/start`, `/menu`, ou `/help`) : **un seul message** + grille inline :
`📦 Stock · 📥 Import · 📋 Commandes · 💰 Finance · ⚠️ Alertes · 🛑 Danger · ❓ Aide`. Rafraîchi **en place** (`editMessageText`) ; chaque tuile mène à un sous-écran avec « ⬅️ Retour ».

**C2. Navigation par boutons (fini les IDs bruts).**
- **Stock** → choix forfait (Basic/Premium/Platinum/Trial) → **liste paginée** d'items (label = `folder_code`/`admin_label`, badge santé + slots + jours restants) → **détail** → actions : `Désactiver · Expirer · Quota atteint · Supprimer` — **toutes avec confirmation inline** (aujourd'hui seuls delete/superdelete confirment).
- **Alertes** : liste des risques/stock-bas en cours + action directe (`Importer <forfait>`).
- Les commandes texte actuelles (`/review <id>`, `/disable <id>`, …) **restent** comme raccourcis ; le hub devient la surface primaire.

**C3. Import unifié.** Un seul flux guidé `📥 Import` (branche **Payant**/**Trial**, puis forfait, puis coller config/URL, puis confirmer) qui remplace l'éparpillement `/add` + `/add_wizard` + `/trial_import` + `/trial_wizard` + `/add_trial` (les commandes restent en alias).

**C4. `setMyCommands` regroupé** + grammaire design alignée (emoji cohérents, Markdown, confirmations systématiques sur les ops mutatives).

## Invariants anti-contradiction
- **I-A** Réallocation **uniquement** vers un config **même forfait, sain, avec slot libre, couvrant la promesse** (date ET quota le cas échéant). Jamais un downgrade silencieux de la promesse.
- **I-B** **Zéro nœud inventé.** Pas de stock couvrant ⇒ alerte + statu quo, jamais une fausse réallocation.
- **I-C** Le quota/expiration **vendus** restent la vérité visible/appliquée côté client (Spec 1) ; la réallocation est **invisible** pour le client (juste un nouveau nœud servi).
- **I-D** Procurement = **manuel**. Le moteur ne fait que **redistribuer** le stock existant.
- **I-E** Toute mutation (réallocation, disable, expire, delete) est **journalisée** (`AdminEvent`) et idempotente.

## Error handling
- **Pas de candidat** : `REALLOCATION_FAILED_NO_STOCK` + alerte bot + statu quo (résidu honnête Spec 1).
- **Course / double-réallocation** : transaction atomique + re-vérif du verdict ⇒ idempotent.
- **Event/bot indisponible** (cron headless, chat non joignable) : l'alerte est journalisée même si le push échoue ; pas de blocage du moteur.
- **Bot callback sur item disparu** (supprimé entre l'affichage et le clic) : message « élément introuvable » + retour liste (pas de crash).

## Hors périmètre (YAGNI / autres specs)
Procurement automatique / API fournisseur / achat de nœuds ; notification client lors d'une réallocation ; multi-transport ; refonte du modèle de données stock ; migration de la convention 1024 ; tout ce qui n'est pas redistribution du stock existant + surface bot.

## Vérification
- **Backend (ts-node, convention `test:policy`)** : `EntitlementContinuityPolicy` — `isAssignmentAtRisk` (DATE, QUOTA, illimité, données manquantes = pas de risque) ; `selectReallocationCandidate` (même forfait, sain, slot libre, couvre date+quota, meilleur score, `null` si aucun). `ResupplyOrchestrator` — réalloue quand candidat, statu quo + event quand aucun, idempotence, atomicité des slots.
- **Bot (ts-node)** : formatters/keyboards du hub, de la navigation stock (pagination, labels sans ID brut), des confirmations, et du message d'alerte (pas de fuite de secret de config — règle déjà testée dans `admin-bot-formatter.spec.ts`).
- **Manuel (jugement utilisateur)** : observer le bot — hub navigable au doigt, alertes reçues, réallocation visible dans les events/`/stock`.

## Séquencement (3 phases, une vision)
1. **Phase A — moteur** : `EntitlementContinuityPolicy` (pur + tests) → `ResupplyOrchestrator` greffé au healthcheck + `AdminEvent`. Backend déployable seul (continuité active, invisible).
2. **Phase B — alertes** : `InventoryStockMonitor` enrichi + abonnement events → push bot + vue stock/forfait.
3. **Phase C — cockpit UX** : hub + navigation par boutons + import unifié + `setMyCommands` regroupé. Surface, sans toucher le moteur.

Backend (A,B) avant l'esthétique (C) ; mais une seule vision cockpit. Merge main+prod par phase (A/B déclenchent le redeploy Dokploy).
