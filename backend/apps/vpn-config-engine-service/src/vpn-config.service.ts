import { Injectable } from '@nestjs/common';
import { SwimVpnProfile, VpnProtocol } from '@app/contracts';
import * as crypto from 'crypto';
import * as zlib from 'zlib';
import * as net from 'net';

export interface ConfigPipelineResult {
  rawConfig: string;
  parsedProfile: SwimVpnProfile;
  normalizedProfile: SwimVpnProfile;
  classification: {
    protocol: VpnProtocol;
    transport: string;
    security: string;
  };
  preview: {
    title: string;
    address: string;
    port: number;
    protocol: VpnProtocol;
  };
  runtimePayload: {
    protocol: VpnProtocol;
    address: string;
    port: number;
    uuid: string;
    security: string;
    transport: string;
    sni?: string;
    path?: string;
    serviceName?: string;
  };
}

export interface SwimCryptImportResult {
  version: 'crypt1';
  link: string;
  compressed: boolean;
  plaintextBytes: number;
  envelopeBytes: number;
}

export interface ResolvedSwimCryptImportResult {
  version: 'crypt1';
  rawConfig: string;
  compressed: boolean;
}

export interface SupplierResourceMetadata {
  providerName?: string;
  trafficUsedBytes?: number;
  trafficTotalBytes?: number;
  expiresAt?: string;
  connectedDevices?: number;
  deviceLimit?: number;
}

export interface SupplierResourceParseResult {
  rawConfig: string;
  parsedProfile: SwimVpnProfile;
  metadata: SupplierResourceMetadata;
}

@Injectable()
export class VpnConfigService {
  private static readonly CRYPT1_NONCE_BYTES = 12;
  private static readonly CRYPT1_AUTH_TAG_BYTES = 16;

  parse(raw: string): SwimVpnProfile {
    try {
      const pipeline = this.processPipeline(raw);
      return pipeline.normalizedProfile;
    } catch (error) {
      const ingested = this.ingest(raw);
      return this.invalid(ingested, error instanceof Error ? error.message : 'Failed to process config');
    }
  }

  processPipeline(raw: string): ConfigPipelineResult {
    const ingested = this.ingest(raw);
    const parsed = this.parseStage(ingested);
    this.validate(parsed);
    const normalized = this.normalize(parsed);
    const classification = this.classify(normalized);
    const preview = this.preview(normalized);
    const runtimePayload = this.prepareRuntimePayload(normalized);

    return {
      rawConfig: ingested,
      parsedProfile: parsed,
      normalizedProfile: normalized,
      classification,
      preview,
      runtimePayload,
    };
  }

  processSupplierResource(raw: string): SupplierResourceParseResult {
    const extractedRawConfig = this.extractPrimaryConfigCandidate(raw);
    const parsedProfile = this.parse(extractedRawConfig);
    const metadata = this.extractSupplierMetadata(raw);

    return {
      rawConfig: extractedRawConfig,
      parsedProfile,
      metadata,
    };
  }

  private ingest(raw: string): string {
    return raw.trim();
  }

  private parseStage(trimmed: string): SwimVpnProfile {
    try {
      if (trimmed.startsWith('vless://')) {
        return this.parseVless(trimmed);
      }
      if (trimmed.startsWith('https://') || trimmed.startsWith('http://')) {
        return this.parseSubscriptionLink(trimmed);
      }
      if (trimmed.startsWith('ss://')) {
        return this.parseShadowsocks(trimmed);
      }

      return this.invalid(trimmed, 'Unsupported protocol');
    } catch (e) {
      return this.invalid(trimmed, e instanceof Error ? e.message : 'Failed to parse config');
    }
  }

  private validate(profile: SwimVpnProfile): void {
    if (profile.validationState === 'INVALID') {
      throw new Error(profile.errorMessage || 'Invalid profile');
    }
    if (!profile.address || !profile.port) {
      throw new Error('Missing address or port');
    }
  }

  private normalize(profile: SwimVpnProfile): SwimVpnProfile {
    return {
      ...profile,
      address: profile.address.toLowerCase(),
      transport: profile.transport.toLowerCase(),
      security: profile.security.toLowerCase(),
    };
  }

  private classify(profile: SwimVpnProfile): ConfigPipelineResult['classification'] {
    return {
      protocol: profile.protocol,
      transport: profile.transport,
      security: profile.security,
    };
  }

