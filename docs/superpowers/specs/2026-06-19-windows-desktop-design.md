# SWIMVPN Windows (Compose Multiplatform Desktop) — design & plan

> Statut : **en développement** (branche `feat/windows-desktop`). Objectif utilisateur : une **app Windows .exe instable** avec **toutes les capacités** de l'app Android et les **mêmes caractéristiques UI**.

## Décision de stack (verrouillée par l'utilisateur)
**Compose Multiplatform Desktop** (JVM) — parité UI/logique maximale : on réutilise le langage Compose + les **design tokens** + (à terme) la logique pure-Kotlin de l'app Android.

## TASK AUDIT
- **App Android** = Jetpack Compose (Material3). Tokens dans `ui/theme/SwimDesignTokens.kt` (Compose **pur** : `androidx.compose.ui.graphics.Color`, `dp`, `compositionLocalOf`) + `ui/theme/Theme.kt`. Compose Multiplatform expose **le même namespace `androidx.compose.*`** → tokens/thème **portables quasi tels quels** ; seuls les bouts Android (`Context`, `R.drawable`, `WindowCompat`, polices via `R`) sont à adapter.
- **Moteur VPN Android** = `VpnService` + tun2socks JNI + `libxray.so`. **Non portable.** Sur Windows : `xray.exe` (Xray-core build Windows) en **process enfant** ; v1 instable = **mode proxy système** (pas de driver/admin), TUN complet (wintun + tun2socks) = phase ultérieure.
- **Backend partagé** (comptes, /store/plans, SwimPay) — réutilisable via HTTP (phase 2, comme Android).
- **Risque clé** : ne PAS convertir le module Android en KMP d'un coup (régression sur l'app livrée 1.0.11). → **module desktop autonome** qui **porte** (copie) tokens + composables ; extraction `commonMain` partagé = refactor de phase 2.
- **Toolchains présentes** : JDK (AS jbr), Gradle 8.11.1, Rust, Node. OK pour Compose Desktop (JVM).

## IMPLEMENTATION PLAN (incrémental, par milestones)
Module **autonome** `windows/` (Gradle JVM + `org.jetbrains.compose`), Android **intact**.

### M1 — exe qui tourne avec l'UI brand
- `windows/` : `settings.gradle.kts`, `build.gradle.kts` (kotlin jvm 2.0.21 + compose 1.7.x + compose-compiler plugin), wrapper réutilisant gradle-8.11.1 déjà téléchargé.
- Porter `SwimDesignTokens.kt` (copie, package `com.swimvpn.desktop.theme`) + `Theme.kt` desktop (schémas Material3 conservés, retrait des inserts Android).
- `Main.kt` : `application { Window(...) }` true-black, taille fixe ~420×860 (ratio mobile).
- `ui/HomeScreen.kt` : fond + **bouton power circulaire** (cœur violet + anneau + glow, fidèle au grammar hardware), libellé statut (Déconnecté/Connexion/Connecté), pill serveur, dock simplifié.
- **Vérif** : `./gradlew :run` → fenêtre rendue. Screenshot.

### M2 — moteur VPN (proxy système, instable)
- `vpn/XrayProcess.kt` : bundle `xray.exe` + geoip/geosite dans `resources/` ; génère le `config.json` (VLESS/REALITY, **même builder logique** que côté Android, porté) ; spawn + supervise le process ; SOCKS local (127.0.0.1:10808).
- `vpn/SystemProxy.kt` : pose/retire le proxy système Windows (registre `Internet Settings` + `InternetSetOption` refresh) pointant le SOCKS local.
- `vpn/VpnController.kt` : machine d'état (DISCONNECTED→CONNECTING→CONNECTED→ERROR), sonde de liveness (probe SOCKS) façon Android.
- `ui/ConfigImportScreen.kt` : coller une config VLESS (import), sélection.
- **Vérif device** (cette machine = Windows 11) : importer une config → Connect → trafic via le proxy (test navigateur) → Disconnect restaure le proxy.

### M3 — parité (phases suivantes, hors v1 instable)
TUN complet (wintun + hev tun2socks Windows) pour router TOUT le trafic ; agent adaptatif + profils camouflage (porter la logique pure-Kotlin `adaptive/`) ; auth backend + abonnement + SwimPay ; auto-update ; packaging signé (EV cert → SmartScreen) + winget/MSIX.

## Out of scope (v1 instable)
Driver TUN/wintun, signature EV, Microsoft Store, agent/camouflage complet, paiements — tout en M3+.

## Vérification
- M1 : `:run` rend l'UI ; pas de dépendance Android.
- M2 : connect/disconnect réel via proxy système, testé navigateur sur cette machine.
- Android **inchangé** (aucun fichier `android/` touché) → zéro régression sur 1.0.11.
