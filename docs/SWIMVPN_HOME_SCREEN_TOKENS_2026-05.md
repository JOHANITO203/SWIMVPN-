# SWIMVPN Home Screen Tokens - 2026-05

Document source pour le premier ecran Home / Connected et ses composants.

Ce fichier complete `docs/SWIMVPN_DESIGN_DNA_2026-05.md`. Il ne remplace pas
l'ADN global: il extrait les tokens specifiques du mock Home et du dock
metaball fournis pour guider l'implementation Android.

## Sources visuelles

- Mock Home connected: `Gemini_Generated_Image_fqx77fqx77fqx77f (1).png`
- Mock dock metaball: `ChatGPT Image 20 mai 2026, 01_52_32.png`

## Intention d'ecran

Le Home doit donner l'impression d'un objet materiel premium plutot que d'une
page Android classique. L'utilisateur voit immediatement trois signaux:

- le VPN est connecte;
- la connexion est protegee;
- le noeud courant est selectionnable sans surcharge technique.

L'ecran ne doit pas devenir un dashboard. Il reste calme, tactile, sombre,
luxueux, compact et vivant.

## Anatomie de l'ecran Home

1. Fond OLED noir avec bloom violet froid cote droit.
2. Bouton profil circulaire en haut a droite.
3. Orb VPN central: mesh violet vivant autour d'un bouton power monolithique.
4. Bloc statut centre: `Connected`, sous-texte, puis `Protected`.
5. Pilule serveur: pays, ville, mode auto, chevron.
6. Carte statistiques: Downloaded, Uploaded, Connected.
7. Dock metaball flottant: Home actif, autres noeuds inactifs.

## Tokens couleur

| Token | Valeur | Usage Home |
| --- | --- | --- |
| `home.background.deep` | `#07070B` | base OLED, zones les plus sombres |
| `home.background.base` | `#0D0C13` | fond principal |
| `home.surface.base` | `#131317` | boutons, dock, cartes |
| `home.surface.elevated` | `#1A1A20` | surfaces hautes et anneaux |
| `home.surface.highlight` | `#24232B` | reflet haut, bevel doux |
| `home.purple.primary` | `#8A6AF1` | icones actives, orb, dock actif |
| `home.purple.active` | `#A489E7` | halo power, contour mesh |
| `home.purple.deep` | `#443677` | profondeur dock et badges |
| `home.success.green` | `#35D978` | point et label Protected |
| `home.text.primary` | `#F3F1F6` | titres, labels principaux |
| `home.text.secondary` | `#A6A1B3` | sous-titres |
| `home.text.muted` | `#6E6978` | inactif, details |
| `home.divider.subtle` | `rgba(255,255,255,0.08)` | separateurs stats |

## Tokens fond

Le fond est une scene, pas un simple `Color.Black`.

```text
background:
  base: #07070B
  radialGlow:
    origin: right center / upper-right
    color: rgba(138, 106, 241, 0.32)
    radius: 58% to 72% of screen width
    falloff: very soft
  secondaryVignette:
    color: rgba(0, 0, 0, 0.62)
    edges: left and bottom
```

Regles:

- aucune texture bruitee;
- aucun environnement cyberpunk;
- aucun HUD;
- le bloom violet doit rester localise et respirer derriere l'orb et les cartes.

## Tokens layout

Les valeurs exactes pourront etre ajustees au rendu Compose, mais les
proportions doivent rester stables.

```text
screenHorizontalPadding: 32dp to 40dp on phone
topInsetVisualPadding: 32dp to 44dp
homeOrbTopBand: upper 44% of screen
statusToServerGap: 28dp to 36dp
serverToStatsGap: 18dp to 24dp
dockBottomMargin: 28dp to 36dp
minimumTouchTarget: 48dp
```

La composition doit eviter que le dock masque la carte stats. Sur petit ecran,
le bloc stats peut etre legerement reduit, mais le dock reste flottant.

## Composant: bouton profil

```text
size: 64dp to 76dp
shape: circle
surface: matte black radial gradient
icon: user outline, white
stroke: rgba(255,255,255,0.10)
shadow: 0 14dp 34dp rgba(0,0,0,0.48)
glow: localized purple bloom behind container
```

