// Tokens du build « Brutaliste premium » (route #cine, landing racine).
// Canvas noir IMMUABLE ; texte blanc en paliers (white/80·70·60·50) ; accent = blanc
// (boutons pleins bg-white / bordés border-white — pas de variable d'accent runtime).

// Vidéo « journey » (tiny-planet, plan continu 24,8 s, 1080p). SERVIE EN MÊME-ORIGINE depuis
// public/assets → Content-Type `video/mp4` INLINE + range (ce que les <video> mobiles exigent).
// NB: GitHub Releases la servait en `application/octet-stream` + attachment → refusée par les
// <video> mobiles (iOS surtout) ; desktop sniffait et jouait quand même. Same-origin règle ça.
export const CINE_JOURNEY = '/assets/drone-journey.mp4';

// CTA téléchargement — Windows (release GitHub) + Android (APK servi par la landing,
// même URL que le JSON-LD downloadUrl de index.html).
export const CINE_DOWNLOAD_URL =
  'https://github.com/JOHANITO203/SWIMVPN-/releases/latest/download/SWIMVPN.exe';
export const CINE_ANDROID_URL = '/downloads/swimvpn.apk';

// Version affichée dans l'écran Téléchargement (aligner sur la release Android courante).
export const CINE_APP_VERSION = 'v1.0.12';
