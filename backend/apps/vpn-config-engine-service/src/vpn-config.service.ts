import { Injectable } from "@nestjs/common";
import { SwimVpnProfile, VpnProtocol } from "@app/contracts";
import * as crypto from "crypto";
import * as zlib from "zlib";
import * as net from "net";
import { promises as dns } from "dns";

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
    host?: string;
    serviceName?: string;
    headerType?: string;
    flow?: string;
    sid?: string;
    pbk?: string;
    fp?: string;
    spiderX?: string;
    alpn?: string[];
    allowInsecure?: boolean;
    method?: string;
    password?: string;
    plugin?: string;
    pluginOptions?: string;
  };
}

export interface SwimCryptImportResult {
  version: "crypt1";
  link: string;
  compressed: boolean;
  plaintextBytes: number;
  envelopeBytes: number;
}

export interface ResolvedSwimCryptImportResult {
  version: "crypt1";
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
      return this.invalid(
        ingested,
        error instanceof Error ? error.message : "Failed to process config",
      );
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
      const lower = trimmed.toLowerCase();
      if (lower.startsWith("vless://")) {
        return this.parseVless(trimmed);
      }
      if (lower.startsWith("vmess://")) {
        return this.parseVmess(trimmed);
      }
      if (lower.startsWith("trojan://")) {
        return this.parseTrojan(trimmed);
      }
      if (lower.startsWith("ss://")) {
        return this.parseShadowsocks(trimmed);
      }
      if (this.isJsonConfig(trimmed)) {
        return this.parseJsonConfig(trimmed);
      }
      if (lower.startsWith("https://") || lower.startsWith("http://")) {
        return this.parseSubscriptionLink(trimmed);
      }