Le bouton profil ne doit pas introduire de carte, de label ou de logo.

## Composant: orb VPN vivant

### Proportions

```text
orbMeshDiameter: 0.68 to 0.76 screen width
powerButtonDiameter: 0.44 to 0.52 screen width
outerRingThickness: 18dp to 26dp
innerRingGap: 8dp to 12dp
powerIconSize: 54dp to 68dp
```

### Mesh orbital

Le mesh est une membrane protectrice violette autour du bouton.

```text
mesh.color.primary: rgba(164, 137, 231, 0.80)
mesh.color.secondary: rgba(138, 106, 241, 0.42)
mesh.particleOpacity: 0.28 to 0.72
mesh.lineOpacity: 0.22 to 0.50
mesh.strokeWidth: 1dp to 2dp
mesh.glow: 0 0 42dp rgba(138,106,241,0.42)
mesh.motion: slow organic wave, not chaotic
```

Implementation acceptable:

- `Canvas` Compose avec arcs, points, chemins ondules et halo radial;
- ou asset raster/vectorise si le pixel-perfect est prioritaire;
- animation lente uniquement: respiration, rotation subtile, deformation douce.

Interdits:

- visualiseur audio;
- globe;
- particules bruyantes;
- neon agressif.

### Bouton power

```text
shape: circle
outerMaterial: vertical dark hardware gradient
innerMaterial: recessed black radial gradient
innerShadow: inset bottom dark pressure
topHighlight: thin subtle crescent
ringStroke: rgba(164,137,231,0.25)
iconColor: #A489E7
iconGlow: 0 0 28dp rgba(138,106,241,0.52)
```

Le bouton doit paraitre cliquable et physique, comme une piece monolithique
usinée. Il ne doit jamais devenir un bouton bleu plat.

## Composant: statut connection

```text
title.text: Connected
title.size: 38sp to 46sp
title.weight: 700
title.color: #F3F1F6
subtitle.text: Your connection is secure
subtitle.size: 17sp to 20sp
subtitle.weight: 400
subtitle.color: #A6A1B3
protected.dot.size: 10dp to 12dp
protected.color: #35D978
protected.text.size: 18sp to 22sp
protected.glow: 0 0 16dp rgba(53,217,120,0.35)
alignment: center
```

Le statut doit rester lisible sans surcharger l'ecran. Pas de logs, pas de
diagnostics techniques, pas d'etat invente par l'UI: l'etat vient du runtime.

## Composant: server pill

```text
height: 84dp to 96dp
shape: pill / 9999px radius
paddingHorizontal: 14dp to 18dp
surfaceGradient: 180deg rgba(36,35,43,0.82) -> rgba(14,14,18,0.96)
stroke: rgba(255,255,255,0.10)
innerHighlight: inset top rgba(255,255,255,0.08)
shadow: 0 18dp 45dp rgba(0,0,0,0.45)
```

Contenu:

```text
leftBadge.size: 58dp to 68dp
leftBadge.shape: circle
leftBadge.background: purple deep radial gradient
leftIcon: globe, purple active
title: country, 20sp to 24sp, #F3F1F6
subtitle: city + auto mode, 16sp to 18sp, #A6A1B3
rightChevronContainer.size: 58dp to 68dp
rightChevronContainer.shape: circle
rightIcon: chevron, #F3F1F6 with reduced opacity
```

Regle produit:

- ce composant ouvre la selection de localisation ou le choix de config;
- il ne doit pas afficher quota/expiration;
- les donnees affichees doivent venir du noeud selectionne ou du mode auto.

## Composant: stats card

```text
height: 150dp to 190dp visible above dock
shape: large monolithic card, radius 42dp to 56dp
surface: matte black hardware gradient
stroke: rgba(255,255,255,0.08)
shadow: 0 18dp 45dp rgba(0,0,0,0.45)
topHighlight: rgba(255,255,255,0.07)
```

Colonnes:

```text
columns: 3 equal
icons: purple primary, 24dp to 28dp
labels: Downloaded / Uploaded / Connected
labelSize: 17sp to 20sp
labelColor: #F3F1F6
divider: vertical, rgba(255,255,255,0.10), very short
```

