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

@Injectable()
export class VpnConfigService {
  private static readonly CRYPT1_NONCE_BYTES = 12;

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

  private ingest(raw: string): string {
    return raw.trim();
  }

  private parseStage(trimmed: string): SwimVpnProfile {
    try {
      if (trimmed.startsWith('vless://')) {
        return this.parseVless(trimmed);
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
}