      return this.invalid(trimmed, "Unsupported protocol");
    } catch (e) {
      return this.invalid(
        trimmed,
        e instanceof Error ? e.message : "Failed to parse config",
      );
    }
  }

  private validate(profile: SwimVpnProfile): void {
    if (profile.validationState === "INVALID") {
      throw new Error(profile.errorMessage || "Invalid profile");
    }
    if (!profile.address || !profile.port) {
      throw new Error("Missing address or port");
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

  private classify(
    profile: SwimVpnProfile,
  ): ConfigPipelineResult["classification"] {
    return {
      protocol: profile.protocol,
      transport: profile.transport,
      security: profile.security,
    };
  }

  private preview(profile: SwimVpnProfile): ConfigPipelineResult["preview"] {
    return {
      title: profile.displayTitle,
      address: profile.address,
      port: profile.port,
      protocol: profile.protocol,
    };
  }

  private prepareRuntimePayload(
    profile: SwimVpnProfile,
  ): ConfigPipelineResult["runtimePayload"] {
    return {
      protocol: profile.protocol,
      address: profile.address,
      port: profile.port,
      uuid: profile.uuid,
      security: profile.security,
      transport: profile.transport,
      sni: profile.sni,
      path: profile.path,
      host: profile.host,
      serviceName: profile.serviceName,
      headerType: profile.headerType,
      flow: profile.flow,
      sid: profile.sid,
      pbk: profile.pbk,
      fp: profile.fp,
      spiderX: profile.spiderX,
      alpn: profile.alpn,
      allowInsecure: profile.allowInsecure,
      method: profile.method,
      password: profile.password,
      plugin: profile.plugin,
      pluginOptions: profile.pluginOptions,
    };
  }

  private parseVless(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const params = url.searchParams;
    const uuid = decodeURIComponent(url.username);
    const address = url.hostname;
    const port = this.parsePort(url.port, 443);
    const security = params.get("security") || "none";
    const transport = this.normalizeTransport(params.get("type") || "tcp");
    const host = params.get("host") || undefined;
    const path = params.get("path") || undefined;
    const serviceName =
      params.get("serviceName") || (transport === "grpc" ? path : undefined);
    const headerType = params.get("headerType") || undefined;
    const flow = params.get("flow") || undefined;
    const sid = params.get("sid") || undefined;
    const pbk = params.get("pbk") || undefined;
    const fp = params.get("fp") || undefined;
    const spiderX = params.get("spx") || undefined;
    const sni = params.get("sni") || host || undefined;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.VLESS,
      uuid,
      address,
      port,
      security,
      transport,
      sni,
      path,
      host,
      serviceName,
      headerType,
      flow,
      sid,
      pbk,
      fp,
      spiderX,
      alpn: this.parseCsv(params.get("alpn")),
      allowInsecure: this.parseBooleanParam(
        params,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.decodeFragment(url.hash, "VLESS Config"),
      validationState: "VALID",
    };
  }

  private parseVmess(raw: string): SwimVpnProfile {
    const encoded = raw.slice("vmess://".length).trim();
    const json = JSON.parse(this.decodeBase64Flexible(encoded)) as Record<
      string,
      unknown
    >;
    const address = this.requiredString(
      json.add,
      "Missing address in VMess config",
    );
    const port = this.parsePortValue(json.port, "Missing port in VMess config");
    const uuid = this.requiredString(
      json.id,
      "Missing user ID in VMess config",
    );
    const transport = this.normalizeTransport(
      this.optionalString(json.net) || "tcp",
    );
    const security = this.optionalString(json.tls) || "none";
    const path = this.optionalString(json.path);
    const host = this.extractHostHeader(json.host);
    const serviceName =
      this.optionalString(json.serviceName) ||
      (transport === "grpc" ? path : undefined);
    const sni =
      this.optionalString(json.sni) ||
      this.optionalString(json.serverName) ||
      host ||
      undefined;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.VMESS,
      uuid,
      address,
      port,
      security,
      transport,
      sni,
      path,
      host,
      serviceName,
      headerType:
        this.optionalString(json.headerType) || this.optionalString(json.type),
      flow: this.optionalString(json.flow),
      fp: this.optionalString(json.fp),
      alpn: this.extractAlpn(json.alpn),
      allowInsecure: this.parseBooleanRecord(
        json,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.optionalString(json.ps) || `VMess: ${address}`,
      validationState: "VALID",
    };
  }

  private parseTrojan(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const params = url.searchParams;
    const password = decodeURIComponent(url.username);
    const address = url.hostname;
    const port = this.parsePort(url.port, 443);
    const transport = this.normalizeTransport(params.get("type") || "tcp");
    const security = params.get("security") || "tls";
    const host = params.get("host") || undefined;
    const path = params.get("path") || undefined;
    const serviceName =
      params.get("serviceName") || (transport === "grpc" ? path : undefined);
    const sni = params.get("sni") || host || address;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.TROJAN,
      uuid: password,
      password,
      address,
      port,
      security,
      transport,
      sni,
      path,
      host,
      serviceName,
      fp: params.get("fp") || undefined,
      alpn: this.parseCsv(params.get("alpn")),
      allowInsecure: this.parseBooleanParam(
        params,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.decodeFragment(url.hash, "Trojan Config"),
      validationState: "VALID",
    };
  }

  private parseShadowsocks(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    let userInfo = url.username;
    let host = url.hostname;
    let port = this.parsePort(url.port, 8388);
    let method = "";
    let password = "";

    try {
      const decoded = this.decodeBase64Flexible(userInfo);
      if (decoded.includes(":")) {
        const parts = decoded.split(":");
        method = parts[0];
        password = parts.slice(1).join(":");
      } else {
        throw new Error("encoded userinfo did not contain method/password");
      }
    } catch {
      const decodedUserInfo = decodeURIComponent(userInfo);
      if (decodedUserInfo.includes(":")) {
        const parts = decodedUserInfo.split(":");
        method = parts[0];
        password = parts.slice(1).join(":");
      } else if (!host && !url.port) {
        const fullDecoded = this.decodeBase64Flexible(url.host);
        const [cred, server] = fullDecoded.split("@");
        const [parsedMethod, parsedPassword] = cred.split(":");
        const [parsedHost, parsedPort] = server.split(":");
        method = parsedMethod;
        password = parsedPassword;
        host = parsedHost;
        port = this.parsePort(parsedPort, 8388);
      } else {
        method = "unknown";
        password = decodedUserInfo;
      }
    }

    return {
      rawConfig: raw,
      protocol: VpnProtocol.SHADOWSOCKS,
      uuid: password,
      password,
      method,
      address: host,
      port,
      security: method,
      transport: "tcp",
      plugin: url.searchParams.get("plugin") || undefined,
      pluginOptions: url.searchParams.get("plugin-opts") || undefined,
      displayTitle: this.decodeFragment(url.hash, "SS Config"),
      validationState: "VALID",
    };
  }

  private parseJsonConfig(raw: string): SwimVpnProfile {
    const root = JSON.parse(raw) as Record<string, unknown>;
    const outbound = this.extractPrimaryOutbound(root);
    if (!outbound) {
      return this.invalid(
        raw,
        "No supported outbound found in JSON configuration",
      );
    }

    const protocol = this.requiredString(
      outbound.protocol,
      "Missing protocol in outbound configuration",
    ).toLowerCase();
    const streamSettings = this.asRecord(outbound.streamSettings);

    switch (protocol) {
      case "vless":
        return this.parseVlessJsonOutbound(raw, outbound, streamSettings);
      case "vmess":
        return this.parseVmessJsonOutbound(raw, outbound, streamSettings);
      case "trojan":
        return this.parseTrojanJsonOutbound(raw, outbound, streamSettings);
      case "shadowsocks":
        return this.parseShadowsocksJsonOutbound(raw, outbound);
      default:
        return this.invalid(
          raw,
          `Unsupported protocol '${protocol}' in JSON configuration`,
        );
    }
  }

  private parseVlessJsonOutbound(
    raw: string,
    outbound: Record<string, unknown>,
    streamSettings?: Record<string, unknown>,
  ): SwimVpnProfile {
    const vnext = this.firstRecord(this.asRecord(outbound.settings)?.vnext);
    const user = this.firstRecord(vnext?.users);
    const address = this.requiredString(
      vnext?.address,
      "Missing address in VLESS JSON configuration",
    );
    const port = this.parsePortValue(
      vnext?.port,
      "Missing port in VLESS JSON configuration",
    );
    const uuid = this.requiredString(
      user?.id,
      "Missing user ID in VLESS JSON configuration",
    );
    const security = this.optionalString(streamSettings?.security) || "none";
    const transport = this.normalizeTransport(
      this.optionalString(streamSettings?.network) || "tcp",
    );
    const tls = this.asRecord(streamSettings?.tlsSettings);
    const reality = this.asRecord(streamSettings?.realitySettings);
    const ws = this.asRecord(streamSettings?.wsSettings);
    const grpc = this.asRecord(streamSettings?.grpcSettings);
    const http = this.asRecord(streamSettings?.httpSettings);
    const tcp = this.asRecord(streamSettings?.tcpSettings);
    const tcpHeader = this.asRecord(tcp?.header);

    return {
      rawConfig: raw,
      rawJson: raw,
      protocol: VpnProtocol.VLESS,
      uuid,
      address,
      port,
      security,
      transport,
      sni:
        this.optionalString(tls?.serverName) ||
        this.optionalString(reality?.serverName) ||
        address,
      path: this.optionalString(ws?.path) || this.optionalString(http?.path),
      host:
        this.extractHostHeader(
          ws?.headers ? this.asRecord(ws.headers)?.Host : undefined,
        ) || this.extractHostHeader(http?.host),
      serviceName: this.optionalString(grpc?.serviceName),
      headerType: this.optionalString(tcpHeader?.type),
      flow:
        this.optionalString(user?.flow) || this.optionalString(outbound.flow),
      sid: this.optionalString(reality?.shortId),
      pbk: this.optionalString(reality?.publicKey),
      fp:
        this.optionalString(tls?.fingerprint) ||
        this.optionalString(reality?.fingerprint),
      spiderX: this.optionalString(reality?.spiderX),
      alpn: this.extractAlpn(tls?.alpn),
      allowInsecure: this.parseBooleanRecord(
        tls,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.optionalString(outbound.tag) || `VLESS: ${address}`,
      validationState: "VALID",
    };
  }

  private parseVmessJsonOutbound(
    raw: string,
    outbound: Record<string, unknown>,
    streamSettings?: Record<string, unknown>,
  ): SwimVpnProfile {
    const vnext = this.firstRecord(this.asRecord(outbound.settings)?.vnext);
    const user = this.firstRecord(vnext?.users);
    const address = this.requiredString(
      vnext?.address,
      "Missing address in VMess JSON configuration",
    );
    const port = this.parsePortValue(
      vnext?.port,
      "Missing port in VMess JSON configuration",
    );
    const uuid = this.requiredString(
      user?.id,
      "Missing user ID in VMess JSON configuration",
    );
    const tls = this.asRecord(streamSettings?.tlsSettings);
    const ws = this.asRecord(streamSettings?.wsSettings);
    const grpc = this.asRecord(streamSettings?.grpcSettings);
    const http = this.asRecord(streamSettings?.httpSettings);

    return {
      rawConfig: raw,
      rawJson: raw,
      protocol: VpnProtocol.VMESS,
      uuid,
      address,
      port,
      security: this.optionalString(streamSettings?.security) || "none",
      transport: this.normalizeTransport(
        this.optionalString(streamSettings?.network) || "tcp",
      ),
      sni: this.optionalString(tls?.serverName) || address,
      path: this.optionalString(ws?.path) || this.optionalString(http?.path),
      host:
        this.extractHostHeader(
          ws?.headers ? this.asRecord(ws.headers)?.Host : undefined,
        ) || this.extractHostHeader(http?.host),
      serviceName: this.optionalString(grpc?.serviceName),
      fp: this.optionalString(tls?.fingerprint),
      alpn: this.extractAlpn(tls?.alpn),
      allowInsecure: this.parseBooleanRecord(
        tls,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.optionalString(outbound.tag) || `VMess: ${address}`,
      validationState: "VALID",
    };
  }

  private parseTrojanJsonOutbound(
    raw: string,
    outbound: Record<string, unknown>,
    streamSettings?: Record<string, unknown>,
  ): SwimVpnProfile {
    const server = this.firstRecord(this.asRecord(outbound.settings)?.servers);
    const address = this.requiredString(
      server?.address,
      "Missing address in Trojan JSON configuration",
    );
    const port = this.parsePortValue(
      server?.port,
      "Missing port in Trojan JSON configuration",
    );
    const password = this.requiredString(
      server?.password,
      "Missing password in Trojan JSON configuration",
    );
    const tls = this.asRecord(streamSettings?.tlsSettings);
    const ws = this.asRecord(streamSettings?.wsSettings);
    const grpc = this.asRecord(streamSettings?.grpcSettings);
    const http = this.asRecord(streamSettings?.httpSettings);

    return {
      rawConfig: raw,
      rawJson: raw,
      protocol: VpnProtocol.TROJAN,
      uuid: password,
      password,
      address,
      port,
      security: this.optionalString(streamSettings?.security) || "tls",
      transport: this.normalizeTransport(
        this.optionalString(streamSettings?.network) || "tcp",
      ),
      sni: this.optionalString(tls?.serverName) || address,
      path: this.optionalString(ws?.path) || this.optionalString(http?.path),
      host:
        this.extractHostHeader(
          ws?.headers ? this.asRecord(ws.headers)?.Host : undefined,
        ) || this.extractHostHeader(http?.host),
      serviceName: this.optionalString(grpc?.serviceName),
      fp: this.optionalString(tls?.fingerprint),
      alpn: this.extractAlpn(tls?.alpn),
      allowInsecure: this.parseBooleanRecord(
        tls,
        "allowInsecure",
        "insecure",
        "tlsInsecure",
        "skip-cert-verify",
      ),
      displayTitle: this.optionalString(outbound.tag) || `Trojan: ${address}`,
      validationState: "VALID",
    };
  }

  private parseShadowsocksJsonOutbound(
    raw: string,
    outbound: Record<string, unknown>,
  ): SwimVpnProfile {
    const server = this.firstRecord(this.asRecord(outbound.settings)?.servers);
    const address = this.requiredString(
      server?.address,
      "Missing address in Shadowsocks JSON configuration",
    );
    const port = this.parsePortValue(
      server?.port,
      "Missing port in Shadowsocks JSON configuration",
    );
    const password = this.requiredString(
      server?.password,
      "Missing password in Shadowsocks JSON configuration",
    );
    const method = this.requiredString(
      server?.method,
      "Missing method in Shadowsocks JSON configuration",
    );

    return {
      rawConfig: raw,
      rawJson: raw,
      protocol: VpnProtocol.SHADOWSOCKS,
      uuid: password,
      password,
      method,
      address,
      port,
      security: method,
      transport: "tcp",
      plugin: this.optionalString(server?.plugin),
      pluginOptions:
        this.optionalString(server?.plugin_opts) ||
        this.optionalString(server?.pluginOptions),
      displayTitle:
        this.optionalString(outbound.tag) || `Shadowsocks: ${address}`,
      validationState: "VALID",
    };
  }

  private parseSubscriptionLink(raw: string): SwimVpnProfile {
    const url = new URL(raw);
    const port = url.port
      ? parseInt(url.port, 10)
      : url.protocol === "http:"
        ? 80
        : 443;

    return {
      rawConfig: raw,
      protocol: VpnProtocol.UNKNOWN,
      uuid: url.pathname || url.hostname,
      address: url.hostname,
      port,
      security: url.protocol.replace(":", ""),
      transport: "subscription",
      path: url.pathname || undefined,
      displayTitle: url.hostname,
      validationState: "VALID",
    };
  }

  async checkHealth(
    raw: string,
  ): Promise<{ alive: boolean; latency?: number }> {
    const profile = this.parse(raw);
    if (profile.validationState === "INVALID") {
      return { alive: false };
    }
    if (await this.isBlockedHealthcheckHost(profile.address)) {
      return { alive: false };
    }

    return new Promise((resolve) => {
      const start = Date.now();
      const socket = new net.Socket();

      socket.setTimeout(3000); // 3 seconds timeout

      socket.on("connect", () => {
        const latency = Date.now() - start;
        socket.destroy();
        resolve({ alive: true, latency });
      });

      socket.on("error", () => {
        socket.destroy();
        resolve({ alive: false });
      });

      socket.on("timeout", () => {
        socket.destroy();
        resolve({ alive: false });
      });

      socket.connect(profile.port, profile.address);
    });
  }

  private async isBlockedHealthcheckHost(host: string): Promise<boolean> {
    const normalized = host
      .trim()
      .toLowerCase()
      .replace(/^\[|\]$/g, "");
    if (
      !normalized ||
      normalized === "localhost" ||
      normalized.endsWith(".localhost")
    ) {
      return true;
    }

    if (net.isIP(normalized)) {
      return this.isBlockedHealthcheckIp(normalized);
    }

    try {
      const addresses = await dns.lookup(normalized, { all: true });
      return addresses.some((entry) =>
        this.isBlockedHealthcheckIp(entry.address),
      );
    } catch {
      return true;
    }
  }

  private isBlockedHealthcheckIp(address: string): boolean {
    const family = net.isIP(address);
    if (family === 4) {
      const octets = address
        .split(".")
        .map((part) => Number.parseInt(part, 10));
      if (octets.length !== 4 || octets.some((part) => Number.isNaN(part))) {
        return true;
      }
      const [a, b] = octets;
      return (
        a === 0 ||
        a === 10 ||
        a === 127 ||
        (a === 100 && b >= 64 && b <= 127) ||
        (a === 169 && b === 254) ||
        (a === 172 && b >= 16 && b <= 31) ||
        (a === 192 && b === 168) ||
        (a === 192 && b === 0) ||
        (a === 198 && (b === 18 || b === 19)) ||
        a >= 224
      );
    }

    if (family === 6) {
      const normalized = address.toLowerCase();
      return (
        normalized === "::" ||
        normalized === "::1" ||
        normalized.startsWith("fc") ||
        normalized.startsWith("fd") ||
        normalized.startsWith("fe80:") ||
        normalized.startsWith("ff")
      );
    }

    return true;
  }

  generateSwimCryptImport(data: {
    rawConfig: string;
    compress?: boolean;
  }): SwimCryptImportResult {
    const key = this.getCrypt1Key();
    const ingested = this.ingest(data.rawConfig);
    if (!ingested) {
      throw new Error("rawConfig is required");
    }

    const plain = Buffer.from(ingested, "utf8");
    const payload = data.compress ? zlib.gzipSync(plain) : plain;
    const nonce = crypto.randomBytes(VpnConfigService.CRYPT1_NONCE_BYTES);
    const cipher = crypto.createCipheriv("aes-256-gcm", key, nonce);
    const ciphertext = Buffer.concat([cipher.update(payload), cipher.final()]);
    const authTag = cipher.getAuthTag();
    const envelope = Buffer.concat([nonce, ciphertext, authTag]);

    return {
      version: "crypt1",
      link: `swimvpn://crypt1/${this.toBase64Url(envelope)}`,
      compressed: Boolean(data.compress),
      plaintextBytes: plain.length,
      envelopeBytes: envelope.length,
    };
  }

  resolveSwimCryptImport(data: {
    encryptedLink: string;
  }): ResolvedSwimCryptImportResult {
    const key = this.getCrypt1Key();
    const payload = this.extractCrypt1Payload(data.encryptedLink);
    const envelope = Buffer.from(this.fromBase64Url(payload), "base64");
    const minEnvelopeBytes =
      VpnConfigService.CRYPT1_NONCE_BYTES +
      VpnConfigService.CRYPT1_AUTH_TAG_BYTES +
      1;
    if (envelope.length < minEnvelopeBytes) {
      throw new Error("SWIMVPN crypt1 payload is too short");
    }

    const nonce = envelope.subarray(0, VpnConfigService.CRYPT1_NONCE_BYTES);
    const authTag = envelope.subarray(
      envelope.length - VpnConfigService.CRYPT1_AUTH_TAG_BYTES,
    );
    const ciphertext = envelope.subarray(
      VpnConfigService.CRYPT1_NONCE_BYTES,
      envelope.length - VpnConfigService.CRYPT1_AUTH_TAG_BYTES,
    );
    const decipher = crypto.createDecipheriv("aes-256-gcm", key, nonce);
    decipher.setAuthTag(authTag);
    const plain = Buffer.concat([
      decipher.update(ciphertext),
      decipher.final(),
    ]);
    const compressed = this.isGzip(plain);
    const unpacked = compressed ? zlib.gunzipSync(plain) : plain;

    return {
      version: "crypt1",
      rawConfig: unpacked.toString("utf8").trim(),
      compressed,
    };
  }

  private getCrypt1Key(): Buffer {
    const raw = process.env.SWIMVPN_CRYPT1_KEY_BASE64?.trim();
    if (!raw) {
      throw new Error("SWIMVPN_CRYPT1_KEY_BASE64 is not configured");
    }

    const key = Buffer.from(this.fromBase64Url(raw), "base64");
    if (key.length !== 32) {
      throw new Error(
        "SWIMVPN_CRYPT1_KEY_BASE64 must decode to 32 bytes for AES-256-GCM",
      );
    }
    return key;
  }

  private toBase64Url(value: Buffer): string {
    return value
      .toString("base64")
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/g, "");
  }

  private decodeBase64Flexible(value: string): string {
    const compact = value.trim().replace(/\s+/g, "");
    return Buffer.from(this.fromBase64Url(compact), "base64").toString("utf8");
  }

  private isJsonConfig(value: string): boolean {
    const trimmed = value.trim();
    return (
      (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
      (trimmed.startsWith("[") && trimmed.endsWith("]"))
    );
  }

  private extractPrimaryOutbound(
    root: Record<string, unknown>,
  ): Record<string, unknown> | undefined {
    const candidates = Array.isArray(root.outbounds)
      ? root.outbounds
      : root.outbound
        ? [root.outbound]
        : [];

    return candidates
      .map((candidate) => this.asRecord(candidate))
      .find((candidate) => {
        const protocol = this.optionalString(
          candidate?.protocol,
        )?.toLowerCase();
        return (
          protocol === "vless" ||
          protocol === "vmess" ||
          protocol === "trojan" ||
          protocol === "shadowsocks"
        );
      });
  }

  private asRecord(value: unknown): Record<string, unknown> | undefined {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return value as Record<string, unknown>;
    }
    return undefined;
  }

  private firstRecord(value: unknown): Record<string, unknown> | undefined {
    if (!Array.isArray(value)) {
      return undefined;
    }
    return this.asRecord(value[0]);
  }

  private optionalString(value: unknown): string | undefined {
    return typeof value === "string" && value.trim() ? value.trim() : undefined;
  }

  private requiredString(value: unknown, message: string): string {
    const parsed = this.optionalString(value);
    if (!parsed) {
      throw new Error(message);
    }
    return parsed;
  }

  private parsePort(value: string | undefined, fallback: number): number {
    if (!value) {
      return fallback;
    }
    const port = Number.parseInt(value, 10);
    return Number.isInteger(port) ? port : fallback;
  }

  private parsePortValue(value: unknown, message: string): number {
    const port =
      typeof value === "number"
        ? value
        : Number.parseInt(String(value ?? ""), 10);
    if (!Number.isInteger(port) || port <= 0 || port > 65535) {
      throw new Error(message);
    }
    return port;
  }

  private normalizeTransport(value: string): string {
    const normalized = value.toLowerCase();
    if (normalized === "websocket") {
      return "ws";
    }
    if (
      normalized === "h2" ||
      normalized === "httpupgrade" ||
      normalized === "xhttp" ||
      normalized === "splithttp"
    ) {
      return "http";
    }
    return normalized;
  }

  private decodeFragment(hash: string, fallback: string): string {
    const raw = hash.replace(/^#/, "");
    return raw ? decodeURIComponent(raw) : fallback;
  }

  private parseCsv(value: string | null | undefined): string[] | undefined {
    const parsed =
      value
        ?.split(",")
        .map((item) => item.trim())
        .filter(Boolean) ?? [];
    return parsed.length ? parsed : undefined;
  }

  private extractAlpn(value: unknown): string[] | undefined {
    if (Array.isArray(value)) {
      const parsed = value.filter(
        (item): item is string =>
          typeof item === "string" && item.trim().length > 0,
      );
      return parsed.length ? parsed : undefined;
    }
    if (typeof value === "string") {
      return this.parseCsv(value);
    }
    return undefined;
  }

  private extractHostHeader(value: unknown): string | undefined {
    if (Array.isArray(value)) {
      return value
        .find(
          (item): item is string =>
            typeof item === "string" && item.trim().length > 0,
        )
        ?.trim();
    }
    return this.optionalString(value);
  }

  private parseBooleanParam(
    params: URLSearchParams,
    ...keys: string[]
  ): boolean | undefined {
    for (const key of keys) {
      const value = params.get(key);
      if (value == null) continue;
      return value === "1" || value.toLowerCase() === "true";
    }
    return undefined;
  }

  private parseBooleanRecord(
    record: Record<string, unknown> | undefined,
    ...keys: string[]
  ): boolean | undefined {
    if (!record) {
      return undefined;
    }
    for (const key of keys) {
      const value = record[key];
      if (typeof value === "boolean") return value;
      if (typeof value === "string")
        return value === "1" || value.toLowerCase() === "true";
    }
    return undefined;
  }

  private fromBase64Url(value: string): string {
    const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
    return normalized.padEnd(
      normalized.length + ((4 - (normalized.length % 4)) % 4),
      "=",
    );
  }

  private extractCrypt1Payload(value: string): string {
    const trimmed = value.trim();
    const prefix = "swimvpn://crypt1/";
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
      uuid: "",
      address: "",
      port: 0,
      security: "",
      transport: "",
      displayTitle: "Invalid Config",
      validationState: "INVALID",
      errorMessage: msg,
    };
  }

  private extractPrimaryConfigCandidate(raw: string): string {
    const trimmed = raw.trim();
    if (
      trimmed.startsWith("vless://") ||
      trimmed.startsWith("ss://") ||
      trimmed.startsWith("http://") ||
      trimmed.startsWith("https://")
    ) {
      return trimmed.split(/\s+/)[0].trim();
    }

    const directUrlMatches = Array.from(
      trimmed.matchAll(/https?:\/\/[^\s)]+/gi),
    ).map((match) => match[0].trim());
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

    const providerName = lines.find(
      (line) =>
        /^[A-Za-z][A-Za-z0-9+\-_ ]{2,40}$/.test(line) &&
        !/^https?:\/\//i.test(line) &&
        !/^wb\./i.test(line) &&
        !/subscription page/i.test(line),
    );

    const usedTrafficLine = lines.find((line) =>
      /израсходовано|used/i.test(line),
    );
    const totalTrafficLine = lines.find((line) => /трафик|traffic/i.test(line));
    const expiryLine = lines.find((line) => /истекает|expires/i.test(line));
    const connectedLine = lines.find((line) =>
      /подключили|connected/i.test(line),
    );
    const deviceLimitLine = lines.find((line) =>
      /лимит устройств|device limit/i.test(line),
    );

    const usedTrafficMatch = usedTrafficLine?.match(
      /([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])\s*\/\s*([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])/u,
    );
    const totalTrafficMatch =
      totalTrafficLine?.match(/([\d.,]+)\s*([ГгGgМмMmКкKkТтTt]?[БB])/u) ||
      usedTrafficMatch;

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
        ? this.normalizeHumanExpiry(
            expiryMatch[1],
            expiryMatch[2],
            expiryMatch[3],
          )
        : undefined,
      connectedDevices: connectedMatch
        ? parseInt(connectedMatch[1], 10)
        : undefined,
      deviceLimit: deviceLimitMatch
        ? parseInt(deviceLimitMatch[1], 10)
        : undefined,
    };
  }

  private toBytes(rawNumber: string, rawUnit: string): number {
    const numeric = Number.parseFloat(rawNumber.replace(",", "."));
    const normalizedUnit = rawUnit
      .toUpperCase()
      .replace("Б", "B")
      .replace("Г", "G")
      .replace("М", "M")
      .replace("К", "K")
      .replace("Т", "T")
      .replace("Т", "T")
      .replace("TB", "TB")
      .replace("GB", "GB")
      .replace("MB", "MB")
      .replace("KB", "KB");

    const factor = normalizedUnit.startsWith("TB")
      ? 1024 ** 4
      : normalizedUnit.startsWith("GB")
        ? 1024 ** 3
        : normalizedUnit.startsWith("MB")
          ? 1024 ** 2
          : normalizedUnit.startsWith("KB")
            ? 1024
            : 1;

    return Math.round(numeric * factor);
  }

  private normalizeHumanExpiry(
    day: string,
    monthWord: string,
    year: string,
  ): string | undefined {
    const monthIndex = this.resolveMonthIndex(monthWord);
    if (!monthIndex) {
      return undefined;
    }

    const date = new Date(
      Date.UTC(
        Number.parseInt(year, 10),
        monthIndex - 1,
        Number.parseInt(day, 10),
      ),
    );
    return Number.isNaN(date.getTime())
      ? undefined
      : date.toISOString().replace(".000", "");
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
