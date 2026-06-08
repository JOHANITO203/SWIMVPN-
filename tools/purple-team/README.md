# tools/purple-team

Dev/CI purple-team tooling for SWIMVPN. **Not app code, not run through the VPN tunnel.** Each tool
measures something concrete and refuses to assert what it cannot measure (no "stealth score" theater).

## reality-probe-check.mjs

Verifies that the VLESS+REALITY servers in a subscription are **probe-resistant**: an unauthenticated
client (no REALITY handshake) connecting with `servername = donor SNI` should get the donor's **genuine
CA-issued certificate** back, because REALITY transparently relays unauthenticated connections to its
real donor site. That is exactly what defeats active probing (the attack that killed Shadowsocks/Trojan).
A server that returns a self-signed/mismatched cert, or resets, is exposing itself.

```sh
node tools/purple-team/reality-probe-check.mjs <subscription-url | file | -> [--verbose] [--json]
# fetch the sub yourself (proxy-aware) and pipe it in to keep the probe on a direct path:
curl -s -A v2rayNG/1.9.5 "<sub-url>" | node tools/purple-team/reality-probe-check.mjs -
```

- Output masks host/SNI by default (`--verbose` reveals them — LOCAL only).
- Uses **none** of the REALITY secrets (pbk/sid/uuid); only host:port:sni.
- **Honest limit:** tests the SERVER's relay behaviour (location-independent). It does NOT prove evasion
  of a specific censor's DPI — that needs an in-country vantage point.

Verdicts: `RESISTANT` (genuine donor cert) · `SUSPICIOUS` (self-signed / SAN mismatch / expired / reset)
· `UNREACHABLE` (couldn't connect from this vantage — inconclusive, not a server verdict).

Tests (pure logic, no network): `node --test` from this directory.
