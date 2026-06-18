# SWIMVPN Windows — inventaire de parité (parseurs + features) Android → Windows

> Carte exhaustive et **honnête** de ce qui est reproduit sur Windows vs Android. Fondée sur le
> code réel. Légende : ✅ partagé/identique · 🟰 équivalent (réimplémenté fidèle) · 🟡 partiel ·
> 🔴 pas encore (M3, avec plan de repro).

## A. Parseurs & formats d'entrée (le cœur — « même niveau de parsing »)
Tous **compilés depuis les mêmes fichiers source** `com.swimvpn.app.config(.subscriptionparser)` (copiés au build par `syncEngine`, zéro drift).

| Format / entrée | Android | Windows | Détail |
|---|---|---|---|
| **VLESS** (URL + JSON) | `ConfigParserEngine` | ✅ | REALITY / TLS / none ; TCP/WS/gRPC/HTTP2 ; flow xtls-rprx-vision ; pbk/sid/fp/sni |
| **VMess** (URL base64 + JSON) | ✅ | ✅ | port String **ou** num (corrigé) ; ws/grpc/tcp/h2/kcp/quic |
| **Trojan** (URL + JSON) | ✅ | ✅ | tls/reality ; transports |
| **Shadowsocks** (SIP002 + legacy + JSON) | ✅ | ✅ | userinfo base64 SIP002 (corrigé) ; plugin |
| **Proxy SOCKS5/HTTP** (BYO) | ✅ | ✅ (parsing) | `parseProxyEndpoint` partagé |
| **Abonnement : URL `https://`** | fetch `SubscriptionFetcher` (OkHttp) | 🟰 | fetch desktop `HttpURLConnection` (async), puis **même décodeur** |
| **Abonnement : payload** | `SubscriptionPayloadDecoder` | ✅ | base64 multi-passe · URL-encodé · Happ · **SIP008 JSON** · **Clash YAML** · **sing-box** · listes |
| **Métadonnées abonnement** (trafic/expiry) | `SubscriptionMetadataParser` | 🟡 | parsées par le moteur partagé mais **pas encore affichées** (UI quota/expiry = M3) |
| hysteria2 / tuic / wireguard | « unsupported modern config » | 🟰 | **même limite** qu'Android (non parsés des deux côtés) |

**Vérifié e2e** sur la vraie URL d'abonnement (wb.routerwb.ru) : 11 liens VLESS → 11 parsés OK.

## B. Construction du config Xray + connexion (runtime)
| Élément | Android | Windows | Détail |
|---|---|---|---|
| Build outbound (proto+transport+sécurité) | `XrayOutboundBuilder`/`XrayStreamSettingsBuilder` | ✅ partagé | même JSON outbound |
| Binaire Xray | libxray.so | 🟰 xray.exe **v26.3.27 (même version)** |
| Inbounds locaux (SOCKS/HTTP) | via TunnelRuntimeAdapter | 🟰 `EngineConfig` |
| **Capture du trafic** | VpnService+tun2socks (tout) | 🟰 **TUN global (WinTUN+tun2socks)** + repli proxy système |
| Preuve de trafic au démarrage | `awaitStartupHealthProof` | 🟰 `VpnController.probeTraffic` |
| **Post-traitement runtime** (override fingerprint uTLS, **pré-résolution DNS du serveur par IP**, FakeDNS) | `TunnelRuntimeAdapter` | 🔴 **M3** — *peut affecter REALITY/DPI* ; à porter (logique Gson pure) |
| Reconnexion/backoff, liveness sentinelle | `TunnelHealthSentinel`+agent | 🔴 M3 |

## C. UI / écrans
| Écran | Android | Windows | Détail |
|---|---|---|---|
| Navigation (dock metaball) | `SwimMetaballDock` | 🟰 `SwimDock` (4 onglets, même grammaire) |
| Accueil (statut, bouton power, pill serveur) | ✅ | 🟰 |
| Serveurs — **importés** (liste/select/suppr/import) | ✅ | ✅ (import URL/blob/lien corrigé) |
| Serveurs — **premium/backend** | ✅ | 🔴 M3 (dépend de l'auth backend) |
| Abonnement (cartes plans) | backend `/plans` | 🟡 catalogue statique → checkout web (in-app + plans live = M3) |
| Compte (identité, plan, logout) | backend | 🟡 infos basiques (identité backend = M3) |
| Réglages techniques | ✅ | 🟡 TUN/proxy ✅ ; langue/thème/auto-connect/kill-switch/**agent IA**/**geo-bypass**/**camouflage** = M3 |
| Proxy BYO (sonde+pool) | `ProxyScreen` | 🔴 M3 (le parsing proxy existe ; l'écran sonde = M3) |
| Onboarding / Support | ✅ | Onboarding 🔴 (desktop saute) ; Support 🟰 (lien web) |
| Persistance | DataStore chiffré | 🟰 `state.json` (%LOCALAPPDATA%) — **pas chiffré** (M3) |

## D. Plateforme / backend / sécurité
| Élément | Android | Windows | Détail |
|---|---|---|---|
| Backend (auth, bootstrap, /store/plans, SwimPay, trial/freemium) | ✅ | 🔴 **M3** — réutilisable via HTTP (gros morceau) |
| Agent adaptatif + profils camouflage | `adaptive/*`, `CamouflageProfile` | 🔴 M3 (logique pure portable ; persistance DataStore à shimer) |
| Geo-bypass (routing split) | `XrayRoutingBuilder` (partagé) + UI | 🔴 M3 (builder dispo ; UI/persistance à câbler) |
| Auto-connect / boot / kill-switch | ✅ | 🔴 M3 |
| Signature | keystore APK | 🔴 non signé (EV cert → SmartScreen = M3) ; admin TUN détecté |
| Icône | ic_launcher | ✅ (fenêtre + .ico) |

## Synthèse honnête
- **Parseurs & formats d'entrée = PARITÉ COMPLÈTE** ✅ (même source ; URL d'abonnement + tous formats + 4 bugs corrigés au passage qui bénéficient à Android).
- **Connexion** : transport (TUN) + build outbound = parité ; **manque le post-traitement runtime** (`TunnelRuntimeAdapter` : DNS-par-IP + fingerprint + FakeDNS) qui peut compter pour REALITY/DPI → **priorité M3**.
- **Reste M3** (volume) : backend (auth/plans/paiement), agent/camouflage, écrans secondaires, geo-bypass UI, signature, chiffrement persistance.

## Prochaine repro prioritaire (hors backend)
1. **`TunnelRuntimeAdapter` post-traitement** porté côté desktop (DNS pré-résolu par IP + override fingerprint + FakeDNS) — fiabilise la connexion REALITY comme sur Android.
2. **Agent adaptatif + camouflage** (`adaptive/*` pur-Kotlin) + persistance shimée.
3. **Métadonnées abonnement** (trafic/expiry) affichées via `SubscriptionParser`.
