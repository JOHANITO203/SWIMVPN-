# Design — Bot stock → intelligence proactive (additif)

> Statut : **DESIGN, à valider avant implémentation** (flow backlog P0). Lecture seule + alertes.
> Aucun changement paiement / entitlement / sécurité. Auto-pause des ventes = **GATE** (non inclus).

## Context
Le cockpit stock actuel est **réactif** : il affiche des comptes par catégorie/statut et envoie une alerte
low-stock avec un **seuil codé en dur = 5** (`checkStockAndNotify`, `inventory.service.ts:1628`). L'objectif :
le rendre **proactif** — prévenir AVANT la rupture, avec des seuils par plan, une vélocité de consommation et
une prévision d'épuisement + suggestion de réappro. Tout s'appuie sur des données et une infra **déjà en place**.

## Ce qui existe déjà (on étend, on ne duplique pas)
- **Alerte low-stock** : `InventoryService.checkStockAndNotify(category)` → `low_stock_alert` (seuil **5 en dur**). `admin.controller.ts:28` → `formatLowStockAlert` → Telegram.
- **Scheduler** : `InventoryService` (OnModuleInit) lance un `setInterval` healthcheck (30 min, `INVENTORY_HEALTHCHECK_INTERVAL_MS`) → `runScheduledHealthCheck()` (`inventory.service.ts:1533`). Point d'accroche idéal.
- **Stats** : `getInventoryStats()` (`inventory.service.ts:1195`) groupe par `[category,status]`. À enrichir.
- **Données de consommation** : `Order.fulfilled_at`/`created_at`, `OrderAssignment.assigned_at`, `Plan.duration_label`/`quota_gb`. Pas de requête « vélocité » existante mais les données sont là.
- **Journal** : `AdminEvent` couvre DÉJÀ `PLAN_QUOTA_EXHAUSTED`, `SOURCE_QUOTA_EXHAUSTED`, `SUPPLIER_CONFIG_EXPIRED`, `ASSIGNMENT_REVOKED`, `ASSIGNMENT_MOVED`, `CONFIG_HEALTH_UPDATED`. → le point (d) du backlog est **quasi couvert** : il reste surtout à **surfacer** ces événements dans le cockpit (pas à les créer).
- **Cockpit** : `/stock` overview + cockpit interactif (admin-bot) + `formatRecentAlerts`.

## L'upgrade (additif)

### 1. Seuils par plan (remplace le `5` en dur)
- Config via env `STOCK_THRESHOLDS` (ex. `WEEK=8,MONTH=5,QUARTER=3`) avec défauts ; parsée dans une **policy pure** `stock-intelligence.policy.ts` (testable, pas d'I/O). `checkStockAndNotify` lit le seuil du plan au lieu de `5`.

### 2. Vélocité de consommation (par catégorie)
- Nouvelle requête : nombre d'`OrderAssignment` (ou `Order.fulfilled_at`) par `category` sur une **fenêtre récente** (défaut 14 j, env `STOCK_VELOCITY_WINDOW_DAYS`). → `dailyRate = count / windowDays` (lissé). Fonction pure dans la policy ; la requête Prisma dans le service.

### 3. Prévision d'épuisement + suggestion de réappro
- `daysOfStock = availableHealthy / max(dailyRate, ε)` → « ≈ X jours de stock <plan> ».
- `reorderQty = max(0, ceil(targetDaysCover * dailyRate) - availableHealthy)` (env `STOCK_TARGET_DAYS_COVER`, défaut 21).
- Toutes ces formules = **fonctions pures** dans `stock-intelligence.policy.ts` (couvertes par tests).

### 4. Surface (alertes + cockpit)
- **Alerte proactive** : pendant `runScheduledHealthCheck`, pour chaque catégorie, si `available < seuil` **OU** `daysOfStock < STOCK_FORECAST_ALERT_DAYS` (défaut 5) → émettre un **nouvel** event `stock_forecast_alert { category, available, dailyRate, daysOfStock, reorderQty }` (même chemin que `low_stock_alert` : `admin.controller` `@EventPattern` → `admin-bot.formatter` `formatStockForecastAlert` → Telegram). Anti-spam : ne ré-alerter une catégorie qu'une fois par fenêtre (dedup en mémoire, comme l'esprit existant).
- **Cockpit** : enrichir l'overview `/stock` pour afficher, par plan : disponible · seuil · vélocité (/j) · « ≈ X j » · réappro suggérée. (Vue additive, pas de refonte.)

## Fichiers (cibles)
- **Nouveau** `backend/apps/inventory-delivery-service/src/stock-intelligence.policy.ts` — pur : parse seuils, `computeDailyRate`, `computeDaysOfStock`, `computeReorderQty`, `shouldAlert`.
- `inventory-delivery-service/src/inventory.service.ts` — requête vélocité + branchement dans `runScheduledHealthCheck` + remplacement du seuil dur dans `checkStockAndNotify` + enrichir `getInventoryStats`.
- `admin-control-service/src/admin.controller.ts` — `@EventPattern('stock_forecast_alert')`.
- `admin-control-service/src/admin-bot.formatter.ts` — `formatStockForecastAlert` + enrichir l'overview cockpit.
- `.env.example` — `STOCK_THRESHOLDS`, `STOCK_VELOCITY_WINDOW_DAYS`, `STOCK_TARGET_DAYS_COVER`, `STOCK_FORECAST_ALERT_DAYS`.

## GATE (préparé, NON activé sans ton OK)
- **Auto-pause des ventes** quand `available == 0` ou sous un seuil critique : touche le revenu/clients → **non implémenté** ; à décider. (Aujourd'hui : fulfillment passe déjà en PENDING quand pas de capacité — pas de pause de vente.)

## Vérification (à l'implémentation)
- Test `stock-intelligence.policy.spec.ts` (ts-node, ajouté à `test:policy`) : seuils parsés, rate/forecast/reorder corrects, `shouldAlert` (seuil ET prévision), edge cases (rate=0, available=0).
- `npm run typecheck` ; `nest build inventory-delivery-service` + `admin-control-service` ; non-régression `test:policy`.
- Pas de migration (aucun nouveau modèle ; seuils en env, calculs en mémoire).

## Hors périmètre
Auto-pause des ventes (GATE) · refonte du cockpit · nouveaux modèles Prisma · changement de la logique de réallocation (déjà gérée par `ResupplyOrchestrator`).
