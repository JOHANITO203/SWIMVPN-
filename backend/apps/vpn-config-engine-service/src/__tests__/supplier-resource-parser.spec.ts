import { VpnConfigService } from '../vpn-config.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const rawSupplierMessage = `
🔑 Ваша подписка:

https://wb.routerwb.ru/jtz5386jCHkztYRZ

⏳ Осталось: 28 дней, 19 часов, 20 минут
Истекает: 21 мая 2026 года


📱 Вы подключили: 2

📦 Тариф подписки:📁 Группа: Базовый ⚡️ 8 стран
🕒 Тариф: ⚪️ 1 мес. (−22%)
📊 Трафик: 1000 ГБ
📉 Израсходовано: 6.7 ГБ / 1000 ГБ
📱 Лимит устройств: 3


Подключите свое устройство по кнопкам ниже👇

wb.routerwb.ru (https://wb.routerwb.ru/jtz5386jCHkztYRZ)
VlessWB
VlessWB Subscription page
`.trim();

const service = new VpnConfigService();
const parsed = service.processSupplierResource(rawSupplierMessage);

assert(
  parsed.rawConfig === 'https://wb.routerwb.ru/jtz5386jCHkztYRZ',
  'supplier resource should keep the extracted subscription URL as rawConfig',
);
assert(parsed.parsedProfile.validationState === 'VALID', 'supplier resource should parse as valid');
assert(parsed.parsedProfile.address === 'wb.routerwb.ru', 'subscription host parsing failed');
assert(parsed.metadata.providerName === 'VlessWB', 'providerName parsing failed');
assert(parsed.metadata.connectedDevices === 2, 'connected devices parsing failed');
assert(parsed.metadata.deviceLimit === 3, 'device limit parsing failed');
assert(
  parsed.metadata.trafficTotalBytes === 1000 * 1024 * 1024 * 1024,
  'traffic total parsing failed',
);
assert(
  Math.abs((parsed.metadata.trafficUsedBytes || 0) - 6.7 * 1024 * 1024 * 1024) < 2048,
  'traffic used parsing failed',
);
assert(parsed.metadata.expiresAt === '2026-05-21T00:00:00Z', 'expiry parsing failed');

const vmessPayload = Buffer.from(JSON.stringify({
  v: '2',
  ps: 'Trial VMess',
  add: 'vmess-trial.example',
  port: '443',
  id: '11111111-1111-1111-1111-111111111111',
  net: 'tcp',
  tls: 'tls',
})).toString('base64');
const embeddedRuntime = service.processSupplierResource(`
Trial managed nodes:
vmess://${vmessPayload}
trojan://secret@trojan-trial.example:443?security=tls#Trojan
`.trim());

assert(
  embeddedRuntime.rawConfig === `vmess://${vmessPayload}`,
  'supplier resource should prefer embedded runtime configs before generic text fallback',
);
assert(embeddedRuntime.parsedProfile.validationState === 'VALID', 'embedded VMess should parse as valid');
assert(embeddedRuntime.parsedProfile.address === 'vmess-trial.example', 'embedded VMess host parsing failed');

console.log('supplier resource parser tests passed');
