# Comparatif paiements — SWIMVPN (options légitimes d'encaissement)

> **Date** : 2026-06-23 · **Statut** : doc de décision (P0 backlog).
> **Honnêteté** : structure et faits stables = solides ; **les chiffres exacts de frais et les politiques "VPN accepté ?" évoluent vite** → tout ce qui est marqué `≈` ou `[à vérifier]` doit être confirmé sur le site du provider avant décision. Aucune optimisation d'évitement KYC/AML (interdit, voir BACKLOG WON'T DO).

## 0. Contexte SWIMVPN (ce qui contraint le choix)
- **VPN = catégorie marchande "high-risk"** (MCC anonymisation/digital goods) → beaucoup de PSP grand public **interdisent ou restreignent**.
- **Devises** : USD (catalogue), RUB, XOF. **Audience** : Telegram-centric + forte base **Russie/CIS** + **Afrique francophone** (XOF).
- **Technique** : backend NestJS → besoin **API-first** (REST + webhooks ; idéalement abonnements).
- **Existant** : "SwimPay" (probablement un **agrégateur** qui fronte des rails RU/CIS sans qu'on ait d'entité locale) + crypto. Tribute **abandonné** (webhook = identité Telegram seule, pas d'email → pas de corrélation commande propre).

## 1. Le vrai problème (à dire franchement)
1. **Stripe / PayPal / Adyen** : les VPN/services d'anonymisation sont **fréquemment prohibés ou suspendus** (clauses "anonymization/VPN"). Pas un rail fiable pour nous. `[à vérifier au cas par cas]`
2. **Visa/Mastercard ont quitté la Russie en 2022** : une carte étrangère ne marche pas chez un acquéreur RU, et une carte **Mir** (RU) ne marche pas hors RU. → **aucun PSP global unique** ne couvre RU + monde.
3. **Vendre un VPN EN Russie = exposition réglementaire** (la RU restreint les VPN). C'est un **problème légal, pas un problème de processeur** — à ne pas "contourner" via un rail, juste à connaître.

**Conséquence** : la bonne architecture n'est pas "un PSP parfait" mais **multi-rail par région**, avec fallback.

---

## 2. Catégorie A — Merchant-of-Record (MoR) digital-goods → *rail global recommandé*
Le MoR est le **vendeur légal** : il gère TVA/sales-tax mondiale, chargebacks, fraude, et te verse un **net**. Idéal pour vendre du logiciel/abonnement à l'international sans monter 30 entités fiscales.

| Provider | Modèle | VPN/anonymisation ? | Devises / settlement | API | Frais ≈ |
|---|---|---|---|---|---|
| **Paddle** | MoR | Focus SaaS/software ; **VPN à confirmer** (prudents sur anonymisation) `[vérifier]` | mondial, payout USD/EUR virement | REST + webhooks + subs | ≈ 5 % + 0,50 $ `[vérifier]` |
| **FastSpring** | MoR | Digital goods, historiquement souple `[vérifier VPN]` | mondial, payout multi-devise | REST + webhooks + subs | ≈ 5–8 % `[vérifier]` |
| **Lemon Squeezy** | MoR | **Racheté par Stripe** → risque de durcissement policy VPN `[vérifier]` | mondial, payout via Wise/Stripe | REST + webhooks + subs | ≈ 5 % + 0,50 $ |
| **2Checkout / Verifone** | MoR/PSP | Historiquement **high-risk-friendly** | très large, multi-devise | REST + webhooks + subs | ≈ 6 %+ `[vérifier]` |
| **PayPro Global** | MoR | **High-risk / digital goods friendly** | large | REST + webhooks + subs | ≈ 5–9 % `[vérifier]` |

- **Onboarding** : entité business généralement requise + KYB (docs société). Délai jours→semaines.
- **Atout décisif** : un seul intégration → cartes internationales + TVA gérée + abonnements. **Couvre l'essentiel "monde hors RU".**
- **Risque clé à lever** : la **politique VPN réelle** de chacun (postuler + obtenir l'accord écrit avant de bâtir dessus). 2Checkout/Verifone et PayPro Global sont les plus probables d'accepter ; Paddle/Lemon Squeezy les plus à risque de refus.

## 3. Catégorie B — Processeurs crypto API-first → *rail complémentaire fort (RU/CIS + censure)*
Axe de tri : **custodial vs non-custodial**, **settlement fiat vs crypto-only**, **coins** (USDT TRC20 = frais réseau faibles, très utilisé en RU/CIS), **KYC marchand**, **acceptation RU**.

| Provider | Custody | Settlement fiat ? | Coins clés | API | Frais ≈ | KYC marchand |
|---|---|---|---|---|---|---|
| **BTCPay Server** | **Non-custodial, self-hosted** | non (tu encaisses en crypto direct) | BTC + Lightning (+ altcoins via plugins) | REST + webhooks (Greenfield API) | **0 % processeur** (juste réseau) | **aucun** (c'est ton serveur) |
| **NOWPayments** | Custodial | **oui** (auto-convert + payout) | 100+ dont USDT TRC20/ERC20 | REST + webhooks + subs | ≈ 0,5–1 % + réseau | email→KYB selon volume `[vérifier]` |
| **CoinGate** | Custodial | **oui** (payout EUR/crypto) | BTC/LN + nombreux | REST + webhooks | ≈ 1 % | KYB |
| **Cryptomus** | Custodial | partiel | USDT multi-chaînes, populaire RU `[vérifier sanctions]` | REST + webhooks | ≈ 0,4–1 % | léger→KYB `[vérifier]` |
| **Plisio** | Custodial | partiel | large | REST + webhooks | ≈ 0,5 % | léger |
| **Coinbase Commerce** | semi | via Coinbase | majors + USDC | REST + webhooks | ≈ 1 % | compte Coinbase |
| **OpenNode** | Custodial | oui (BTC) | **Bitcoin/Lightning** focus | REST + webhooks | ≈ 1 % | KYB |

- **Recommandation crypto** : **BTCPay** (souveraineté, 0 % de frais, résistant à la censure, parfait pour notre éthique + RU) **+** un custodial (**NOWPayments** ou **CoinGate**) pour l'**auto-conversion fiat** et l'UX grand public. **Prioriser USDT TRC20** pour la base RU/CIS.
- **Honnêteté** : le payout fiat ajoute frais + KYC ; rester en stablecoin évite la volatilité ; vérifier que le provider **n'exclut pas la RU** (certains custodials filtrent).

## 4. Catégorie C — Telegram-natif → *bon fit audience, économie/contraintes à accepter*
- **Telegram Payments (Bot Payments API 2.0)** : excellent UX in-bot, mais c'est un **front** — tu y branches **un PSP sous-jacent** (donc il hérite de SES restrictions VPN). Ne règle pas le problème PSP, il l'emballe.
- **Telegram Stars** : monnaie in-app pour biens numériques dans les bots/apps. **Telegram prend une part** ; si l'achat passe par l'app iOS/Android, **Apple/Google prennent ~30 %** → érosion forte. Payout en **TON**. **Corrélation** : liée au **telegram user id** (même limite que Tribute) → il faut **le bot pour ponter identité→commande+email**.
- **TON / Wallet Pay** : crypto on-Telegram, API Wallet Pay, payout TON.
- **Verdict** : option **optionnelle** pour un flux d'achat 100 % in-bot. À n'ajouter que si on accepte la part prélevée + on construit le pont identité→commande via le bot.

## 5. Catégorie D — Russie / CIS → *le rail le plus contraint (garder SwimPay)*
YooKassa (ЮKassa), CloudPayments, Robokassa, Tinkoff/T-Bank, **SBP** (paiement instantané inter-banques, massif en RU), cartes **Mir**.
- **Réalité** : la plupart exigent une **entité légale russe + compte bancaire RU résident**. Un marchand étranger **ne s'onboarde pas** facilement. QIWI : **licence révoquée en 2024** `[vérifier]` → hors-jeu.
- **C'est précisément pourquoi "SwimPay" existe** (agrégateur qui fronte ces rails). → **Garder SwimPay pour la RU/CIS** ; ne pas tenter d'onboarder un PSP RU en direct sans entité.
- **Rappel légal** : VPN-en-RU = exposition réglementaire (à connaître, pas à "résoudre" via paiement).

## 6. Catégorie E — XOF / Afrique de l'Ouest (UEMOA)
Mobile Money dominant : **Orange Money, MTN MoMo, Moov, Wave**. Agrégateurs API : **CinetPay** (UEMOA/XOF, mobile money + cartes), **PayDunya** (Sénégal/XOF), **Flutterwave** (large Afrique, XOF via MoMo), **Wave**, **Bizao**, **Hub2**, **KkiaPay** (Bénin), **Paystack** (plutôt Nigeria/NGN).
- **Supportent XOF** : CinetPay, PayDunya, Flutterwave, Bizao = oui (mobile money UEMOA). API REST + webhooks.
- **Le blocage** : settlement/payout exige souvent une **entité + compte bancaire dans la région** ; onboarding KYB local. Frais ≈ 1,5–3,5 % `[vérifier]`.
- **VPN** : digital goods généralement OK `[vérifier]`.
- **Meilleurs fits API-first XOF** : **CinetPay** et **PayDunya** — **si** on peut satisfaire entité/payout local ; sinon **différer** (un MoR de la catégorie A couvre déjà les cartes internationales, y compris des clients africains by card).

---

## 7. Synthèse — quel rail pour quel besoin
| Besoin | Rail recommandé | Pourquoi |
|---|---|---|
| Monde (hors RU), cartes + TVA + abos | **1 MoR digital-goods** (2Checkout/Verifone ou PayPro Global en tête) | une intégration, USD net, taxe gérée ; **lever la policy VPN d'abord** |
| Censure / RU-CIS / éthique / 0 frais | **BTCPay (self-host)** + **NOWPayments/CoinGate** | non-custodial + auto-convert fiat ; USDT TRC20 |
| Russie / CIS grand public | **Garder SwimPay** | agrégateur, évite l'entité RU ; rails RU inaccessibles en direct |
| Achat in-bot Telegram | **Telegram Stars / Wallet Pay** *(optionnel)* | fit audience ; accepter la part prélevée + pont identité |
| Afrique de l'Ouest (XOF) | **CinetPay / PayDunya** *(si entité/payout local)* sinon **différer** | mobile money ; sinon le MoR encaisse déjà les cartes |

## 8. Reco concrète (multi-rail, ordre d'action)
1. **Shortlist 2 MoR** (PayPro Global + 2Checkout/Verifone) → **postuler et obtenir par écrit l'acceptation "VPN"** avant tout dev. *(C'est le verrou n°1 : ne rien construire avant l'accord.)*
2. **Monter un BTCPay Server** (self-host, 0 % frais, API Greenfield) + brancher **NOWPayments ou CoinGate** pour l'auto-conversion fiat et l'UX. Prioriser **USDT TRC20**.
3. **Conserver SwimPay** pour RU/CIS (ne pas chercher un PSP RU direct).
4. **Évaluer l'économie de Telegram Stars** (part prélevée + store) avant de l'ajouter ; sinon laisser.
5. **XOF** : tester **CinetPay**/**PayDunya** uniquement si l'entité/payout local est réaliste ; sinon s'appuyer sur le MoR pour les cartes.

## 9. À vérifier en live avant décision (volatile)
- Politique **"VPN/anonymisation accepté ?"** réelle de chaque MoR (Paddle, Lemon Squeezy post-Stripe surtout) et de chaque processeur crypto custodial.
- **Frais exacts** (les `≈` ci-dessus) + minimums de payout + réserves roulantes éventuelles.
- **Statut QIWI** (révocation 2024) et **exclusions RU** côté processeurs crypto custodials.
- **Onboarding XOF** : possibilité réelle pour un marchand non-africain (CinetPay/PayDunya/Flutterwave).
- Ce qu'**est** juridiquement "SwimPay" (agrégateur ? sous quelle juridiction ?) pour cadrer son rôle dans l'archi.

> **Note de méthode** : ce comparatif est bâti sur une connaissance du domaine (catégories, modèles, contraintes 2022→2025 stables). Les éléments `[à vérifier]`/`≈` nécessitent une confirmation web/contact provider — déclenchable en un passage de recherche live une fois l'outil sous-agent autorisé (cf. § permissions).
