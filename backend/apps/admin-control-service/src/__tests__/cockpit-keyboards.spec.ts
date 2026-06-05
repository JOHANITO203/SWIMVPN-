import { cockpitHubKeyboard, formatCockpitHub, ADMIN_BOT_COMMANDS, cockpitPaletteKeyboard, formatCockpitPalette } from '../admin-bot.formatter';

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

console.log('cockpit-keyboards.spec.ts passed');
