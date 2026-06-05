# Admin Stock Cockpit — Phase C (cockpit UX reorg) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Turn the flat ~28-command bot into an interactive **cockpit**: a persistent hub, a discoverable **🧩 Actions palette** (run actions by buttons, no command memorization), button-driven stock navigation (pick a config — no raw IDs), and a unified import entry. Text commands stay as power-user shortcuts.

**Architecture:** All new cockpit SCREENS are callback-driven and refresh **in place** via `ctx.editMessageText`. They reuse the EXISTING machinery (`replyInventoryReview` + `getInventoryActionKeyboard`, `inventoryClient.send({cmd:'list_inventory_overview'})`, the import wizard sessions, finance dashboard). Auth is already enforced by the global `this.bot.use()` middleware — new commands/actions inherit it. Branch: `feat/stock-cockpit-phase-c` (off main 8bd3670).

**Tech Stack:** Telegraf (`Markup.inlineKeyboard`, `ctx.editMessageText`, `ctx.answerCbQuery`, `this.bot.action(/regex/)`), NestJS, Prisma. Pure keyboard/pagination builders tested via ts-node chained in `test:policy`.

**Callback-data convention (new cockpit routes):** `cockpit:<screen>[:<arg>...]` (e.g. `cockpit:home`, `cockpit:palette`, `cockpit:stock`, `cockpit:stocklist:WEEK:0`). Existing routes (`review:scope:id`, `disable:...`, etc.) are REUSED unchanged.

**Convention for cockpit screens:** a screen renderer takes `ctx` + an `edit: boolean`. When invoked from a callback (`ctx.callbackQuery` truthy) → `ctx.editMessageText(text, {parse_mode:'Markdown', ...keyboard})`; when from a `/command` → `ctx.reply(...)`. Mirror the existing `replyFinanceDashboard(ctx, isUpdate)` pattern (admin-bot.service.ts:1466).

**Machine note:** RAM-constrained. Tests = ts-node specs + `nest build admin-control-service`. No assembleRelease.

---

### Task C1: Hub screen + grouped command menu

**Files:**
- Modify: `backend/apps/admin-control-service/src/admin-bot.formatter.ts` (add `cockpitHubKeyboard()`, `formatCockpitHub()`; regroup `ADMIN_BOT_COMMANDS` ordering with a `/cockpit` + `/menu` entry)
- Modify: `backend/apps/admin-control-service/src/admin-bot.service.ts` (register `/cockpit`,`/menu`; point `/start`,`/help` at the hub; add `cockpit:home` action)
- Test: `backend/apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts`

- [ ] **Step 1: Write failing test** `cockpit-keyboards.spec.ts`:
```typescript
import { cockpitHubKeyboard, formatCockpitHub, ADMIN_BOT_COMMANDS } from '../admin-bot.formatter';

function assert(c: boolean, m: string) { if (!c) throw new Error(m); }

// Hub keyboard: a Telegraf Markup with inline_keyboard rows; each button has text + callback_data.
const hub = cockpitHubKeyboard();
const rows = (hub as any).reply_markup.inline_keyboard;
const datas = rows.flat().map((b: any) => b.callback_data);
for (const d of ['cockpit:stock', 'cockpit:alerts', 'cockpit:import', 'cockpit:finance', 'cockpit:palette', 'cockpit:danger']) {
  assert(datas.includes(d), `hub exposes ${d}`);
}
assert(formatCockpitHub().includes('Cockpit'), 'hub header names the cockpit');

// Command menu still valid + exposes the hub entry.
assert(ADMIN_BOT_COMMANDS.some((c: any) => c.command === 'cockpit'), 'menu exposes /cockpit');
assert(ADMIN_BOT_COMMANDS.every((c: any) => /^[a-z0-9_]{1,32}$/.test(c.command)), 'all command names valid');

console.log('cockpit-keyboards.spec.ts passed');
```

- [ ] **Step 2: Run, expect FAIL** — `cd backend ; npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts`

