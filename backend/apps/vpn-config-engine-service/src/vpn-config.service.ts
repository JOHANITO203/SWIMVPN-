import { Injectable } from '@nestjs/common';
import { SwimVpnProfile, VpnProtocol } from '@app/contracts';
import * as net from 'net';

@Injectable()
export class VpnConfigService {
  parse(raw: string): SwimVpnProfile {
    // ... (existing code)
    try {
      const trimmed = raw.trim();
      if (trimmed.startsWith('vless://')) {
        return this.parseVless(trimmed);
      }
      if (trimmed.startsWith('ss://')) {
        return this.parseShadowsocks(trimmed);
      }

      return this.invalid(trimmed, 'Unsupported protocol');
    } catch (e) {
      return this.invalid(raw, e.message);
    }
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
