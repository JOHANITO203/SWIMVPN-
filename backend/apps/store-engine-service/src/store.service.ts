import { Inject, Injectable } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { firstValueFrom, timeout } from 'rxjs';

type RuntimeEndpoint = {
  host: string;
  port: number;
  protocol: string;
  displayName?: string | null;
  rawConfig: string;
  security?: string | null;
  transport?: string | null;
};

export function parseRuntimeEndpoint(rawConfig?: string | null): RuntimeEndpoint | null {
  const firstConfigLine = extractRuntimeConfigLines(rawConfig)[0];

  if (!firstConfigLine) {
    return null;
  }

  const protocolMatch = firstConfigLine.match(/^([a-z0-9+.-]+):\/\//i);
  const protocol = protocolMatch?.[1]?.toUpperCase() || 'UNKNOWN';

  if (protocol === 'VMESS') {
    const payload = firstConfigLine.slice('vmess://'.length).trim();
    const decoded = decodeBase64Url(payload);
    if (decoded) {
      try {
        const parsed = JSON.parse(decoded);
        const host = String(parsed.add || parsed.host || '').trim();
        const port = toSafePort(parsed.port) || 443;
        if (host) {
          return {
            host,
            port,
            protocol,
            rawConfig: firstConfigLine,
            displayName: typeof parsed.ps === 'string' ? parsed.ps : null,
            security: typeof parsed.tls === 'string' ? parsed.tls : null,
            transport: typeof parsed.net === 'string' ? parsed.net : null,
          };
        }
      } catch {
        return null;
      }
    }
    return null;
  }

  try {
    const url = new URL(firstConfigLine);
    const host = url.hostname;
    if (!host) {
      return null;
    }

    return {
      host,
      port: toSafePort(url.port) || (url.protocol === 'http:' ? 80 : 443),
      protocol,
      rawConfig: firstConfigLine,
      displayName: url.hash ? decodeURIComponent(url.hash.slice(1)) : null,
      security: url.searchParams.get('security'),
      transport: url.searchParams.get('type'),
    };
  } catch {
    return null;
  }
}

type ManagedRuntimeNode = RuntimeEndpoint & {
  id?: string;
  uuid?: string;
};

function extractRuntimeConfigLines(rawConfig?: string | null): string[] {
  const raw = rawConfig?.trim();
  if (!raw) {
    return [];
  }
  if (/^https?:\/\//i.test(raw)) {
    return [];
  }

  const direct = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .flatMap((line) =>
      Array.from(line.matchAll(/\b(?:vless|vmess|trojan|ss):\/\/[^\s]+/gi)).map((match) => match[0]),
    );
  if (direct.length > 0) {
    return direct;
  }

  const decoded = decodeBase64Url(raw.replace(/\s+/g, ''));
  if (!decoded) {
    return [];
  }

  return decoded
    .split(/\r?\n/)
    .map((line) => line.trim())
    .flatMap((line) =>
      Array.from(line.matchAll(/\b(?:vless|vmess|trojan|ss):\/\/[^\s]+/gi)).map((match) => match[0]),
    );
}

function parseRuntimeEndpoints(rawConfig?: string | null): ManagedRuntimeNode[] {
  return extractRuntimeConfigLines(rawConfig)
    .map((line) => parseRuntimeEndpoint(line))
    .filter((endpoint): endpoint is RuntimeEndpoint => endpoint !== null);
}

function decodeBase64Url(value: string): string | null {
  try {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    return Buffer.from(padded, 'base64').toString('utf8');
  } catch {
    return null;
  }
}

function toSafePort(value: unknown): number | null {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    return null;
  }
  return parsed;
}