  private preview(profile: SwimVpnProfile): ConfigPipelineResult['preview'] {
    return {
      title: profile.displayTitle,
      address: profile.address,
      port: profile.port,
      protocol: profile.protocol,
    };
  }

  private prepareRuntimePayload(profile: SwimVpnProfile): ConfigPipelineResult['runtimePayload'] {
    return {
      protocol: profile.protocol,
      address: profile.address,
      port: profile.port,
      uuid: profile.uuid,
      security: profile.security,
      transport: profile.transport,
      sni: profile.sni,
      path: profile.path,
      serviceName: profile.serviceName,
    };
  }

  private parseVless(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const protocol = VpnProtocol.VLESS;
    const uuid = url.username;
    const address = url.hostname;
    const port = parseInt(url.port);

    const params = url.searchParams;

    // Core transport params
    const security = params.get('security') || 'none';
    const transport = params.get('type') || 'tcp';

    // Advanced params
    const sni = params.get('sni') || undefined;
    const path = params.get('path') || undefined;
    const serviceName = params.get('serviceName') || undefined;
    const headerType = params.get('headerType') || undefined;
    const flow = params.get('flow') || undefined;
    const sid = params.get('sid') || undefined;
    const pbk = params.get('pbk') || undefined; // Reality Public Key
    const fp = params.get('fp') || undefined;   // Fingerprint

    const displayTitle = decodeURIComponent(url.hash.replace('#', '')) || 'VLESS Config';

    return {
      rawConfig: raw,
      protocol,
      uuid,
      address,
      port,
      security,
      transport,
      sni,
      path,
      serviceName,
      headerType,
      flow,
      sid,
      pbk,
      fp,
      displayTitle,
      validationState: 'VALID',
    };
  }

  private parseShadowsocks(raw: string): SwimVpnProfile {
    // Format: ss://base64(method:password)@host:port#tag
    // Or: ss://base64(method:password@host:port)#tag (Legacy)
    const url = new URL(raw);
    let userInfo = url.username;
    let host = url.hostname;
    let port = parseInt(url.port);
    let method = '';
    let password = '';

    try {
      // Try to decode userInfo if it's base64
      const decoded = Buffer.from(userInfo, 'base64').toString('utf-8');
      if (decoded.includes(':')) {
        const parts = decoded.split(':');
        method = parts[0];
        password = parts.slice(1).join(':');
      } else {
        // Handle cases where the whole string might be base64 (legacy)
        const fullDecoded = Buffer.from(url.host, 'base64').toString('utf-8');
        // method:pass@host:port
        const [cred, server] = fullDecoded.split('@');
        [method, password] = cred.split(':');
        const [h, p] = server.split(':');
        host = h;
        port = parseInt(p);
      }
    } catch (e) {
      // If base64 fails, maybe it's not encoded (rare)
      method = 'unknown';
      password = userInfo;
    }

    return {
      rawConfig: raw,
      protocol: VpnProtocol.SHADOWSOCKS,
      uuid: password, // Use password as unique identifier
      address: host,
      port: port,
      security: method,
      transport: 'tcp',
      displayTitle: decodeURIComponent(url.hash.replace('#', '')) || 'SS Config',
      validationState: 'VALID',
    };
  }

