# SWIMVPN — matrice des différences Android ↔ Windows (UI → moteur)

> Référence de parité. **Légende** : ✅ identique/partagé · 🟰 équivalent (réimplémenté fidèle) · 🟡 partiel (v1) · 🔴 absent en v1 (roadmap M3) · ⛔ non applicable.

## Moteur (le cœur — garantie « mêmes liens & configs »)
| Élément | Android | Windows v1 | Parité |
|---|---|---|---|
| **Parsing des liens** (`ConfigParserEngine`) | source canonique | **MÊME source** compilée (copiée au build par `syncEngine`) | ✅ partagé |
| Extraction multi-liens / blobs (`VpnConfigLinkExtractor`) | ✓ | **MÊME source** | ✅ partagé |
| Modèles protocoles (`ProtocolModels`, `SwimVpnProfile`) | ✓ | **MÊME source** | ✅ partagé |
| Normalisation (`ConfigNormalizationEngine`) | ✓ | **MÊME source** | ✅ partagé |
| Builder outbound Xray (`XrayOutboundBuilder`) | ✓ | **MÊME source** | ✅ partagé |
| Stream/transport/sécurité (`XrayStreamSettingsBuilder`, `XrayRoutingBuilder`) | ✓ | **MÊME source** | ✅ partagé |
| Subscription decode (`subscriptionparser/*`) | ✓ | **MÊME source** | ✅ partagé |
| **Protocoles supportés** | VLESS · VMess · Trojan · Shadowsocks (URL + JSON) | **identiques** (même parseur) | ✅ |
| **Transports/sécurité** | TCP/WS/gRPC · REALITY/TLS/none | **identiques** (même builder) | ✅ |
| Binaire Xray | `libxray.so` (gomobile) | `xray.exe` natif | 🟰 **même version v26.3.27, même format config** |
| JSON lib | Gson | Gson (même) | ✅ |
| Deps Android du moteur | `android.util.Log`/`Base64` | **shimées** (JVM) sous `src/main/kotlin/android/util` | 🟰 |
| Assemblage runtime (`TunnelRuntimeAdapter` : override fp, pré-résolution DNS, FakeDNS) | ✓ | outbound partagé + inbounds locaux ; post-traitements Android **pas encore** portés | 🟡 (M3) |
| Agent adaptatif + profils camouflage (`adaptive/`, `CamouflageProfile`) | ✓ | 🔴 (M3 — logique pure portable, persistance DataStore à shimer) |
| Subscription **fetch** réseau (`SubscriptionFetcher`/OkHttp) | ✓ | 🔴 (M3 — import manuel pour l'instant) |

## Transport du tunnel (la vraie différence d'OS)
| Élément | Android | Windows v1 | Parité |
|---|---|---|---|
| Capture du trafic | `VpnService` + tun2socks → **tout le trafic** (TUN) | **proxy système WinINet** → apps proxy-aware | 🟡 → 🔴 TUN global = M3 (wintun) |
| Élévation | service VPN (consent OS) | aucune (v1) ; admin requis pour wintun (M3) | 🟰 |
| Sonde de trafic au démarrage | preuve SOCKS (`awaitStartupHealthProof`) | **même approche** (`VpnController.probeTraffic`, 5×2.8s) | 🟰 |

## UI
| Élément | Android | Windows v1 | Parité |
|---|---|---|---|
| **Icône appli** | `ic_launcher` | **MÊME** (`mipmap-xxxhdpi/ic_launcher` → fenêtre + `.ico` exe) | ✅ |
| Framework | Jetpack Compose | Compose Multiplatform Desktop | 🟰 (même paradigme) |
| Design tokens | `SwimDesignTokens.kt` | **porté verbatim** (Compose pur, même namespace) | ✅ (copie ; `commonMain` partagé = M3) |
| Thème (Material3, true black, accent #8A6AF1) | ✓ | **même** | ✅ |
| Bouton power / grammaire hardware | Canvas multi-couches complet | version fidèle (bol/cœur/anneau/glow) | 🟰 |
| Écrans | Home · Servers · Subscription · Profile · Proxy · Technical · Onboarding · Support | **Home + import** seulement | 🟡 (autres = M3) |

## Plateforme / distribution
| Élément | Android | Windows v1 | Parité |
|---|---|---|---|
| Backend (auth, /store/plans, SwimPay) | ✓ | 🔴 (M3 — réutilisable via HTTP) |
| Persistance | DataStore + chiffré | 🔴 (M3) |
| Signature | keystore (APK) | **non signé** (EV cert → SmartScreen = M3) |
| Packaging | APK/AAB | `.exe`/MSI (jpackage Compose) | 🟰 |
| Auto-update | (landing) | 🔴 (M3) |

## Synthèse
- **Garantie liens/configs = OK** : le parsing et la construction du config Xray sont le **même code source** que l'app Android (copié au build, zéro drift) + même Xray-core. Tout lien/config accepté sur Android l'est sur Windows.
- **Différences restantes = surtout le transport** (proxy système vs TUN global) et les **features périphériques** (agent/camouflage, backend, signature) → roadmap **M3**.
