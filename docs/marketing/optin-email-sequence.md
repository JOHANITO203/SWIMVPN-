# Séquence email opt-in — SWIMVPN (campagne marketing)

> Livrable du loop autonome. Cible : abonnés **CONFIRMED** uniquement (capture double opt-in déjà en
> prod — `Subscriber`, `POST /api/v1/newsletter/subscribe` → confirmation Resend). Envoi via **Resend**
> (FROM `support@swimvpn.pro`, sous-domaine vérifié). **Déclenchement = geste user/plateforme**, pas autonome.
>
> Règles de conformité, non négociables :
> - N'envoyer qu'aux **CONFIRMED** (consentement vérifié).
> - **Lien de désinscription dans CHAQUE email** (`{{unsubscribe_url}}` — endpoint à câbler, cf. backlog).
> - Adresse postale / identité de l'expéditeur en pied (CAN-SPAM).
> - Pas d'achat/scraping de listes. Cadence raisonnable (≤ 1 email / 2-3 jours au lancement).
>
> Placeholders : `{{first_name|"there"}}`, `{{download_windows_url}}`, `{{download_android_url}}`,
> `{{offers_url}}`, `{{unsubscribe_url}}`. RU à ajouter (l'audience est FR/RU/EN) une fois FR/EN validés.

---

## E0 — Confirmation (déjà implémentée en code, transactionnel)
Référence : `EmailSenderService.sendOptInConfirmation`. Sujet FR « Confirme ton inscription à SWIMVPN ».
Ne pas dupliquer ici — c'est le double opt-in. La séquence ci-dessous démarre **après** confirmation.

## E1 — Bienvenue (J+0, immédiatement après confirmation)
**Objet** — FR : « Bienvenue. Voici comment reprendre le contrôle. » · EN : « Welcome. Here's how to take back control. »

FR :
> Salut {{first_name|"toi"}},
>
> Merci d'avoir confirmé — tu fais maintenant partie des gens qui refusent que leur connexion soit surveillée ou bloquée.
>
> SWIMVPN, en 1 minute :
> - **Anti-censure** : tunnel Xray/REALITY qui passe là où les VPN classiques sont bloqués.
> - **Sur tes appareils** : Windows ([télécharger]({{download_windows_url}})) et Android ([télécharger]({{download_android_url}})).
> - **Sans logs.** Paiement dans l'app (SwimPay : RUB/USD/XOF) ou crypto.
>
> Installe, connecte-toi, c'est actif en moins d'une minute. Une question ? Réponds simplement à ce mail.
>
> — L'équipe SWIMVPN
> [Se désinscrire]({{unsubscribe_url}})

EN :
> Hi {{first_name|"there"}},
>
> Thanks for confirming — you're now among the people who refuse to let their connection be watched or blocked.
>
> SWIMVPN in 1 minute:
> - **Anti-censorship**: an Xray/REALITY tunnel that gets through where classic VPNs are blocked.
> - **On your devices**: Windows ([download]({{download_windows_url}})) and Android ([download]({{download_android_url}})).
> - **No logs.** Pay in-app (SwimPay: RUB/USD/XOF) or crypto.
>
> Install, connect — you're live in under a minute. Questions? Just reply to this email.
>
> — The SWIMVPN team
> [Unsubscribe]({{unsubscribe_url}})

## E2 — Pourquoi Xray/REALITY (J+2, éducation)
**Objet** — FR : « Pourquoi ton ancien VPN se fait bloquer (et pas nous) » · EN : « Why your old VPN gets blocked (and we don't) »

FR (corps court) : les DPI repèrent les VPN classiques à leur signature TLS. **REALITY** fait passer le trafic pour une vraie connexion HTTPS vers un site légitime → indétectable par signature. Concrètement pour toi : ça marche dans les réseaux les plus filtrés. + Tu peux **importer tes propres configs** (VLESS, VMess, Trojan, Shadowsocks, Xray/V2Ray) si tu en as déjà.
CTA : [Choisir un serveur et tester]({{download_android_url}}). Pied : désinscription.

EN : DPI spots classic VPNs by their TLS signature. **REALITY** makes traffic look like a real HTTPS connection to a legitimate site → undetectable by signature. For you: it works on the most filtered networks. Plus you can **import your own configs** (VLESS, VMess, Trojan, Shadowsocks, Xray/V2Ray). CTA + unsubscribe.

## E3 — Confiance & no-logs (J+5)
**Objet** — FR : « Ce qu'on ne sait pas de toi » · EN : « What we don't know about you »

FR : Pas de logs d'activité. Pas de revente de données. Paiement **sans store** : directement dans l'app via SwimPay (RUB/USD/XOF) ou en crypto (Bitcoin, USDT) — même confort, plus de discrétion. (Court, honnête, pas de sur-promesse.) CTA : [Voir les offres]({{offers_url}}). Pied : désinscription.

EN : No activity logs. No data resale. Pay **without a store**: directly in-app via SwimPay (RUB/USD/XOF) or crypto (Bitcoin, USDT). CTA + unsubscribe.

## E4 — Conversion douce (J+9)
**Objet** — FR : « Prêt à passer en illimité ? » · EN : « Ready to go unlimited? »

FR : Rappel des plans (Basic / Premium / Platinum) + ce qu'ils débloquent (serveurs premium, durée). Ton calme, pas agressif : « si SWIMVPN t'a été utile, un abonnement nous aide à tenir les serveurs ». CTA unique : [Voir les offres]({{offers_url}}). Pied : désinscription.

EN : Plans recap (Basic / Premium / Platinum) + what they unlock. Calm tone. Single CTA: [See plans]({{offers_url}}). Unsubscribe.

---

## Notes d'exécution
- **Une seule CTA par email.** Texte court, mobile-first (l'audience est majoritairement mobile).
- **Cadence** : E1 J+0, E2 J+2, E3 J+5, E4 J+9 ; puis 1 broadcast / 2-3 semaines max.
- **Segmentation v2** : séparer ceux qui ont téléchargé (via un événement) de ceux qui n'ont pas bougé.
- **À câbler avant tout envoi** (cf. backlog) : endpoint de **désinscription** (`{{unsubscribe_url}}`) + identité expéditeur en pied. Sans ça, ne PAS lancer (conformité).
- **RU** : traduire E1-E4 une fois FR/EN validés (audience russophone importante).
