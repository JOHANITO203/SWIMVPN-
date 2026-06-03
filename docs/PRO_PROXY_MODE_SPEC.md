# SWIMVPN+ — Spec « Colle & Nage » · Mode Pro (BYO proxy résidentiel)

> Spec produit/UX (2026-06-03). Objectif : permettre à un user Android de **coller un proxy
> résidentiel acheté ailleurs** et l'utiliser system-wide, **orchestré par l'IA**, sans jamais
> toucher au technique. Mode **Avancé cloisonné** — le 1-tap grand public reste intact.
> Validé par un besoin réel : « j'ai acheté un proxy résidentiel, aucune app Android propre ne
> savait le lire ».

---

## 0. Principe directeur — UX-first
1. **Un seul geste : coller.** L'user colle une chaîne ; l'IA fait *tout* le reste (parse, test,
   DNS, rotation). Zéro champ technique, zéro choix de protocole, zéro jargon.
2. **La palette technique disparaît.** SOCKS5/HTTP, outbound Xray, tun2socks, DNS/UDP : invisibles.
   L'user voit du langage humain (« Fonctionne · sortie 🇫🇷 · 142 ms »).
3. **Métaphore de marque — « entre les griffes ».** Le requin *avale* le proxy collé et nage à
   travers. Le champ de collage est l'unique point d'entrée, mis en scène entre les griffes.
4. **Jamais par défaut.** Accessible via un coin « Avancé / Pro » discret. Le héros 1-tap
   (FULL_TUNNEL serveurs SWIMVPN) ne bouge pas.

---

## 1. Le parcours (le cœur de la feature)

| Étape | Ce que fait l'user | Ce que fait l'app (caché) |
|---|---|---|
| **0 · Entrée** | Tape « + Mon proxy » dans le coin Avancé | — |
| **1 · Coller** | Colle sa chaîne dans **un seul champ géant** (« Colle ton proxy ici 🦈 ») | nettoie espaces/retours/préfixes |
| **2 · Détection** | (rien) | auto-détecte le format → carte propre : « Proxy résidentiel · 🇫🇷 · SOCKS5 ✓ » |
| **3 · Test invisible** | (rien — ou tape « Tester ») | sonde « works here » de l'IA : atteignabilité + géo réelle + latence |
| **4 · Connecter** | Tape le **même bouton héros** | FULL_TUNNEL routé via le proxy collé |
| **5 · Vivant** | Voit un statut humain | rotation/health auto en arrière-plan |

**Règle d'or** : entre l'étape 1 et 4, l'user ne fait **aucun choix technique**. S'il veut, un lien
« Détails » discret révèle le protocole/host (repliable), jamais imposé.

---

## 2. Formats acceptés (auto-détectés — jamais demandés à l'user)
L'app reconnaît seule, au collage :
- `socks5://user:pass@host:port` · `socks5h://…` · `http://user:pass@host:port` · `https://…`
- `host:port:user:pass` (format résidentiel le + courant) et variantes d'ordre
- `host:port` (sans auth) · `user:pass@host:port`
- **Liste multi-lignes** → traitée comme un **pool** (rotation IA)
- collage « sale » (espaces, guillemets, `Proxy:` en préfixe, retours) → nettoyé automatiquement

Si le format est ambigu → **on tente, on teste, on tranche par le résultat** (jamais un dialogue
« choisis SOCKS ou HTTP »).

---

## 3. Ce qu'on cache (la palette technique → langage humain)

| Sous le capot | Ce que voit l'user |
|---|---|
| outbound Xray `proxySettings` chaîné, tun2socks, FULL_TUNNEL | « Connecté · ton proxy » |
| SOCKS5 vs HTTP, auth, `socks5h` resolve | (rien — auto) |
| sonde d'atteignabilité + géo-IP + latence | « Fonctionne · sortie 🇫🇷 · 142 ms » |
| proxy TCP-only / pas d'UDP-associate | « DNS sécurisé automatiquement » (on route le DNS via le tunnel) |
| endpoint mort dans un pool | « On a basculé sur un proxy plus rapide » |
| échec auth | « Identifiants du proxy refusés — recolle-le » |

