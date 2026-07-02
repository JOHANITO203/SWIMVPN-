# Onion Stealth — device spike (the gate before shipping)

Onion Stealth chains an embedded Tor client **under** the REALITY tunnel:

```
app → tun → tun2socks → xray socks-in(10808) → xray "tor" outbound → Tor SOCKS(9050)
Tor → Socks5Proxy 127.0.0.1:10810 → xray "tor-egress" inbound → "proxy"(REALITY) → server → guard → exit
```

The config layer + `TorController` are implemented and compile. **Nothing ships until this spike passes on a real device** — it validates that the anonymity promise is real (no leaks), which is the one thing unit tests cannot prove.

## Preconditions
- `SwimVpnService.startTorTunnel` is ALREADY wired behind `BuildConfig.DEBUG` (starts `TorController`,
  `awaitReady(90_000)`, then the normal tun2socks path with a Tor-latency-tolerant probe — not the 600 ms
  REALITY gate). Release builds still refuse `TOR_TUNNEL`.
- Trigger (already wired for debug builds): open **Technical settings → Routing** and tap the **"Onion"**
  route light (only shown when `BuildConfig.DEBUG`), then connect from Home. This persists
  `RuntimeMode.TOR_TUNNEL` and the manual-connect path now requests VPN consent for it. Release builds neither
  show the pill nor accept the mode (a production UI toggle is deferred until this spike passes).
- A working REALITY server profile (the same one used for FULL_TUNNEL).
- Build a debug APK (`assembleDebug`) — first run downloads Tor consensus (bootstrap 10–45 s). Expect TWO
  foreground-service notifications during the spike (SwimVpn + TorService); consolidating them is deferred.

## Pass/fail checks (all must pass)
1. **Bootstrap** — `TorController.state` reaches `READY` within the timeout; logcat shows Tor STATUS_ON.
2. **You are on Tor** — open `https://check.torproject.org` → "Congratulations. This browser is configured to use Tor." Exit IP = a Tor exit, not the REALITY server IP.
3. **No DNS leak** — run `https://dnsleaktest.com` (extended). No resolver belonging to our REALITY server / hosting provider appears. Cross-check server-side: the REALITY server must see **zero** DNS (:53) from this session.
4. **No IP/UDP leak** — `https://ipleak.dns` (WebRTC + IPv4/IPv6). No IPv6 exit; no QUIC/UDP path bypassing Tor (Tor is TCP-only). Confirm the IPv6 `::/0`→block rule and UDP-443 handling hold.
5. **ISP sees only REALITY** — capture the underlying network (or inspect server logs): the only outbound is REALITY to our server; no direct Tor guard/ORPort connections leave the device.
6. **Operator blindness** — on the REALITY server, confirm traffic is Tor-to-guard (opaque), i.e. destinations are NOT visible to us.
7. **Censored-network survival** — repeat on a network/profile that blocks Tor directly (or simulate). Onion Stealth must still connect because REALITY carries Tor.

## If a check fails
- DNS leak → revisit `applyTorChaining` DNS handling (FakeDNS answers locally; hostnames must reach Tor as domains via `domainStrategy=AsIs`). Do NOT ship.
- UDP/QUIC leak → tighten UDP-443 blocking before Tor.
- Bootstrap never completes on 4G → check `Socks5Proxy` egress reaches REALITY; verify the xray `tor-egress` inbound is listening.

Only after 1–7 are green does the UI toggle / entitlement / Phase-1 per-app split work begin.
