import { Injectable } from '@nestjs/common';
import { SwimVpnProfile, VpnProtocol } from '@app/contracts';
import * as crypto from 'crypto';
import * as zlib from 'zlib';
import * as net from 'net';
import { promises as dns } from 'dns';
import * as http from 'http';
import * as https from 'https';
import {
  parseHttpHeaders,
  parseSubscriptionMetadata,
  headerHasValues,
  type SubscriptionHeaderMetadata,
} from './subscription-metadata.parser';

interface RemoteSubscriptionResponse {
  body: string;
  subscriptionUserInfo?: string;
  profileUpdateInterval?: string;
}

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

export interface ManagedRuntimeNode {
  id: string;
  rawConfig: string;
  protocol: VpnProtocol;
  host: string;
  port: number;
  uuid: string;
  security: string;
  transport: string;
  displayName: string;
  sni?: string;
  path?: string;
  serviceName?: string;
  headerType?: string;
  flow?: string;
  sid?: string;
  pbk?: string;
  fp?: string;
}

@Injectable()
export class VpnConfigService {
  private static readonly CRYPT1_NONCE_BYTES = 12;
  private static readonly CRYPT1_AUTH_TAG_BYTES = 16;
  private static readonly REMOTE_SUBSCRIPTION_TIMEOUT_MS = 3000;
  private static readonly REMOTE_SUBSCRIPTION_MAX_BYTES = 512 * 1024;
  private static readonly REMOTE_SUBSCRIPTION_MAX_REDIRECTS = 3;
  private static readonly REMOTE_SUBSCRIPTION_HEADERS = {
    'User-Agent': 'v2rayN/6.23',
    Accept: 'text/plain, application/json, application/yaml, */*',
    'Cache-Control': 'no-cache',
  };

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

  async processSupplierResource(raw: string): Promise<SupplierResourceParseResult> {
    const ingested = this.ingest(raw || '');
    const runtimeCandidates = this.extractRuntimeConfigCandidates(ingested);
    const extractedRawConfig = runtimeCandidates[0] || this.extractPrimaryConfigCandidate(raw);
    const isSubscription = this.isRemoteSubscriptionUrl(extractedRawConfig);

    // For a subscription URL, fetch ONCE and keep both the body (for nodes) and the response
    // headers — the `Subscription-Userinfo` header carries the expiry/traffic the app relies on.
    let subscriptionBody: string | null = null;
    let headerMetadata: SubscriptionHeaderMetadata | undefined;
    let resolvedRuntimeNodes: ManagedRuntimeNode[] = [];
    if (isSubscription) {
      try {
        const fetched = await this.fetchRemoteSubscriptionPayload(extractedRawConfig);
        subscriptionBody = fetched.body;
        const parsedHeaders = parseHttpHeaders(
          fetched.subscriptionUserInfo,
          fetched.profileUpdateInterval,
          extractedRawConfig,
        );
        headerMetadata = headerHasValues(parsedHeaders) ? parsedHeaders : undefined;
        resolvedRuntimeNodes = this.parseManagedRuntimeNodes(fetched.body);
      } catch {
        resolvedRuntimeNodes = [];
      }
    }

    const parsedProfile = resolvedRuntimeNodes[0]
      ? this.parse(resolvedRuntimeNodes[0].rawConfig)
      : isSubscription
        ? this.invalid(extractedRawConfig, 'Remote subscription did not expose runtime nodes')
        : this.parse(extractedRawConfig);

    // App-parity EXPIRY/TRAFFIC extraction. Priority: Subscription-Userinfo header (fetch) → the
    // pasted supplier text AND/OR fetched body (broad date + traffic regexes). We deliberately keep
    // the existing extractor's provider/connected/device detection unchanged (out of scope here).
    const metadataText = [raw, subscriptionBody].filter(Boolean).join('\n\n');
    const enriched = parseSubscriptionMetadata(
      metadataText,
      isSubscription ? extractedRawConfig : undefined,
      headerMetadata,
    );
    const legacy = this.extractSupplierMetadata(raw);
    const metadata: SupplierResourceMetadata = {
      providerName: legacy.providerName,
      trafficUsedBytes: enriched.trafficUsedBytes ?? legacy.trafficUsedBytes,
      trafficTotalBytes: enriched.trafficTotalBytes ?? legacy.trafficTotalBytes,
      expiresAt: enriched.expiresAt ?? legacy.expiresAt,
      connectedDevices: legacy.connectedDevices,
      deviceLimit: legacy.deviceLimit,
    };

    return {
      rawConfig: extractedRawConfig,
      parsedProfile,
      metadata,
    };
  }

