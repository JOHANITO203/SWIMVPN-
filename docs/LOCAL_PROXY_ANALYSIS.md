# SWIMVPN+ — Analyse LOCAL_PROXY : pourquoi il ne route pas

> Audit lecture seule (2026-06-03). Symptôme rapporté : en mode LOCAL_PROXY le VPN est « lent /
> ne marche pas ».

## Diagnostic (sourcé)
En LOCAL_PROXY, l'app monte un proxy **SOCKS + HTTP sur `127.0.0.1`** via Xray
(`socks-in` + `http-in`, `TunnelRuntimeAdapter.kt:347-348`, listen 127.0.0.1), puis
`startLocalProxy` (`SwimVpnService.kt:645-692`) démarre Xray et passe **RUNNING**.

**🔴 Problème : rien ne consomme ce proxy.**
- **Pas de tun** : aucun `VpnService.establish()` / `addRoute` dans `startLocalProxy`.
- **Pas de proxy système** : aucun `VpnService.Builder.setHttpProxy(...)` ni `ProxyInfo` dans le code.
- `policyForMode(LOCAL_PROXY)` ne configure que le **DNS/domainStrategy interne d'Xray**
  (`TunnelRuntimeAdapter.kt:504`), pas le routage du trafic du device.
- Seul consommateur de `127.0.0.1:socksPort` = la **sonde de santé interne** de l'app
  (`probeTrafficThroughProxy`, `SwimVpnService.kt:1114-1131`) → la sonde réussit → l'UI affiche
  « connecté », mais le trafic des autres apps **ne passe pas** par le proxy.

→ Le device n'est **pas tunnelé** (trafic en direct). Avec un proxy Wi-Fi manuel sur
`127.0.0.1:httpPort`, seul le HTTP de ce Wi-Fi passerait (partiel). Sinon : aucune protection.

## Pourquoi (contrainte Android)
On ne peut pas router le trafic système vers un proxy localhost **sans tun VpnService** (ou root) :
1. `VpnService.Builder.setHttpProxy(ProxyInfo("127.0.0.1", httpPort))` (API 29+) — exige **quand même
   un tun** + ne couvre **que HTTP/HTTPS**. Demi-VPN.
2. Un vrai tun + tun2socks → **c'est FULL_TUNNEL** (qui marche déjà).

→ LOCAL_PROXY tel qu'implémenté = proxy localhost **sans consommateur** = non-fonctionnel comme VPN.

## Options
- **A — Retirer LOCAL_PROXY** comme mode grand public ; garder FULL_TUNNEL. Corollaire : le fallback
  proxy (branche `feat/oem-hardening`) est un filet faible → préférer l'isolation crash tun2socks
  (`:vpn` process).
- **B — Réimplémenter** en « VPN HTTP-proxy » (VpnService minimal + `setHttpProxy` + routes) — reste
  un tun + HTTP-only, demi-mesure.
- **C — Mode « avancé / manuel »** (proxy Xray pour power-users), clairement étiqueté, jamais par défaut.

## Reframe technique (si « mode avancé »)
Pour qu'un mode avancé route VRAIMENT le device, il faut **le tun FULL_TUNNEL** ; ce qui change,
c'est l'**outbound** : Xray peut chaîner son outbound vers un **proxy upstream fourni par l'user**
(ex. proxy résidentiel) via `proxySettings`. Donc « LOCAL_PROXY avancé » = **FULL_TUNNEL routé via un
upstream proxy** (réutilise le tunnel qui marche), pas le proxy localhost cassé. L'agent adaptatif
peut health-check / faire tourner ces upstreams.

**Verdict** : le bon chemin grand public = FULL_TUNNEL durci. LOCAL_PROXY à retirer ou requalifier
« avancé » = FULL_TUNNEL + upstream proxy (voir réflexion proxies résidentiels).
</content>
