# Créer une société à l'étranger pour accéder aux paiements carte — opérateur Côte d'Ivoire

> Live-sourcé 2026-06-23. **Légitime** (pas d'évitement KYC/fiscalité). ⚠️ **Aucune société « sans KYC » légale n'existe** — le passeport est toujours requis ; le but ici = une **juridiction qui accepte les non-résidents/Africains** + l'accès aux processeurs. **Ceci n'est pas un conseil juridique/fiscal — consulter un conseiller fiscal ivoirien avant de t'appuyer dessus.**

## Contexte
La CI étant exclue par 2Checkout (cf. comparatif §11), une **société dans une juridiction supportée** rouvre l'accès aux processeurs carte (Stripe / 2Checkout / Paddle). Comparaison des options remote.

## Comparatif (vérifié)
| Critère | **UK Ltd** ✅ reco | **US LLC** | **Estonia OÜ** | **Géorgie (pays)** |
|---|---|---|---|---|
| Non-résident africain OK ? | **Oui, 100 % remote** (vérif ID via agent ACSP + passeport biométrique) | Oui, remote, **pas de SSN** | Oui MAIS **déplacement physique** (empreintes) — point de retrait le + proche : **Le Caire / Johannesburg** | Oui, remote/notarié |
| Coût ouverture | ~**£300-400** tout compris | New Mexico ~**$50** / Wyoming **$100** (+ agent ~$100/an) | ~**€400-700** | bas |
| Coût annuel | ~£500-1 500 (compta + adresse) | NM **0** / WY ~$60 / DE $300 + agent | élevé (**contact person obligatoire** + compta) | bas |
| Capital min | **£1** | **0** | **0** (depuis 2023) | bas |
| Délai | **1-3 jours** | EIN par fax ~quelques jours | **6-12 semaines** | ~24 h |
| Banque (non-résident CI) | **Wise + Payoneer** ✅ | **Payoneer** ✅ ; Mercury = **risque élevé/refus** pour résident CI ; Brex = exige fondateur US ❌ | Wise + Payoneer | Bank of Georgia/TBC (due diligence) |
| **Stripe / 2Checkout / Paddle** | **Les 3 ✅** | **Les 3 ✅** (Stripe = la raison n°1) | Les 3 ✅ | ❌ **Stripe/Paddle NE supportent PAS la Géorgie** → inutile pour ça |
| Impôt société | 19 % (≤£50k) / 25 % | en général **0 impôt US** si pas de revenu US (ECI), MAIS **déclaration 5472+1120 obligatoire** même à 0 (pénalité $25k si oubli) | **0 % sur bénéfice réinvesti**, taxé à la distribution (24 % en 2026) | bas |

## Reco : **UK Ltd** (le plus simple) → débloque 2Checkout + VPN
La chaîne complète, cohérente pour un opérateur CI :
1. **Créer une UK Ltd** (remote, ~£300-400, 1-3 j, agent de formation + passeport).
2. → **2Checkout/Verifone 2Monetize** : le UK est supporté **et le VPN est accepté** (Astrill VPN = leur client, cf. §9). MoR = ils gèrent la TVA mondiale.
3. → **Payout via Payoneer → ta banque ivoirienne** (FCFA, ~1,5 % + FX, 2-5 j) — **Payoneer dessert la CI** (Ecobank CI, SGBCI, BICICI, NSIA, BNI, SIB…).
- Alternative Stripe-first : **US LLC** (New Mexico ~$50) si tu veux Stripe en direct, mais **banque plus dure** pour un résident CI (compter sur Payoneer ; Mercury risque de refuser).
- **Estonia** : seulement si le 0 % sur bénéfice réinvesti t'intéresse ET que tu peux voyager (Le Caire/Joburg) — sinon le déplacement tue l'intérêt.
- **Géorgie** : ❌ pour les processeurs (Stripe/Paddle absents).

## « Récupérer l'argent chez toi » (corrigé)
- ✅ **Payoneer DESSERT la Côte d'Ivoire** : reçois en USD/EUR, retire vers une banque ivoirienne en FCFA (~1,5 % + FX, min ~$50). **C'est le rail fiable, pour TOUTES ces structures.**
- ⚠️ **Wise** : sert à *envoyer* vers une banque CI, mais **pas de compte Wise personnel pour un résident CI** + carte Wise inutilisable en CI.
- Autres : carte débit business (Mercury/Relay), virement SWIFT vers banque CI, off-ramp crypto (USDT→P2P/mobile money — plus risqué côté compta).

## ⚠️ Caveats honnêtes (ne pas sauter)
1. **Tu restes redevable de l'impôt en Côte d'Ivoire.** Un résident fiscal CI est en principe imposable sur son revenu mondial. La société étrangère **résout l'accès aux paiements, pas ton impôt local.** → **conseiller fiscal CI obligatoire.**
2. **Substance / cohérence** : banques et processeurs vérifient où l'entreprise est réellement opérée + la résidence du propriétaire. Une coquille « papier » incohérente (ID CI / adresse / banque qui ne collent pas) se fait **geler**. Rester honnête.
3. **Gérer une UK Ltd depuis la CI** peut créer une **résidence fiscale/PE en CI** (règles de management & control) → dual-résidence. À cadrer avec le conseiller.
4. **VPN = catégorie high-risk** : pays supporté ≠ approbation garantie ; viser un **MoR** (2Checkout — VPN accepté ; Paddle restreint le VPN, cf. §9).
5. **US LLC** : la déclaration **5472 + 1120 pro-forma est obligatoire chaque année même à 0 $** (pénalité $25 000).

## À vérifier en direct avant de t'engager
Présence exacte de la CI sur la liste interdite de **Mercury** (page 403) · acceptation du **VPN par Paddle** · traitement fiscal **CI** d'un revenu de société étrangère (conseiller local) · éligibilité **Revolut Business** pour un résident CI.

## Verdict
**UK Ltd = le meilleur compromis** (remote, rapide, pas cher, banque Wise/Payoneer, débloque 2Checkout qui accepte le VPN, payout Payoneer→banque CI). **US LLC** = bon pour Stripe mais banque plus dure. **Estonia** = seulement si voyage + tax-deferral. **Géorgie** = non (pas de Stripe/Paddle). **Dans tous les cas : conseiller fiscal CI avant de lancer.**

---

## ⭐ Conclusion pratique (vérifié live 2026-06-24) — SaaS encaissable DEPUIS la CI, sans société
Pour des **SaaS génériques** (pas le VPN), **pas besoin de créer une entité à l'étranger pour démarrer** :
- **Paddle (MoR) = OUI pour un vendeur ivoirien** *(conditionnel mais effectif)* : la **CI n'est PAS** sur la liste d'exclusion Paddle (le Mali oui, pas la CI) ; **individu/auto-entrepreneur accepté** (KYB sauté, juste KYC identité via Sumsub = pièce + justif d'adresse) ; **payout via Payoneer → banque CI (FCFA)**, min $100, payé le 15. Conditions : **site produit en ligne** au domain-review + passer le KYC. SaaS générique = OK (le VPN, lui, est en catégorie restreinte). [Paddle pays supportés](https://www.paddle.com/help/start/intro-to-paddle/which-countries-are-supported-by-paddle) · [vérification](https://www.paddle.com/help/start/account-verification/what-is-account-verification)
- **Le levier décisif = le rail de payout.** ⚠️ **PayPal est SEND-ONLY en Côte d'Ivoire** (impossible de *recevoir/retirer*) → tout MoR qui ne paie la CI que par PayPal est **mort**. Seuls **Payoneer** (confirmé) et **virement** atteignent la CI. [PayPal CI send-only](https://www.france24.com/en/africa/20220916-online-payments-a-headache-for-ivory-coast-s-e-merchants)
- **Plan B (si Paddle refuse)** : **FastSpring** (CI non exclue, payout virement) ou **PayPro Global** (payout Payoneer $2). **À ÉVITER** : Lemon Squeezy, Polar, Creem, Gumroad-PayPal — tous **Stripe/PayPal-dépendants** (Stripe ne couvre pas la CI ; PayPal send-only). Creem exclut explicitement la CI.

**→ Le plan immédiat : Paddle depuis la CI + Payoneer.** L'incorporation (UK Ltd / Géorgie) devient **optionnelle**, pour de l'optimisation fiscale plus tard — et la Géorgie 1 % ne paie que si tu **t'installes** là-bas (sinon CI t'impose sur le revenu mondial).