La carte stats doit rester minimaliste. Pas de graphes, pas de valeurs
techniques secondaires si elles degradent la lisibilite du Home.

## Composant: dock metaball Home

### Structure

Noeuds Home:

1. Home actif.
2. Servers inactif.
3. Subscription inactif.
4. Settings inactif.

Le mock standalone montre le meme objet avec un noeud profil actif: cette
variation confirme la matiere, le contour organique et le comportement actif,
mais l'ecran Home doit activer `Home`.

### Geometrie

```text
dock.width: 82% to 88% screen width
dock.height: 92dp to 118dp
nodeDiameter.inactive: 72dp to 86dp
nodeDiameter.active: 88dp to 104dp
valleys: concave transitions between nodes
outerShape: fused metaball body, never simple capsule
bottomMargin: 28dp to 36dp
```

### Matiere

```text
dock.base: #101014
dock.gradient: radial/linear dark hardware mix
dock.topHighlight: rgba(255,255,255,0.16) along upper contour
dock.lowerPurpleEdge: rgba(138,106,241,0.22)
dock.innerDepression: rgba(0,0,0,0.44)
dock.shadow: 0 24dp 55dp rgba(0,0,0,0.58)
```

### Noeud actif

```text
active.background: purple radial gradient #A489E7 -> #6D49E8
active.glow: 0 0 34dp rgba(138,106,241,0.55)
active.icon: white
active.label: optional on compact Home, required if dock includes labels
active.dot: tiny purple dot below label when label visible
```

### Noeuds inactifs

```text
inactive.background: recessed black circle
inactive.icon: white at 68% to 78% opacity
inactive.stroke: rgba(255,255,255,0.08)
inactive.glow: none or extremely soft
```

Implementation acceptable:

- une shape Compose custom via `Path`;
- ou un fond raster 9-patch-like si le pixel-perfect metaball est prioritaire;
- icones Compose par-dessus avec zones tactiles separees.

Interdit:

- `NavigationBar` Material par defaut;
- simple rectangle arrondi;
- capsule reguliere sans concavites;
- noeud actif detache du corps.

### Extraction geometrique appliquee au Home Android

Ces valeurs sont issues du mock dock et de la capture reelle Android du
2026-05-20. Elles servent de calque local pour la premiere passe Compose.

```text
screen.capture.width: 1080px
dock.base.width: 320dp
dock.base.height: 89dp
dock.centerY: 45dp
dock.lobeCenters.x: 44.5dp, 121.5dp, 198.5dp, 275.5dp
dock.inactiveOuterDiameter: 80dp
dock.activeOuterDiameter: 89dp
dock.inactiveOuterRadius: 40dp
dock.activeOuterRadius: 44.5dp
dock.inactiveInnerRecess: 54dp
dock.activePurpleCircle: 58dp
dock.waistHeight: 40dp target
dock.waistToRadiusRatio: 0.60
dock.spacingToRadiusRatio: 2.0
activeIcon.size: 22dp
inactiveIcon.size: 18dp
activeLabel.size: 7sp on Home only
body.stroke: 0.60dp white at 4.5% alpha
body.topHighlight: 0.78dp white at 16% alpha
body.purpleEdge: 0.78dp purple primary at 11% alpha
dock.homeBottomPadding: 34dp
dock.motion:
  activeTransition: 280ms cubic-bezier(0.22, 1, 0.36, 1)
  pressScale: 0.96
  breathingDuration: 4200ms
  breathingScale: 1.00 to 1.01
dock.material:
  noGrain: true
  noNoise: true
  smoothMoldedBlack: true
  localizedActiveGlow: true
```

### Matrice de divergence dock

