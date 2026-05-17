import { VpnProtocol } from "@app/contracts";
import { VpnConfigService } from "../vpn-config.service";

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function b64Url(value: string): string {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

const service = new VpnConfigService();

const vlessReality = service.processPipeline(
  "vless://11111111-1111-4111-8111-111111111111@edge.example.com:443?type=grpc&security=reality&sni=cdn.example.com&serviceName=swim&flow=xtls-rprx-vision&pbk=pubKey&sid=abcd&fp=chrome&spx=%2F#Reality",
);
assert(
  vlessReality.normalizedProfile.protocol === VpnProtocol.VLESS,
  "VLESS protocol parsing failed",
);
assert(
  vlessReality.normalizedProfile.transport === "grpc",
  "VLESS transport normalization failed",
);
assert(
  vlessReality.normalizedProfile.security === "reality",
  "VLESS Reality security parsing failed",
);
assert(
  vlessReality.runtimePayload.pbk === "pubKey",
  "VLESS Reality public key not preserved",
);
assert(
  vlessReality.runtimePayload.spiderX === "/",
  "VLESS Reality spiderX not decoded",
);
assert(
  vlessReality.runtimePayload.flow === "xtls-rprx-vision",
  "VLESS flow not preserved",
);

const vmessJson = JSON.stringify({
  v: "2",
  ps: "VMess WS",
  add: "vmess.example.com",
  port: "443",
  id: "22222222-2222-4222-8222-222222222222",
  net: "ws",
  tls: "tls",
  host: "front.example.com",
  path: "/ws",
  sni: "sni.example.com",
  alpn: "h2,http/1.1",
  fp: "firefox",
});
const vmess = service.processPipeline(`vmess://${b64Url(vmessJson)}`);
assert(
  vmess.normalizedProfile.protocol === VpnProtocol.VMESS,
  "VMess protocol parsing failed",
);
assert(
  vmess.normalizedProfile.address === "vmess.example.com",
  "VMess address parsing failed",
);
assert(
  vmess.runtimePayload.host === "front.example.com",
  "VMess host header not preserved",
);
assert(
  vmess.runtimePayload.alpn?.join(",") === "h2,http/1.1",
  "VMess ALPN parsing failed",
);

const trojan = service.processPipeline(
  "trojan://secret-password@trojan.example.com:443?type=ws&security=tls&sni=tls.example.com&host=front.example.com&path=%2Ftrojan&allowInsecure=true#Trojan",
);
assert(
  trojan.normalizedProfile.protocol === VpnProtocol.TROJAN,
  "Trojan protocol parsing failed",
);
assert(
  trojan.runtimePayload.password === "secret-password",
  "Trojan password not preserved",
);
assert(
  trojan.runtimePayload.allowInsecure === true,
  "Trojan allowInsecure not parsed",
);
assert(trojan.runtimePayload.path === "/trojan", "Trojan path not decoded");

const ss = service.processPipeline(
  `ss://${b64Url("aes-256-gcm:secret")}@ss.example.com:8388?plugin=v2ray-plugin&plugin-opts=${encodeURIComponent("tls;host=front.example.com")}#SS`,
);
assert(
  ss.normalizedProfile.protocol === VpnProtocol.SHADOWSOCKS,
  "Shadowsocks protocol parsing failed",
);
assert(
  ss.runtimePayload.method === "aes-256-gcm",
  "Shadowsocks method not preserved",
);
assert(
  ss.runtimePayload.plugin === "v2ray-plugin",
  "Shadowsocks plugin not preserved",
);
assert(
  ss.runtimePayload.pluginOptions === "tls;host=front.example.com",
  "Shadowsocks plugin options not preserved",
);

const xrayJson = JSON.stringify({
  outbounds: [
    {
      tag: "proxy",
      protocol: "vless",
      settings: {
        vnext: [
          {
            address: "json.example.com",
            port: 443,
            users: [
              {
                id: "33333333-3333-4333-8333-333333333333",
                flow: "xtls-rprx-vision",
              },
            ],
          },
        ],
      },
      streamSettings: {
        network: "tcp",
        security: "reality",
        realitySettings: {
          serverName: "cdn.example.com",
          publicKey: "jsonPubKey",
          shortId: "ef01",
          spiderX: "/",
          fingerprint: "chrome",
        },
      },
    },
  ],
});
const jsonProfile = service.processPipeline(xrayJson);
assert(
  jsonProfile.normalizedProfile.protocol === VpnProtocol.VLESS,
  "JSON VLESS protocol parsing failed",
);
assert(
  jsonProfile.normalizedProfile.rawConfig === xrayJson,
  "JSON raw config was not preserved intact",
);
assert(
  jsonProfile.normalizedProfile.rawJson === xrayJson,
  "JSON rawJson was not preserved",
);
assert(
  jsonProfile.runtimePayload.pbk === "jsonPubKey",
  "JSON Reality public key not preserved",
);
assert(
  jsonProfile.runtimePayload.sid === "ef01",
  "JSON Reality shortId not preserved",
);

console.log("vpn config parser parity tests passed");
