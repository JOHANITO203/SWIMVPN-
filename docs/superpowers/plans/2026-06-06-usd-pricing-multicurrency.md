# USD Pricing + Multi-Currency Indicator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make the plan catalog price USD-anchored and show a truthful "SwimPay supports these currencies" indicator on the app + landing — leaving rails/checkout/collection to the separate SwimPay project.

**Architecture:** Additive only. Add `Plan.price_usd` (keep `price_rub` as the RUB-rail price), add nullable `Order.currency`/`amount` (ready for SwimPay to fill later — not wired here), add a backend `supportedCurrencies` capability config (default `["RUB"]`), expose `priceUsd` + `supportedCurrencies` on the existing client offers endpoint, and render the USD headline + a capability-driven currency indicator on the Android subscribe screen and the landing pricing section. No geolocation, no auto currency selection. Crypto stays the separate `crypto-pay` rail, never under the SwimPay label.

**Tech Stack:** NestJS monorepo (apps/*), Prisma/Postgres, Android/Kotlin Compose, Vite/React landing. RAM-constrained: backend = `npm run test:policy` + `nest build` of touched services; Android = `testDebugUnitTest --tests … --rerun-tasks`; no `assembleRelease`.

---

## Scope

Source spec: `docs/superpowers/specs/2026-06-06-usd-pricing-multicurrency-design.md` (commit 8ebf71e). **In scope:** USD catalog price, capability flag, offers-API exposure, app + landing display/indicator. **Out of scope (depends on SwimPay, deferred):** SwimPay payment callback, populating `Order.currency`/`amount`, admin multi-currency revenue normalization. We only ADD the nullable Order fields so SwimPay can fill them later.

## File Structure

- `backend/prisma/schema.prisma` — modify: `Plan.price_usd`, `Order.currency`/`amount`.
- `backend/prisma/migrations/<new>/migration.sql` — create (via `prisma migrate dev`).
- `backend/apps/<store-service>/src/payment-capability.ts` — create: the `supportedCurrencies` source (config-driven).
- the store service handler for `get_active_plans` — modify: include `price_usd` + `supportedCurrencies`.
- `android/app/src/main/java/com/swimvpn/app/data/model/NetworkModels.kt` — modify: Plan DTO `priceUsd` + `supportedCurrencies`.
- `android/app/src/main/java/com/swimvpn/app/ui/screens/SubscriptionScreen.kt` — modify: USD headline + indicator.
- `android/app/src/main/res/values{,-en,-fr,-ru}/strings.xml` — modify: indicator strings.
- `src/components/landing/LandingPage.tsx` (+ the pricing component it uses) — modify: USD + indicator.

---

## Task 0: Branch

- [ ] **Step 1**
```bash
git checkout main
git checkout -b feat/usd-pricing-multicurrency
```

---

## Task 1: Prisma — USD catalog price + Order multi-currency fields (additive)

**Files:** `backend/prisma/schema.prisma` (model `Plan` ~line 113-126, `price_rub` ~121; model `Order` ~line 137-155, `amount_rub`).

- [ ] **Step 1: Read** `backend/prisma/schema.prisma` models `Plan` and `Order` to confirm exact current fields.

- [ ] **Step 2: Add `price_usd` to `Plan`** (right after `price_rub`):
```prisma
  price_rub      Decimal      @db.Decimal(10, 2)
  price_usd      Decimal      @default(0) @db.Decimal(10, 2)  // catalog source of truth (USD). 0 until set.
```

- [ ] **Step 3: Add nullable currency+amount to `Order`** (after `amount_rub`; nullable so existing RUB orders are unaffected and SwimPay fills these later):
```prisma
  amount_rub   Decimal     @db.Decimal(10, 2)
  currency     String?     // paid currency (e.g. "RUB","USD","XOF"); null = legacy RUB order
  amount       Decimal?    @db.Decimal(12, 2)  // paid amount in `currency`; populated by SwimPay later
```

- [ ] **Step 4: Generate the migration**
```bash
cd backend && npx prisma migrate dev --name add_price_usd_and_order_currency --create-only
```
Then inspect the generated SQL: it must be ADD COLUMN only (no drops, no NOT NULL on existing rows without default). `price_usd` has a default(0) so it backfills safely; `currency`/`amount` are nullable. Apply with `npx prisma migrate dev` (or `prisma generate` + the project's migrate step).

- [ ] **Step 5: Regenerate the client + build**
```bash
cd backend && npx prisma generate && npx nest build customer-order-service
```
Expected: builds clean (the new fields are optional in writes).

- [ ] **Step 6: Commit**
```bash
git add backend/prisma/schema.prisma backend/prisma/migrations
git commit -m "feat(pricing): add Plan.price_usd (catalog) + nullable Order.currency/amount (additive)"
```

> NOTE: backfilling real USD values per plan is a DATA task the user does in admin (set each plan's `price_usd`); the migration only adds the column with default 0. Do NOT FX-convert from price_rub.

---

## Task 2: `supportedCurrencies` capability (config-driven, default ["RUB"])

**Files:** create `backend/apps/<store-service>/src/payment-capability.ts` (the service that handles `get_active_plans` — find it: `grep -rn "get_active_plans" backend/apps`; it is the store/customer service). Test alongside the project's `test:policy` ts-node specs.

- [ ] **Step 1: Write the failing test** (add a ts-node spec wired into `test:policy`, mirroring an existing policy spec's structure):
```ts
import { resolveSupportedCurrencies } from '../src/payment-capability';
import { strict as assert } from 'assert';

// default = only what SwimPay collects today
assert.deepEqual(resolveSupportedCurrencies(undefined), ['RUB']);
// env override: comma list, upper-cased, trimmed, deduped, validated
assert.deepEqual(resolveSupportedCurrencies('usd, rub ,xof'), ['USD', 'RUB', 'XOF']);
// junk is dropped
assert.deepEqual(resolveSupportedCurrencies('rub,zzz,'), ['RUB']);
console.log('payment-capability OK');
```

- [ ] **Step 2: Run it, verify FAIL** (module missing): `cd backend && npx ts-node apps/<store-service>/test/payment-capability.policy.ts`

- [ ] **Step 3: Implement** `payment-capability.ts`:
```ts
/** Currencies SwimPay actually collects right now. Driven by env so the user flips rails on without a
 *  code change; the truthful indicator renders exactly this list. Crypto is NOT here (separate rail). */
const KNOWN = new Set(['USD', 'RUB', 'XOF']);

export function resolveSupportedCurrencies(raw: string | undefined): string[] {
  if (!raw || !raw.trim()) return ['RUB']; // today: SwimPay collects RUB only
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of raw.split(',')) {
    const c = part.trim().toUpperCase();
    if (KNOWN.has(c) && !seen.has(c)) { seen.add(c); out.push(c); }
  }
  return out.length ? out : ['RUB'];
}
```
Read the env via the existing config pattern (`process.env.SWIMPAY_SUPPORTED_CURRENCIES`); confirm how the service reads env (ConfigService vs process.env) and follow it.

- [ ] **Step 4: Run it, verify PASS.** Add the spec to the `test:policy` chain in `backend/package.json` (follow how existing policy specs are chained).

- [ ] **Step 5: Commit**
```bash
git add backend/apps backend/package.json
git commit -m "feat(pricing): supportedCurrencies capability (env-driven, default RUB; crypto excluded)"
```

---

## Task 3: Offers API exposes `priceUsd` + `supportedCurrencies`

**Files:** the handler for `{ cmd: 'get_active_plans' }` (gateway route `store.controller.ts:@Get('plans')` → `storeClient.send({cmd:'get_active_plans'})`). Find the microservice handler: `grep -rn "get_active_plans" backend/apps`.

- [ ] **Step 1: Read** the `get_active_plans` handler + the DTO/shape it returns (today it returns plans incl. `price_rub`).

- [ ] **Step 2: Include the new fields** in the returned plan shape — add `price_usd` (from the plan row) and a top-level/per-response `supportedCurrencies` (from `resolveSupportedCurrencies(process.env.SWIMPAY_SUPPORTED_CURRENCIES)`). Keep `price_rub` for the legacy RU flow. Concretely, where the handler maps plans, add `price_usd: plan.price_usd.toString()` to each plan, and attach `supportedCurrencies` to the response (either per-plan or as a sibling field — match the existing response envelope; if it returns a bare array, wrap as `{ plans, supportedCurrencies }` and update the consumers in Task 4/5 accordingly).

- [ ] **Step 3: Build** `cd backend && npx nest build <store-service> && npx nest build gateway-service` — clean.

- [ ] **Step 4: Commit**
```bash
git add backend/apps
git commit -m "feat(pricing): offers API exposes priceUsd + supportedCurrencies"
```

---

## Task 4: Android — USD headline + truthful currency indicator

**Files:** `data/model/NetworkModels.kt` (Plan DTO, `priceRub` ~line 11), `ui/screens/SubscriptionScreen.kt` (`formatPlanPrice` ~1138, `PriceBlock` ~446, `SubscriptionPlanUi.price` ~98, the `.filter { priceRub … }` ~138), `res/values{,-en,-fr,-ru}/strings.xml`.

- [ ] **Step 1: Read** the three sites in `SubscriptionScreen.kt` (mapping to `SubscriptionPlanUi`, `PriceBlock`, `formatPlanPrice`) and the Plan DTO in `NetworkModels.kt`.

- [ ] **Step 2: Extend the Plan DTO** in `NetworkModels.kt` (add after `priceRub`):
```kotlin
    @SerializedName("price_usd") val priceUsd: String? = null,
    @SerializedName("supportedCurrencies") val supportedCurrencies: List<String>? = null,
