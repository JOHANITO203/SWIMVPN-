# Admin Stock Cockpit — Phase B (surface continuity in the bot) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make the Phase A continuity engine OBSERVABLE in the admin Telegram bot: push design-aligned alerts (no-stock continuity alerts + per-pass reallocation summary + upgraded low-stock) and add a per-plan stock-health view so the admin knows what to refill.

**Architecture:** The bot (`admin-control-service`) already listens on TCP 3003 with `@EventPattern` handlers calling `adminBotService.sendAdminAlert()`. Phase A emits `continuity_alert` (no consumer yet). Phase B adds the consumer + a per-pass summary emit + pure design-aligned formatters + a `/stock_health` command fetching `list_inventory_overview`. Builds on the SAME branch as Phase A (`feat/stock-cockpit-phase-a`) so A+B merge together.

**Tech Stack:** NestJS microservices (TCP), Telegraf, Prisma; pure formatters tested via ts-node chained in `test:policy`.

**Scope:** Phase B only. OUT: the full cockpit hub/nav reorg + unified import (Phase C). Single `ADMIN_CHAT_ID` push is fine (sole admin); multi-admin fanout is out of scope.

**Machine note:** RAM-constrained. Tests = targeted ts-node specs + `nest build admin-control-service` / `inventory-delivery-service`. No assembleRelease.

---

### Task B1: Per-pass continuity summary emit (orchestrator)

**Why:** Successful reallocations are written as `AdminEvent` (DB) but not surfaced. Emit ONE low-noise summary per pass so the bot can report activity (avoids per-assignment spam).

**Files:**
- Modify: `backend/apps/inventory-delivery-service/src/resupply-orchestrator.ts`
- Modify: `backend/apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts`

- [ ] **Step 1: Extend the test** — add to the spec a new scenario asserting that after a pass with at least one reallocation OR failure, `adminClient.emit('continuity_pass_summary', {...})` was called once with `{ reallocated, failed }`. Reuse `makeDeps`. Concretely, in the EXISTING scenario 1 (a reallocation happens), add at the end:
```typescript
  assert(writes.emitted.some((m: any) => m.ev === 'continuity_pass_summary' && m.payload.reallocated === 1), 'emits per-pass summary with reallocated count');
```
And in the healthy no-op scenario (scenario 3), assert NO summary is emitted:
```typescript
  assert(!writes.emitted.some((m: any) => m.ev === 'continuity_pass_summary'), 'no summary when nothing happened');
```

- [ ] **Step 2: Run it, expect FAIL** — `cd backend ; npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts` → FAIL (no summary emitted).

- [ ] **Step 3: Implement** — at the END of `runReallocationPass`, before `return`, add:
```typescript
    if (reallocated > 0 || failed > 0) {
      this.adminClient.emit('continuity_pass_summary', { reallocated, failed });
    }
    return { checked: assignments.length, reallocated, failed };
```
(Replace the existing `return` with this block.)

- [ ] **Step 4: Run it, expect PASS.**

- [ ] **Step 5: lint + commit**
```bash
cd backend ; npm run lint
git add backend/apps/inventory-delivery-service/src/resupply-orchestrator.ts backend/apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts
git commit -m "feat(inventory): emit continuity_pass_summary once per reallocation pass"
```

---

### Task B2: Pure design-aligned formatters (TDD)

**Why:** Alert + stock-health messages must match the bot's emoji+Markdown grammar and be unit-testable (no secret leakage).

**Files:**
- Modify: `backend/apps/admin-control-service/src/admin-bot.formatter.ts` (add functions)
- Create: `backend/apps/admin-control-service/src/__tests__/continuity-formatter.spec.ts`

