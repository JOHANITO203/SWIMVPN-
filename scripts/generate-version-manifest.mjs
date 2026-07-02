#!/usr/bin/env node
// Génère public/version.json — le manifest de l'auto-update sideload Android.
// Source de vérité = android/app/build.gradle (versionCode/versionName) + SHA-256 de l'APK
// réellement servi (public/downloads/swimvpn.apk). À lancer À CHAQUE RELEASE, après avoir
// posé le nouvel APK dans public/downloads/ et AVANT le commit/déploiement de la landing
// (design: docs/superpowers/specs/2026-06-14-auto-update-design.md — un manifest édité à la
// main dérive, comme la leçon du prerender).
//
// Usage :
//   node scripts/generate-version-manifest.mjs \
//     [--apk-file <path>] [--apk-url <url>] \
//     [--changelog-ru "…"] [--changelog-fr "…"] [--changelog-en "…"] [--min-supported <code>]
//
// --apk-file : APK à hasher (défaut public/downloads/swimvpn.apk). En CI = l'APK fraîchement buildé.
// --apk-url  : URL enregistrée dans le manifest (défaut app.swimvpn.pro ; en CI = l'asset Release GitHub,
//              tag-spécifique pour que le SHA-256 corresponde toujours à l'APK servi).
// minSupportedCode ne monte QUE volontairement (fix critique/sécurité) : par défaut il est
// conservé depuis le version.json existant (ou 1).

import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(import.meta.dirname, '..')
const GRADLE = resolve(ROOT, 'android/app/build.gradle')
const OUT = resolve(ROOT, 'public/version.json')

const args = process.argv.slice(2)
const argOf = (name) => {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : undefined
}

const APK = resolve(ROOT, argOf('--apk-file') ?? 'public/downloads/swimvpn.apk')
const APK_URL = argOf('--apk-url') ?? 'https://app.swimvpn.pro/downloads/swimvpn.apk'
if (!APK_URL.startsWith('https://')) {
  console.error(`generate-version-manifest: --apk-url doit être en https:// (reçu ${APK_URL}).`)
  process.exit(1)
}

const gradle = readFileSync(GRADLE, 'utf8')
const versionCode = Number(gradle.match(/^\s*versionCode\s+(\d+)\s*$/m)?.[1])
const versionName = gradle.match(/^\s*versionName\s+"([^"]+)"\s*$/m)?.[1]
if (!Number.isInteger(versionCode) || versionCode <= 0 || !versionName) {
  console.error('generate-version-manifest: versionCode/versionName introuvables dans build.gradle — abandon.')
  process.exit(1)
}

if (!existsSync(APK)) {
  console.error(`generate-version-manifest: APK introuvable (${APK}) — poser/builder l'APK d'abord.`)
  process.exit(1)
}
const sha256 = createHash('sha256').update(readFileSync(APK)).digest('hex')

const previous = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : {}
const minSupportedCode = Number(argOf('--min-supported') ?? previous.minSupportedCode ?? 1)
if (!Number.isInteger(minSupportedCode) || minSupportedCode <= 0 || minSupportedCode > versionCode) {
  console.error(`generate-version-manifest: minSupportedCode invalide (${minSupportedCode}) — doit être 1..${versionCode}.`)
  process.exit(1)
}

const changelog = {
  ru: argOf('--changelog-ru') ?? previous.changelog?.ru ?? '',
  fr: argOf('--changelog-fr') ?? previous.changelog?.fr ?? '',
  en: argOf('--changelog-en') ?? previous.changelog?.en ?? '',
}

const manifest = {
  latestVersionCode: versionCode,
  versionName,
  apkUrl: APK_URL,
  sha256,
  minSupportedCode,
  changelog,
}

writeFileSync(OUT, JSON.stringify(manifest, null, 2) + '\n')
console.log(`version.json: code ${versionCode} (${versionName}), minSupported ${minSupportedCode}`)
console.log(`apkUrl: ${APK_URL}`)
console.log(`sha256: ${sha256}`)
