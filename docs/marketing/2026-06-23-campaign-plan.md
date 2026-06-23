# Plan de campagne marketing — SWIMVPN (compliant, live-sourcé 2026-06)

> Livrable P0. **Assets + stratégie prêts à lancer ; le déclenchement d'envoi/publication reste le geste de l'user** (pas autonome).
> Sources web citées en ligne. Compagnon : [partenaires & affiliation](2026-06-23-partners-affiliate.md) · [séquence email opt-in](optin-email-sequence.md).

## ⚠️ La règle légale qui gouverne tout (RU) — à lire avant le reste
Depuis le **1er septembre 2025 (loi 281-ФЗ)**, **faire la publicité / populariser un VPN auprès d'une audience résidant en Russie est une infraction administrative**, et la **responsabilité retombe sur l'annonceur (заказчик = nous)** : amendes personnes morales **200 000–500 000 ₽** par infraction (la FAS lit « publicité » largement — un simple lien VPN dans un canal Telegram a déjà été qualifié de pub). *Utiliser* un VPN n'est pas pénalisé ; en faire la **promo payante vers la RU**, oui.
Depuis **mars 2024**, *publier* de l'info de contournement (guides, listes de serveurs, « comment débloquer ») est aussi interdit.

**Conséquences, non négociables :**
1. **RU/CIS = organique uniquement.** Aucune pub VPN payante ciblant la Russie, aucune page promo hébergée en RU, aucune entité/staff RU qui fronte le marketing.
2. **Reach russophone = via la diaspora** (créateurs/canaux hors juridiction RU), jamais ciblé « intérieur RU ».
3. **Règle créative partout** : « connexion privée, sécurisée, **résistante à la censure** » — **jamais** « contourne Roskomnadzor / bats le pare-feu / 100 % anonyme » (c'est ce qui fait bannir sur tous les réseaux pub ET ce qui crée le risque légal).
4. **L'Afrique francophone (XOF) est le marché ouvert, légal, à plus faible risque → à prioriser pour le payant.**

Sources : [Meduza 01/09/2025](https://meduza.io/en/feature/2025/09/01/no-more-phone-sharing-vpn-ads-or-foreign-agent-teachers) · [RIA — amendes 281-ФЗ](https://ria.ru/20250901/shtraf-2038724982.html) · [Moscow Times explainer](https://www.themoscowtimes.com/2025/08/06/how-russias-new-internet-restrictions-work-and-how-to-get-around-them-a90117). *(Application extraterritoriale contre un annonceur étranger = non testée juridiquement — UNVERIFIED ; le contenu reste sanctionnable là où il est vu en RU.)*

---

## 1. Segments d'audience
| Segment | Où ils sont | Douleur | Message qui résonne |
|---|---|---|---|
| **RU/CIS chercheurs de contournement** (intention max) | GitHub (Xray/Reality/zapret), **ntc.party**, Habr, 4PDA, canaux Telegram de configs | les protocoles se font bloquer en heures ; VPN retirés des stores | **la résilience protocolaire comme message** (multi-transport Reality/VLESS + SS-2022 + WireGuard + fallback xHTTP) « qui continue de marcher ». Crédibilité technique, pas du copywriting pub. |
| **RU/CIS grand public privacy** | bouche-à-oreille, après avoir déjà contourné | « connecté mais pas d'internet », instabilité 4G | fiabilité + privacy (PAS « bats l'État ») |
| **Afrique francophone mobile-first (XOF)** | Facebook groups, WhatsApp (notes vocales), YouTube (44 % reach SN), Telegram | coupures événementielles (élections/manifs), coût data, multi-SIM | « **reste connecté même quand les réseaux sont coupés** » + économie de data + multi-opérateur + prix XOF / paiement local |
| **Global privacy-conscious** | r/privacy, Privacy Guides, HN, GitHub, créateurs YouTube privacy | méfiance envers les VPN à logs/affiliation | client open-source, résistance DPI, paiement crypto anonyme, audits |

Sources : [Access Now KeepItOn 2024](https://www.accessnow.org/press-release/africa-keepiton-internet-shutdowns-2024/) · [DataReportal Sénégal 2025](https://datareportal.com/reports/digital-2025-senegal) · [Privacy Guides — critères VPN](https://www.privacyguides.org/en/vpn/).

## 2. Carte des canaux (+ règle d'auto-promo)
### Reddit — canal **faible** pour CETTE audience
RU largement coupée de Reddit (throttling Cloudflare depuis juin 2025 ; Reddit bannit le ciblage pub RU) ; faible pénétration en Afrique francophone ; **pas de gros subreddit anti-censure actif** (cet écosystème vit sur GitHub/Telegram).
- r/privacy (~1,4M) : **Règle 13 interdit de recommander un VPN précis** → répondre aux questions de catégorie seulement.
- r/PrivacyGuides : promo VPN **interdite**, posts réservés aux mods → fermé.
- r/VPN : reviews-only, hostile au vendeur.
- **r/cybersecurity** (~1,1M) : auto-promo <10 %, 1×/sem, flair « Corporate Blog » → **viable, discipliné**.
- **r/selfhosted** (~766K) : <10 % + valeur → bon angle « proxy self-host ».
- **r/AskARussian**, **r/Senegal / r/Africa** : Q&A authentique + divulgation d'affiliation.
- **Morts (vérifié)** : r/realityclient, r/outline_vpn n'existent pas ; r/Shadowsocks quasi-mort.
- Maxime FTC : divulguer l'emploi sur l'endorsement lui-même (« Disclosure: I work on SWIMVPN »).
Sources : [règles r/privacy](https://www.reddit.com/r/privacy/about/rules/) · [r/cybersecurity advertising](https://www.reddit.com/r/cybersecurity/wiki/advertising_guidelines).

### Telegram — **le hub naturel** (on est déjà bot-centric)
Découverte native faible → la couche de découverte = **catalogues tiers** : **TGStat** ([tgstat.com](https://tgstat.com/)), **Telemetr.io**. Mécanique de croissance = **cross-posting, citations, dossiers partagés**, pas la recherche.
- **Organique (primaire RU/CIS)** : pas de cold-DM/link-dump ([Spam FAQ](https://telegram.org/faq_spam)) ; apporter de la valeur technique d'abord ; **notre canal/bot = le hub** (home canonique, support, livraison de configs, surface de parrainage).
- **Posts payants (GEOs légales seulement — Afrique/global, PAS RU)** : deals directs admin (opaque, sans escrow) **ou marketplace [Telega.io](https://telega.io/)** (canaux ≥1000 subs, **escrow intégré**, ~12,5 % commission) → à privilégier contre l'arnaque aux faux subs.
- **Telegram Ads officiel** ([ads.telegram.org/guidelines](https://ads.telegram.org/guidelines)) : ~160 car., VPN non listé comme interdit (réseau le plus permissif) ; entrée directe ~€2M, **via revendeur ~€3–5k** ; **pas pour la RU**.

### Forums / communautés techniques (le vrai moteur RU/CIS — présence, pas pitch)
**ntc.party** ([ntc.party](https://ntc.party/)) · GitHub [net4people/bbs](https://github.com/net4people/bbs), [XTLS/Xray-core](https://github.com/XTLS/Xray-core) · **Habr** · **4PDA** · Tor Project Forum · Roskomsvoboda (advocacy/news).

### Échelle de crédibilité globale (front doors légitimes)
- **Privacy Guides — Project Showcase** ([discuss.privacyguides.net/c/.../showcase/13](https://discuss.privacyguides.net/c/privacy/showcase/13)) : on coche déjà résistance-DPI + paiement crypto + WireGuard ; **la *recommandation* complète exige open-source + audits répétés + propriété publique** (hors de portée à court terme → viser un fil Showcase + feedback).
- **Show HN** ([news.ycombinator.com/showhn.html](https://news.ycombinator.com/showhn.html)) : autorisé pour son propre travail s'il y a un truc exécutable ; write-up technique du moteur anti-DPI, zéro langage marketing, pas de demande d'upvote.
- **Lobsters**, **Product Hunt** (jamais demander d'upvotes ; relaunch à 6 mois), **AlternativeTo / Slant** (auto-submit gratuit).
- **Sites de review VPN** : quasi tous **affiliés / pay-to-list** → traiter comme du payant déguisé (détail dans [partenaires](2026-06-23-partners-affiliate.md)).

## 3. Nuances régionales
**RU/CIS** : voir §0. Chemin compliant = **organique only** (ntc.party, GitHub, Habr, 4PDA, bouche-à-oreille, bot Telegram), **framing privacy/protocole pas contournement**, distribution sideload. Enforcement réel : RKN a restreint **12 600 matériels de promo VPN (janv–avr 2025)**, **100+ VPN retirés de l'App Store RU**, ~**439–469 services bloqués** début 2026. CIS : Biélorussie (coupures événementielles), Kazakhstan (épisodique autour des manifs).

**Afrique francophone (XOF)** : demande documentée et récurrente (Sénégal fév. 2024 : coupure mobile + blocage FB/X/WhatsApp/IG/YouTube/Telegram ; Guinée : blocage social media très long ; 2024 = record 21 coupures dans 15 pays africains). Audience Android entrée/milieu, **prépayé sensible au coût data**, multi-SIM, **français écrit + langues locales parlées**, **WhatsApp notes vocales** = canal majeur. Canaux : Facebook groups/Pages (valeur d'abord, validation admin), WhatsApp Communities/Channels, Telegram, **YouTube tutos** (sponsoriser micro-créateurs tech FR par pays), RP via médias tech FR (**Socialnetlink, Agence Ecofin/WeAreTech.Africa, CIO Mag, AfriqueITNews**). Message FR : « **accès quand ça coupe** » (#1), économie de data, multi-opérateur, prix XOF + paiement local ; ton simple, vidéo/voix > texte long.
Sources : [NetBlocks Sénégal](https://netblocks.org/reports/social-media-restricted-and-mobile-internet-cut-in-senegal-amid-political-unrest-W80QkaAK) · [GSMA Mobile Economy Africa 2025](https://www.gsma.com/solutions-and-impact/connectivity-for-good/mobile-economy/africa/).

## 4. Réalité de la pub payante
| Réseau | VPN | Contrainte / mine |
|---|---|---|
| Google Ads | VPN générique OK, scruté | vérification d'identité annonceur ; « cacher localisation/identité = contournement » interdit |
| Meta (FB/IG) | pas d'exception | enforcement « Circumventing Systems » → comptes désactivés souvent ; **haut risque** |
| Apple Search Ads | besoin d'une fiche App Store | **indispo pour SWIMVPN sideload-only** |
| Reddit Ads | autorisé par défaut | bannit le contournement copyright ; **bannit le ciblage RU** ; bloque les users VPN |
| Telegram Ads | **non interdit** (le plus permissif) | €2M direct / ~€3–5k via revendeur ; **pas pour la RU** |
**Où le payant marche vraiment** : (1) **notre propre programme d'affiliation** (rail d'acquisition VPN standard, payouts souvent en **crypto** → colle à nos rails) ; (2) réseaux **native/push** qui acceptent les offres VPN (Clickadu, RichAds, HilltopAds) — toujours framing privacy. Garde-fou universel : jamais « bats le censeur / 100 % anonyme ».
Sources : [Google circumventing systems](https://support.google.com/adspolicy/answer/15938075?hl=en) · [Meta ad standards](https://transparency.meta.com/policies/ad-standards/).

---

## 5. Stratégie priorisée (le bottom-line)
1. **RU/CIS = organique only, par la loi.** Crédibilité technique (ntc.party, GitHub Xray/Reality, Habr, 4PDA), bouche-à-oreille, **hub bot/canal Telegram**, **résilience-protocole-comme-message**. Pas de framing contournement, pas d'assets hébergés en RU.
2. **Afrique francophone = territoire payant légal.** Sponsoring tutos YouTube FR + posts Telegram via **Telega.io** (escrow) + organique Facebook/WhatsApp + RP médias tech FR. « Accès quand ça coupe », prix XOF, paiement local.
3. **Pub directe grands réseaux = faible ROI / risque de suspension** pour un VPN sideload → remplacer par **programme d'affiliation (payouts crypto) + réseaux native/push VPN-friendly**.
4. **Échelle de crédibilité globale** : open-sourcer le moteur anti-DPI → Show HN (exécutable) + write-up Lobsters → Product Hunt (sans mendier d'upvotes) + AlternativeTo/Slant → fil **Privacy Guides Showcase**.
5. **Email opt-in** (déjà capturé en prod) : nurture E1–E4 ([séquence](optin-email-sequence.md)) — **après câblage de la désinscription** (cf. backlog).
6. **Une règle créative partout** : « privé, sécurisé, **résistant à la censure** » — jamais « bats le censeur ».

## Flags à reconfirmer
Application extraterritoriale RU (non testée) · comptes de services bloqués (439 vs 469) · texte exact des règles pub Reddit/Telegram (pages bot-bloquées) · chiffres users WhatsApp/TikTok/Telegram par pays africain (non publiés).