  parseManagedRuntimeNodes(rawConfig: string): ManagedRuntimeNode[] {
    const candidates = this.extractRuntimeConfigCandidates(this.ingest(rawConfig || ''));
    const seen = new Set<string>();
    const nodes: ManagedRuntimeNode[] = [];

    for (const candidate of candidates) {
      if (seen.has(candidate)) {
        continue;
      }
      seen.add(candidate);

      const profile = this.parseRuntimeCandidate(candidate);
      if (profile.validationState !== 'VALID') {
        continue;
      }

      const normalized = this.normalize(profile);
      if (!normalized.address || !this.safePort(normalized.port)) {
        continue;
      }
      nodes.push({
        id: this.runtimeNodeId(candidate),
        rawConfig: candidate,
        protocol: normalized.protocol,
        host: normalized.address,
        port: normalized.port,
        uuid: normalized.uuid,
        security: normalized.security,
        transport: normalized.transport,
        displayName: normalized.displayTitle,
        sni: normalized.sni,
        path: normalized.path,
        serviceName: normalized.serviceName,
        headerType: normalized.headerType,
        flow: normalized.flow,
        sid: normalized.sid,
        pbk: normalized.pbk,
        fp: normalized.fp,
      });
    }

    return nodes;
  }

  async resolveManagedRuntimeNodes(rawConfig: string): Promise<ManagedRuntimeNode[]> {
    const ingested = this.ingest(rawConfig || '');
    if (!this.isRemoteSubscriptionUrl(ingested)) {
      return this.parseManagedRuntimeNodes(ingested);
    }

    try {
      const payload = await this.fetchRemoteSubscriptionPayload(ingested);
      return this.parseManagedRuntimeNodes(payload.body);
    } catch {
      return [];
    }
  }

  private ingest(raw: string): string {
    return raw.trim();
  }

