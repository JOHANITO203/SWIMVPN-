import { VpnConfigService } from '../vpn-config.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function toBase64Url(value: string) {
  return Buffer.from(value, 'utf8').toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

const vlessOne = 'vless://11111111-1111-1111-1111-111111111111@first.example:443?security=reality&type=tcp&sni=first.example#First%20Node';
const vlessTwo = 'vless://22222222-2222-2222-2222-222222222222@second.example:8443?security=tls&type=ws&path=%2Fws#Second%20Node';
const vlessWithoutPort = 'vless://55555555-5555-5555-5555-555555555555@default-port.example?security=tls&type=tcp#Default%20Port';
const vmessPayload = toBase64Url(JSON.stringify({
  v: '2',
  ps: 'VMess Node',
  add: 'vmess.example',
  port: '2083',
  id: '33333333-3333-3333-3333-333333333333',
  net: 'ws',
  tls: 'tls',
  path: '/vmess',
  host: 'vmess-sni.example',
}));
const trojan = 'trojan://password@trojan.example:443?security=tls&type=tcp&sni=trojan.example#Trojan%20Node';
const trojanWithoutPort = 'trojan://password@trojan-default.example?security=tls&type=tcp#Trojan%20Default%20Port';

async function main() {
  const service = new VpnConfigService();

  const directNodes = service.parseManagedRuntimeNodes([
    vlessOne,
    vlessTwo,
    `vmess://${vmessPayload}`,
    trojan,
  ].join('\n'));

  assert(directNodes.length === 4, 'multi-line runtime payload should expose every supported runtime node');
  assert(directNodes[0].protocol === 'VLESS', 'first node protocol should be VLESS');
  assert(directNodes[0].host === 'first.example', 'first VLESS host should be parsed');
  assert(directNodes[0].port === 443, 'first VLESS port should be parsed');
  assert(directNodes[0].rawConfig === vlessOne, 'first VLESS raw config must be preserved intact');
  assert(directNodes[1].displayName === 'Second Node', 'VLESS hash display name should be decoded');
  assert(directNodes[2].protocol === 'VMESS', 'VMess node should be parsed from base64 JSON');
  assert(directNodes[2].host === 'vmess.example', 'VMess add field should become host');
  assert(directNodes[2].transport === 'ws', 'VMess net field should become transport');
  assert(directNodes[2].security === 'tls', 'VMess tls field should become security');
  assert(directNodes[2].rawConfig === `vmess://${vmessPayload}`, 'VMess raw config must be preserved intact');
  assert(directNodes[3].protocol === 'TROJAN', 'Trojan node should be parsed');
  assert(directNodes[3].uuid === 'password', 'Trojan password should be preserved as runtime credential');

  const defaultPortNodes = service.parseManagedRuntimeNodes([
    vlessWithoutPort,
    trojanWithoutPort,
  ].join('\n'));

  assert(defaultPortNodes.length === 2, 'runtime nodes without explicit ports should still parse');
  assert(defaultPortNodes[0].port === 443, 'VLESS without explicit port should default to 443');
  assert(defaultPortNodes[1].port === 443, 'Trojan without explicit port should default to 443');

  const encodedSubscriptionPayload = Buffer.from(`${vlessOne}\n${vlessTwo}`, 'utf8').toString('base64');
  const decodedNodes = service.parseManagedRuntimeNodes(encodedSubscriptionPayload);

  assert(decodedNodes.length === 2, 'base64 subscription payload should decode into runtime lines');
  assert(decodedNodes[0].rawConfig === vlessOne, 'decoded first raw config must be preserved intact');
  assert(decodedNodes[1].host === 'second.example', 'decoded second VLESS host should be parsed');

  const subscriptionUrlNodes = service.parseManagedRuntimeNodes('https://wb.routerwb.ru/jtz5386jCHkztYRZ');
  assert(subscriptionUrlNodes.length === 0, 'https subscription URLs must not be exposed as runtime nodes');

  const invalidNodes = service.parseManagedRuntimeNodes('not a vpn config');
  assert(invalidNodes.length === 0, 'unsupported payload should not produce runtime nodes');

  console.log('managed nodes parser tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