- [ ] **Step 3: Implement formatter additions** — READ the existing `Markup` import + `ADMIN_BOT_COMMANDS` in `admin-bot.formatter.ts`. Add (reuse `Markup` from telegraf — confirm it's imported there or import it):
```typescript
export function formatCockpitHub(): string {
  return [
    '🎛️ *SWIMVPN — Cockpit admin*',
    'Choisis une section ci-dessous.',
  ].join('\n');
}

export function cockpitHubKeyboard() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('📦 Stock', 'cockpit:stock'), Markup.button.callback('⚠️ Alertes', 'cockpit:alerts')],
    [Markup.button.callback('📥 Import', 'cockpit:import'), Markup.button.callback('💰 Finance', 'cockpit:finance')],
    [Markup.button.callback('🧩 Actions', 'cockpit:palette'), Markup.button.callback('🛑 Danger', 'cockpit:danger')],
  ]);
}
```
Add a `{ command: 'cockpit', description: 'Open the admin cockpit (menu)' }` and `{ command: 'menu', description: 'Open the admin cockpit (menu)' }` near the top of `ADMIN_BOT_COMMANDS` (after help).

- [ ] **Step 4: Implement service wiring** — in `setupCommands()`:
  - Add `this.bot.command('cockpit', async (ctx) => { await this.replyCockpitHub(ctx, false); });` and the same for `'menu'`.
  - Change `/start` and `/help` handlers to call `this.replyCockpitHub(ctx, false)` (keep `helpText()` reachable as a fallback inside the palette later).
  - Add the renderer + action:
```typescript
  private async replyCockpitHub(ctx: any, edit: boolean) {
    const text = formatCockpitHub();
    const kb = cockpitHubKeyboard();
    if (edit && ctx.callbackQuery) {
      await ctx.editMessageText(text, { parse_mode: 'Markdown', ...kb });
    } else {
      await ctx.reply(text, { parse_mode: 'Markdown', ...kb });
    }
  }
```
  - Add action `this.bot.action(/^cockpit:home$/, async (ctx) => { await ctx.answerCbQuery(); await this.replyCockpitHub(ctx, true); });`
  - Import `formatCockpitHub`, `cockpitHubKeyboard` from `./admin-bot.formatter`.

- [ ] **Step 5: Run test (PASS) + build** — the spec passes; `npm run lint ; npx nest build admin-control-service`.

- [ ] **Step 6: Chain spec into test:policy + commit** — append ` && ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts` to `test:policy`.
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts backend/package.json
git commit -m "feat(admin-bot): cockpit hub screen + /cockpit /menu + grouped menu"
```

---

### Task C2: 🧩 Actions palette (discoverable commands)

**Why:** The user's core ask — run every action from labeled buttons, no command memorization. ID-targeted actions route into the stock picker (Task C3).

**Files:**
- Modify: `admin-bot.formatter.ts` (`cockpitPaletteKeyboard()`, `formatCockpitPalette()`)
- Modify: `admin-bot.service.ts` (`cockpit:palette` + `cockpit:danger` actions)
- Test: extend `cockpit-keyboards.spec.ts`

- [ ] **Step 1: Extend the test** — append:
```typescript
const pal = cockpitPaletteKeyboard();
const pdatas = (pal as any).reply_markup.inline_keyboard.flat().map((b: any) => b.callback_data);
for (const d of ['stock', 'cockpit:stock_health', 'pending', 'cockpit:import', 'finance_refresh', 'cockpit:manage', 'cockpit:home']) {
  assert(pdatas.includes(d), `palette exposes ${d}`);
}
assert(formatCockpitPalette().includes('Actions'), 'palette names Actions');
```
(Run → FAIL.)

- [ ] **Step 2: Implement formatter** — buttons reuse EXISTING callback datas where they exist (`stock`, `pending`, `finance_refresh`, `add_expense_start`, `import_menu`) + new cockpit routes for the rest:
```typescript
export function formatCockpitPalette(): string {
  return [
    '🧩 *Actions du bot*',
    'Tape un bouton — pas besoin de retenir les commandes.',
  ].join('\n');
}

export function cockpitPaletteKeyboard() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('📦 Stock', 'stock'), Markup.button.callback('📊 Santé stock', 'cockpit:stock_health')],
    [Markup.button.callback('⏳ En attente', 'pending'), Markup.button.callback('🧾 Commandes', 'cockpit:orders')],
    [Markup.button.callback('🔧 Gérer une config', 'cockpit:manage'), Markup.button.callback('♻️ Vérif santé', 'cockpit:healthcheck')],
    [Markup.button.callback('📥 Importer', 'cockpit:import')],
    [Markup.button.callback('💰 Finance', 'finance_refresh'), Markup.button.callback('➕ Dépense', 'add_expense_start')],
    [Markup.button.callback('👥 Users', 'cockpit:users'), Markup.button.callback('⬅️ Retour', 'cockpit:home')],
  ]);
}
```

- [ ] **Step 3: Implement service actions** — add:
  - `this.bot.action(/^cockpit:palette$/, ...)` → `ctx.answerCbQuery()` + `editMessageText(formatCockpitPalette(), {parse_mode:'Markdown', ...cockpitPaletteKeyboard()})`.
  - `this.bot.action(/^cockpit:stock_health$/, ...)` → `answerCbQuery('Lecture...')` + call `this.replyStockHealth(ctx)` (existing).
  - `this.bot.action(/^cockpit:healthcheck$/, ...)` → `answerCbQuery('Vérification...')` + reuse the `/healthcheck` body (extract it to a `private async runHealthcheckReply(ctx)` if inline today, and call from both the command and this action).
  - `this.bot.action(/^cockpit:orders$/, ...)` and `/^cockpit:users$/` → reuse the `/orders` and `/users` bodies (extract to `private async replyRecentOrders(ctx)` / `replyUsersStats(ctx)` and call from both command + action).
  - `this.bot.action(/^cockpit:danger$/, ...)` → editMessageText a short danger screen with buttons routing to the manage-picker in "danger mode" (`cockpit:manage`) + back. (Keep it simple: danger = the same manage picker; delete/superdelete already live on the item detail keyboard.)
  - Import the two new formatter fns.
  NOTE: extracting `/orders`,`/users`,`/healthcheck` bodies into private reply-methods is in-scope (DRY so both command + palette button reuse them). Do not change their behavior.

- [ ] **Step 4: Build + test** — `npx ts-node ... cockpit-keyboards.spec.ts` PASS; `npm run lint ; npx nest build admin-control-service`.

- [ ] **Step 5: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts
git commit -m "feat(admin-bot): cockpit Actions palette (discoverable, button-driven)"
```

