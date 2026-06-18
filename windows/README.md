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
# proxy requis si l'accès GitHub passe par un proxy (téléchargement de xray) :
./gradlew run -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10809
# packager un exe :
./gradlew createDistributable      # dossier app
./gradlew packageReleaseExe        # installeur .exe
```

## v1 instable — limites assumées
- Mode **proxy système** (apps respectant WinINet : navigateurs, etc.) — pas encore TUN global.
- Pas de signature EV (SmartScreen avertira « éditeur inconnu »).
- Import config VLESS manuel ; agent/camouflage/abonnement = phases suivantes (parité M3).
