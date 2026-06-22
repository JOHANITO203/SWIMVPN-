// Tokens du build « Éditorial sombre cinématique » (route #cine).
// Canvas noir IMMUABLE ; seul l'accent varie selon la palette choisie.
import type { CSSProperties } from 'react';

export type CinePalette = { accent: string; hover: string; glow: string };

// Palette « Blanc Neige » — accent #FFFFFF / hover #AEB8C2.
export const CINE_ACCENT: CinePalette = {
  accent: '#FFFFFF',
  hover: '#AEB8C2',
  glow: 'rgba(255,255,255,0.45)',
};

// Vidéo « journey » (tiny-planet, plan continu 24,8 s, 1080p). SERVIE EN MÊME-ORIGINE depuis
// public/assets → Content-Type `video/mp4` INLINE + range (ce que les <video> mobiles exigent).
// NB: GitHub Releases la servait en `application/octet-stream` + attachment → refusée par les
// <video> mobiles (iOS surtout) ; desktop sniffait et jouait quand même. Same-origin règle ça.
export const CINE_JOURNEY = '/assets/drone-journey.mp4';
// Timestamps (s) des 4 ancres = 4 positions de rotation, réparties sur les 24,8 s.
export const CINE_ANCHORS = [2.0, 9.0, 16.0, 23.0];

// CTA principal → téléchargement (release GitHub).
export const CINE_DOWNLOAD_URL =
  'https://github.com/JOHANITO203/SWIMVPN-/releases/latest/download/SWIMVPN.exe';

// Variables CSS de l'accent, consommées par .cine-cta (index.css).
export const cineAccentVars = {
  ['--cine-accent']: CINE_ACCENT.accent,
  ['--cine-accent-hover']: CINE_ACCENT.hover,
  ['--cine-accent-glow']: CINE_ACCENT.glow,
} as CSSProperties;