@Injectable()
export class StoreService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient?: ClientProxy,
  ) {}

  async getActivePlans() {
    return this.prisma.plan.findMany({
      where: {
        active: true,
        price_rub: { gt: 0 },
      },
      orderBy: { display_order: 'asc' },
    });
  }

  async getServers(data: { userNumber: string; deviceId?: string }) {
    const userNumber = data.userNumber;
    const deviceId = data.deviceId?.trim();
    // SECURITY BOUNDARY: Verify entitlement before returning backend servers
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: userNumber },
      include: {
        orders: {
          where: {
            status: { in: ['FULFILLED', 'PAID', 'PENDING_FULFILLMENT'] },
          },
          orderBy: { created_at: 'desc' },
          take: 10,
          include: {
            assignments: {
              where: { access_status: 'ACTIVE' },
              orderBy: { assigned_at: 'desc' },
              take: 1,
              include: {
                inventory_item: true,
              },
            },
            plan: true,
          },
        },
      },
    });

    if (!customer) {
      return [];
    }

    if (!deviceId || !customer.device_id || customer.device_id !== deviceId) {
      return [];
    }

    const latestOrder = customer.orders.find((order) => order.assignments.length > 0);
    const activeAssignment = latestOrder?.assignments[0];
    const inventoryItem = activeAssignment?.inventory_item;

    // If there is no active assignment or the access is expired, block premium servers
    if (
      !latestOrder ||
      !activeAssignment ||
      activeAssignment.access_status !== 'ACTIVE' ||
      !inventoryItem
    ) {
      return [];
    }

    if (
      inventoryItem.health_status === 'EXPIRED' ||
      inventoryItem.health_status === 'DISABLED' ||
      this.isSourceQuotaExceeded(inventoryItem.source_quota_bytes, inventoryItem.source_used_bytes) ||
      this.isPlanQuotaExceeded(latestOrder.plan?.quota_label || '', activeAssignment.measured_used_bytes)
    ) {
      return [];
    }

    // Paid access expiration is supplier-managed. Trials keep the local short window.
    const isTrial = latestOrder.order_ref.startsWith('TRIAL-') || latestOrder.payment_ref === 'TRIAL:3D';
    const providerExpiresAt = activeAssignment.expires_at || inventoryItem.supplier_expires_at;
    const trialExpiresAt =
      isTrial && latestOrder.fulfilled_at
        ? new Date(latestOrder.fulfilled_at.getTime() + 3 * 24 * 60 * 60 * 1000)
        : null;
    const expiresAt = isTrial ? this.pickEarlierDate(trialExpiresAt, providerExpiresAt) : providerExpiresAt;
    if (expiresAt && expiresAt.getTime() < Date.now()) {
      return [];
    }

    const managedNodes = await this.parseManagedNodes(inventoryItem.raw_config);
    if (managedNodes.length === 0) {
      return [];
    }

    return managedNodes.map((node) => {
      const displayName =
        node.displayName ||
        inventoryItem.batch_name ||
        inventoryItem.supplier_provider_name ||
        inventoryItem.display_protocol ||
        'Assigned premium config';

      return {
        id: `assignment:${activeAssignment.id}:${this.stableNodeHash(node.rawConfig)}`,
        country: displayName,
        city: displayName,
        host: node.host,
        port: node.port,
        protocol: node.protocol || inventoryItem.display_protocol || inventoryItem.config_type || 'UNKNOWN',
        tags: ['ASSIGNED', 'PREMIUM'],
        planScope: 'PREMIUM',
        countryCode: null,
        rawConfig: node.rawConfig,
        source: 'backend',
        providerName: inventoryItem.supplier_provider_name,
        expiresAt: (activeAssignment.expires_at || inventoryItem.supplier_expires_at)?.toISOString() || null,
      };
    });
  }

  private async parseManagedNodes(rawConfig: string): Promise<ManagedRuntimeNode[]> {
    if (this.vpnConfigClient) {
      try {
        const nodes = await firstValueFrom(
          this.vpnConfigClient
            .send<ManagedRuntimeNode[]>({ cmd: 'parse_managed_nodes' }, { rawConfig })
            .pipe(timeout(1500)),
        );
        if (Array.isArray(nodes) && nodes.length > 0) {
          return nodes.filter((node) => this.isRuntimeNode(node));
        }
      } catch {
        // Fall back to the local parser so store keeps serving direct assigned configs if the parser service is unavailable.
      }
    }

    return parseRuntimeEndpoints(rawConfig);
  }

  private isRuntimeNode(node: ManagedRuntimeNode): boolean {
    return Boolean(
      node &&
      node.rawConfig &&
      /^(vless|vmess|trojan|ss):\/\//i.test(node.rawConfig) &&
      node.host &&
      toSafePort(node.port),
    );
  }

  private stableNodeHash(rawConfig: string): string {
    let hash = 0;
    for (let index = 0; index < rawConfig.length; index += 1) {
      hash = Math.imul(31, hash) + rawConfig.charCodeAt(index) | 0;
    }
    return (hash >>> 0).toString(16).padStart(8, '0');
  }

  private isSourceQuotaExceeded(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private parseQuotaLabelToGb(quotaLabel: string) {
    const match = quotaLabel.match(/(\d+(?:[.,]\d+)?)/);
    if (!match) {
      return 0;
    }

    const parsed = Number.parseFloat(match[1].replace(',', '.'));
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private quotaLabelToBytes(quotaLabel: string) {
    const parsedGb = this.parseQuotaLabelToGb(quotaLabel);
    if (!Number.isFinite(parsedGb) || parsedGb <= 0) {
      return 0n;
    }

    return BigInt(Math.round(parsedGb * 1024 * 1024 * 1024));
  }

  private isPlanQuotaExceeded(quotaLabel: string, measuredUsedBytes?: bigint | null) {
    const quotaBytes = this.quotaLabelToBytes(quotaLabel);
    if (quotaBytes <= 0n) {
      return false;
    }

    return (measuredUsedBytes ?? 0n) >= quotaBytes;
  }

  private pickEarlierDate(first?: Date | null, second?: Date | null) {
    if (!first) {
      return second || null;
    }

    if (!second) {
      return first;
    }

    return first.getTime() <= second.getTime() ? first : second;
  }
}
