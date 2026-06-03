# SWIMVPN+ — Plan de migration iOS & PC (Desktop)

> Rapport d'architecture (2026-06-03). État actuel : client **Android (Kotlin/Compose)** +
> moteur **Xray-core** (binaire packagé `libxray.so` par ABI, exécuté en process) + **tun2socks**
> (hev-socks5-tunnel, JNI) pour FULL_TUNNEL + backend **NestJS** (`api.swimvpn.pro`) + pipeline de
> config (VLESS/Reality/VMess/Trojan/Shadowsocks/subscriptions) + agent adaptatif déterministe.

## Réutilisable tel quel
- **Backend NestJS** (`api.swimvpn.pro`) : 100% partagé — iOS/PC tapent la même API (bootstrap,
  plans, checkout, profil, servers). Zéro changement backend.
- **Moteur VPN = Xray-core (Go)** : tourne partout, seul l'**emballage** change par plateforme.
- **Logique métier** : parser de config, agent adaptatif déterministe, client API, entitlement —
  code pur, partageable.

## Stratégie : noyau partagé + intégration VPN par plateforme
- **Kotlin Multiplatform (KMP)** pour extraire le commun (parser + agent adaptatif + client API +
  entitlement) → partagé Android/iOS/Desktop.
- **UI** : Compose Multiplatform (Android + Desktop, iOS désormais possible) **ou** SwiftUI natif iOS.
- L'**intégration tunnel** reste spécifique par OS, mais Xray-core est le tronc commun.

## iOS
| Aspect | Approche |
|---|---|
| Tunnel | `NEPacketTunnelProvider` (Network Extension) — **entitlement Apple "Packet Tunnel Provider"** (compte dev payant + review App Store) |
| Moteur | Xray-core via **gomobile → xcframework**, embarqué dans l'extension (PAS d'exécutable lancé — interdit iOS, contrairement à Android) |
| tun↔proxy | l'extension reçoit les paquets IP → pipe vers Xray (tun inbound) ou tun2socks gomobile |
| UI | SwiftUI (ou Compose MP) ; réutilise la logique KMP |
| Contraintes | budget mémoire de l'extension (~15-50 Mo → Xray lean) ; review App Store ; **pas de souci OEM-killer** |

## PC (Windows / macOS / Linux)
| Aspect | Approche |
|---|---|
| Shell/UI | **Compose Multiplatform Desktop** (max de réutilisation Android) ; alt : Tauri/Electron/Flutter |
| Moteur | binaire Xray-core natif + **TUN système** : Windows = **Wintun** ; macOS = **utun** ; Linux = `tun` ; + tun2socks |
| Privilèges | TUN = admin/root → Windows service/élévation ; **macOS helper privilégié + System Extension + notarisation** (le + dur) ; Linux `setcap`/root |
| Distribution | Windows MSI/NSIS · macOS .dmg notarisé · Linux AppImage/deb |

## Phasage
1. **Phase 0 — KMP** : extraire parser + agent adaptatif + client API en module partagé.
2. **Phase 1 — iOS** : NEPacketTunnelProvider + Xray gomobile + UI. (Effort moyen-élevé.)
3. **Phase 2 — Desktop** : shell Compose MP + Xray binaire + TUN par OS + signing/notarisation. (Effort élevé.)

## Effort / risque
- Backend + logique KMP = le levier (faible risque).
- iOS Network Extension + entitlement + gomobile = moyen.
- Desktop TUN privilégié + notarisation macOS = le plus coûteux.
- Le parser de config et l'agent adaptatif se réutilisent sur les 3 → gros gain.
</content>
