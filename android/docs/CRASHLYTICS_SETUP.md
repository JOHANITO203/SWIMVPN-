# Activer Firebase Crashlytics (+ NDK) — SWIMVPN+

Le terrain est déjà préparé : tout le code passe par [`CrashReporter`](../app/src/main/java/com/swimvpn/app/diagnostics/CrashReporter.kt)
(aujourd'hui en logcat seul, **zéro dépendance**, donc zéro risque build). Les échecs VPN
(`startup`, `tun2socks_bridge`) y sont déjà routés. Activer Crashlytics = les étapes mécaniques
ci-dessous, **rien d'autre à câbler dans le code applicatif**.

> Pourquoi Crashlytics-**NDK** : les crashs OEM (Xiaomi/Redmi, certains Samsung milieu de gamme)
> sont très probablement des **signaux natifs** (SIGSEGV/SIGABRT) dans le tun2socks JNI in-process,
> qu'un `try/catch` ne peut pas capturer. Le NDK reporter donne la **stack native + le modèle de
> device + l'ABI** sans accès physique au téléphone du testeur.

## 1. Projet Firebase
1. Crée un projet Firebase, ajoute une app Android avec le package **`com.swimvpn.app`**.
2. Télécharge `google-services.json` et place-le dans **`android/app/google-services.json`**.
   (À ajouter au `.gitignore` si le repo est public — il contient des identifiants projet.)

## 2. Gradle — racine `android/build.gradle` (buildscript)
Ajoute les 2 classpaths :
```groovy
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:8.9.1'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0'
        classpath 'com.google.gms:google-services:4.4.2'                  // + AJOUT
        classpath 'com.google.firebase:firebase-crashlytics-gradle:3.0.2' // + AJOUT
    }
}
```

## 3. Gradle — `android/app/build.gradle`
En haut, applique les plugins **de façon conditionnelle** (le build reste fonctionnel même sans
le JSON, ex. CI sans secret) :
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
// Crashlytics ne s'active que si le google-services.json est présent.
if (file('google-services.json').exists()) {
    apply plugin: 'com.google.gms.google-services'
    apply plugin: 'com.google.firebase.crashlytics'
}
```
Dans `android { buildTypes { release { ... } } }`, active l'upload des symboles natifs :
```groovy
release {
    // ... existant ...
    firebaseCrashlytics {
        nativeSymbolUploadEnabled true
        // unstrippedNativeLibsDir résout les libs natives (libhev-socks5-tunnel, libswimvpn_tun2socks_jni)
    }
}
```
Dans `dependencies` :
```groovy
implementation platform('com.google.firebase:firebase-bom:33.7.0')
implementation 'com.google.firebase:firebase-crashlytics-ktx'
implementation 'com.google.firebase:firebase-crashlytics-ndk'
```

## 4. Code — activer le seam
Dans [`CrashReporter.kt`](../app/src/main/java/com/swimvpn/app/diagnostics/CrashReporter.kt) :
- ajoute l'import `import com.google.firebase.crashlytics.FirebaseCrashlytics`
- dé-commente les lignes `// CRASHLYTICS:` dans `log`, `setKey`, `recordException`, `recordVpnFailure`.

Aucune autre modification : les call sites (échecs VPN) sont déjà en place.

## 5. Vérifier
- `./gradlew :app:assembleRelease` doit builder (upload symboles si configuré).
- Provoquer un crash test → vérifier l'arrivée dans la console Firebase Crashlytics.
- Les non-fatals `vpn_failure` (stage/cause) doivent apparaître avec le device + l'ABI.

## Note ABI (32-bit)
Xray-core ne publie **pas** de binaire Android `armeabi-v7a` (uniquement `arm64-v8a` + `amd64`).
Les vrais devices 32-bit ne sont donc pas supportés (l'ABI n'est pas la cause des crashs sur les
Redmi arm64). Crashlytics confirmera la répartition réelle des ABIs des testeurs ; n'ajouter
`armeabi-v7a` que si des devices 32-bit apparaissent **et** seulement avec un binaire Xray armv7
compilé depuis les sources (chantier build séparé).
