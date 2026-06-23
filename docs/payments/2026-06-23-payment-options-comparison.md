# Comparatif paiements — SWIMVPN (options légitimes d'encaissement)

> **Date** : 2026-06-23 · **Statut** : doc de décision (P0 backlog).
> **Honnêteté** : structure et faits stables = solides ; **les chiffres exacts de frais et les politiques "VPN accepté ?" évoluent vite**. Aucune optimisation d'évitement KYC/AML (interdit, voir BACKLOG WON'T DO).
> **✅ Mise à jour 2026-06-23** : vérifications live effectuées (sources web) — voir **§9** (corrections majeures : Paddle VPN-restreint, 2Checkout = lead, Cryptomus à éviter, Coinbase Commerce hors-jeu, QIWI mort, blocage entité XOF, éco Telegram Stars).

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
| **Paddle** | MoR | ⚠️ **VPN = "Restricted Category" explicite** (AUP item 19 → très dur) — *vérifié §9* | mondial, payout USD/EUR virement | REST + webhooks + subs | ≈ 5 % + 0,50 $ |
| **FastSpring** | MoR | Digital goods, historiquement souple `[vérifier VPN]` | mondial, payout multi-devise | REST + webhooks + subs | ≈ 5–8 % `[vérifier]` |
| **Lemon Squeezy** | MoR | **Racheté par Stripe** → risque de durcissement policy VPN `[vérifier]` | mondial, payout via Wise/Stripe | REST + webhooks + subs | ≈ 5 % + 0,50 $ |
| **2Checkout / Verifone** | MoR/PSP | ✅ **VPN PAS dans la liste interdite** (AUP lue) — *vérifié §9* | très large, multi-devise | REST + webhooks + subs | **3,5 %+0,35 $** (2Sell) / **4,5 %+0,45 $** (2Subscribe) |
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
| **Cryptomus** | Custodial | partiel | USDT multi-chaînes | REST + webhooks | 0,4–2 % | ⚠️ **À ÉVITER** : pénalité AML ~177 M CAD (FINTRAC oct. 2025), lié RU/Garantex — *vérifié §9* |
| **Plisio** | Custodial | partiel | large | REST + webhooks | ≈ 0,5 % | léger |
| **Coinbase Commerce** | custodial | via Coinbase | majors + USDC (pas de Lightning) | REST + webhooks | ≈ 1 % | ⚠️ **fermé hors US/Singapour depuis 31/03/2026** — *vérifié §9* |
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
1. **MoR de tête = 2Checkout/Verifone** (VPN non interdit + tarif confirmés, §9) → postuler ; **PayPro Global / FastSpring** en devis parallèle ; **éviter Paddle** (VPN = catégorie restreinte). **Obtenir par écrit l'acceptation "VPN" avant tout dev** *(verrou n°1)*.
2. **Monter un BTCPay Server** (self-host, 0 % frais, API Greenfield) + brancher **NOWPayments ou CoinGate** pour l'auto-conversion fiat et l'UX. Prioriser **USDT TRC20**.
3. **Conserver SwimPay** pour RU/CIS (ne pas chercher un PSP RU direct).
4. **Évaluer l'économie de Telegram Stars** (part prélevée + store) avant de l'ajouter ; sinon laisser.
5. **XOF** : tester **CinetPay**/**PayDunya** uniquement si l'entité/payout local est réaliste ; sinon s'appuyer sur le MoR pour les cartes.

## 9. Vérifié en live (2026-06-23, sources web)
Corrections/confirmations qui changent la reco :

**MoR :**
- **Paddle** — ⚠️ **VPN = "Restricted Category" EXPLICITE** (item 19 de l'AUP, à côté de spyware/captcha-solving) → onboarding VPN très dur, **pas un canal libre**. Tarif standard ≈ 5 %+0,50 $. [AUP Paddle](https://www.paddle.com/help/start/intro-to-paddle/what-am-i-not-allowed-to-sell-on-paddle) **(CONFIRMÉ)**
- **2Checkout / Verifone** — ✅ **VPN/proxy/anonymiseur PAS dans la liste interdite** (AUP lue), tarif **2Sell 3,5 %+0,35 $ / 2Subscribe 4,5 %+0,45 $**, sans frais mensuel → **meilleur candidat MoR** (acceptation + tarif confirmés). [AUP](https://www.2checkout.com/legal/acceptance/) **(CONFIRMÉ)**
- **Lemon Squeezy** — Stripe-owned ; migration vers **Stripe Managed Payments** (preview publique fév. 2026) ; 5 %+0,50 $ ; clause VPN non adressée (docs 403). **(PARTIEL)**
- **PayPro Global** & **FastSpring** — **aucun tarif public** (devis only) ; VPN non nommé dans leurs interdits. **(PARTIEL)**

**Crypto :**
- **NOWPayments** — 0,5 % (1 % avec conversion), **non-custodial par défaut**, payout fiat 2-5 j ~1,5-2,3 %, **sert les marchands RU** (se markete comme gateway Russie). [pricing](https://nowpayments.io/pricing) **(CONFIRMÉ)**
- **CoinGate** — ~1 %, settlement **EUR/SEPA**, KYC (entité EU MiCA), **EXCLUT la Russie** (+ USA). [supported-countries](https://coingate.com/supported-countries) **(CONFIRMÉ)**
- **Cryptomus** — ⚠️ **drapeau rouge** : custodial, KYC depuis fév. 2025, **lié à la Russie + pénalité AML ~177 M CAD de FINTRAC (oct. 2025)** (liens vers Garantex sanctionné). [TRM Labs](https://www.trmlabs.com/resources/blog/russia-linked-payment-processor-cryptomus-likely-behind-launch-of-parallel-service-heleket) **(CONFIRMÉ — à éviter)**
- **BTCPay** — ✅ self-host, **non-custodial, 0 % de frais** processeur (coût = VPS + réseau). **(CONFIRMÉ)**
- **Coinbase Commerce** — ⚠️ **fermé aux marchands hors US/Singapour depuis le 31 mars 2026** → probablement **disqualifié** pour nous. 1 %, custodial, pas de Lightning. **(CONFIRMÉ)**
- **OpenNode** — ~1 %, Lightning, semi-custodial. **(CONFIRMÉ)**

**RU :** **QIWI = mort** — licence bancaire **révoquée le 21 fév. 2024** par la Banque de Russie (violations AML), en liquidation. [CBR](https://www.cbr.ru/eng/press/pr/?id=39708) **(CONFIRMÉ)**

**XOF :** **CinetPay / PayDunya / Flutterwave** supportent XOF + mobile money + API/webhooks, **mais tous exigent en pratique une entité en région UEMOA + compte de settlement local** → **blocage structurel** pour un marchand non-africain (aucun chemin "sans entité locale" documenté → à confirmer marchand par marchand). **Paystack ≠ XOF** (NGN/GHS/ZAR/KES, seul la Côte d'Ivoire en UEMOA). **Bizao = apparemment en faillite.** **Hub2** = infra, pas turnkey. Flutterwave : **VPN pas dans les interdits** (confirmé). Frais ≈ 3 % (tables exactes 403). **(CONFIRMÉ pour le blocage entité ; PARTIEL pour les frais)**

**Telegram Stars :** payout **0,013 $/Star** (~13 $/1000), **rétention 21 j**, retrait **via Fragment → TON** uniquement (pas de banque directe), **~30 % Apple/Google sur mobile** (~32 % all-in vers fiat ; ~6-16 % si achat desktop), **données = identité Telegram seule** (pas d'email → corrélation faible, **confirme la décision d'abandon de Tribute**). VPN non interdit mais zone de jugement. [Stars API](https://core.telegram.org/bots/payments-stars) **(CONFIRMÉ)**

### Impact sur la reco
- **MoR de tête = 2Checkout/Verifone** (VPN non interdit + tarif confirmés), pas Paddle (VPN restreint). PayPro Global/FastSpring = à demander en devis.
- **Crypto : BTCPay + NOWPayments** (NOWPayments sert la RU, non-custodial) ; **CoinGate** seulement si cible hors-RU ; **Cryptomus à éviter** (AML) ; **Coinbase Commerce hors-jeu** si non US/SG.
- **XOF différé** sauf si entité régionale réaliste → s'appuyer sur le MoR pour les cartes.
- **Telegram Stars** : ~32 % de perte mobile + corrélation faible → marginal, non prioritaire.

### Reste UNVERIFIED (contact direct requis)
Tarifs exacts PayPro Global / FastSpring / CinetPay / PayDunya (non publics ou 403) · clause VPN exacte de Lemon Squeezy (403) · approbation réelle d'un VPN anti-censure par Paddle/FastSpring/PayPro en pratique (le texte ne tranche pas → dépend de la candidature) · existence d'un onboarding XOF sans entité locale.

---

## 10. Action — 2Checkout/Verifone (à contacter) + NOWPayments (intégration)

### 2Checkout/Verifone — vérifié live 2026-06-23
**Moyens de paiement acceptés (côté client)** : **45+ moyens, 100 devises, 190+ pays**.
- **Cartes (mondial)** : Visa, Visa Electron, Mastercard, Maestro, American Express, Discover, JCB.
- **Portefeuilles** : PayPal (sauf RU), Apple Pay, Google Pay.
- **Locaux (Europe)** : SEPA Direct Debit, iDEAL (+ autres selon pays).
- **Abonnements récurrents** : oui (renouvellement auto, upgrades, essais, relances).
- **Afrique de l'Ouest** : **cartes seulement** (pas de mobile money / pas de rails XOF locaux).
- **Russie/CIS** : **inutilisable** (pas de Mir, PayPal exclu, Visa/MC ne traitent plus les cartes RU) → **SwimPay reste** pour la RU.

**VPN accepté** : ✅ non listé dans les interdits + **étude de cas publique « Astrill VPN »** (ils servent déjà des VPN). Réserve : l'angle anti-censure peut attirer un examen d'underwriting → obtenir l'accord écrit avant de coder. [AUP](https://www.2checkout.com/legal/acceptance/) · [cas Astrill](https://www.2checkout.com/clients/astrill-vpn-success-story.html)

**Tarifs (sans frais mensuel/setup ; surcharge cross-border +2 %)** :
- **2Sell 3,5 %+0,35 $** (cartes+PayPal) · **2Subscribe 4,5 %+0,45 $** (+ gestion abos) · **2Monetize 6 %+0,60 $** (MoR complet : TVA/taxes mondiales gérées). → pour un VPN digital vendu mondialement, viser **2Monetize** (taxe gérée) ou **2Subscribe**.

**📞 CONTACTER MAINTENANT** :
- **Inscription self-serve (gratuit)** : https://www.2checkout.com/pricing/ → « Sign up for free » → activation dans le panel.
- **Parler aux ventes (onboard rapide/volume)** : formulaire https://www.2checkout.com/pricing/enterprise/
- **Support / emails (docs)** : https://www.2checkout.com/merchant-support/ · `info@2checkout.com` · `compliance@2checkout.com`
- **À préparer (KYB)** : type d'activité (**individuel/auto-entrepreneur accepté**), enregistrement société (si société), n° fiscal (TIN), **passeport/CNI** du signataire (+ associés ≥10 %). Validation = underwriting (délai non publié, ~jours à 2 sem. selon avis tiers).

### ⚠️ 11. RÉALITÉ MARCHAND CÔTE D'IVOIRE (change la reco — vérifié live 2026-06-23)
L'opérateur ([[operator.ts]] = Johane Arthur Oyaraht) est **établi en Côte d'Ivoire**. Ça change tout :
- ❌ **2Checkout/Verifone REFUSE la CI** : elle est sur leur **liste de pays marchands restreints** (avec Sénégal, Mali, Bénin, Ghana… presque toute l'UEMOA). **Ne pas postuler.** [restricted-countries](https://docs.2checkout.com/) **(CONFIRMÉ)**
- ❌ **Payoneer et Wise ne couvrent pas la CI** → les rails de payout des MoR cartes ne t'atteignent pas. [Payoneer pays](https://supportedcountries.com/payoneer/) · [Wise](https://wise.com/help/articles/2813542/) **(CONFIRMÉ)**
- ⚠️ Cartes monde via MoR : **Paddle** ne bloque PAS la CI (mais Paddle restreint les VPN — §9) ; **FastSpring/PayPro Global** = CI non confirmé. Tous **conditionnés à un virement vers une banque ivoirienne** → à confirmer en direct, incertain.
- ✅ **Le retournement positif** : comme tu es **établi en CI (UEMOA)**, le blocage « entité locale » du §6 **tombe pour TOI** → tu peux onboarder **CinetPay ou PayDunya** et encaisser **Mobile Money (Orange/MTN/Moov/Wave) + cartes en XOF**, payé sur un compte local. C'était bloqué pour un marchand étranger — **pas pour toi**.
- ✅ **Crypto = rail mondial fiable, indépendant du pays** : BTCPay (self-host) + NOWPayments → l'argent va sur ton wallet, peu importe la CI. **(CONFIRMÉ)**

**Reco RÉVISÉE pour un opérateur CI (multi-rail) :**
1. **Afrique/XOF** → **CinetPay** ou **PayDunya** (Mobile Money + cartes XOF) — tu qualifies maintenant. *Rail local principal.*
2. **Mondial / audience anti-censure** → **crypto** (BTCPay + NOWPayments, USDT-TRC20). *Rail global fiable.*
3. **RU/CIS** → **SwimPay** (inchangé).
4. **Cartes reste-du-monde (Europe/Amériques)** = le trou : 2Checkout exclu, MoR↦payout-CI incertain. À explorer en direct (FastSpring/PayPro « acceptez-vous un marchand CI + virement banque ivoirienne ? ») ; sinon la crypto couvre les clients privacy mondiaux.

> Les pages légales (Terms/Privacy/Refund/Contact, opérateur CI) restent utiles : **CinetPay/PayDunya et les processeurs sérieux exigent aussi un site avec ces pages.**

### NOWPayments — comment ça marche (vérifié live)
**Flux** : ton backend crée un paiement/facture → NOWPayments génère **une adresse crypto unique + le montant** → le client paie → NOWPayments **surveille la blockchain** → t'envoie un **webhook (IPN)** à chaque changement de statut → quand statut = **`finished`**, tu débloques l'abonnement.
- **2 modes** : **facture hébergée** (`POST /v1/invoice` → tu rediriges vers `invoice_url`, **le plus simple**) ou **API paiement** (`POST /v1/payment`, UI maison).
- **API REST** : base `https://api.nowpayments.io/v1/`, auth par header **`x-api-key`**, **sandbox** dispo. Endpoints clés : `/payment`, `/invoice`, `/payment/{id}`, `/currencies`, `/estimate`, `/min-amount`.
- **✅ Webhooks (IPN) = OUI** : POST JSON vers ta callback URL, header **`x-nowpayments-sig`** à vérifier (**HMAC-SHA512**, clés JSON triées récursivement + `JSON.stringify`, avec l'**IPN secret**). Statuts : `waiting → confirming → confirmed → sending → finished` (+ `partially_paid`/`failed`/`expired`). **Débloquer uniquement sur `finished`**, handler **idempotent** (clé = `payment_id`/`order_id`).
- **Pratique** : non-custodial par défaut ; coins 300+ dont **USDT-TRC20** (privilégier pour petits montants/frais bas), BTC, Lightning ; frais **0,5 %** (1 % avec conversion) ; **min par paire** (vérifier `/min-amount` — les plans à 3,49 $ peuvent être sous le min BTC) ; crypto-only = pas de KYC, fiat = KYC ; **sert la RU** (mais prudence sanctions sur le payout fiat → confirmer avec eux).
- **Récurrent** : existe mais **par lien email** (pas de prélèvement auto comme une carte) → pour un renouvellement in-app, faire **un paiement ponctuel par cycle**.

Sources : [API](https://nowpayments.zendesk.com/hc/en-us/articles/21345824322717-API-and-endpoint-description) · [IPN setup](https://nowpayments.zendesk.com/hc/en-us/articles/21395546303389-IPN-and-how-to-setup) · [pricing](https://nowpayments.io/pricing).
