export enum VpnProtocol {
  VLESS = 'VLESS',
  VMESS = 'VMESS',
  TROJAN = 'TROJAN',
  SHADOWSOCKS = 'SHADOWSOCKS',
  UNKNOWN = 'UNKNOWN'
}

export interface SwimVpnProfile {
  rawConfig: string;
  protocol: VpnProtocol;
  uuid: string;
  address: string;
  port: number;
  security: string; // e.g. "reality", "tls"
  transport: string; // e.g. "grpc", "ws"
  sni?: string;
  path?: string;
  serviceName?: string;
  headerType?: string;
  flow?: string;
  sid?: string;
  pbk?: string;
  fp?: string;
  displayTitle: string;
  validationState: 'VALID' | 'INVALID';
  errorMessage?: string;
}
