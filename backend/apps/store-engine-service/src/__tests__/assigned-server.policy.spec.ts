import { of } from 'rxjs';
import { StoreService, parseRuntimeEndpoint } from '../store.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

async function main() {
  const parsed = parseRuntimeEndpoint('vless://uuid@example.com:8443?security=tls#Berlin%20Premium');
  assert(parsed?.host === 'example.com', 'VLESS host should be parsed from assigned config');
  assert(parsed?.port === 8443, 'VLESS port should be parsed from assigned config');
  assert(parsed?.protocol === 'VLESS', 'VLESS protocol should be preserved');
  assert(parsed?.displayName === 'Berlin Premium', 'VLESS display name should be decoded');

  const subscriptionUrl = parseRuntimeEndpoint('https://wb.routerwb.ru/jtz5386jCHkztYRZ');
  assert(subscriptionUrl === null, 'supplier subscription URLs must not be exposed as HTTPS runtime servers');

  let healthStatus = 'HEALTHY';
  let measuredUsedBytes = 1n;
  let assignmentExpiresAt: Date | null = new Date(Date.now() + 60_000);
  let supplierExpiresAt: Date | null = new Date(Date.now() + 60_000);
  let rawConfig = [
    'vless://uuid-one@assigned-one.example:443?security=tls#Assigned%20One',
    'vless://uuid-two@assigned-two.example:8443?security=reality#Assigned%20Two',
  ].join('\n');
  const vpnConfigClient = {
    send: () => of([
      {
        id: 'node-1',
        rawConfig: 'vless://uuid-one@assigned-one.example:443?security=tls#Assigned%20One',
        protocol: 'VLESS',
        host: 'assigned-one.example',
        port: 443,
        security: 'tls',
        transport: 'tcp',
        displayName: 'Assigned One',
      },
      {
        id: 'node-2',
        rawConfig: 'vless://uuid-two@assigned-two.example:8443?security=reality#Assigned%20Two',
        protocol: 'VLESS',
        host: 'assigned-two.example',
        port: 8443,
        security: 'reality',
        transport: 'tcp',
        displayName: 'Assigned Two',
      },
    ]),
  };
  const service = new StoreService({
    customer: {
      findUnique: async () => ({
        id: 'customer-1',
        public_id: 'SW-TEST',
        device_id: 'device-1',
        orders: [
          {
            id: 'order-1',
            order_ref: 'ORD-1',
            payment_ref: 'CARD_MANUAL:APPROVED',
            fulfilled_at: new Date('2020-01-01T00:00:00.000Z'),
            plan: { code: 'MONTH', quota_label: '50 GB' },
            assignments: [
              {
                id: 'assignment-1',
                access_status: 'ACTIVE',
                expires_at: assignmentExpiresAt,
                measured_used_bytes: measuredUsedBytes,
                inventory_item: {
                  id: 'inventory-1',
                  raw_config: rawConfig,
                  config_type: 'VLESS',
                  display_protocol: 'VLESS',
                  batch_name: 'Assigned batch',
                  supplier_provider_name: 'Provider',
                  health_status: healthStatus,
                  source_quota_bytes: BigInt(1_000),
                  source_used_bytes: BigInt(1),
                  supplier_expires_at: supplierExpiresAt,
                },
              },
            ],
          },
        ],
      }),
    },
  } as any, vpnConfigClient as any);

  const servers = await service.getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(servers.length === 2, 'active assignment should expose every managed runtime node');
  assert(servers[0].id.startsWith('assignment:assignment-1:'), 'server id should be tied to assignment and node');
  assert(servers[0].host === 'assigned-one.example', 'first server host should come from assigned raw config');
  assert(
    servers[0].rawConfig === 'vless://uuid-one@assigned-one.example:443?security=tls#Assigned%20One',
    'first server must include its preserved raw runtime config',
  );
  assert(servers[1].host === 'assigned-two.example', 'second server host should come from assigned raw config');
  assert(servers[0].source === 'backend', 'assigned premium server should be marked backend source');
  assert(servers[0].load === 0, 'server load should reflect source quota usage percent');
  assert(servers[0].trafficUsedBytes === '1', 'server should expose source used bytes as a JSON-safe string');
  assert(servers[0].trafficTotalBytes === '1000', 'server should expose source quota bytes as a JSON-safe string');
  assert(servers[0].availabilityStatus === 'AVAILABLE', 'healthy assigned source should expose available status');

  rawConfig = 'https://wb.routerwb.ru/jtz5386jCHkztYRZ';
  const subscriptionUrlServers = await new StoreService({
    customer: {
      findUnique: async () => ({
        id: 'customer-1',
        public_id: 'SW-TEST',
        device_id: 'device-1',
        orders: [
          {
            id: 'order-1',
            order_ref: 'ORD-1',
            payment_ref: 'CARD_MANUAL:APPROVED',
            fulfilled_at: new Date('2020-01-01T00:00:00.000Z'),
            plan: { code: 'MONTH', quota_label: '50 GB' },
            assignments: [
              {
                id: 'assignment-1',
                access_status: 'ACTIVE',
                expires_at: assignmentExpiresAt,
                measured_used_bytes: measuredUsedBytes,
                inventory_item: {
                  id: 'inventory-1',
                  raw_config: rawConfig,
                  config_type: 'SUBSCRIPTION',
                  display_protocol: 'SUBSCRIPTION',
                  batch_name: 'Subscription batch',
                  supplier_provider_name: 'Provider',
                  health_status: healthStatus,
                  source_quota_bytes: BigInt(1_000),
                  source_used_bytes: BigInt(1),
                  supplier_expires_at: supplierExpiresAt,
                },
              },
            ],
          },
        ],
      }),
    },
  } as any, { send: () => of([]) } as any).getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(subscriptionUrlServers.length === 0, 'https subscription URLs must not be exposed as runtime servers');
  rawConfig = [
    'vless://uuid-one@assigned-one.example:443?security=tls#Assigned%20One',
    'vless://uuid-two@assigned-two.example:8443?security=reality#Assigned%20Two',
  ].join('\n');

  measuredUsedBytes = 50n * 1024n * 1024n * 1024n;
  const planQuotaExceededServers = await service.getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(planQuotaExceededServers.length === 0, 'plan quota exhaustion must block premium servers');
  measuredUsedBytes = 1n;

  healthStatus = 'FULL';
  const fullServers = await service.getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(fullServers.length === 2, 'FULL inventory should still serve already assigned active customers');
  assert(fullServers[0].availabilityStatus === 'CONGESTED', 'FULL assigned source should be exposed as congested but usable');

  healthStatus = 'EXPIRED';
  const expiredServers = await service.getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(expiredServers.length === 0, 'EXPIRED inventory must not expose premium servers');

  healthStatus = 'HEALTHY';
  assignmentExpiresAt = null;
  supplierExpiresAt = new Date(Date.now() - 60_000);
  const supplierExpiredServers = await service.getServers({ userNumber: 'SW-TEST', deviceId: 'device-1' });
  assert(
    supplierExpiredServers.length === 0,
    'supplier expiry must block premium servers when assignment expiry is absent',
  );

  console.log('assigned server policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
