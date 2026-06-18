# SWIMVPN — Windows (Compose Multiplatform Desktop)

> **Statut : v1 instable.** App desktop `.exe` réutilisant le langage UI de l'app Android
> (design tokens portés tels quels) et le moteur Xray. Voir le plan :
> [`docs/superpowers/specs/2026-06-19-windows-desktop-design.md`](../docs/superpowers/specs/2026-06-19-windows-desktop-design.md).

## Architecture
- **Module autonome** (Gradle JVM + `org.jetbrains.compose`) — l'app Android (`../android`) n'est **pas** touchée. Partage de source via `commonMain` = refactor de phase 2.
- **UI** : `theme/SwimDesignTokens.kt` (porté verbatim depuis Android) + `theme/Theme.kt` + `ui/` (mêmes caractéristiques : true black, accent violet, bouton power).
- **Moteur** (`vpn/`) : `xray.exe` (téléchargé au build par la task `fetchXray`, v26.3.27 comme Android) lancé en process enfant → SOCKS/HTTP local → **proxy système Windows** (mode v1 instable, sans driver/admin). TUN complet (wintun) = M3.

## Lancer / builder
```bash
cd windows
# proxy requis si l'accès GitHub passe par un proxy (téléchargement de xray/tun2socks/wintun) :
./gradlew run -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10809
```

## Construire l'installateur (.exe)
`packageExe` utilise **jpackage** (qui n'est PAS dans le jbr d'Android Studio) + **WiX 3.x**.
Prérequis (one-time) :
- **JDK complet 17+** avec `jpackage` (ex. Temurin 21 — le jbr d'AS ne l'a pas).
- **WiX Toolset 3.14** (`candle.exe`/`light.exe`) sur le PATH.

```bash
export JAVA_HOME=/c/Users/<you>/jdk21/jdk-21.0.5+11      # JDK complet (jpackage + jmods)
export PATH="/c/Users/<you>/wix314:$JAVA_HOME/bin:$PATH"  # WiX sur le PATH
./gradlew packageExe        # → build/compose/binaries/main/exe/SWIMVPN-1.0.0.exe (installeur per-user)
# alternatives :
./gradlew createDistributable   # dossier app portable (sans installeur, sans WiX)
./gradlew packageMsi            # installeur .msi
```
L'installateur embarque un JRE + `xray.exe` + `tun2socks.exe` + `wintun.dll` + geoip/geosite + l'icône.
Installation **per-user** (`%LOCALAPPDATA%\SWIMVPN`, sans admin). **Lancer en administrateur** pour le mode TUN (sinon repli proxy).

## v1 instable — limites assumées
- Mode **proxy système** (apps respectant WinINet : navigateurs, etc.) — pas encore TUN global.
- Pas de signature EV (SmartScreen avertira « éditeur inconnu »).
- Import config VLESS manuel ; agent/camouflage/abonnement = phases suivantes (parité M3).
