import { createServer } from 'net';
import { VpnConfigService } from '../vpn-config.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

async function listenLocalhost(): Promise<{ port: number; close: () => Promise<void> }> {
  const server = createServer((socket) => socket.end());
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('Unable to allocate localhost test port');
  }
  return {
    port: address.port,
    close: () => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())),
  };
}

async function main() {
  const local = await listenLocalhost();
  try {
    const service = new VpnConfigService();
    const health = await service.checkHealth(
      `vless://11111111-1111-1111-1111-111111111111@127.0.0.1:${local.port}?security=tls#Localhost`,
    );

    assert(
      health.alive === false,
      'inventory healthcheck must not scan loopback/private destinations from supplier configs',
    );
  } finally {
    await local.close();
  }
}

main()
  .then(() => console.log('healthcheck SSRF policy tests passed'))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