  private parseStage(trimmed: string): SwimVpnProfile {
    try {
      if (trimmed.startsWith('vless://')) {
        return this.parseVless(trimmed);
      }
      if (trimmed.startsWith('vmess://')) {
        return this.parseVmess(trimmed);
      }
      if (trimmed.startsWith('trojan://')) {
        return this.parseTrojan(trimmed);
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
    const port = this.safePort(url.port) || 443;

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

  private parseVmess(raw: string): SwimVpnProfile {
    const payload = raw.slice('vmess://'.length).trim();
    const decoded = this.decodeBase64Text(payload);
    if (!decoded) {
      return this.invalid(raw, 'Invalid VMess payload');
    }

    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(decoded);
    } catch {
      return this.invalid(raw, 'Invalid VMess JSON');
    }

    const address = this.stringValue(parsed.add) || this.stringValue(parsed.host);
    const port = this.safePort(parsed.port) || 443;
    const uuid = this.stringValue(parsed.id) || this.stringValue(parsed.uuid);
    if (!address || !uuid) {
      return this.invalid(raw, 'Missing VMess address or id');
    }

    return {
      rawConfig: raw,
      protocol: VpnProtocol.VMESS,
      uuid,
      address,
      port,
      security: this.stringValue(parsed.tls) || this.stringValue(parsed.security) || 'none',
      transport: this.stringValue(parsed.net) || this.stringValue(parsed.type) || 'tcp',
      sni: this.stringValue(parsed.sni) || this.stringValue(parsed.host),
      path: this.stringValue(parsed.path),
      headerType: this.stringValue(parsed.type),
      displayTitle: this.stringValue(parsed.ps) || address,
      validationState: 'VALID',
    };
  }

  private parseTrojan(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const params = url.searchParams;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.TROJAN,
      uuid: decodeURIComponent(url.username),
      address: url.hostname,
      port: this.safePort(url.port) || 443,
      security: params.get('security') || 'tls',
      transport: params.get('type') || 'tcp',
      sni: params.get('sni') || undefined,
      path: params.get('path') || undefined,
      serviceName: params.get('serviceName') || undefined,
      displayTitle: decodeURIComponent(url.hash.replace('#', '')) || 'Trojan Config',
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
    if (await this.isBlockedHealthcheckHost(profile.address)) {
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

  private async isBlockedHealthcheckHost(host: string): Promise<boolean> {
    const normalized = host.trim().toLowerCase().replace(/^\[|\]$/g, '');
    if (!normalized || normalized === 'localhost' || normalized.endsWith('.localhost')) {
      return true;
    }

    if (net.isIP(normalized)) {
      return this.isBlockedHealthcheckIp(normalized);
    }

    try {
      const addresses = await dns.lookup(normalized, { all: true });
      return addresses.some((entry) => this.isBlockedHealthcheckIp(entry.address));
    } catch {
      return true;
    }
  }

  private isBlockedHealthcheckIp(address: string): boolean {
    const family = net.isIP(address);
    if (family === 4) {
      const octets = address.split('.').map((part) => Number.parseInt(part, 10));
      if (octets.length !== 4 || octets.some((part) => Number.isNaN(part))) {
        return true;
      }
      const [a, b] = octets;
      return a === 0 ||
        a === 10 ||
        a === 127 ||
        (a === 100 && b >= 64 && b <= 127) ||
        (a === 169 && b === 254) ||
        (a === 172 && b >= 16 && b <= 31) ||
        (a === 192 && b === 168) ||
        (a === 192 && b === 0) ||
        (a === 198 && (b === 18 || b === 19)) ||
        a >= 224;
    }

    if (family === 6) {
      const normalized = address.toLowerCase();
      return normalized === '::' ||
        normalized === '::1' ||
        normalized.startsWith('fc') ||
        normalized.startsWith('fd') ||
        normalized.startsWith('fe80:') ||
        normalized.startsWith('ff');
    }

    return true;
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

  private parseRuntimeCandidate(raw: string): SwimVpnProfile {
    try {
      if (raw.toLowerCase().startsWith('vless://')) {
        return this.parseVless(raw);
      }
      if (raw.toLowerCase().startsWith('vmess://')) {
        return this.parseVmess(raw);
      }
      if (raw.toLowerCase().startsWith('trojan://')) {
        return this.parseTrojan(raw);
      }
      if (raw.toLowerCase().startsWith('ss://')) {
        return this.parseShadowsocks(raw);
      }

      return this.invalid(raw, 'Unsupported runtime protocol');
    } catch (error) {
      return this.invalid(raw, error instanceof Error ? error.message : 'Failed to parse runtime config');
    }
  }

  private extractRuntimeConfigCandidates(raw: string): string[] {
    if (!raw) {
      return [];
    }

    const candidates: string[] = [];
    const seen = new Set<string>();
    for (const carrier of this.expandTextCarriers(raw)) {
      if (this.isRemoteSubscriptionUrl(carrier)) {
        continue;
      }

      for (const candidate of [
        ...this.extractRuntimeLines(carrier),
        ...this.extractJsonRuntimeCandidates(carrier),
        ...this.extractClashYamlRuntimeCandidates(carrier),
      ]) {
        if (!seen.has(candidate)) {
          seen.add(candidate);
          candidates.push(candidate);
        }
      }
    }

    return candidates;
  }

  private extractRuntimeLines(raw: string): string[] {
    return raw
      .split(/\r?\n/)
      .map((line) => line.trim())
      .flatMap((line) =>
        Array.from(line.matchAll(/\b(?:vless|vmess|trojan|ss):\/\/[^\s]+/gi)).map((match) => match[0]),
      )
      .map((line) => this.trimRuntimeDelimiters(line))
      .filter((line) => /^(vless|vmess|trojan|ss):\/\//i.test(line));
  }

  private trimRuntimeDelimiters(value: string): string {
    return value.trim().replace(/^[\s"'`,;[\]{}<>()]+|[\s"'`,;[\]{}<>()]+$/g, '');
  }

  private expandTextCarriers(raw: string): string[] {
    const initial = raw.trim().replace(/^\uFEFF/, '').trim();
    if (!initial) {
      return [];
    }

    const results: string[] = [];
    const queue = [initial];
    const seen = new Set<string>();
    const maxPasses = 10;

    while (queue.length > 0 && seen.size < maxPasses) {
      const current = queue.shift()!.trim();
      if (!current || seen.has(current)) {
        continue;
      }
      seen.add(current);
      results.push(current);

      if (this.isRemoteSubscriptionUrl(current)) {
        continue;
      }
      if (this.hasImportableCarrierContent(current)) {
        continue;
      }

      const happ = this.unwrapHappAdd(current);
      if (happ && !seen.has(happ)) {
        queue.push(happ);
      }

      const percentDecoded = this.decodePercentEncoded(current);
      if (percentDecoded && !seen.has(percentDecoded)) {
        queue.push(percentDecoded);
      }

      const base64Decoded = this.decodeBase64Text(
        current
          .replace(/\s+/g, '')
          .replace(/-/g, '+')
          .replace(/_/g, '/'),
      );
      if (base64Decoded && this.looksLikeTextCarrier(base64Decoded) && !seen.has(base64Decoded)) {
        queue.push(base64Decoded.trim());
      }
    }

    return results;
  }

  private hasImportableCarrierContent(input: string): boolean {
    const trimmed = input.trim();
    return this.extractRuntimeLines(trimmed).length > 0 ||
      trimmed.startsWith('{') ||
      trimmed.startsWith('[') ||
      trimmed.split(/\r?\n/).some((line) => line.trim() === 'proxies:');
  }

  private unwrapHappAdd(input: string): string | null {
    if (!input.toLowerCase().startsWith('happ://add/')) {
      return null;
    }

    const wrapped = input.slice('happ://add/'.length).trim();
    if (!wrapped) {
      return null;
    }

    return this.decodePercentEncoded(wrapped) || wrapped;
  }

  private decodePercentEncoded(input: string): string | null {
    if (!/%[0-9a-f]{2}/i.test(input)) {
      return null;
    }
    try {
      const decoded = decodeURIComponent(input).trim();
      return decoded && decoded !== input ? decoded : null;
    } catch {
      return null;
    }
  }

  private looksLikeTextCarrier(input: string): boolean {
    const trimmed = input.trim();
    if (!trimmed || trimmed.includes('\uFFFD')) {
      return false;
    }
    const printable = Array.from(trimmed).filter((char) =>
      !/[\x00-\x08\x0B\x0C\x0E-\x1F]/.test(char),
    ).length;
    return printable / trimmed.length >= 0.9;
  }

  private extractJsonRuntimeCandidates(input: string): string[] {
    const trimmed = input.trim();
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
      return [];
    }

    try {
      return this.extractRuntimeCandidatesFromJsonValue(JSON.parse(trimmed));
    } catch {
      return [];
    }
  }

  private extractRuntimeCandidatesFromJsonValue(value: unknown): string[] {
    if (typeof value === 'string') {
      return this.extractRuntimeConfigCandidates(value);
    }
    if (Array.isArray(value)) {
      return value.flatMap((item) => this.extractRuntimeCandidatesFromJsonValue(item));
    }
    if (!value || typeof value !== 'object') {
      return [];
    }

    const objectValue = value as Record<string, unknown>;
    const outbounds = Array.isArray(objectValue.outbounds) ? objectValue.outbounds : [objectValue];
    const links: string[] = [];
    for (const outbound of outbounds) {
      if (!outbound || typeof outbound !== 'object') {
        continue;
      }
      const link = this.buildRuntimeLinkFromOutbound(outbound as Record<string, unknown>);
      if (link) {
        links.push(link);
      }
    }
    return links;
  }

  private buildRuntimeLinkFromOutbound(outbound: Record<string, unknown>): string | null {
    const singBoxType = this.stringValue(outbound.type)?.toLowerCase();
    if (singBoxType) {
      return this.buildSingBoxRuntimeLink(outbound, singBoxType);
    }

    const protocol = this.stringValue(outbound.protocol)?.toLowerCase();
    if (!protocol) {
      return null;
    }
    return this.buildXrayRuntimeLink(outbound, protocol);
  }

  private buildXrayRuntimeLink(outbound: Record<string, unknown>, protocol: string): string | null {
    const settings = this.recordValue(outbound.settings);
    const streamSettings = this.recordValue(outbound.streamSettings);
    const tag = this.stringValue(outbound.tag);

    if (protocol === 'vless' || protocol === 'vmess') {
      const vnext = this.firstRecord(settings?.vnext);
      const user = this.firstRecord(vnext?.users);
      const address = this.stringValue(vnext?.address);
      const port = this.safePort(vnext?.port);
      const uuid = this.stringValue(user?.id) || this.stringValue(user?.uuid);
      if (!address || !port || !uuid) {
        return null;
      }
      if (protocol === 'vless') {
        const params = this.commonStreamParams(streamSettings);
        const flow = this.stringValue(user?.flow) || this.stringValue(outbound.flow) || this.stringValue(settings?.flow);
        if (flow) {
          params.flow = flow;
        }
        return this.buildVlessLink(uuid, address, port, params, tag || `VLESS: ${address}`);
      }
      return this.buildVmessLink({
        id: uuid,
        add: address,
        port,
        ps: tag || `VMess: ${address}`,
        ...this.vmessFieldsFromStream(streamSettings),
      });
    }

    if (protocol === 'trojan') {
      const server = this.firstRecord(settings?.servers);
      const address = this.stringValue(server?.address);
      const port = this.safePort(server?.port);
      const password = this.stringValue(server?.password);
      if (!address || !port || !password) {
        return null;
      }
      return this.buildTrojanLink(password, address, port, this.commonStreamParams(streamSettings), tag || `Trojan: ${address}`);
    }

    if (protocol === 'shadowsocks') {
      const server = this.firstRecord(settings?.servers);
      const address = this.stringValue(server?.address);
      const port = this.safePort(server?.port);
      const method = this.stringValue(server?.method) || 'aes-256-gcm';
      const password = this.stringValue(server?.password);
      if (!address || !port || !password) {
        return null;
      }
      return this.buildShadowsocksLink(method, password, address, port, tag || `Shadowsocks: ${address}`);
    }

    return null;
  }

  private buildSingBoxRuntimeLink(outbound: Record<string, unknown>, type: string): string | null {
    if (!['vless', 'vmess', 'trojan', 'shadowsocks'].includes(type)) {
      return null;
    }
    const address = this.stringValue(outbound.server);
    const port = this.safePort(outbound.server_port);
    const tag = this.stringValue(outbound.tag);
    if (!address || !port) {
      return null;
    }

    const params = this.commonSingBoxParams(outbound);
    if (type === 'vless') {
      const uuid = this.stringValue(outbound.uuid);
      if (!uuid) {
        return null;
      }
      const flow = this.stringValue(outbound.flow);
      if (flow) {
        params.flow = flow;
      }
      return this.buildVlessLink(uuid, address, port, params, tag || `VLESS: ${address}`);
    }
    if (type === 'vmess') {
      const uuid = this.stringValue(outbound.uuid);
      if (!uuid) {
        return null;
      }
      return this.buildVmessLink({
        id: uuid,
        add: address,
        port,
        ps: tag || `VMess: ${address}`,
        ...this.vmessFieldsFromParams(params),
      });
    }
    if (type === 'trojan') {
      const password = this.stringValue(outbound.password);
      if (!password) {
        return null;
      }
      return this.buildTrojanLink(password, address, port, params, tag || `Trojan: ${address}`);
    }

    const method = this.stringValue(outbound.method);
    const password = this.stringValue(outbound.password);
    if (!method || !password) {
      return null;
    }
    return this.buildShadowsocksLink(method, password, address, port, tag || `Shadowsocks: ${address}`);
  }

  private extractClashYamlRuntimeCandidates(input: string): string[] {
    if (!input.split(/\r?\n/).some((line) => line.trim() === 'proxies:')) {
      return [];
    }

    const entries: Record<string, string>[] = [];
    let current: Record<string, string> | null = null;
    const flush = () => {
      if (current) {
        entries.push(current);
        current = null;
      }
    };

    for (const rawLine of input.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line === 'proxies:') {
        continue;
      }
      if (line.startsWith('- ')) {
        flush();
        current = {};
        const pair = this.parseYamlPair(line.slice(2));
        if (pair) {
          current[pair[0]] = pair[1];
        }
        continue;
      }
      const pair = this.parseYamlPair(line);
      if (pair && current) {
        current[pair[0]] = pair[1];
      }
    }
    flush();

    return entries
      .map((entry) => this.buildClashRuntimeLink(entry))
      .filter((link): link is string => Boolean(link));
  }

  private buildClashRuntimeLink(values: Record<string, string>): string | null {
    const type = values.type?.toLowerCase();
    const address = values.server;
    const port = this.safePort(values.port);
    if (!type || !address || !port) {
      return null;
    }
    const tag = values.name || `${type}: ${address}`;
    const params: Record<string, string> = {};
    if (values.network) {
      params.type = values.network === 'websocket' ? 'ws' : values.network;
    }
    if (values.tls === 'true') {
      params.security = 'tls';
    }
    if (values.servername) {
      params.sni = values.servername;
    }
    if (values.sni) {
      params.sni = values.sni;
    }
    if (values['client-fingerprint']) {
      params.fp = values['client-fingerprint'];
    }
    if (values.path) {
      params.path = values.path;
    }
    if (values.Host || values.host) {
      params.host = values.Host || values.host;
    }
    if (values.flow) {
      params.flow = values.flow;
    }

    if (type === 'vless') {
      return values.uuid ? this.buildVlessLink(values.uuid, address, port, params, tag) : null;
    }
    if (type === 'trojan') {
      return values.password ? this.buildTrojanLink(values.password, address, port, params, tag) : null;
    }
    if (type === 'ss' || type === 'shadowsocks') {
      return values.cipher && values.password
        ? this.buildShadowsocksLink(values.cipher, values.password, address, port, tag)
        : null;
    }
    return null;
  }

  private parseYamlPair(line: string): [string, string] | null {
    const index = line.indexOf(':');
    if (index <= 0) {
      return null;
    }
    const key = line.slice(0, index).trim();
    const value = line.slice(index + 1).trim().replace(/^['"]|['"]$/g, '');
    return key && value ? [key, value] : null;
  }

  private commonStreamParams(streamSettings?: Record<string, unknown>): Record<string, string> {
    if (!streamSettings) {
      return {};
    }
    const params: Record<string, string> = {};
    const network = this.stringValue(streamSettings.network);
    if (network) {
      params.type = this.normalizeNetworkName(network);
    }
    const security = this.stringValue(streamSettings.security);
    if (security) {
      params.security = security;
    }

    const tlsSettings = this.recordValue(streamSettings.tlsSettings);
    const realitySettings = this.recordValue(streamSettings.realitySettings);
    const wsSettings = this.recordValue(streamSettings.wsSettings);
    const httpSettings = this.recordValue(streamSettings.httpSettings);
    const grpcSettings = this.recordValue(streamSettings.grpcSettings);
    const tcpSettings = this.recordValue(streamSettings.tcpSettings);

    this.assignParam(params, 'sni', this.stringValue(tlsSettings?.serverName));
    this.assignParam(params, 'fp', this.stringValue(tlsSettings?.fingerprint));
    const alpn = Array.isArray(tlsSettings?.alpn) ? tlsSettings?.alpn.filter((item) => typeof item === 'string').join(',') : undefined;
    this.assignParam(params, 'alpn', alpn);
    this.assignParam(params, 'pbk', this.stringValue(realitySettings?.publicKey));
    this.assignParam(params, 'sid', this.stringValue(realitySettings?.shortId));
    this.assignParam(params, 'spx', this.stringValue(realitySettings?.spiderX));
    this.assignParam(params, 'path', this.stringValue(wsSettings?.path) || this.stringValue(httpSettings?.path));
    this.assignParam(params, 'host', this.extractHostHeader(wsSettings?.headers) || this.extractHostHeader(wsSettings?.host) || this.extractHostHeader(httpSettings?.host));
    this.assignParam(params, 'serviceName', this.stringValue(grpcSettings?.serviceName));
    const tcpHeader = this.recordValue(tcpSettings?.header);
    this.assignParam(params, 'headerType', this.stringValue(tcpHeader?.type));
    return params;
  }

  private commonSingBoxParams(outbound: Record<string, unknown>): Record<string, string> {
    const params: Record<string, string> = {};
    const transport = this.recordValue(outbound.transport);
    const tls = this.recordValue(outbound.tls);
    const reality = this.recordValue(tls?.reality);
    const utls = this.recordValue(tls?.utls);

    const transportType = this.stringValue(transport?.type);
    if (transportType) {
      params.type = this.normalizeNetworkName(transportType);
    }
    this.assignParam(params, 'path', this.stringValue(transport?.path));
    this.assignParam(params, 'host', this.stringValue(transport?.host));
    this.assignParam(params, 'serviceName', this.stringValue(transport?.service_name));

    if (tls?.enabled === true) {
      params.security = reality?.enabled === true ? 'reality' : 'tls';
    }
    this.assignParam(params, 'sni', this.stringValue(tls?.server_name));
    this.assignParam(params, 'fp', this.stringValue(utls?.fingerprint));
    this.assignParam(params, 'pbk', this.stringValue(reality?.public_key));
    this.assignParam(params, 'sid', this.stringValue(reality?.short_id));
    this.assignParam(params, 'spx', this.stringValue(reality?.spider_x));
    return params;
  }

  private vmessFieldsFromStream(streamSettings?: Record<string, unknown>): Record<string, unknown> {
    const params = this.commonStreamParams(streamSettings);
    return this.vmessFieldsFromParams(params);
  }

  private vmessFieldsFromParams(params: Record<string, string>): Record<string, unknown> {
    return {
      net: params.type || 'tcp',
      tls: params.security || 'none',
      path: params.path,
      host: params.host,
      sni: params.sni,
      fp: params.fp,
      alpn: params.alpn,
      serviceName: params.serviceName,
      flow: params.flow,
      pbk: params.pbk,
      sid: params.sid,
      spx: params.spx,
    };
  }

  private normalizeNetworkName(value: string): string {
    const normalized = value.toLowerCase();
    if (normalized === 'websocket') {
      return 'ws';
    }
    if (['http', 'h2', 'httpupgrade', 'xhttp', 'splithttp'].includes(normalized)) {
      return normalized === 'h2' ? 'httpupgrade' : normalized;
    }
    return normalized;
  }

  private buildVlessLink(uuid: string, host: string, port: number, params: Record<string, string>, tag: string): string {
    return `vless://${encodeURIComponent(uuid)}@${host}:${port}${this.queryString(params)}#${encodeURIComponent(tag)}`;
  }

  private buildTrojanLink(password: string, host: string, port: number, params: Record<string, string>, tag: string): string {
    return `trojan://${encodeURIComponent(password)}@${host}:${port}${this.queryString(params)}#${encodeURIComponent(tag)}`;
  }

  private buildShadowsocksLink(method: string, password: string, host: string, port: number, tag: string): string {
    const credentials = this.toBase64Url(Buffer.from(`${method}:${password}`, 'utf8'));
    return `ss://${credentials}@${host}:${port}#${encodeURIComponent(tag)}`;
  }

  private buildVmessLink(fields: Record<string, unknown>): string {
    const compact = Object.fromEntries(
      Object.entries({
        v: '2',
        ...fields,
      }).filter(([, value]) => value !== undefined && value !== null && value !== ''),
    );
    return `vmess://${this.toBase64Url(Buffer.from(JSON.stringify(compact), 'utf8'))}`;
  }

  private queryString(params: Record<string, string>): string {
    const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== '');
    if (entries.length === 0) {
      return '';
    }
    return `?${entries.map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&')}`;
  }

  private assignParam(target: Record<string, string>, key: string, value?: string) {
    if (value && value.trim()) {
      target[key] = value.trim();
    }
  }

  private extractHostHeader(value: unknown): string | undefined {
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
    if (Array.isArray(value)) {
      return value.find((item) => typeof item === 'string' && item.trim());
    }
    if (value && typeof value === 'object') {
      const record = value as Record<string, unknown>;
      return this.stringValue(record.Host) || this.stringValue(record.host);
    }
    return undefined;
  }

  private recordValue(value: unknown): Record<string, unknown> | undefined {
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : undefined;
  }

  private firstRecord(value: unknown): Record<string, unknown> | undefined {
    if (!Array.isArray(value)) {
      return undefined;
    }
    const first = value.find((item) => item && typeof item === 'object' && !Array.isArray(item));
    return first as Record<string, unknown> | undefined;
  }

  private isRemoteSubscriptionUrl(value: string): boolean {
    return /^https?:\/\//i.test(value.trim());
  }

  private async fetchRemoteSubscriptionPayload(
    rawUrl: string,
    redirects = 0,
    cookieHeader?: string,
  ): Promise<RemoteSubscriptionResponse> {
    if (redirects > VpnConfigService.REMOTE_SUBSCRIPTION_MAX_REDIRECTS) {
      throw new Error('Too many subscription redirects');
    }

    const url = new URL(rawUrl);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw new Error('Unsupported subscription URL protocol');
    }
    if (await this.isBlockedHealthcheckHost(url.hostname)) {
      throw new Error('Blocked subscription URL host');
    }

    const transport = url.protocol === 'https:' ? https : http;
    return new Promise((resolve, reject) => {
      const headers: Record<string, string> = {
        ...VpnConfigService.REMOTE_SUBSCRIPTION_HEADERS,
      };
      if (cookieHeader) {
        headers.Cookie = cookieHeader;
      }
      const request = transport.get(url, {
        headers,
        timeout: VpnConfigService.REMOTE_SUBSCRIPTION_TIMEOUT_MS,
      }, (response) => {
        const status = response.statusCode || 0;
        const location = response.headers.location;
        if ([301, 302, 303, 307, 308].includes(status) && location) {
          response.resume();
          const nextUrl = new URL(location, url).toString();
          const nextCookieHeader = this.mergeSetCookieHeader(cookieHeader, response.headers['set-cookie']);
          this.fetchRemoteSubscriptionPayload(nextUrl, redirects + 1, nextCookieHeader).then(resolve, reject);
          return;
        }
        if (status < 200 || status >= 300) {
          response.resume();
          reject(new Error(`Subscription fetch failed with status ${status}`));
          return;
        }

        // Capture the metadata headers the way v2rayNG/Happ do — this is where a subscription's
        // expiry (`expire=<unix>`) and traffic live. Header names are case-insensitive.
        const headerValue = (name: string): string | undefined => {
          const raw = response.headers[name];
          return Array.isArray(raw) ? raw[0] : raw;
        };
        const subscriptionUserInfo = headerValue('subscription-userinfo');
        const profileUpdateInterval = headerValue('profile-update-interval');

        const chunks: Buffer[] = [];
        let received = 0;
        response.on('data', (chunk: Buffer) => {
          received += chunk.length;
          if (received > VpnConfigService.REMOTE_SUBSCRIPTION_MAX_BYTES) {
            request.destroy(new Error('Subscription payload too large'));
            return;
          }
          chunks.push(chunk);
        });
        response.on('end', () =>
          resolve({
            body: Buffer.concat(chunks).toString('utf8'),
            subscriptionUserInfo,
            profileUpdateInterval,
          }),
        );
      });
      request.on('timeout', () => request.destroy(new Error('Subscription fetch timed out')));
      request.on('error', reject);
    });
  }

  private mergeSetCookieHeader(
    currentCookieHeader: string | undefined,
    setCookieHeader: string[] | string | undefined,
  ): string | undefined {
    if (!setCookieHeader) {
      return currentCookieHeader;
    }

    const cookies = new Map<string, string>();
    for (const cookie of (currentCookieHeader || '').split(';')) {
      const [name, ...rest] = cookie.trim().split('=');
      if (name && rest.length > 0) {
        cookies.set(name, `${name}=${rest.join('=')}`);
      }
    }

    const setCookies = Array.isArray(setCookieHeader) ? setCookieHeader : [setCookieHeader];
    for (const setCookie of setCookies) {
      const firstPart = setCookie.split(';')[0]?.trim();
      const [name] = firstPart.split('=');
      if (name && firstPart) {
        cookies.set(name, firstPart);
      }
    }

    return cookies.size > 0 ? Array.from(cookies.values()).join('; ') : undefined;
  }

  private runtimeNodeId(rawConfig: string): string {
    return crypto.createHash('sha256').update(rawConfig).digest('hex').slice(0, 16);
  }

  private decodeBase64Text(value: string): string | null {
    try {
      const normalized = this.fromBase64Url(value);
      const decoded = Buffer.from(normalized, 'base64').toString('utf8').trim();
      return decoded ? decoded : null;
    } catch {
      return null;
    }
  }

  private stringValue(value: unknown): string | undefined {
    return typeof value === 'string' && value.trim() ? value.trim() : undefined;
  }

  private safePort(value: unknown): number | undefined {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 1 && parsed <= 65535 ? parsed : undefined;
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
