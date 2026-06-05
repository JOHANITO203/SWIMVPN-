import { cockpitHubKeyboard, formatCockpitHub, ADMIN_BOT_COMMANDS, cockpitPaletteKeyboard, formatCockpitPalette, paginate, cockpitStockListKeyboard } from '../admin-bot.formatter';

function assert(c: boolean, m: string) { if (!c) throw new Error(m); }

const hub = cockpitHubKeyboard();
const rows = (hub as any).reply_markup.inline_keyboard;
const datas = rows.flat().map((b: any) => b.callback_data);
for (const d of ['cockpit:stock', 'cockpit:alerts', 'cockpit:import', 'cockpit:finance', 'cockpit:palette', 'cockpit:danger']) {
  assert(datas.includes(d), `hub exposes ${d}`);
}
assert(formatCockpitHub().includes('Cockpit'), 'hub header names the cockpit');

assert(ADMIN_BOT_COMMANDS.some((c: any) => c.command === 'cockpit'), 'menu exposes /cockpit');
assert(ADMIN_BOT_COMMANDS.every((c: any) => /^[a-z0-9_]{1,32}$/.test(c.command)), 'all command names valid');

const pal = cockpitPaletteKeyboard();
const pdatas = (pal as any).reply_markup.inline_keyboard.flat().map((b: any) => b.callback_data);
for (const d of ['stock', 'cockpit:stock_health', 'pending', 'cockpit:import', 'finance_refresh', 'cockpit:manage', 'cockpit:home']) {
  assert(pdatas.includes(d), `palette exposes ${d}`);
}
assert(formatCockpitPalette().includes('Actions'), 'palette names Actions');

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

console.log('cockpit-keyboards.spec.ts passed');
