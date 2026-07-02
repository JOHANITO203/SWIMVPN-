# Release Android (sideload) — runbook

La CI (`.github/workflows/android-release.yml`) fait tout le rituel : build APK signé →
Release GitHub (asset) → `public/version.json` (apkUrl tag-spécifique, SHA-256 apparié) commité
sur `main` → Dokploy redéploie la landing. L'auto-update in-app distribue ensuite aux utilisateurs.

## Couper une release

1. Bumpe la version dans `android/app/build.gradle` :
   ```
   versionCode 15        // +1, monotone
   versionName "1.0.14"
   ```
2. Commit sur `main` (ex. `release(android): 1.0.14`).
3. Tag **annoté** `android-v<versionName>` — le message du tag devient le changelog du manifest :
   ```
   git tag -a android-v1.0.14 -m "Serveurs plus rapides, correctifs de connexion."
   git push origin android-v1.0.14
   ```
   ⚠️ Le tag DOIT correspondre à `versionName` (la CI échoue sinon). Préfixe `android-v` obligatoire
   (les tags Windows `v*` du même repo ne déclenchent pas ce workflow).

La CI produit la Release, l'APK, `version.json`, et pousse le commit `version.json` sur `main`.
Rien d'autre à faire.

## Secrets GitHub requis (Settings → Secrets and variables → Actions)

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | le keystore encodé : `base64 -w0 <keystore.jks>` (une seule ligne) |
| `SIGNING_STORE_PASSWORD` | mot de passe du store |
| `SIGNING_KEY_ALIAS` | alias de la clé (le keystore SWIMVPN utilise `key0`) |
| `SIGNING_KEY_PASSWORD` | mot de passe de la clé (si vide, le store password est réutilisé) |

Le cert de prod doit produire l'empreinte SHA-256 `c16bb1f1…2268` (sinon les APK signés par une
autre clé ne pourront PAS mettre à jour les installs existantes — Android rejette).

Pas de PAT à créer : le `GITHUB_TOKEN` intégré crée la Release et pousse `version.json` (permission
`contents: write` déclarée dans le workflow).

## Cas particuliers

- **Fix critique / forcer la mise à jour** : relève `minSupportedCode` dans `public/version.json` sur
  `main` (la CI le préserve tel quel aux releases suivantes). Les installs sous ce code voient l'écran
  bloquant « mise à jour requise ».
- **Changelog localisé** : le message de tag sert les 3 langues à l'identique. Pour des notes par
  langue, édite `public/version.json` `changelog` sur `main` après la release (préservé ensuite).
- **Flotte ≤ 1.0.12** : sans le mécanisme d'auto-update → un dernier sideload manuel de la 1.0.13,
  ensuite tout est automatique.
- **Landing** : le bouton Android lit `/version.json` au runtime → suit automatiquement chaque release,
  aucune retouche de la landing nécessaire.