| Surface | Mock | Capture Android V4 | Correction appliquee |
| --- | --- | --- | --- |
| Silhouette | quatre masses fusionnees, non capsule | bonne base 340x89 | conservee |
| Transitions | vallees concaves lisses | vallees presentes mais encore trop independantes du noeud actif | courbes recalculees avec rayon par lobe |
| Matiere | noir mat plein, highlight fin | corps trop gris/ruban | fill assombri, strokes reduits |
| Noeud actif | outer 89dp, purple 58dp, glow localise | actif Home visible mais le corps etait calcule comme un lobe 80dp | rayon actif 44.5dp injecte dans la silhouette |
| Noeuds inactifs | recess 54dp, icones fines | inactifs trop contrastes | icones 18dp, recess plus doux |
| Contour | relief superieur discret | contours trop visibles | top highlight fin, purple edge tres doux |
| Labels | absent sur mock dock standalone | Home label visible par contexte Home | label conserve mais reduit a 7sp |
| Capture isolee | dock seul, actif a droite, sans texte | capture Home complete melangee avec stats et barre systeme | capture temporaire realisee puis surface debug supprimee |
| Halo actif | bloom localise autour du noeud actif | halo calcule sur Home seulement | halo centre sur le noeud actif reel |
| Calage Home | dock flottant avec respiration sous la stats card | dock trop colle au bas | bottom padding Home remonte a 34dp |
| Recess inactifs | noirs mais lisibles, effet enfonce | trop bouches sur telephone | gradient interne eclairci sans changer les icones |
| Matiere active | violet profond dans un anneau noir | actif trop pastille/plat | glow reduit, anneau externe assombri, gradient violet approfondi |
| Diametre cercles icones | cercles presents, propres et parfaitement circulaires | cercles trop grands autour des icones | retour aux diametres cible 80/89dp avec couches 3D propres |
| Texture | surface lisse, dense, noire, sans bruit | grain/texture procedural rendaient le dock sale | grain, smoke, stries, random overlay supprimes |
| Motion | core actif glisse, glow suit, body se deforme subtilement | changement visuel trop instantane | `Animatable` sur active center + influence rayon body |
| Layering actif | core violet integre dans la cavite, icone nette au-dessus | core pouvait etre masque par le bowl | core extrait dans une couche animee dediee |

La dimension globale et la position de l'orb restent conservees. Cette passe
ne modifie que la matiere du dock et son calage interne.

## Tokens materiels centralises

Ces tokens sont la source commune pour le dock, le bouton Start central et le
bouton User top-right. Aucun de ces composants ne doit inventer une nouvelle
matiere.

```text
SwimDesignTokens.Material
  ShellTop: rgba(255,255,255,0.07)
  ShellMid: #17171C
  ShellBottom: #07070B
  BowlTop: #101116
  BowlMid: #05060A
  BowlBottom: black alpha 0.96
  PurpleCoreTop: #B89AFF
  PurpleCoreMid: #8A6AF1
  PurpleCoreBottom: #5D3BD8
  OuterDarkVeil: black alpha 0.40
  BowlInnerShadow: black alpha 0.60

SwimDesignTokens.Highlight
  InnerTop: white alpha 0.08
  BowlRim: white alpha 0.04
  BodyStroke: white alpha 0.055
  SkinSheen: white alpha 0.18
  PurpleEdge: purple primary alpha 0.11

SwimDesignTokens.Shadow
  Dock: 24dp
  StartButton: 28dp
  UserButton: 14dp
  ActiveIconGlow: 16dp
  InnerBottomAlpha: 0.45

SwimDesignTokens.Motion
  PressScale: 0.96
  DockTransitionMs: 280
  DockBreathingMs: 4200
  DockBreathingScale: 1.01
  DockGlowIdleAlpha: 0.68
  DockGlowPeakAlpha: 0.74
```

## Architecture multicouche commune

Le dock, le bouton Start et le bouton User partagent le meme modele:

```text
Layer 1 - Outer Shell:
  corps noir mat, gradient shell top/mid/bottom.

Layer 2 - Recessed Bowl:
  cavite sombre, shadow interne, rim highlight discret.

Layer 3 - Core:
  actif seulement pour les surfaces violettes, gradient PurpleCoreTop/Mid/Bottom.

Layer 4 - Icon Plane:
  icone nette, jamais floutee.

Layer 5 - Skin Overlay:
  sheen/specular doux en haut-gauche, sans glassmorphism agressif.

Layer 6 - Local Glow:
  glow violet localise autour du noeud actif ou du Start button.
```

Le bouton User utilise une version miniature et sobre de cette architecture:
shell noir, bowl interne, icone blanche, micro sheen, glow violet tres faible.