- [ ] **Step 1: Write the failing test** `continuity-formatter.spec.ts`:
```typescript
import {
  formatContinuityNoStockAlert,
  formatContinuityPassSummary,
  formatLowStockAlert,
  formatStockHealth,
  type StockHealthItem,
} from '../admin-bot.formatter';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const noStock = formatContinuityNoStockAlert({ category: 'WEEK', count: 2 });
assert(noStock.includes('Basic'), 'no-stock alert names the plan label');
assert(/2/.test(noStock), 'no-stock alert shows the at-risk count');
assert(noStock.includes('/stock_health') || noStock.includes('Import'), 'no-stock alert hints an action');

const summary = formatContinuityPassSummary({ reallocated: 3, failed: 1 });
assert(summary.includes('3'), 'summary shows reallocated count');
assert(summary.includes('1'), 'summary shows failed count');

const low = formatLowStockAlert({ category: 'MONTH', remaining: 1 });
assert(low.includes('Premium'), 'low-stock names the plan label');
assert(/1/.test(low), 'low-stock shows remaining');

const now = 1_000_000_000_000;
const day = 24 * 60 * 60 * 1000;
const items: StockHealthItem[] = [
  { category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 10 * day },
  { category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 2, maxResaleSlots: 2, supplierExpiresAtMs: now + 2 * day },
  { category: 'MONTH', healthStatus: 'DEGRADED', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: null },
];
const health = formatStockHealth(items, now);
assert(health.includes('Basic'), 'health view lists Basic');
assert(health.includes('Premium'), 'health view lists Premium');
assert(health.includes('Platinum'), 'health view lists Platinum (even if empty)');
assert(health.includes('1 allocatable'), 'Basic has 1 allocatable (one healthy with a free slot)');

console.log('continuity-formatter.spec.ts passed');
```

- [ ] **Step 2: Run it, expect FAIL** (functions undefined): `cd backend ; npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/continuity-formatter.spec.ts`

- [ ] **Step 3: Implement** — append to `admin-bot.formatter.ts` (reuse the existing `CATEGORY_LABELS` map at ~line 46 and `PlanCategory` import; READ them first and reuse, do not redefine):
```typescript
export interface StockHealthItem {
  category: string;
  healthStatus: string;
  usedResaleSlots: number;
  maxResaleSlots: number;
  supplierExpiresAtMs: number | null;
}

function planLabel(category: string): string {
  return CATEGORY_LABELS[category] || category;
}

export function formatContinuityNoStockAlert(input: { category: string; count: number }): string {
  return [
    '⚠️ *Continuité — stock à sec*',
    `Forfait : ${planLabel(input.category)}`,
    `${input.count} accès à risque non couverts (config mourante, aucun stock sain).`,
    'Action : importe du stock — /stock_health',
  ].join('\n');
}

export function formatContinuityPassSummary(input: { reallocated: number; failed: number }): string {
  return [
    '🔄 *Continuité — passe terminée*',
    `✅ Réallocations : ${input.reallocated}`,
    `⚠️ Échecs (stock manquant) : ${input.failed}`,
  ].join('\n');
}

export function formatLowStockAlert(input: { category: string; remaining: number }): string {
  return [
    '📉 *Stock bas*',
    `Forfait : ${planLabel(input.category)}`,
    `${input.remaining} config(s) allouable(s) restante(s) — pense à refiller.`,
  ].join('\n');
}

export function formatStockHealth(items: StockHealthItem[], nowMs: number): string {
  const day = 24 * 60 * 60 * 1000;
  const lines = ['📦 *Santé du stock par forfait*'];
  for (const category of [PlanCategory.WEEK, PlanCategory.MONTH, PlanCategory.QUARTER]) {
    const group = items.filter((i) => i.category === category);
    const allocatable = group.filter(
      (i) => i.healthStatus === 'HEALTHY' && i.usedResaleSlots < i.maxResaleSlots,
    ).length;
    const lifes = group
      .map((i) => i.supplierExpiresAtMs)
      .filter((ms): ms is number => ms !== null)
      .map((ms) => Math.max(0, Math.round((ms - nowMs) / day)));
    const avgLife = lifes.length ? Math.round(lifes.reduce((a, b) => a + b, 0) / lifes.length) : null;
    const expiringSoon = lifes.filter((d) => d <= 7).length;
    lines.push(`\n*${planLabel(String(category))}*`);
    lines.push(`${allocatable} allocatable / ${group.length} total`);
    if (avgLife !== null) lines.push(`Vie moy. ~${avgLife} j · ${expiringSoon} sous 7 j`);
  }
  return lines.join('\n');
}
```