---

### Task C3: Stock navigation by buttons (pick a config — no raw IDs)

**Files:**
- Modify: `admin-bot.formatter.ts` (`cockpitStockCategoriesKeyboard()`, `cockpitStockListKeyboard(items, category, page)`, `paginate()` helper)
- Modify: `admin-bot.service.ts` (`cockpit:stock`, `cockpit:stocklist:CATEGORY:PAGE`, `cockpit:manage` actions)
- Test: extend `cockpit-keyboards.spec.ts` (pagination + list keyboard)

- [ ] **Step 1: Extend the test** — pure pagination + list-keyboard:
```typescript
import { paginate, cockpitStockListKeyboard } from '../admin-bot.formatter';
const arr = Array.from({ length: 13 }, (_, i) => ({ id: 'id' + i, folderCode: 'F' + i, healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2 }));
const p0 = paginate(arr, 0, 5);
assert(p0.items.length === 5 && p0.page === 0 && p0.totalPages === 3, 'page 0 of 13 @5');
const p2 = paginate(arr, 2, 5);
assert(p2.items.length === 3 && !p2.hasNext && p2.hasPrev, 'last page partial');
const lk = cockpitStockListKeyboard(p0.items as any, 'WEEK', 0, p0.totalPages);
const ld = (lk as any).reply_markup.inline_keyboard.flat().map((b: any) => b.callback_data);
assert(ld.some((d: string) => d.startsWith('review:paid:id0')), 'item button routes to existing review:paid:<id>');
assert(ld.includes('cockpit:stocklist:WEEK:1'), 'next-page button present');
assert(ld.includes('cockpit:stock'), 'back-to-categories present');
```
(Run → FAIL.)

- [ ] **Step 2: Implement formatter** —
```typescript
export function paginate<T>(items: T[], page: number, size: number) {
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  const p = Math.min(Math.max(0, page), totalPages - 1);
  const start = p * size;
  return { items: items.slice(start, start + size), page: p, totalPages, hasPrev: p > 0, hasNext: p < totalPages - 1 };
}

export function cockpitStockCategoriesKeyboard() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('Basic 🟢', 'cockpit:stocklist:WEEK:0'), Markup.button.callback('Premium 💎', 'cockpit:stocklist:MONTH:0')],
    [Markup.button.callback('Platinum 🏆', 'cockpit:stocklist:QUARTER:0')],
    [Markup.button.callback('⬅️ Retour', 'cockpit:home')],
  ]);
}

export interface CockpitStockListItem {
  id: string; folderCode?: string | null; healthStatus: string; usedResaleSlots: number; maxResaleSlots: number;
}

export function cockpitStockListKeyboard(items: CockpitStockListItem[], category: string, page: number, totalPages: number) {
  const rows: any[] = items.map((i) => [
    Markup.button.callback(
      `${getStatusEmoji(i.healthStatus)} ${i.folderCode || i.id.slice(0, 8)} (${i.usedResaleSlots}/${i.maxResaleSlots})`,
      `review:paid:${i.id}`,
    ),
  ]);
  const nav: any[] = [];
  if (page > 0) nav.push(Markup.button.callback('◀️', `cockpit:stocklist:${category}:${page - 1}`));
  if (page < totalPages - 1) nav.push(Markup.button.callback('▶️', `cockpit:stocklist:${category}:${page + 1}`));
  if (nav.length) rows.push(nav);
  rows.push([Markup.button.callback('⬅️ Forfaits', 'cockpit:stock'), Markup.button.callback('🏠 Hub', 'cockpit:home')]);
  return Markup.inlineKeyboard(rows);
}
```
(`getStatusEmoji` already exists in the formatter — reuse it.)