Le bouton Start utilise la version pleine:
shell externe, anneau intermediaire, bowl central, icone power, peau speculaire
et glow violet localise integre a l'orb.

### Formule de jonction entre icones

La forme entre deux icones doit etre construite comme une approximation
metaball Bezier, pas comme une capsule ou une vague libre.

```text
centers: [40dp, 126dp, 210dp, 296dp]
cy: 45dp
inactiveR: 37dp
activeR: 41dp
ri: if active lobe then activeR else inactiveR
waistHalfHeight: min(r1, r2) * 0.60
topY[i]: cy - ri
bottomY[i]: cy + ri
waistTopY: cy - waistHalfHeight
waistBottomY: cy + waistHalfHeight
controlCircleX[i]: ri * 0.55
controlWaistX: d * 0.15
```

Pour chaque paire de centres adjacents:

```text
d = cx2 - cx1
wx = (cx1 + cx2) / 2
r1 = radius at cx1
r2 = radius at cx2
wh = min(r1, r2) * 0.60

top segment:
cubicTo(cx1 + r1*0.55, cy-r1, wx - d*0.15, cy-wh, wx, cy-wh)
cubicTo(wx + d*0.15, cy-wh, cx2 - r2*0.55, cy-r2, cx2, cy-r2)

bottom segment:
cubicTo(cx1 - r1*0.55, cy+r1, wx + d*0.15, cy+wh, wx, cy+wh)
cubicTo(wx - d*0.15, cy+wh, cx2 + r2*0.55, cy+r2, cx2, cy+r2)
```

Regle visuelle: les jonctions doivent etre des tailles de guepe rondes et
continues. Elles ne doivent jamais former une pointe, une vague libre, ou une
barre capsule.

## Iconographie

Utiliser des icones outline simples, optiquement epaisses mais pas massives:

- Home: maison arrondie.
- Servers: base de donnees ou globe serveur selon ecran.
- Subscription: wallet/carte.
- Settings: engrenage.
- Server pill: globe.
- Power: symbole power.

Les icones restent blanches ou violettes. Pas de palette multicolore sur le
Home, sauf etat vert `Protected`.

## Regles negatives Home

Ne pas utiliser:

- cartes rectangulaires Android;
- coins nets;
- bouton power bleu;
- barre de navigation Material standard;
- logo en haut a gauche;
- panels techniques;
- liste de serveurs sur Home;
- quota ou expiration sur la server pill;
- glow violet global et uniforme;
- texte qui explique comment utiliser l'app.

## Frontiere fonctionnelle

Ces tokens sont purement presentationnels. L'implementation ne doit pas:

- inventer un etat `Connected`;
- bypasser les checks VPN;
- changer les contrats trial/subscription;
- modifier le parsing VPN;
- toucher au backend.

Le Home affiche l'etat fourni par le runtime Android et les donnees de noeud
issues de la source active.

## Composants Compose cibles

Noms recommandes pour une implementation future:

```text
SwimHomeScreen
SwimDarkLuxuryBackground
SwimProfileButton
SwimPowerOrb
SwimConnectionStatus
SwimServerPill
SwimStatsCard
SwimMetaballDock
SwimDockNode
```

Les tokens doivent etre centralises avant usage:

```text
SwimDesignTokens.Color
SwimDesignTokens.Shape
SwimDesignTokens.Elevation
SwimDesignTokens.Spacing
SwimDesignTokens.Home
```

## Checklist de validation visuelle

- Le fond reste noir OLED avec bloom violet localise.
- Le bouton profil est circulaire, sombre, sans label.
- L'orb domine le haut de l'ecran et parait vivant.
- Le bouton power est materiel, profond, non plat.
- `Connected` est le texte le plus lisible apres l'orb.
- `Protected` a un point vert avec halo doux.
- La server pill est une capsule, pas une carte.
- La carte stats ne surcharge pas le Home.
- Le dock est organique, concave, fusionne, pas une capsule standard.
- Le noeud Home actif est violet et integre au corps.
- Les textes ne se chevauchent pas sur 360dp, 390dp, 412dp et 430dp.
- Les zones tactiles restent superieures ou egales a 48dp.
- Le dock ne masque pas les actions critiques.
