# USD Pricing + Multi-Currency Indicator — Design

> Brainstormed 2026-06-06. Scopes ONLY the SWIMVPN- repo's side. The multi-currency payment rails,
> currency selection, and collection live in the separate **SwimPay** project (the user's parallel
> work) and are out of scope here.

## 1. Decisions (locked)

- **Catalog price is USD.** A plan's canonical price is a single USD figure — one source of truth,
  which also anchors settlement (receive USD/USDT → swap to local). No per-country price tables, no
  PPP overrides in this repo (the user owns any local-amount logic on the SwimPay side).
- **No geo-detection.** A VPN structurally breaks IP geolocation (the product hides the user's
  country), so we do NOT detect country/currency. **The user chooses how to pay at the SwimPay
  checkout.** This repo never guesses a currency.
- **Truthful "supported currencies" indicator.** The pricing surfaces a clear note that SwimPay
  accepts multiple currencies (USD · RUB · XOF) to reassure the buyer and promote the aggregator. The
  list is **data-driven by actual SwimPay capability**, never hardcoded — we never advertise a
  currency SwimPay cannot yet collect.
- **SwimPay owns the rest.** SwimPay reads the catalog (USD price + supported currencies), presents
  the payment-method/currency choice, collects, and reports the paid currency+amount back.

## 2. Honest scope split

- **This repo (buildable now):** make the plan price USD; expose it + the supported-currency
  capability via the offers API; display the USD price + the truthful currency indicator on the app
  and the landing; add the Order fields that SwimPay will populate.
- **SwimPay (the user, separate repo):** the rail-first checkout, currency selection, conversion,
  collection, and the confirmation callback.
- **Dependency:** the Order/accounting side depends on SwimPay's callback shape (what it reports on a
  paid order). This spec defines the **contract**; the fields are added here, SwimPay fills them.

## 3. Data model (backend NestJS / Prisma)

- **`Plan.price_usd Decimal(10,2)`** — the canonical catalog price (source of truth). **Keep
  `Plan.price_rub`** as the price of the *RUB rail* specifically (the existing Russian SwimPay flow
  keeps working in RUB unchanged). `price_usd` is additive; nothing RUB is removed.
- **`Order`** gains **`currency String`** and **`amount Decimal(12,2)`** = the currency and amount the
  buyer actually paid (RUB for the RU rail, USD/XOF for others). `amount_rub` stays for the existing
  RU accounting; for non-RUB orders it is null or an FX-normalized figure SwimPay supplies.
- **Supported-currency capability:** a small config/flag (e.g. `PaymentCapability.supportedCurrencies:
  string[]`) — ideally fed from SwimPay's live capability, or a backend config the user flips as rails
  go live. Drives the indicator so it stays truthful.
- Migration: add `price_usd` (backfill from a one-time chosen USD figure per plan — the user sets
  these, NOT an FX conversion), add `Order.currency`/`amount`, add the capability config. Reversible,
  additive.

## 4. Offers API

- The existing offers/plans endpoint returns, per plan: `{ priceUsd, supportedCurrencies }` (and the
  legacy `priceRub` while the RU flow needs it). One endpoint for the app AND the landing.
- No currency resolution, no geo — the response is currency-neutral (USD catalog + the supported list).

## 5. Clients (Android app + landing Vite/React)

- **Display the USD price** as the headline figure on every offer (app subscribe screen + landing
  pricing section).
- **Truthful currency indicator** next to/under the price: a clear note + badges, e.g.
  `Payable via SwimPay — 🇺🇸 USD · 🇷🇺 RUB · 🌍 XOF`, rendered from `supportedCurrencies` (so XOF only
  appears once the capability flag includes it). i18n strings ×4 (fr/en/fr/ru) for the note.
- **No geo, no auto-currency, no emphasis.** The currency choice happens at the SwimPay checkout the
  buyer opens — this repo only shows the USD price + the honest "we accept these" note.
- The "Subscribe/Pay" action hands SwimPay the plan id + `priceUsd`; SwimPay drives the rest.

## 6. SwimPay ⇄ repo contract (interface, not built here)

- **SwimPay reads:** `{ planId, priceUsd, supportedCurrencies }` from the offers API.
- **SwimPay reports back** on a confirmed payment: `{ planId, userRef, paidCurrency, paidAmount,
  providerRef, ... }` → this repo records it on the `Order` (currency + amount) and fulfils as today.
- Until SwimPay supports a given currency, that currency is absent from `supportedCurrencies` → the
  indicator doesn't show it, and the only live rails remain whatever SwimPay currently has (RUB +
  crypto). No false promises.

## 7. Out of scope / deferred

- All SwimPay internals: rails (mobile money / card / crypto), currency selection UX, conversion,
  collection, settlement (Grey/Payoneer/stablecoin treasury).
- PPP / local-amount calibration (the user handles any non-USD amount logic in SwimPay).
- **Admin revenue-report normalization** across currencies: deferred until multi-currency orders
  actually exist. When they do, the admin bot's RUB sums must normalize multi-currency orders to one
  reporting unit (USD or RUB) — a follow-up once SwimPay emits non-RUB orders.

## 8. Honest limit

This repo's piece is small and is **not independently valuable** — a USD price + an honest "we accept
USD/RUB/XOF" note does nothing until SwimPay actually collects those currencies. Its value is to make
the catalog + buyer-facing promise USD-anchored and truthful so SwimPay can plug in. Sequencing-wise it
can ship anytime (it degrades gracefully: today the indicator shows only what SwimPay collects, i.e.
RUB + crypto), and lights up as the user turns rails on in SwimPay.