---

## 4. États & microcopy (les écrans)

- **Vide** : champ géant + ex. grisé `socks5://user:pass@host:port` · sous-titre : « Colle le proxy
  que tu as acheté. On s'occupe du reste. »
- **Détecté** : carte verte « Proxy résidentiel détecté » + badge géo + bouton **Tester & Connecter**.
- **Test en cours** : animation requin + « On vérifie que ça nage ici… » (la sonde « works here »).
- **OK** : « ✅ Ça marche — sortie 🇫🇷 · 142 ms » → bouton **Connecter** (le héros).
- **Live** : bandeau « Connecté via ton proxy · 🇫🇷 · 142 ms » + (si pool) « 1/12 actif, le + rapide ».
- **Pool** : « 12 proxies détectés — on garde le meilleur actif, rotation auto ». Pas de gestion manuelle.
- **Erreurs (traduites)** :
  - injoignable → « Ce proxy ne répond pas. Vérifie qu'il est actif chez ton fournisseur. »
  - auth KO → « Identifiants refusés. Recolle la ligne complète (host:port:user:pass). »
  - format illisible → « On n'a pas reconnu ce proxy. Colle la ligne brute de ton fournisseur. »
  - fuite DNS détectée → corrigé en silence, info douce : « DNS sécurisé. »

---

## 5. Rôle de l'IA (orchestration invisible) — la vraie valeur
Réutilise l'agent adaptatif déterministe existant :
- **Works-here probe** : le proxy atteint-il vraiment le net + la géo annoncée, *sur CE réseau* ?
- **Scoring** latence/stabilité par endpoint (× réseau Wi-Fi/mobile, comme les serveurs).
- **Rotation / évitement des morts** : sur un pool, garde le meilleur, bascule sans que l'user agisse.
- **Auto-fix DNS/UDP** : si le proxy ne porte pas l'UDP, route le DNS via le tunnel (anti-fuite).
- **Pas de gadget** : gérer un résidentiel flaky est pénible → l'IA *est* le différenciateur.

---

## 6. Architecture (le moteur derrière — bref)
- **Pas** le proxy localhost cassé (cf. `LOCAL_PROXY_ANALYSIS.md`). On part de **FULL_TUNNEL** (le tun
  qui marche) et on change l'**outbound** : Xray `proxySettings` chaîne vers le proxy collé.
  Flux : `device → tun → tun2socks → Xray → [proxy résidentiel user] → internet`.
- Du point de vue OS : **un seul VPN** (rien de spécial). Per-app routing = évolution V3.
- Réutilise le pipeline de parsing de config existant + la sonde santé existante.

---

## 7. Sécurité
- Creds du proxy **chiffrés au repos** (même traitement que les raw VPN configs), **jamais loggés**.
- **Restent locaux** : pas envoyés au backend (le proxy est la propriété de l'user).
- Effaçables en 1 tap (« Oublier ce proxy »).

---

## 8. Scope & phasage
- **POC (V1)** : 1 proxy collé → auto-détection → test « works here » → Connecter (FULL_TUNNEL+outbound).
- **V2** : pool multi-lignes + rotation/scoring IA + auto-fix DNS.
- **V3** : per-app routing, per-site/géo routing, sauvegarde de plusieurs proxies.

## 9. Risques (rappel)
- **Store** : toute feature « proxy » peut être flaggée → cloisonner en Avancé, copy sobre, BYO
  (pas de revente d'IP) = défendable.
- **Proxies TCP-only / no-UDP** : géré par l'auto-fix DNS + works-here probe.
- **Support** : parsing robuste + erreurs en langage humain = moins de tickets.
</content>