```
(If the offers response was wrapped `{ plans, supportedCurrencies }` in Task 3, add `supportedCurrencies` to the response wrapper DTO instead and thread it down.)

- [ ] **Step 3: Add a USD formatter** next to `formatPlanPrice` in `SubscriptionScreen.kt`:
```kotlin
private fun formatPlanPriceUsd(priceUsd: String?): String {
    val v = priceUsd?.replace(',', '.')?.toBigDecimalOrNull() ?: return ""
    return "$" + v.stripTrailingZeros().toPlainString()
}
```

- [ ] **Step 4: Make the headline USD** — where `SubscriptionPlanUi.price` is built (~line 950 `price = formatPlanPrice(priceRub)`), prefer USD when present:
```kotlin
        price = formatPlanPriceUsd(priceUsd).ifEmpty { formatPlanPrice(priceRub) },
```
Keep the `priceRub > 0` filter at line 138 (it gates visibility; USD is additive). Thread `priceUsd` + `supportedCurrencies` into `SubscriptionPlanUi` (add fields) so the composable can render the indicator.

- [ ] **Step 5: Render the indicator** in `PriceBlock` (or just under it): a small line built from `supportedCurrencies`:
```kotlin
// honest: shows ONLY what SwimPay collects (from supportedCurrencies). Crypto is a separate option, not here.
val badges = (plan.supportedCurrencies ?: listOf("RUB")).joinToString(" · ")
Text(text = stringResource(R.string.pricing_swimpay_supports, badges), /* small, muted style */)
```

- [ ] **Step 6: Strings ×4** — add after an existing pricing string in each `strings.xml`:
  - values/ & values-fr/: `<string name="pricing_swimpay_supports">Payable via SwimPay — %1$s</string>`
  - values-en/: `<string name="pricing_swimpay_supports">Pay via SwimPay — %1$s</string>`
  - values-ru/: `<string name="pricing_swimpay_supports">Оплата через SwimPay — %1$s</string>`

- [ ] **Step 7: Compile + unit test**
```bash
cd android && ./gradlew :app:compileDebugKotlin --rerun-tasks --max-workers=1 --no-parallel --no-daemon
```
Expected: BUILD SUCCESSFUL. (No new unit logic beyond formatters; if you extract `formatPlanPriceUsd` purely, add a tiny test mirroring any existing `SubscriptionScreen` formatter test.)

- [ ] **Step 8: Commit**
```bash
git add android/app/src/main/java/com/swimvpn/app/data/model/NetworkModels.kt android/app/src/main/java/com/swimvpn/app/ui/screens/SubscriptionScreen.kt android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml android/app/src/main/res/values-fr/strings.xml android/app/src/main/res/values-ru/strings.xml
git commit -m "feat(pricing): Android shows USD headline + truthful SwimPay currency indicator"
```

---

## Task 5: Landing — USD price + truthful indicator

**Files:** `src/components/landing/LandingPage.tsx` (`priceCurrency: 'RUB'` ~line 387) and the pricing/offer component it renders. Read first: how the landing gets its prices (hardcoded copy vs fetched from `/plans`).

- [ ] **Step 1: Read** the landing pricing section + `landingContent.ts` to see if prices are static copy or fetched.

- [ ] **Step 2: Display USD** — set the displayed headline price to the USD figure and `priceCurrency: 'USD'` in the schema.org block (line ~387). If prices are fetched from `/plans`, read `price_usd`; if static copy, update the copy to the USD figures the user provides.

- [ ] **Step 3: Add the indicator** under the price — a small line: `Payable via SwimPay — {supportedCurrencies.join(' · ')}` (fetch `supportedCurrencies` from `/plans` if available, else a build-time constant defaulting to `['RUB']` with a clear comment to flip when SwimPay adds rails). Crypto, if advertised, is a SEPARATE line, never under the SwimPay label.

- [ ] **Step 4: Build the front** `npm run build` — succeeds (no type errors).

- [ ] **Step 5: Commit**
```bash
git add src/components/landing
git commit -m "feat(pricing): landing shows USD price + truthful SwimPay currency indicator"
```

---

## Task 6: Verify & STOP (no merge)

- [ ] **Step 1: Backend** `cd backend && npm run test:policy` (incl. the new capability spec) + `npx nest build customer-order-service gateway-service` — green.
- [ ] **Step 2: Android** `cd android && ./gradlew :app:compileDebugKotlin --rerun-tasks --max-workers=1 --no-parallel --no-daemon` — green.
- [ ] **Step 3: Front** `npm run build` — green.
- [ ] **Step 4:** `git diff --stat main...HEAD` — only intended files; no migration drops; `Order.currency`/`amount` are nullable; `supportedCurrencies` defaults to `["RUB"]`.
- [ ] **Step 5: STOP.** Report status. Merge to main+production + setting real `price_usd` values + flipping `SWIMPAY_SUPPORTED_CURRENCIES` require explicit user action (prod = image of main).

---

## Notes for the implementer

- **Additive-only invariant:** nothing RUB is removed; the RU SwimPay flow keeps reading `price_rub`. `price_usd` default 0 and the nullable Order fields guarantee existing rows/flows are untouched.
- **Truthful indicator:** the currency badges render `supportedCurrencies` from the backend (today `["RUB"]`). USD/XOF appear only when the user sets `SWIMPAY_SUPPORTED_CURRENCIES` after wiring those rails in SwimPay. Never hardcode the full list.
- **No geo, no auto-currency.** The buyer picks the currency at the SwimPay checkout. This repo only displays the USD headline + the honest "we accept these via SwimPay" note.
- **Crypto** stays the separate `crypto-pay` rail and is never shown under the SwimPay label.