- [ ] **Step 3: Implement service actions** —
  - `this.bot.action(/^cockpit:stock$/, ...)` → `answerCbQuery()` + editMessageText('📦 *Stock — choisis un forfait*', {parse_mode, ...cockpitStockCategoriesKeyboard()}).
  - `this.bot.action(/^cockpit:stocklist:(WEEK|MONTH|QUARTER):(\d+)$/, async (ctx) => {...})`:
```typescript
      await ctx.answerCbQuery('Chargement...');
      const category = ctx.match[1];
      const page = parseInt(ctx.match[2], 10) || 0;
      const overview = await firstValueFrom(this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {}));
      const items = (Array.isArray(overview) ? overview : []).filter((i: any) => i.category === category);
      const pg = paginate(items, page, 5);
      const text = `📦 *${category}* — ${items.length} config(s) · page ${pg.page + 1}/${pg.totalPages}`;
      await ctx.editMessageText(text, { parse_mode: 'Markdown', ...cockpitStockListKeyboard(pg.items as any, category, pg.page, pg.totalPages) });
```
  - `this.bot.action(/^cockpit:manage$/, ...)` → same as `cockpit:stock` (the picker; tapping an item opens `review:paid:<id>` whose existing keyboard already has Disable/Expire/Delete/SuperDelete). answerCbQuery + edit to categories with a manage hint.
  Import `paginate`, `cockpitStockCategoriesKeyboard`, `cockpitStockListKeyboard` from the formatter.
  NOTE: tapping an item triggers the EXISTING `review:paid:<id>` action (admin-bot.service.ts:626) → `replyInventoryReview` which today `ctx.reply`s the detail + `getInventoryActionKeyboard`. That's acceptable (detail as a fresh message with its action buttons). Do NOT refactor replyInventoryReview here.

- [ ] **Step 4: Build + test** — spec PASS; `npm run lint ; npx nest build admin-control-service`.

- [ ] **Step 5: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts
git commit -m "feat(admin-bot): cockpit stock navigation (category -> paginated list -> review)"
```

---

### Task C4: Unified import entry

**Files:**
- Modify: `admin-bot.formatter.ts` (`cockpitImportKeyboard()`)
- Modify: `admin-bot.service.ts` (`cockpit:import` action + `cockpit:import:paid` / `cockpit:import:trial`)

- [ ] **Step 1: Implement formatter**
```typescript
export function cockpitImportKeyboard() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('💳 Payant (forfait)', 'cockpit:import:paid')],
    [Markup.button.callback('🧪 Trial', 'cockpit:import:trial')],
    [Markup.button.callback('⬅️ Retour', 'cockpit:home')],
  ]);
}
```

- [ ] **Step 2: Implement service actions** —
  - `this.bot.action(/^cockpit:import$/, ...)` → answerCbQuery + editMessageText('📥 *Import* — paye ou trial ?', {parse_mode, ...cockpitImportKeyboard()}).
  - `this.bot.action(/^cockpit:import:paid$/, ...)` → answerCbQuery + start the EXISTING paid wizard: `this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'category' }); await ctx.reply(formatImportWizardCategoryPrompt());` (reuse the exact `/add_wizard` body).
  - `this.bot.action(/^cockpit:import:trial$/, ...)` → answerCbQuery + `this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'trial_config' }); await ctx.reply(formatTrialImportWizardConfigPrompt());` (reuse `/trial_wizard` body).
  Import `cockpitImportKeyboard` (+ confirm `formatImportWizardCategoryPrompt`/`formatTrialImportWizardConfigPrompt` already imported — they are, used by the commands).

- [ ] **Step 3: Build** — `npm run lint ; npx nest build admin-control-service`.

- [ ] **Step 4: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/admin-bot.service.ts
git commit -m "feat(admin-bot): cockpit unified import entry (paid/trial -> existing wizard)"
```

---

### Task C5: Alerts view (recent continuity/stock events)