  private parseSubscriptionLink(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const port = url.port ? parseInt(url.port, 10) : url.protocol === 'http:' ? 80 : 443;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.UNKNOWN,
      uuid: url.pathname || url.hostname,
      address: url.hostname,
      port,
      security: url.protocol.replace(':', ''),
      transport: 'subscription',
      path: url.pathname || undefined,
      displayTitle: url.hostname,
      validationState: 'VALID',
    };
  }

  async checkHealth(raw: string): Promise<{ alive: boolean; latency?: number }> {
    const profile = this.parse(raw);
    if (profile.validationState === 'INVALID') {
      return { alive: false };
    }

    return new Promise((resolve) => {
      const start = Date.now();
      const socket = new net.Socket();

      socket.setTimeout(3000); // 3 seconds timeout

      socket.on('connect', () => {
        const latency = Date.now() - start;
        socket.destroy();
        resolve({ alive: true, latency });
      });

      socket.on('error', () => {
        socket.destroy();
        resolve({ alive: false });
      });

      socket.on('timeout', () => {
        socket.destroy();
        resolve({ alive: false });
      });

      socket.connect(profile.port, profile.address);
    });
  }

  generateSwimCryptImport(data: { rawConfig: string; compress?: boolean }): SwimCryptImportResult {
    const key = this.getCrypt1Key();
    const ingested = this.ingest(data.rawConfig);
    if (!ingested) {
      throw new Error('rawConfig is required');
    }

    const plain = Buffer.from(ingested, 'utf8');
    const payload = data.compress ? zlib.gzipSync(plain) : plain;
    const nonce = crypto.randomBytes(VpnConfigService.CRYPT1_NONCE_BYTES);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce);
    const ciphertext = Buffer.concat([cipher.update(payload), cipher.final()]);
    const authTag = cipher.getAuthTag();
    const envelope = Buffer.concat([nonce, ciphertext, authTag]);

    return {
      version: 'crypt1',
      link: `swimvpn://crypt1/${this.toBase64Url(envelope)}`,
      compressed: Boolean(data.compress),
      plaintextBytes: plain.length,
      envelopeBytes: envelope.length,
    };
  }

  resolveSwimCryptImport(data: { encryptedLink: string }): ResolvedSwimCryptImportResult {
    const key = this.getCrypt1Key();
    const payload = this.extractCrypt1Payload(data.encryptedLink);
    const envelope = Buffer.from(this.fromBase64Url(payload), 'base64');
    const minEnvelopeBytes = VpnConfigService.CRYPT1_NONCE_BYTES + VpnConfigService.CRYPT1_AUTH_TAG_BYTES + 1;
    if (envelope.length < minEnvelopeBytes) {
      throw new Error('SWIMVPN crypt1 payload is too short');
    }

    const nonce = envelope.subarray(0, VpnConfigService.CRYPT1_NONCE_BYTES);
    const authTag = envelope.subarray(envelope.length - VpnConfigService.CRYPT1_AUTH_TAG_BYTES);
    const ciphertext = envelope.subarray(
      VpnConfigService.CRYPT1_NONCE_BYTES,
      envelope.length - VpnConfigService.CRYPT1_AUTH_TAG_BYTES,
    );
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, nonce);
    decipher.setAuthTag(authTag);
    const plain = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
    const compressed = this.isGzip(plain);
    const unpacked = compressed ? zlib.gunzipSync(plain) : plain;

    return {
      version: 'crypt1',
      rawConfig: unpacked.toString('utf8').trim(),
      compressed,
    };
  }

  private getCrypt1Key(): Buffer {
    const raw = process.env.SWIMVPN_CRYPT1_KEY_BASE64?.trim();
    if (!raw) {
      throw new Error('SWIMVPN_CRYPT1_KEY_BASE64 is not configured');
    }

    const key = Buffer.from(this.fromBase64Url(raw), 'base64');
    if (key.length !== 32) {
      throw new Error('SWIMVPN_CRYPT1_KEY_BASE64 must decode to 32 bytes for AES-256-GCM');
    }
    return key;
  }

  private toBase64Url(value: Buffer): string {
    return value
      .toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '');
  }

  private fromBase64Url(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    return normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
  }

  private extractCrypt1Payload(value: string): string {
    const trimmed = value.trim();
    const prefix = 'swimvpn://crypt1/';
    if (trimmed.toLowerCase().startsWith(prefix)) {
      return trimmed.slice(prefix.length);
    }
    return trimmed;
  }

  private isGzip(value: Buffer): boolean {
    return value.length >= 2 && value[0] === 0x1f && value[1] === 0x8b;
  }

  private invalid(raw: string, msg: string): SwimVpnProfile {
    return {
      rawConfig: raw,
      protocol: VpnProtocol.UNKNOWN,
      uuid: '',
      address: '',
      port: 0,
      security: '',
      transport: '',
      displayTitle: 'Invalid Config',
      validationState: 'INVALID',
      errorMessage: msg,
    };
  }

  private extractPrimaryConfigCandidate(raw: string): string {
    const trimmed = raw.trim();
    if (
      trimmed.startsWith('vless://') ||
      trimmed.startsWith('ss://') ||
      trimmed.startsWith('http://') ||
      trimmed.startsWith('https://')
    ) {
      return trimmed.split(/\s+/)[0].trim();
    }

    const directUrlMatches = Array.from(trimmed.matchAll(/https?:\/\/[^\s)]+/gi)).map((match) =>
      match[0].trim(),
    );
    if (directUrlMatches.length > 0) {
      return directUrlMatches[0];
    }

    const embeddedUrlMatch = trimmed.match(/\((https?:\/\/[^\s)]+)\)/i);
    if (embeddedUrlMatch?.[1]) {
      return embeddedUrlMatch[1].trim();
    }

    return trimmed;
  }

  private extractSupplierMetadata(raw: string): SupplierResourceMetadata {
    const trimmed = raw.trim();
    const lines = trimmed
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    const providerName = lines.find((line) =>
      /^[A-Za-z][A-Za-z0-9+\-_ ]{2,40}$/.test(line) &&
      !/^https?:\/\//i.test(line) &&
      !/^wb\./i.test(line) &&
      !/subscription page/i.test(line),
    );

    const usedTrafficLine = lines.find((line) => /израсходовано|used/i.test(line));
    const totalTrafficLine = lines.find((line) => /трафик|traffic/i.test(line));
    const expiryLine = lines.find((line) => /истекает|expires/i.test(line));
    const connectedLine = lines.find((line) => /подключили|connected/i.test(line));
    const deviceLimitLine = lines.find((line) => /лимит устройств|device limit/i.test(line));

    const usedTrafficMatch = usedTrafficLine?.match(
      /([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])\s*\/\s*([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])/u,
    );
    const totalTrafficMatch =
      totalTrafficLine?.match(/([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])/u) || usedTrafficMatch;

    const trafficUsedBytes = usedTrafficMatch
      ? this.toBytes(usedTrafficMatch[1], usedTrafficMatch[2])
      : undefined;
    const trafficTotalBytes = usedTrafficMatch
      ? this.toBytes(usedTrafficMatch[3], usedTrafficMatch[4])
      : totalTrafficMatch
        ? this.toBytes(totalTrafficMatch[1], totalTrafficMatch[2])
      : undefined;

    const connectedMatch = connectedLine?.match(/(\d+)/);
    const deviceLimitMatch = deviceLimitLine?.match(/(\d+)/);
    const expiryMatch = expiryLine?.match(/(\d{1,2})\s+([^\d\s]+)\s+(\d{4})/u);

    return {
      providerName,
      trafficUsedBytes,
      trafficTotalBytes,
      expiresAt: expiryMatch
        ? this.normalizeHumanExpiry(expiryMatch[1], expiryMatch[2], expiryMatch[3])
        : undefined,
      connectedDevices: connectedMatch ? parseInt(connectedMatch[1], 10) : undefined,
      deviceLimit: deviceLimitMatch ? parseInt(deviceLimitMatch[1], 10) : undefined,
    };
  }

  private toBytes(rawNumber: string, rawUnit: string): number {
    const numeric = Number.parseFloat(rawNumber.replace(',', '.'));
    const normalizedUnit = rawUnit
      .toUpperCase()
      .replace('Б', 'B')
      .replace('Г', 'G')
      .replace('М', 'M')
      .replace('К', 'K')
      .replace('Т', 'T')
      .replace('Т', 'T')
      .replace('TB', 'TB')
      .replace('GB', 'GB')
      .replace('MB', 'MB')
      .replace('KB', 'KB');

    const factor =
      normalizedUnit.startsWith('TB')
        ? 1024 ** 4
        : normalizedUnit.startsWith('GB')
          ? 1024 ** 3
          : normalizedUnit.startsWith('MB')
            ? 1024 ** 2
            : normalizedUnit.startsWith('KB')
              ? 1024
              : 1;

    return Math.round(numeric * factor);
  }

  private normalizeHumanExpiry(day: string, monthWord: string, year: string): string | undefined {
    const monthIndex = this.resolveMonthIndex(monthWord);
    if (!monthIndex) {
      return undefined;
    }

    const date = new Date(Date.UTC(Number.parseInt(year, 10), monthIndex - 1, Number.parseInt(day, 10)));
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString().replace('.000', '');
  }

  private resolveMonthIndex(monthWord: string): number | undefined {
    const normalized = monthWord.trim().toLowerCase();
    const months: Record<string, number> = {
      января: 1,
      февраля: 2,
      марта: 3,
      апреля: 4,
      мая: 5,
      июня: 6,
      июля: 7,
      августа: 8,
      сентября: 9,
      октября: 10,
      ноября: 11,
      декабря: 12,
      january: 1,
      february: 2,
      march: 3,
      april: 4,
      may: 5,
      june: 6,
      july: 7,
      august: 8,
      september: 9,
      october: 10,
      november: 11,
      december: 12,
    };

    return months[normalized];
  }
}
