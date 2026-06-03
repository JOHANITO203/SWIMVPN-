# SWIMVPN+ Android — Compatibilité de la stack avec les 64-bit (recherche + orientation)

> 2026-06-03. Question : la stack (Xray-core + hev-socks5-tunnel/tun2socks JNI) est-elle
> compatible avec les mobiles 64-bit toutes gammes ? Et quelle direction pour que les gens
> utilisent l'app tranquillement ?

## Faits sur NOTRE build (vérifiés dans `android/app/build.gradle`)
- **NDK r27** (`27.0.12077973`) + `minSdk 26` / `target/compileSdk 34`.
- **ABIs = `arm64-v8a` + `x86_64`** (l.123) → tous les vrais devices 64-bit + émulateurs.
  **Pas de `armeabi-v7a`** (Xray-core ne publie pas ce binaire) → pas de support 32-bit pur.
- **16 Ko page size** : `-Wl,-z,max-page-size=16384` passé au CMake (tun2socks JNI, l.133) **et**
  au ndk-build (hev-socks5-tunnel, l.95). Donc nos libs natives **visent déjà le 16 Ko**.
- **Xray** packagé en `libxray.so` dans le dossier natif (l.242) → exec depuis `nativeLibraryDir`
  = méthode correcte sous Android 10+ (W^X/SELinux). C'est pour ça que `useLegacyPackaging true`
  (l.169) — il faut extraire le binaire sur disque pour l'exécuter.
- **tun2socks** = JNI in-process (`libhev-socks5-tunnel.so`), mode préféré.

## Recherche — exigence 16 Ko (Google Play)
- Depuis **1ᵉʳ nov. 2025**, toute nouvelle app/MAJ ciblant Android 15+ **doit supporter le 16 Ko**
  sur les 64-bit. Apps Java/Kotlin pur = déjà OK ; **dès qu'il y a du NDK, il faut recompiler aligné**.
- **NDK r28+ aligne en 16 Ko par défaut.** Pour r27 ou moins, Google recommande **les DEUX** flags :
  `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`.
- AGP 8.5.1+ aligne automatiquement au packaging **seulement pour les libs non compressées**
  (`useLegacyPackaging false`). Nous = legacy packaging → on dépend **uniquement** des flags linker.

### ⚠️ Findings 16 Ko actionnables
1. **Il manque `-Wl,-z,common-page-size=16384`** (on n'a que `max-page-size`). À ajouter aux deux
   chaînes (CMake l.133 + ndk-build l.95), **ou** bumper le NDK en **r28+** (16 Ko par défaut).
2. **`libxray.so` est un exécutable Go** (téléchargé depuis XTLS), pas une lib qu'on compile → son
   alignement 16 Ko est **hors de notre contrôle et non vérifié**. Risque : (a) refus du contrôle
   16 Ko de Play, (b) crash sur device à pages 16 Ko. **À vérifier** avec le script d'alignement
   Android (`check_elf_alignment.sh`) ou `objdump -p libxray.so | grep LOAD` (doit être `2**14`).
3. `useLegacyPackaging true` est **OK pour le 16 Ko** (libs extraites sur un FS aligné) et **requis**
   pour l'exec du binaire Xray. (Note : `DECISIONS.md` mentionne encore `false` — doc à corriger.)

## Recherche — stabilité hev-socks5-tunnel / tun2socks
- hev-socks5-tunnel (heiher) = « lightweight, fast and **reliable** », utilisé par SocksTun (l'app de
  l'auteur) et de nombreux clients. **Aucun pattern de crash SIGSEGV largement rapporté** sur
  Xiaomi/Redmi arm64. → **faible évidence** que tun2socks-JNI soit la cause du crash mid-range.

## Constat épistémique (honnête)
- On **n'a pas** le log du crash (logcat/tombstone/Crashlytics) → la cause est **inconnue**, pas prouvée.
- Indice fort : « **après ma dernière mise à jour** elle bug » = **régression** introduite par une
  version, pas une incompat fondamentale de la stack. L'orbe 3D GL a été ajouté puis retiré → c'est
  l'**hypothèse n°1 à tester**.
- Le build est **bien configuré** pour le 64-bit/16 Ko (à 2 détails près ci-dessus) → l'incompat
  « stack vs 64-bit » est **peu probable** comme cause racine.

## Orientation (direction recommandée)
1. **Mesurer avant de coder.** Tester le build orbe-retiré sur le Redmi :
   - s'il **ne crashe plus** → c'était l'orbe (B3 invalidé), on avance.
   - s'il **crashe encore** → capturer le **tombstone** (`adb bugreport` / `/data/tombstones/`) :
     il nomme la lib fautive (hev-socks5-tunnel ? GL ? OOM ? libxray ?). **C'est la seule preuve.**
2. **Ne PAS construire l'isolation `:vpn-process` (B3) sur une hypothèse** — la faire uniquement si
   le tombstone pointe le natif tun2socks.
3. **Livrer les MAJ robustesse déjà validées** (indépendantes de la cause du crash) : B1/B2 (fuite
   faux-protégé), B4/B5 (perte de données), B6 (FD probes), B7 (GPU reduced-motion), cluster HIGH.
4. **Durcir le 16 Ko** : ajouter `common-page-size` (ou NDK r28) + vérifier l'alignement de `libxray.so`.
5. **Instrumenter le terrain** : Crashlytics **NDK** (pas seulement JVM) via le seam `CrashReporter`,
   pour capturer automatiquement les crashs natifs qu'on ne peut pas reproduire sur chaque OEM.

→ Résumé : la stack est globalement compatible 64-bit ; le « crash mid-range » est très
probablement une **régression applicative** (l'orbe) à confirmer par tombstone, pas une tare de la
stack. On sécurise par la mesure + les MAJ robustesse + 2 détails 16 Ko, pas par un gros refactor à l'aveugle.

## Sources
- [Support 16 KB page sizes — Android Developers](https://developer.android.com/guide/practices/page-sizes)
- [Prepare your apps for Google Play's 16 KB requirement (mai 2025)](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html)
- [Transition to 16 KB page sizes with Android Studio (juil. 2025)](https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html)
- [heiher/hev-socks5-tunnel (GitHub)](https://github.com/heiher/hev-socks5-tunnel)
</content>