**Files:**
- Modify: `admin-bot.formatter.ts` (`formatRecentAlerts(events)`)
- Modify: `admin-bot.service.ts` (`cockpit:alerts` action — query recent AdminEvents)
- Test: extend `cockpit-keyboards.spec.ts` (formatRecentAlerts pure)

- [ ] **Step 1: Extend test**
```typescript
import { formatRecentAlerts } from '../admin-bot.formatter';
const evtxt = formatRecentAlerts([
  { event_type: 'REALLOCATION_FAILED_NO_STOCK', payload_json: { category: 'WEEK' }, created_at: '2026-06-05T10:00:00.000Z' },
  { event_type: 'ASSIGNMENT_REALLOCATED', payload_json: { toItemId: 'x' }, created_at: '2026-06-05T09:00:00.000Z' },
]);
assert(evtxt.includes('Alertes') || evtxt.includes('Continuité'), 'alerts view has a header');
assert(evtxt.includes('REALLOCATION_FAILED_NO_STOCK') || evtxt.includes('stock'), 'shows the failure event');
const empty = formatRecentAlerts([]);
assert(empty.toLowerCase().includes('aucune') || empty.toLowerCase().includes('rien'), 'empty state');
```
(Run → FAIL.)

- [ ] **Step 2: Implement formatter**
```typescript
export interface RecentAlertEvent { event_type: string; payload_json: any; created_at: string | Date; }

export function formatRecentAlerts(events: RecentAlertEvent[]): string {
  if (!events.length) return '⚠️ *Alertes continuité/stock*\nAucune alerte récente.';
  const lines = ['⚠️ *Alertes continuité/stock* (récentes)'];
  for (const e of events.slice(0, 10)) {
    const when = String(e.created_at).slice(0, 16).replace('T', ' ');
    const cat = e.payload_json?.category ? ` ${e.payload_json.category}` : '';
    lines.push(`• ${when} — ${e.event_type}${cat}`);
  }
  return lines.join('\n');
}
```

- [ ] **Step 3: Implement service action** — `this.bot.action(/^cockpit:alerts$/, async (ctx) => {...})`:
```typescript
      await ctx.answerCbQuery('Lecture des alertes...');
      const events = await this.prisma.adminEvent.findMany({
        where: { event_type: { in: ['REALLOCATION_FAILED_NO_STOCK', 'ASSIGNMENT_REALLOCATED', 'CONFIG_ASSIGNED'] } },
        orderBy: { created_at: 'desc' },
        take: 10,
      });
      const back = Markup.inlineKeyboard([[Markup.button.callback('⬅️ Retour', 'cockpit:home')]]);
      await ctx.editMessageText(formatRecentAlerts(events as any), { parse_mode: 'Markdown', ...back });
```
(`this.prisma.adminEvent` is available — used elsewhere in the service. Confirm by reading. Import `formatRecentAlerts` + `Markup` if not already.)

- [ ] **Step 4: Build + test** — spec PASS; `npm run lint ; npx nest build admin-control-service`.

- [ ] **Step 5: Commit**
```bash
git add backend/apps/admin-control-service/src/admin-bot.formatter.ts backend/apps/admin-control-service/src/admin-bot.service.ts backend/apps/admin-control-service/src/__tests__/cockpit-keyboards.spec.ts
git commit -m "feat(admin-bot): cockpit alerts view (recent continuity/stock events)"
```

---

### Task C6: Full verification

- [ ] **Step 1: Full chain** — `cd backend ; npm run test:policy` → all pass (incl. cockpit-keyboards + the pre-existing admin-bot-formatter spec, which asserts every ADMIN_BOT_COMMANDS entry matches `/^[a-z0-9_]{1,32}$/` and required commands exist — confirm `/cockpit`,`/menu` additions don't break it).
- [ ] **Step 2: Build** — `npx nest build admin-control-service`.
- [ ] **Step 3: Report** — DONE. (Controller then merges A-less, this is Phase C only, to main+prod per user instruction.)

---

## Notes
- Cockpit screens refresh in place (`editMessageText`); leaf actions (review/disable/expire/delete) reuse the EXISTING handlers (which `reply` the detail + action keyboard) — acceptable, not refactored here.
- Auth: inherited from the global `this.bot.use()` middleware; no per-action gate needed.
- The 🧩 palette routes ID-targeted actions through the stock picker (`cockpit:manage` → category → item → existing review/action keyboard), so the admin never types an ID.
- Text commands (`/disable <id>`, etc.) remain as power-user shortcuts.
- Out of scope: standardizing ALL legacy screens to editMessageText (the leaf review/finance flows keep their current reply behavior); throttling alert volume.
