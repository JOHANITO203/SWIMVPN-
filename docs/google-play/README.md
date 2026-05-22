# SWIMVPN Google Play Documentation Pack

Date: 2026-05-22

This folder contains draft material for the future Google Play submission of SWIMVPN.

## Source Links

- Google Play VpnService policy: https://support.google.com/googleplay/android-developer/answer/12564964
- Google Play data safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play privacy policy guidance: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play store listing assets guidance: https://support.google.com/googleplay/android-developer/answer/9866151

## Files

- `store-listing-ru.md`: Russian default store listing draft.
- `store-listing-fr.md`: French store listing draft.
- `data-safety-draft.md`: conservative data safety draft to validate before submission.
- `vpnservice-declaration-draft.md`: VpnService declaration draft.
- `release-assets-checklist.md`: assets and review readiness checklist.

## Product Positioning

SWIMVPN is positioned as an Android VPN app that supports:

- in-app purchase of ready-to-use VPN access,
- current pre-release trial access,
- free use with user-provided imported configs,
- import-oriented compatibility across the Xray/V2Ray ecosystem, including VLESS, VLESS Reality, VMess, Trojan, Shadowsocks, JSON Xray, and JSON V2Ray where supported by the runtime/parser,
- an Agent feature that provides live server-selection guidance and indices without taking irreversible decisions away from the user.

## Validation Required Before Play Submission

- Confirm the final package name, app title, screenshots, feature graphic, and privacy policy URL.
- Confirm the final user-visible payment wording for SwimPay and Crypto.
- Confirm final Data Safety answers against actual backend/Android telemetry and logs.
- Confirm VpnService declaration wording against the exact production VPN behavior.
- Confirm that trial claims match production availability.
