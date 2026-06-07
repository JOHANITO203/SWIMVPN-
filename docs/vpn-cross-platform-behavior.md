# SWIMVPN — Comportement du VPN par plateforme (matrice de risques + tests)

> Référence d'ingénierie pour anticiper les pannes de connectivité selon l'OS, l'OEM et le réseau.
> Né du diagnostic d'un « connecté sans Internet en 4G » sur Redmi Note 12 (Android 15 / HyperOS),
> root-causé en comparant à un client tiers fonctionnel (vpsus) qui importe les mêmes serveurs.
>
> **Légende confiance :** 🟢 documenté/observé · 🟡 plausible, à tester · 🔴 hypothèse à valider.

## Socle technique (ce qui conditionne tout)

- Outbound **VLESS + REALITY en IPv4** (TCP) — le serveur est joint en IPv4 (host/IP).
- Cœur **Xray** (Go, `libgojni`) + **`hev-socks5-tunnel`** (tun → SOCKS 127.0.0.1) + tun2socks.
- **FakeDNS + `queryStrategy: UseIPv4`** → les apps ne reçoivent que des enregistrements **A (IPv4)**.
- tun : **IPv4-only** (`10.0.0.2/24`), **`::/0 unreachable`** (depuis v1.0.9), MTU 1280.
- Sous-jacent : suivi du **réseau par défaut** (`registerDefaultNetworkCallback`, depuis v1.0.9).
- Agent adaptatif : apprend la **fiabilité** par réseau×profil, bascule serveur/profil.
- `minSdk 26` (Android 8) · `targetSdk 34` (Android 14). VPN **non-bypassable**, full-tunnel.

## Corrigé en v1.0.9 (vérifié device)

| Bug | Cause | Correctif | Preuve |
|---|---|---|---|
| IPv6 black-hole (4G) → « connecté sans Internet » | tun captait `::/0` mais outbound IPv4 | pas d'IPv6 sur le tun → l'OS pose `::/0 unreachable` → repli IPv4 | tun IPv4-only, `unreachable default`, 4G charge 🟢 |
| Flapping sous-jacent (multi-réseau) → pages qui calent même en WiFi | `registerNetworkCallback` (tous réseaux) + `setUnderlyingNetworks` à chaque callback | `registerDefaultNetworkCallback` (réseau par défaut seul) | `UnderlyingNetworks:[1]`, 0 bascule/25 s, 5/5 TCP 🟢 |

## Android — par version

| Version | Point d'attention | Risque | Conf. |
|---|---|---|---|
| 8–9 | socle VpnService | aucun bloquant | 🟢 |
| 10 | Private DNS (DoT) automatique | interplay avec FakeDNS si l'utilisateur force un DoT par hostname | 🟡 |
| 11 | veille/scoped storage | rien de bloquant | 🟢 |
| 12 | restrictions FGS (démarrage en arrière-plan) | OK si lancé sur action user (observé *FGS Allowed*) | 🟢 |
| 13 | permission notification runtime | notif muette possible, service tourne quand même | 🟢 |
| 14 (target) | `foregroundServiceType` obligatoire | OK si déclaré au manifest | 🟢 |
| 15–16 | durcissements réseau/FGS | observé OK (Redmi 15, Samsung 16) | 🟢 |

## Android — par OEM (le vrai terrain)

| OEM | Comportement | Risque | Mitigation |
|---|---|---|---|
| Pixel / stock, Samsung One UI | réseau par défaut stable | faible (fluide) | — |
| Xiaomi MIUI / HyperOS | batterie agressive + multi-réseau | service tué en fond + flapping | Fix #2 ✅ + **demander d'ignorer l'optim. batterie** + autostart |
| Oppo/Realme ColorOS, Vivo FuntouchOS | kill très agressif en fond | tunnel coupé écran éteint | whitelist batterie + FGS sticky |
| Huawei EMUI/HarmonyOS | pas de GMS, kill agressif | pas de Play + fond tué | APK direct (déjà) + whitelist batterie |

## Réseau — les mines restantes

| Condition | Risque | Conf. | Mitigation / statut |
|---|---|---|---|
| WiFi IPv4-only | aucun | 🟢 | — |
| 4G/5G dual-stack (IPv6 présent) | **réglé** (`::/0 unreachable`) | 🟢 | fait v1.0.9 |
| **Carrier IPv6-only + NAT64/DNS64** | outbound REALITY IPv4 a besoin de **464XLAT/CLAT** pour sortir ; sans CLAT → échec total | 🔴 | **durcissement n°1** : détecter NAT64, s'appuyer sur le CLAT device (présent sur la plupart), fallback ; tester sur un réseau IPv6-only |
| Dual-SIM / WiFi+data simultanés | flapping | 🟢 | Fix #2 ✅ |
| Portail captif (hôtel/aéroport) | connect avant login | 🟢 | refus déjà sur réseau non validé |
| Private DNS (DoT) activé par l'user | conflit possible avec FakeDNS | 🟡 | à tester ; éventuellement coexister/ignorer |
| MTU cellulaire bas / PMTU | fragmentation | 🟢 | MTU 1280 (sûr) ; 1500 à évaluer |

## iOS (port futur — `feat/ios-port`) : à concevoir différemment

- **NEPacketTunnelProvider** : plafond mémoire d'extension serré → le cœur Xray Go peut être trop lourd → **benchmarker** (risque réel). 🔴
- **App Store EXIGE le fonctionnement en IPv6-only (NAT64)** → notre design « IPv6 unreachable + outbound IPv4 » **ne passe pas tel quel** → revoir la gestion IPv6 côté extension. 🟢 (règle Apple)
- Pas de `setUnderlyingNetworks`/`protect()` identiques → handover via **NWPathMonitor**. 🟢
- Pas de `hev-socks5-tunnel`/tun2socks identiques → bridge paquets→SOCKS à réimplémenter (sing-box/tun2socks iOS). 🟢
- Avantage : iOS tue **beaucoup moins** l'extension que les OEM Android. 🟢

## Backlog de durcissement (priorisé)

1. **NAT64 / IPv6-only** (🔴, n°1) — détection + dépendance CLAT + fallback ; le seul cas où v1.0.9 peut encore échouer.
2. **Optimisation batterie** (🟢, rapide) — prompt « ignorer l'optimisation » sur MIUI/ColorOS/EMUI ; évite les coupures écran éteint.
3. **Private DNS (DoT)** (🟡) — valider/garantir la coexistence avec FakeDNS.
4. **MTU 1500** (🟡) — évaluer le gain perf sans casser le cellulaire.

## Matrice de test (à exécuter avant chaque release majeure)

Appareils × réseaux :

| | WiFi v4-only | Dual-stack 4G/5G | IPv6-only/NAT64 | Dual-SIM | Captif |
|---|---|---|---|---|---|
| Pixel/stock | | | | | |
| Samsung One UI | ✅(16) | | | | |
| Xiaomi HyperOS | ✅(15) | ✅(15) | ❌ à tester | ✅(15) | |
| Oppo/Vivo | | | | | |

Critères de succès par cellule : connecte < 3 s · Chrome charge · pas de flapping (0 bascule/30 s) · tient écran éteint 5 min.
