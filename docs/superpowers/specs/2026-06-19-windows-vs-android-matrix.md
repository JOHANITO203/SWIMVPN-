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
| Capture du trafic | `VpnService` + tun2socks → **tout le trafic** (TUN) | **TUN global (WinTUN + tun2socks) → tout le trafic** (`WintunTunnel`, mode par défaut) ; **proxy système** = repli sélectionnable | 🟰 (TUN implémenté) |
| tun2socks | hev-socks5-tunnel (compilé NDK) | tun2socks (xjasonlyu) + `wintun.dll` — équivalent fonctionnel, bridge TUN↔SOCKS | 🟰 |
| Routage | géré par l'OS (VpnService) | **split-default `0/1`+`128/1`** (override sans supprimer la route par défaut) + bypass `/32` du serveur via la vraie gateway + DNS via TUN ; **teardown restaure tout** | 🟰 |
| Élévation | service VPN (consent OS) | **admin requis** pour le TUN (détecté ; sinon avertit + repli proxy) ; auto-elevation de l'exe packagé = à affiner | 🟰 |
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
- **Garantie liens/configs = OK** : parsing + construction du config Xray = **même code source** qu'Android (copié au build, zéro drift) + même Xray-core. Tout lien/config accepté sur Android l'est sur Windows.
- **Transport = parité atteinte** : le **TUN global** (WinTUN + tun2socks) route désormais **tout le trafic** comme Android (mode par défaut, repli proxy sélectionnable). Requiert l'admin (détecté).
- **Différences restantes = features périphériques** : post-traitements runtime (override fp / DNS / FakeDNS de `TunnelRuntimeAdapter`), agent adaptatif + camouflage, backend (auth/abonnement/SwimPay), écrans secondaires, signature EV → roadmap **M3**.