- [ ] **Step 4: Run it, expect PASS.**

- [ ] **Step 5: chain into test:policy + lint + commit** — append to `backend/package.json` `test:policy`: ` && ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/continuity-formatter.spec.ts`. Run `npm run lint`.
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/__tests__/continuity-formatter.spec.ts backend/package.json
git commit -m "feat(admin-bot): design-aligned continuity + stock-health formatters"
```

---

### Task B3: Markdown push + continuity event handlers

**Files:**
- Modify: `backend/apps/admin-control-service/src/admin-bot.service.ts` (`sendAdminAlert` → Markdown; reuse for new alerts)
- Modify: `backend/apps/admin-control-service/src/admin.controller.ts` (new `@EventPattern` handlers)

- [ ] **Step 1: READ** `sendAdminAlert` (~1484-1491) and the existing `@EventPattern('low_stock_alert')` handler (~admin.controller.ts:23). Confirm `this.bot.telegram.sendMessage(this.adminChatId, message)` and the controller injects `adminBotService`.

- [ ] **Step 2: Make `sendAdminAlert` render Markdown** — change the send to pass `{ parse_mode: 'Markdown' }` (so the new emoji+`*bold*` messages render). Exact:
```typescript
  async sendAdminAlert(message: string) {
    if (!this.adminChatId || !this.bot) return;
    try {
      await this.bot.telegram.sendMessage(this.adminChatId, message, { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to send admin alert', error as Error);
    }
  }
```
(Existing plain-text callers still render fine under Markdown for plain strings.)

- [ ] **Step 3: Add the event handlers** in `admin.controller.ts` — mirror the existing `@EventPattern('low_stock_alert')` style, but format with the new functions. Add imports `formatContinuityNoStockAlert`, `formatContinuityPassSummary`, `formatLowStockAlert` from `./admin-bot.formatter`. Replace the EXISTING `low_stock_alert` handler body to use `formatLowStockAlert(data)` (design-aligned), and ADD:
```typescript
  @EventPattern('continuity_alert')
  async handleContinuityAlert(@Payload() data: { kind: string; category: string; assignmentId: string; reasons: string[] }) {
    if (data.kind === 'NO_STOCK') {
      await this.adminBotService.sendAdminAlert(
        formatContinuityNoStockAlert({ category: data.category, count: 1 }),
      );
    }
  }

  @EventPattern('continuity_pass_summary')
  async handleContinuityPassSummary(@Payload() data: { reallocated: number; failed: number }) {
    await this.adminBotService.sendAdminAlert(formatContinuityPassSummary(data));
  }
```
(`continuity_alert` is emitted per at-risk-no-stock assignment; keep it 1-per-event for now — the pass summary aggregates the rest. If noise becomes an issue that's a Phase-C throttle concern.)

- [ ] **Step 4: Build** — `cd backend ; npm run lint ; npx nest build admin-control-service` → 0 errors + compiled.

- [ ] **Step 5: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/admin.controller.ts
git commit -m "feat(admin-bot): consume continuity events + Markdown alert push"
```

---

### Task B4: `/stock_health` command (per-plan view)

**Files:**
- Modify: `backend/apps/admin-control-service/src/admin-bot.service.ts` (register command + handler)
- Modify: `backend/apps/admin-control-service/src/admin-bot.formatter.ts` (add `stock_health` to `ADMIN_BOT_COMMANDS`)

- [ ] **Step 1: READ** `replyStockOverview` (~821-841) for the `inventoryClient.send({ cmd: 'list_inventory_overview' }, {})` pattern + the shape of returned items (fields: `category`, `healthStatus`, `usedResaleSlots`, `maxResaleSlots`, `supplierExpiresAt`). Confirm `supplierExpiresAt` is an ISO string or Date in the overview payload (adapt the mapping below accordingly). Also READ how a command is registered (`this.bot.command('stock', ...)`).

- [ ] **Step 2: Add the command registration + handler** mirroring `replyStockOverview`:
```typescript
    this.bot.command('stock_health', async (ctx) => {
      await this.replyStockHealth(ctx);
    });
```
And the method:
```typescript
  private async replyStockHealth(ctx: any) {
    await ctx.reply('Lecture de la santé du stock...');
    try {
      const overview = await firstValueFrom(
        this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {}),
      );
      const items: StockHealthItem[] = (Array.isArray(overview) ? overview : []).map((i: any) => ({
        category: i.category,
        healthStatus: i.healthStatus,
        usedResaleSlots: i.usedResaleSlots,
        maxResaleSlots: i.maxResaleSlots,
        supplierExpiresAtMs: i.supplierExpiresAt ? new Date(i.supplierExpiresAt).getTime() : null,
      }));
      await ctx.reply(formatStockHealth(items, Date.now()), { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to read stock health', error as Error);
      await ctx.reply('Lecture de la santé du stock impossible.');
    }
  }
```
Add `formatStockHealth, type StockHealthItem` to the existing import from `./admin-bot.formatter`. Confirm `firstValueFrom` + `this.inventoryClient` are already imported/injected (they are, used by `replyStockOverview`).

- [ ] **Step 3: Register the command in the menu** — add to `ADMIN_BOT_COMMANDS` in `admin-bot.formatter.ts` (near `stock`): `{ command: 'stock_health', description: 'Stock health by plan (allocatable, life, alerts)' },`. NOTE: the formatter spec asserts every command matches `/^[a-z0-9_]{1,32}$/` — `stock_health` complies.

- [ ] **Step 4: Build + verify the command-menu spec still passes**
```bash
cd backend
npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts
npm run lint ; npx nest build admin-control-service
```
Expected: `admin bot formatter tests passed` + 0 errors + compiled.

- [ ] **Step 5: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/admin-bot.formatter.ts
git commit -m "feat(admin-bot): /stock_health per-plan stock view"
```

---

### Task B5: Full verification + demo + STOP

- [ ] **Step 1: Full policy chain** — `cd backend ; npm run test:policy` → all pass (now includes continuity-formatter spec).
- [ ] **Step 2: Builds** — `npx nest build admin-control-service ; npx nest build inventory-delivery-service`.
- [ ] **Step 3: Demo the messages (judge before deploy)** — run a throwaway ts-node snippet that prints `formatContinuityNoStockAlert`, `formatContinuityPassSummary`, `formatLowStockAlert`, and `formatStockHealth` with sample data, so the exact Telegram message text can be reviewed without deploying. (Do NOT commit the snippet.)
- [ ] **Step 4: STOP** — report. Do NOT merge. A+B merge to main+prod (Dokploy redeploys inventory-delivery + admin-control) requires user go-ahead. After deploy, the admin judges the live bot.

---

## Notes
- Single `ADMIN_CHAT_ID` push (sole admin) — multi-admin fanout is out of scope.
- `continuity_alert` (NO_STOCK) fires per at-risk-uncovered assignment; the per-pass summary aggregates reallocations/failures. If alert volume is ever noisy, add throttling in Phase C.
- The stock-health view shows supplier-life-based stock levels (what to refill); precise per-assignment at-risk counts are surfaced via the pass-summary/no-stock alerts (engine-side, where the sold-promise is known).
