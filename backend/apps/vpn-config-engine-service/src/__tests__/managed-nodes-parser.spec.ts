import { VpnConfigService } from '../vpn-config.service';
import { createServer } from 'http';

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

  const urlEncodedNodes = service.parseManagedRuntimeNodes(encodeURIComponent(`${vlessOne}\n${trojan}`));
  assert(urlEncodedNodes.length === 2, 'URL-encoded subscription payload should decode into runtime nodes');

  const happNodes = service.parseManagedRuntimeNodes(`happ://add/${encodeURIComponent(vlessTwo)}`);
  assert(happNodes.length === 1, 'Happ add wrapper should unwrap into a runtime node');
  assert(happNodes[0].host === 'second.example', 'Happ unwrapped node host should be parsed');

  const xrayJsonNodes = service.parseManagedRuntimeNodes(JSON.stringify({
    outbounds: [
      {
        tag: 'JSON VLESS Reality',
        protocol: 'vless',
        settings: {
          vnext: [
            {
              address: 'json-vless.example',
              port: 443,
              users: [{ id: '66666666-6666-6666-6666-666666666666', flow: 'xtls-rprx-vision' }],
            },
          ],
        },
        streamSettings: {
          network: 'grpc',
          security: 'reality',
          grpcSettings: { serviceName: 'grpc-service' },
          realitySettings: { publicKey: 'PUBLICKEY', shortId: 'ab12', spiderX: '/' },
        },
      },
      {
        tag: 'JSON Trojan',
        protocol: 'trojan',
        settings: {
          servers: [{ address: 'json-trojan.example', port: 443, password: 'trojan-json-password' }],
        },
        streamSettings: { network: 'tcp', security: 'tls', tlsSettings: { serverName: 'sni.example' } },
      },
    ],
  }));
  assert(xrayJsonNodes.length === 2, 'Xray/V2Ray JSON outbounds should become runtime nodes');
  assert(xrayJsonNodes[0].protocol === 'VLESS', 'first JSON outbound should be VLESS');
  assert(xrayJsonNodes[0].host === 'json-vless.example', 'JSON VLESS host should be parsed');
  assert(xrayJsonNodes[0].transport === 'grpc', 'JSON VLESS transport should be preserved');
  assert(xrayJsonNodes[0].pbk === 'PUBLICKEY', 'JSON VLESS Reality public key should be preserved');
  assert(xrayJsonNodes[1].protocol === 'TROJAN', 'second JSON outbound should be Trojan');

  const singBoxNodes = service.parseManagedRuntimeNodes(JSON.stringify({
    outbounds: [
      {
        type: 'vless',
        tag: 'Sing VLESS',
        server: 'sing-vless.example',
        server_port: 443,
        uuid: '77777777-7777-7777-7777-777777777777',
        flow: 'xtls-rprx-vision',
        tls: {
          enabled: true,
          server_name: 'sing-sni.example',
          utls: { fingerprint: 'chrome' },
          reality: { enabled: true, public_key: 'SINGPUBLICKEY', short_id: 'cd34', spider_x: '/' },
        },
        transport: { type: 'grpc', service_name: 'sing-service' },
      },
      {
        type: 'shadowsocks',
        tag: 'Sing SS',
        server: 'sing-ss.example',
        server_port: 8388,
        method: 'aes-256-gcm',
        password: 'ss-password',
      },
    ],
  }));
  assert(singBoxNodes.length === 2, 'sing-box JSON outbounds should become runtime nodes');
  assert(singBoxNodes[0].host === 'sing-vless.example', 'sing-box VLESS host should be parsed');
  assert(singBoxNodes[1].protocol === 'SHADOWSOCKS', 'sing-box Shadowsocks node should be parsed');

  const clashNodes = service.parseManagedRuntimeNodes(`
proxies:
  - name: Clash VLESS
    type: vless
    server: clash-vless.example
    port: 443
    uuid: 88888888-8888-8888-8888-888888888888
    network: ws
    tls: true
    sni: clash-sni.example
    path: /ws
  - name: Clash Trojan
    type: trojan
    server: clash-trojan.example
    port: 443
    password: clash-password
`);
  assert(clashNodes.length === 2, 'Clash YAML proxies should become runtime nodes');
  assert(clashNodes[0].host === 'clash-vless.example', 'Clash VLESS host should be parsed');

  const subscriptionUrlNodes = service.parseManagedRuntimeNodes('https://wb.routerwb.ru/jtz5386jCHkztYRZ');
  assert(subscriptionUrlNodes.length === 0, 'https subscription URLs must not be exposed as runtime nodes');

  const server = createServer((_request, response) => {
    response.setHeader('subscription-userinfo', 'upload=10; download=20; total=100; expire=1771200000');
    response.setHeader('profile-update-interval', '2');
    response.end(Buffer.from(`${vlessOne}\n${vlessTwo}`, 'utf8').toString('base64'));
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('Unable to allocate local subscription fixture');
  }
  try {
    const resolvedNodes = await service.resolveManagedRuntimeNodes(`http://127.0.0.1:${address.port}/sub`);
    assert(resolvedNodes.length === 0, 'resolver must block localhost/private subscription URLs');
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }

  const remoteResolver = new VpnConfigService() as any;
  remoteResolver.fetchRemoteSubscriptionPayload = async () => ({
    body: Buffer.from(`${vlessOne}\n${trojan}`, 'utf8').toString('base64'),
  });
  const resolvedRemoteNodes = await remoteResolver.resolveManagedRuntimeNodes('https://supplier.example/sub');
  assert(resolvedRemoteNodes.length === 2, 'resolver should parse nodes from fetched subscription payload');
  assert(
    resolvedRemoteNodes.every((node: any) => !node.rawConfig.startsWith('http')),
    'resolved runtime nodes must not expose the supplier subscription URL as rawConfig',
  );

  const redirectServer = createServer((request, response) => {
    if (!request.headers.cookie?.includes('__hash_=ok')) {
      response.statusCode = 302;
      response.setHeader('location', request.url || '/sub');
      response.setHeader('set-cookie', '__hash_=ok; Max-Age=1800; Path=/');
      response.end();
      return;
    }
    assert(
      String(request.headers['user-agent'] || '').includes('v2rayN'),
      'remote subscription fetch should use a VPN subscription client user-agent',
    );
    response.setHeader('content-type', 'text/html; charset=utf-8');
    response.end(Buffer.from(`${vlessOne}\n${trojan}`, 'utf8').toString('base64'));
  });
  await new Promise<void>((resolve) => redirectServer.listen(0, '127.0.0.1', resolve));
  const redirectAddress = redirectServer.address();
  if (!redirectAddress || typeof redirectAddress === 'string') {
    throw new Error('Unable to allocate redirect subscription fixture');
  }
  try {
    const cookieResolver = new VpnConfigService() as any;
    cookieResolver.isBlockedHealthcheckHost = async () => false;
    const cookieNodes = await cookieResolver.resolveManagedRuntimeNodes(`http://127.0.0.1:${redirectAddress.port}/sub`);
    assert(cookieNodes.length === 2, 'resolver should preserve supplier cookies across redirects');
  } finally {
    await new Promise<void>((resolve, reject) => redirectServer.close((error) => error ? reject(error) : resolve()));
  }

  const invalidNodes = service.parseManagedRuntimeNodes('not a vpn config');
  assert(invalidNodes.length === 0, 'unsupported payload should not produce runtime nodes');

  console.log('managed nodes parser tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
