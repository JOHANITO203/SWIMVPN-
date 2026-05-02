import { Injectable } from '@nestjs/common';
import { PrismaService } from '@app/database';

type RuntimeEndpoint = {
  host: string;
  port: number;
  protocol: string;
  displayName?: string | null;
};

export function parseRuntimeEndpoint(rawConfig?: string | null): RuntimeEndpoint | null {
  const firstConfigLine = rawConfig
    ?.split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => /^(vless|vmess|trojan|ss):\/\//i.test(line));

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
            displayName: typeof parsed.ps === 'string' ? parsed.ps : null,
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
      displayName: url.hash ? decodeURIComponent(url.hash.slice(1)) : null,
    };
  } catch {
    return null;
  }
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
  constructor(private readonly prisma: PrismaService) {}

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
    const providerExpiresAt = activeAssignment.expires_at;
    const trialExpiresAt =
      isTrial && latestOrder.fulfilled_at
        ? new Date(latestOrder.fulfilled_at.getTime() + 3 * 24 * 60 * 60 * 1000)
        : null;
    const expiresAt = isTrial ? this.pickEarlierDate(trialExpiresAt, providerExpiresAt) : providerExpiresAt;
    if (expiresAt && expiresAt.getTime() < Date.now()) {
      return [];
    }

    const endpoint = parseRuntimeEndpoint(inventoryItem.raw_config);
    if (!endpoint) {
      return [];
    }

    const displayName =
      endpoint.displayName ||
      inventoryItem.batch_name ||
      inventoryItem.supplier_provider_name ||
      inventoryItem.display_protocol ||
      'Assigned premium config';

    return [
      {
        id: `assignment:${activeAssignment.id}`,
        country: displayName,
        city: displayName,
        host: endpoint.host,
        port: endpoint.port,
        protocol: endpoint.protocol || inventoryItem.display_protocol || inventoryItem.config_type || 'UNKNOWN',
        tags: ['ASSIGNED', 'PREMIUM'],
        planScope: 'PREMIUM',
        countryCode: null,
        source: 'backend',
      },
    ];
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
