# Plan d'implementation - Camouflage adaptatif prouve-par-egress (TDD ordonne)

**REQUIRED SUB-SKILL: subagent-driven-development ou executing-plans** (executer chaque tache RED->GREEN->VERIFY->COMMIT en checkpoint, sans batcher).

## Goal
Garantir un tunnel dont la sortie est PROUVEE (egress reel observe via le SOCKS local, distinct de l'ISP, soutenu), supprimer structurellement le faux positif "connecte sans vrai tunnel", et faire apprendre l'agent UNIQUEMENT sur cette preuve. Interdire l'override d'empreinte uTLS sur REALITY (et XTLS) ou il casse le handshake. Rendre la fragmentation TLS le seul levier de shaping adaptatif, prouve-avant-d'etre-cru. Zero nouveau backend, zero PII, no-regression sur RUNNING / IPv4-only v1.0.9.

## Architecture
Un oracle d'egress-verite pur (`EgressTruthProbe`) + son wrapper I/O (`EgressTruthProber`) produit un verdict {PROVEN, FAILED, NEUTRAL}. `EgressCreditPolicy` (pur) mappe verdict x flag-validated -> credit d'apprentissage. `AutoHealPolicy` (pur) decide un repli AUTO borne. `EgressDurabilityGate` (pur) gouverne la PROMOTION d'un profil non-AUTO (latch fire-once apres survie soutenue). Cote selection: `selectBestCamouflageProfile`/`resolveCamouflageProfile`/cascade MORPH refusent un profil fp-override quand `securityMode == REALITY`. Le store passe v6->v7 avec purge selective des maps de marge polluees. Tout le coeur de decision est pur (Android-free, JUnit) ; le cablage MainViewModel/Service/Prefs est verifie device.

## Tech Stack
Android (Kotlin), coeur Xray (Go) + hev-socks5-tunnel, VLESS+REALITY. minSdk26 / targetSdk34. Tests: JUnit + mockwebserver UNIQUEMENT (build.gradle:448-449) -> aucun test ne peut instancier un `Context`, ni appeler `android.util.Log` (donc `AdaptiveEventLogger.log` reste non-teste; on teste ses field-builders PURS).

---

## DEUX signaux reseau distincts (NE PAS confler) - definition canonique

Le plan utilise DEUX signaux orthogonaux, chacun avec sa propre source :

- **`networkValidated`** = le flag OS `NetworkCapabilities.NET_CAPABILITY_VALIDATED` sur le reseau ACTIF (~"l'OS a confirme un acces Internet reel, pas un portail captif"). Source : `ConnectivityManager.getNetworkCapabilities(activeNetwork)`. Aujourd'hui ce flag n'est PAS lu dans MainViewModel (seul SwimVpnService.kt:1642 le lit). On AJOUTE un helper `networkValidated(): Boolean` a MainViewModel. **Consommateur : `EgressCreditPolicy.decide`** (spec 3.2 : un EGRESS_KO ne devient FAILURE que sur reseau VALIDATED ; derriere un captif il reste NEUTRE pour ne jamais blacklister AUTO).

- **`networkHealthy`** = signal de VIVACITE du LIEN tunnelise, calcule par `EgressTruthProber.probe` : `true` ssi au moins un echo SOCKS a ramene une IP non-vide OU (en cas d'echec/timeout des echos) le secours `generate_204` via SOCKS repond 200..399. **Consommateur : `AutoHealPolicy.decide` (A4)** : un heal vers AUTO ne se declenche que si le lien est vivant mais l'egress non prouve (vrai "connecte sans tunnel utile"). `networkHealthy` DOIT etre `false` sur captif / echos timeout sans secours 204 -> NO_OP (jamais de heal en boucle derriere un portail ou un serveur KO).

Ces deux signaux ne sont JAMAIS le meme booleen. `networkValidated` vient de l'OS (orthogonal au tunnel) ; `networkHealthy` vient de la sonde a travers le tunnel. Ils alimentent deux policies differentes.

---

Commande de test (RAM-contrainte) pour CHAQUE run :
`./gradlew :app:testDebugUnitTest --tests "<FQCN>" --rerun-tasks --max-workers=1 --no-parallel --no-daemon`
Ajouter `-Dorg.gradle.jvmargs=-Xmx2048m` SEULEMENT sur OOM. Pas d'assembleRelease dans la boucle. Suite complete apres chaque unite + apres merge A1-A5+B :
`./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.*" --tests "com.swimvpn.app.config.*" --rerun-tasks --max-workers=1 --no-parallel --no-daemon`

Faits verifies (code reel, cette session) :
- Faux oracle : `recordSuccess(...profileId=_activeCamouflageProfileId.value)` sur `RuntimeStatus.RUNNING` nu @ MainViewModel.kt:237-242.
- `probeTunnelHealthy` hardcode `127.0.0.1:10808` @ MainViewModel.kt:269 ; `DEFAULT_SOCKS_PORT=10808` PRIVATE @ TunnelRuntimeAdapter.kt:25 ; `RuntimePorts.socksPort` default=DEFAULT_SOCKS_PORT @ :45 ; **`RuntimePorts()` est TOUJOURS construit avec les defauts (:93) - le port SOCKS est invariablement 10808 dans toute l'app ; il n'existe AUCUN `runtime` object joignable depuis MainViewModel (le service detient le runtime, le VM envoie un Intent).** Donc le "vrai port" cote VM/prober EST `TunnelRuntimeAdapter.DEFAULT_SOCKS_PORT` ; on l'expose `internal` (single source of truth) au lieu de re-hardcoder 10808.
- `selectBestCamouflageProfile(score, networkType, profiles=fallbackOrder)` @ AdaptiveDecisionAgent.kt:560-577 (strict-greater, AUTO=DEFAULT en tete CamouflageProfile.kt:59,68-69) ; KDoc :549 dit encore "chrome" (FAUX -> corriger en AUTO).
- `securityMode` UNIQUEMENT sur `SwimVpnProfile` (ProtocolModels.kt:27, enum 5 valeurs :125-131 = NONE/TLS/REALITY/XTLS/UNKNOWN), PAS sur `ServerNode`. Parse via `ConfigParserEngine.parseConfig(input).profile?.securityMode` @ :29.
- **fp-on-the-wire** : REALITY -> fingerprint dans `realitySettings` (XrayStreamSettingsBuilder.kt:148-151) ; TLS ET XTLS -> fingerprint dans `tlsSettings` (:128-138), XTLS etant mappe sur security "tls" (`mapSecurity` :60-65, :44-45). Donc un fp-override est applique sur le fil pour REALITY, TLS et XTLS.
- `VERSION="v6"` @ ServerScoreStore.kt:87 ; ServerScoreCodecTest.kt:29 et :183 sont version-agnostiques (`ServerScoreCodec.VERSION`) ; **:274 a un litteral "v6"** (a corriger en `ServerScoreCodec.VERSION`).
- Classpath test = junit + mockwebserver SEULEMENT (build.gradle:448-449) -> pas de test instanciant `ServerScoreStore(context)` ni appelant `AdaptiveEventLogger.log` (android.util.Log).
- `restartActiveServerWithProfile(server, current, profileId: String)` @ MainViewModel.kt:314-331 (prend un id, ne re-resout PAS le profil) ; jamais appele avec "auto" jusqu'ici.
- tun IPv4-only @ SwimVpnService.kt:615-622 + `addDisallowedApplication(packageName)` :630 (baseline hors-tunnel saine) NE PAS TOUCHER.
- Cascade MORPH @ MainViewModel.kt:442-449 via `nextUntriedProfile` = vrai chemin de fuite REALITY (peut piocher chrome/firefox/...).
- Guard de cascade : `handleAdaptiveRuntimeFailure` ouvre par `if (handlingAdaptiveFailure) return; handlingAdaptiveFailure = true` (:333-335) et reset en `finally` (:451-452). `onAdaptiveRuntimeRunning` met `handlingAdaptiveFailure = false` (:263). **A4 reutilise ce meme guard.**
- `CamouflageProfileRepositoryTest.kt` EXISTE DEJA (config/) avec 7 tests (default-is-auto, all-presets, byId, fallback-order, auto-no-shaping, fragment-presets, frag-in-picker). **On AJOUTE les nouveaux tests REALITY-floor a CE fichier, on ne le re-cree PAS.**
- Geo-bypass : `bypassGeoEnabled: StateFlow<Boolean>` @ MainViewModel.kt:140 et `bypassGeoEntries: StateFlow<Set<String>>` @ :148 (memes prefs que le Service lit a :486-489). `XrayRoutingBuilder` defaut = `geoip:private` SEUL (sur : `api.swimvpn.pro` ne matche jamais le prive). Risque : un `directDomains` user matchant le host de preuve -> echo en DIRECT -> faux PROVEN. Garde en A4.
- `currentNetworkType()` @ MainViewModel.kt:1768 ; `NetworkType` = com.swimvpn.app.vpn.NetworkType ; `isBucketedNetwork` = WIFI/CELLULAR/ETHERNET (AdaptiveDecisionAgent.kt:191-194) ; `profileKey(net, null)` -> null (:197-198) => `recordSuccess(profileId=null)` laisse les maps profil INTACTES.

Ordre impose : A1 -> A2(s'appuie sur A1) -> A3 -> A4 -> A5 -> GATE recette device -> B -> T.

---

## PHASE A1 - EgressTruthProbe (coeur pur + wrapper I/O)

- [ ] RED 1 - Creer `android/app/src/test/java/com/swimvpn/app/adaptive/EgressTruthProbeTest.kt` :
```kotlin
package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class EgressTruthProbeTest {
    private fun ok(ip: String?, country: String? = null) =
        EgressTruthProbe.Observation(ok = true, exitIp = ip, country = country)

    @Test fun `sustained differential yields PROVEN with exit ip and country`() {
        val r = EgressTruthProbe.verdict("203.0.113.7", listOf(ok("85.93.1.121", "FI"), ok("85.93.1.121", "FI")))
        assertEquals(EgressTruthProbe.Verdict.PROVEN, r.verdict)
        assertEquals("85.93.1.121", r.exitIp); assertEquals("FI", r.country); assertEquals(false, r.degradedGeo)
    }
}
```
Compile-fail = RED. Run le test cible.

- [ ] GREEN 1 - Creer `android/app/src/main/java/com/swimvpn/app/adaptive/EgressTruthProbe.kt` (AUCUN import android.*) :
```kotlin
package com.swimvpn.app.adaptive

/**
 * Pure egress-truth verdict core (no android.* deps). Given the off-tunnel baseline ISP IP and a list
 * of SOCKS echo observations, decides whether traffic provably left via the server (distinct exit IP,
 * sustained), is the real "connected but no tunnel" failure, or is non-credible (NEUTRAL).
 */
object EgressTruthProbe {

    enum class Verdict { PROVEN, FAILED, NEUTRAL }

    data class Observation(
        val ok: Boolean,
        val exitIp: String? = null,
        val country: String? = null,
        val timedOut: Boolean = false,
    )

    data class Result(
        val verdict: Verdict,
        val exitIp: String? = null,
        val country: String? = null,
        val degradedGeo: Boolean = false,
    )

    fun verdict(baselineIp: String?, echoes: List<Observation>, minSustainedEchoes: Int = 2): Result {
        val successes = echoes.filter { it.ok && !it.exitIp.isNullOrBlank() }
        if (successes.size < minSustainedEchoes) return Result(Verdict.NEUTRAL)
        val consistent = successes.map { it.exitIp }.distinct().size == 1
        if (!consistent) return Result(Verdict.NEUTRAL)
        val first = successes.first()
        if (baselineIp.isNullOrBlank()) {
            return Result(Verdict.PROVEN, exitIp = first.exitIp, country = first.country, degradedGeo = true)
        }
        val allDifferential = successes.all { it.exitIp != baselineIp }
        val allBaseline = successes.all { it.exitIp == baselineIp }
        return when {
            allDifferential -> Result(Verdict.PROVEN, exitIp = first.exitIp, country = first.country)
            allBaseline -> Result(Verdict.FAILED)
            else -> Result(Verdict.NEUTRAL)
        }
    }
}
```
Run cible -> GREEN. COMMIT - `feat(adaptive): EgressTruthProbe pure verdict core (PROVEN on sustained differential)`

- [ ] RED 2..GREEN 5 - Ajouter au test (le code total couvre deja ; corriger `verdict()` a la racine si un cas echoue) :
```kotlin
    @Test fun `single differential echo is NEUTRAL not PROVEN`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7", listOf(ok("85.93.1.121"))).verdict) }
    @Test fun `inconsistent successful echoes are NEUTRAL`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7", listOf(ok("85.93.1.121"), ok("85.93.1.122"))).verdict) }
    @Test fun `egress equals baseline on healthy path is FAILED`() {
        assertEquals(EgressTruthProbe.Verdict.FAILED, EgressTruthProbe.verdict("203.0.113.7", listOf(ok("203.0.113.7"), ok("203.0.113.7"))).verdict) }
    @Test fun `cgnat or missing baseline degrades honestly and carries geo`() {
        val r = EgressTruthProbe.verdict(null, listOf(ok("100.64.0.5", "FI"), ok("100.64.0.5", "FI")))
        assertEquals(EgressTruthProbe.Verdict.PROVEN, r.verdict); assertEquals(true, r.degradedGeo); assertEquals("FI", r.country) }
    @Test fun `echo timeout is NEUTRAL anti spoof`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7",
            listOf(EgressTruthProbe.Observation(ok = false, timedOut = true), EgressTruthProbe.Observation(ok = false, timedOut = true))).verdict) }
    @Test fun `third party echo error is NEUTRAL not FAILED`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7",
            listOf(EgressTruthProbe.Observation(ok = false), EgressTruthProbe.Observation(ok = false))).verdict) }
    @Test fun `blank exit ip among ok echoes is not counted`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7", listOf(ok(""), ok(null))).verdict) }
    @Test fun `minSustained boundary is honored`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7",
            listOf(ok("85.93.1.121"), ok("85.93.1.121")), minSustainedEchoes = 3).verdict) }
    @Test fun `mixed differential and baseline equal is NEUTRAL`() {
        assertEquals(EgressTruthProbe.Verdict.NEUTRAL, EgressTruthProbe.verdict("203.0.113.7", listOf(ok("85.93.1.121"), ok("203.0.113.7"))).verdict) }
```
Run cible -> tout GREEN. COMMIT - `test(adaptive): EgressTruthProbe verdict matrix (sustain, FAILED, CGNAT, NEUTRAL, blank, boundary, mixed)`

- [ ] GREEN 6 (source de verite du port) - TunnelRuntimeAdapter.kt:25 : passer `private const val DEFAULT_SOCKS_PORT = 10808` a `internal const val DEFAULT_SOCKS_PORT = 10808` (valeur INCHANGEE ; `RuntimePorts` :45 et l'inbound :615 referencent toujours). Justification (verifiee) : `RuntimePorts()` est toujours construit avec ce defaut (:93) et il n'existe aucun `runtime` joignable depuis MainViewModel -> ce const EST le port SOCKS reel pour le prober. Re-run la suite config. Attendu : GREEN, port=10808 inchange.

- [ ] GREEN 7 (wrapper I/O, NON unit-teste) - Creer `android/app/src/main/java/com/swimvpn/app/data/network/EgressTruthProber.kt`. Reutilise le pattern SOCKS-GET de ResidentialProxyProbe.kt:45-62, SANS `Authenticator.setDefault`. NE referencer NULLE PART (A2/A4 le brancheront) :
```kotlin
package com.swimvpn.app.data.network

import com.google.gson.JsonParser
import com.swimvpn.app.adaptive.EgressTruthProbe
import com.swimvpn.app.config.TunnelRuntimeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL

object EgressTruthProber {
    // The echo host. MUST be routed THROUGH the tunnel for the proof to be valid (see A4 geo-bypass guard).
    const val ECHO_HOST = "api.swimvpn.pro"
    private const val CALLER_IP = "https://api.swimvpn.pro/api/v1/status/caller-ip"
    private const val GENERATE_204 = "https://www.gstatic.com/generate_204"
    private const val TIMEOUT_MS = 6000

    data class EgressOutcome(val result: EgressTruthProbe.Result, val networkHealthy: Boolean)

    suspend fun captureBaseline(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(CALLER_IP).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS; requestMethod = "GET" }
            try { JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() })
                .asJsonObject.get("ip")?.takeIf { !it.isJsonNull }?.asString } finally { conn.disconnect() }
        }.getOrNull()
    }

    private suspend fun echoViaSocks(socksPort: Int): EgressTruthProbe.Observation = withContext(Dispatchers.IO) {
        try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = (URL(CALLER_IP).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS; instanceFollowRedirects = false; requestMethod = "GET" }
            try {
                val json = JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonObject
                val ip = json.get("ip")?.takeIf { !it.isJsonNull }?.asString
                val country = json.get("country")?.takeIf { !it.isJsonNull }?.asString
                EgressTruthProbe.Observation(ok = !ip.isNullOrBlank(), exitIp = ip, country = country)
            } finally { conn.disconnect() }
        } catch (e: SocketTimeoutException) { EgressTruthProbe.Observation(ok = false, timedOut = true) }
        catch (e: Exception) { EgressTruthProbe.Observation(ok = false) }
    }

    private suspend fun fallback204ViaSocks(socksPort: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = (URL(GENERATE_204).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS; instanceFollowRedirects = false; requestMethod = "GET" }
            try { conn.responseCode in 200..399 } finally { conn.disconnect() }
        }.getOrDefault(false)
    }

    suspend fun probe(socksPort: Int = TunnelRuntimeAdapter.DEFAULT_SOCKS_PORT, minSustainedEchoes: Int = 2, interEchoDelayMs: Long = 11_000L): EgressOutcome {
        val baseline = captureBaseline()
        val echoes = ArrayList<EgressTruthProbe.Observation>(minSustainedEchoes)
        repeat(minSustainedEchoes) { i -> if (i > 0) delay(interEchoDelayMs); echoes += echoViaSocks(socksPort) }
        val result = EgressTruthProbe.verdict(baseline, echoes)
        val anyTimeout = echoes.any { it.timedOut }
        val anySuccess = echoes.any { it.ok && !it.exitIp.isNullOrBlank() }
        // networkHealthy = link-liveness signal (distinct from the OS networkValidated flag). The 204
        // fallback only runs when echoes failed/timed-out: it confirms the tunnel link is alive (not a
        // captive/dead server) but cannot supply a distinct exit IP, so the verdict STAYS NEUTRAL. It
        // exists purely to gate A4's heal: heal only when the link is alive yet egress is unproven.
        val liveLink = anySuccess || (anyTimeout && fallback204ViaSocks(socksPort))
        return EgressOutcome(result = result, networkHealthy = liveLink)
    }
}
```
Note coroutine : `probe` est suspend ; le `delay(11s)` entre echos donne la fenetre soutenue ~22s. Annulation : le caller (A4 `launchEgressProof`) lance la sonde dans `viewModelScope` -> annulee automatiquement a la fin de session/VM ; en plus, A4 re-verifie `VpnManager.state` et l'identite serveur APRES la sonde (scoping explicite).

- [ ] VERIFY + COMMIT - run EgressTruthProbeTest puis la suite adaptive+config. Attendu : BUILD SUCCESSFUL, 0 echec. COMMIT - `feat(egress): SOCKS echo prober + off-tunnel baseline wrapper (DEFAULT_SOCKS_PORT, 204 liveness fallback)`

---

## PHASE A2 - Recablage du credit (retire le faux oracle) - s'appuie sur A1

- [ ] RED 1 - Creer `android/app/src/test/java/com/swimvpn/app/adaptive/EgressCreditPolicyTest.kt` :
```kotlin
package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EgressCreditPolicyTest {
    private val P = EgressTruthProbe.Verdict.PROVEN
    private val F = EgressTruthProbe.Verdict.FAILED
    private val N = EgressTruthProbe.Verdict.NEUTRAL

    @Test fun `proven egress credits success regardless of validated flag`() {
        assertEquals(EgressCreditPolicy.Decision.CREDIT_SUCCESS, EgressCreditPolicy.decide(P, networkValidated = true))
        assertEquals(EgressCreditPolicy.Decision.CREDIT_SUCCESS, EgressCreditPolicy.decide(P, networkValidated = false)) }
    @Test fun `egress failed on validated network credits failure`() {
        assertEquals(EgressCreditPolicy.Decision.CREDIT_FAILURE, EgressCreditPolicy.decide(F, networkValidated = true))
        assertNotEquals(EgressCreditPolicy.Decision.CREDIT_SUCCESS, EgressCreditPolicy.decide(F, networkValidated = true)) }
    @Test fun `egress failed behind captive (not validated) is neutral`() {
        assertEquals(EgressCreditPolicy.Decision.NEUTRAL, EgressCreditPolicy.decide(F, networkValidated = false)) }
    @Test fun `neutral verdict is neutral on any network`() {
        assertEquals(EgressCreditPolicy.Decision.NEUTRAL, EgressCreditPolicy.decide(N, networkValidated = true))
        assertEquals(EgressCreditPolicy.Decision.NEUTRAL, EgressCreditPolicy.decide(N, networkValidated = false)) }
    @Test fun `only proven ever credits success (lock over the full matrix)`() {
        for (v in EgressTruthProbe.Verdict.values()) for (nv in listOf(true, false))
            if (EgressCreditPolicy.decide(v, nv) == EgressCreditPolicy.Decision.CREDIT_SUCCESS)
                assertEquals(EgressTruthProbe.Verdict.PROVEN, v) }
}
```
RED.

- [ ] GREEN 1 - Creer `android/app/src/main/java/com/swimvpn/app/adaptive/EgressCreditPolicy.kt` :
```kotlin
package com.swimvpn.app.adaptive

/**
 * Pure mapping from an egress verdict + the OS NET_CAPABILITY_VALIDATED flag (networkValidated) to a
 * learning-credit decision.
 *
 * SPEC AMENDMENT (locked): a SUSTAINED distinct-exit PROVEN credits SUCCESS regardless of the Android
 * NET_CAPABILITY_VALIDATED flag - the echo physically left via the server, so the observed fact overrides
 * an orthogonal OS flag. Only EGRESS-FAILED on a VALIDATED network is CREDIT_FAILURE (the real "connected
 * but no tunnel"); EGRESS-FAILED behind a captive (NOT validated) and every NEUTRAL verdict are NEUTRAL -
 * never blacklist AUTO behind a portal, never invent a success.
 *
 * NOTE: [networkValidated] is the OS flag, NOT the prober's link-liveness signal (EgressOutcome.networkHealthy).
 */
object EgressCreditPolicy {
    enum class Decision { CREDIT_SUCCESS, CREDIT_FAILURE, NEUTRAL }

    fun decide(verdict: EgressTruthProbe.Verdict, networkValidated: Boolean): Decision = when (verdict) {
        EgressTruthProbe.Verdict.PROVEN -> Decision.CREDIT_SUCCESS
        EgressTruthProbe.Verdict.FAILED -> if (networkValidated) Decision.CREDIT_FAILURE else Decision.NEUTRAL
        EgressTruthProbe.Verdict.NEUTRAL -> Decision.NEUTRAL
    }
}
```
Run cible -> GREEN. COMMIT - `feat(adaptive): EgressCreditPolicy - proven egress is the only success signal`

- [ ] GREEN 2 (recablage MainViewModel - retrait du faux oracle SANS sur-strip) - `onAdaptiveRuntimeRunning()` :234-264 : remplacer `recordSuccess(...profileId=_activeCamouflageProfileId.value)` (:237-242) par `recordSuccess(...profileId = null)` -> `profileKey(net, null)` retourne null (AdaptiveDecisionAgent.kt:197-198) => maps profil INTACTES ; le reste credite le fait "serveur joignable". RETIRER le bloc `BenchmarkCollector.record(success=true)` :243-250 (le credit per-profil migre vers `recordEgressCredit`). GARDER alertGate / log / resets. Ajouter en fin : si agent ON, declencher la preuve d'egress (`launchEgressProof()`, livre en A4) :
```kotlin
    private fun onAdaptiveRuntimeRunning() {
        val serverId = adaptiveActiveServerId ?: (_state.value as? AppState.Success)?.activeServer?.id
        if (serverId != null) {
            // Server-health credit only (profileId = null keeps the per-profile margin maps untouched;
            // those are credited ONLY by proven egress in recordEgressCredit).
            serverScoreStore.recordSuccess(serverId, networkType = currentNetworkType(), hourOfDay = currentHourOfDay(), profileId = null)
            alertedFailingServerIds.remove(serverId)
            AdaptiveEventLogger.log(
                event = if (adaptiveReconnectAttempt > 0) "reconnect_success" else "handshake_success",
                details = mapOf("serverId" to serverId, "attempt" to adaptiveReconnectAttempt))
        }
        adaptiveReconnectAttempt = 0
        handlingAdaptiveFailure = false
        if ((_state.value as? AppState.Success)?.agentEnabled == true) launchEgressProof()
    }
```

- [ ] GREEN 3 (helper `networkValidated` - BLOCKER 2, source OS) - Ajouter a MainViewModel un helper qui lit le flag OS sur le reseau ACTIF (miroir de SwimVpnService.kt:1642, mais pour le VM) :
```kotlin
    /** OS NET_CAPABILITY_VALIDATED on the active network (~"not a captive portal"). NOT the tunnel
     * link-liveness signal (that is EgressOutcome.networkHealthy). Defaults to false when unknown. */
    private fun networkValidated(): Boolean {
        val cm = app.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) { Log.w("MainViewModel", "Unable to read NET_CAPABILITY_VALIDATED", e); false }
    }
```
(`ConnectivityManager`/`NetworkCapabilities`/`Context` deja importes - voir currentNetworkType :1768-1786.)

- [ ] GREEN 4 (helper de credit egress) - Ajouter `recordEgressCredit` dans MainViewModel (garde serverId==null, miroir :235-236). Le credit per-profil passe par `EgressCreditPolicy.decide(verdict, networkValidated())`. Les builders `egressProvenFields`/`earlyDeathFields` sont declares des A2 (purs), figes par tests en B :
```kotlin
    private fun recordEgressCredit(serverId: String?, result: EgressTruthProbe.Result, networkValidated: Boolean) {
        if (serverId == null) return
        val profileId = _activeCamouflageProfileId.value
        val transport = (_state.value as? AppState.Success)?.activeServer?.protocol ?: "unknown"
        when (EgressCreditPolicy.decide(result.verdict, networkValidated)) {
            EgressCreditPolicy.Decision.CREDIT_SUCCESS -> {
                serverScoreStore.recordSuccess(serverId, networkType = currentNetworkType(), hourOfDay = currentHourOfDay(), profileId = profileId)
                BenchmarkCollector.record(SessionBenchmarkRecord(transport, profileId, currentNetworkType(), success = true))
                AdaptiveEventLogger.log("egress_proven", AdaptiveEventLogger.egressProvenFields(profileId, currentNetworkType(), result.country, null, null))
            }
            EgressCreditPolicy.Decision.CREDIT_FAILURE -> {
                serverScoreStore.recordFailure(serverId, System.currentTimeMillis(), networkType = currentNetworkType(), hourOfDay = currentHourOfDay(), profileId = profileId)
                BenchmarkCollector.record(SessionBenchmarkRecord(transport, profileId, currentNetworkType(), success = false))
                AdaptiveEventLogger.log("early_death", AdaptiveEventLogger.earlyDeathFields(profileId, currentNetworkType(), null))
            }
            EgressCreditPolicy.Decision.NEUTRAL -> Unit
        }
    }
```
Declarer aussi des A2, dans `AdaptiveEventLogger.kt`, les SIGNATURES des builders `egressProvenFields`/`earlyDeathFields` (corps complet ci-dessous, fige par tests en Phase B). Ces builders sont PURS (retournent `Map`), seul `log(...)` touche android.util.Log :
```kotlin
    // (in AdaptiveEventLogger, declared here in A2; field-contract frozen by tests in Phase B)
    fun egressProvenFields(profileId: String, networkType: com.swimvpn.app.vpn.NetworkType, country: String?, ageMs: Long?, streak: Int?): Map<String, Any?> = buildMap {
        put("profileId", profileId); put("network", networkType.name)
        country?.let { put("country", it) }; ageMs?.let { put("ageMs", it) }; streak?.let { put("streak", it) }
    }
    fun earlyDeathFields(profileId: String, networkType: com.swimvpn.app.vpn.NetworkType, ageMs: Long?): Map<String, Any?> = buildMap {
        put("profileId", profileId); put("network", networkType.name); ageMs?.let { put("ageMs", it) }
    }
```

- [ ] Compile-check (`./gradlew :app:compileDebugKotlin`) + VERIFY (suite adaptive+config) + COMMIT - `fix(adaptive): credit profile learning only on proven egress; keep server-health credit on RUNNING`

---

## PHASE A3 - Interdiction fp-override sur REALITY (selector + resolve + MORPH)

- [ ] RED 1 (repo helpers) - **AJOUTER** les tests REALITY-floor au fichier EXISTANT `android/app/src/test/java/com/swimvpn/app/config/CamouflageProfileRepositoryTest.kt` (NE PAS recreer le fichier ; il contient deja 7 tests). Ajouter les imports manquants `assertFalse`/`assertSame` ne sont pas requis ; ajouter `import org.junit.Assert.assertFalse`. Inserer ces methodes dans la classe existante :
```kotlin
    // --- REALITY fp-override floor (egress-proven plan) ---

    @Test fun `realitySafe keeps only fp-blank profiles with auto first`() {
        assertEquals(listOf("auto", "frag_light", "frag_aggressive"), CamouflageProfileRepository.realitySafe().map { it.id })
        assertEquals("auto", CamouflageProfileRepository.realitySafe().first().id)
    }

    @Test fun `overridesFingerprint true only for browser profiles`() {
        listOf(CamouflageProfileRepository.AUTO, CamouflageProfileRepository.FRAG_LIGHT, CamouflageProfileRepository.FRAG_AGGRESSIVE)
            .forEach { assertFalse(it.overridesFingerprint()) }
        listOf(CamouflageProfileRepository.CHROME, CamouflageProfileRepository.FIREFOX, CamouflageProfileRepository.SAFARI,
               CamouflageProfileRepository.IOS, CamouflageProfileRepository.RANDOMIZED).forEach { assertTrue(it.overridesFingerprint()) }
    }

    @Test fun `coerceForSecurity bans fp-override on REALITY and XTLS, identity elsewhere`() {
        // REALITY: fp on the wire via realitySettings -> ban overrides.
        assertEquals("auto", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.CHROME, SecurityMode.REALITY).id)
        assertEquals("frag_light", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.FRAG_LIGHT, SecurityMode.REALITY).id)
        // XTLS rides a TLS stream; fp on the wire via tlsSettings -> ban overrides too.
        assertEquals("auto", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.CHROME, SecurityMode.XTLS).id)
        // TLS / NONE / UNKNOWN: override stays (no provider-validated fp to clobber on REALITY-style links).
        assertEquals("chrome", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.CHROME, SecurityMode.TLS).id)
        assertEquals("chrome", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.CHROME, SecurityMode.NONE).id)
        assertEquals("chrome", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.CHROME, SecurityMode.UNKNOWN).id)
        // A non-override profile is always identity, on every mode.
        assertEquals("frag_light", CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.FRAG_LIGHT, SecurityMode.TLS).id)
    }

    @Test fun `securityModeOf returns unknown on null and garbage`() {
        assertEquals(SecurityMode.UNKNOWN, CamouflageProfileRepository.securityModeOf(null))
        assertEquals(SecurityMode.UNKNOWN, CamouflageProfileRepository.securityModeOf("not a config"))
    }
```
RED (les helpers n'existent pas encore).

- [ ] GREEN 1 - Dans `CamouflageProfile.kt` : ajouter l'extension `fun CamouflageProfile.overridesFingerprint(): Boolean = fingerprint.isNotBlank()` (au niveau fichier, sous la data class) ; et dans `object CamouflageProfileRepository` ajouter (import `ConfigParserEngine` deja dans le meme package `com.swimvpn.app.config`) :
```kotlin
    /** Profiles safe on a link whose fingerprint is fixed on the wire (REALITY / XTLS): AUTO + the
     * fragmentation presets (all fp-blank), AUTO first so it wins ties. */
    fun realitySafe(profiles: List<CamouflageProfile> = fallbackOrder): List<CamouflageProfile> =
        profiles.filter { !it.overridesFingerprint() }

    /**
     * Coerce a profile to a wire-safe one for [securityMode]. SecurityMode has 5 values; all 5 are
     * handled explicitly (no silent fallthrough):
     *  - REALITY: fingerprint lives in realitySettings (XrayStreamSettingsBuilder) -> overriding it
     *    breaks the validated handshake -> ban (rabat sur AUTO).
     *  - XTLS: rides a "tls" stream (mapSecurity TLS/XTLS -> "tls"); the fp is set in tlsSettings, on the
     *    wire over a provider-tuned path -> treat like REALITY, ban the override.
     *  - TLS / NONE / UNKNOWN: keep the chosen profile (no provider-fixed fp to clobber). UNKNOWN is the
     *    default for un-parseable configs and stays permissive (the manual picker still works).
     */
    fun coerceForSecurity(profile: CamouflageProfile, securityMode: SecurityMode): CamouflageProfile = when (securityMode) {
        SecurityMode.REALITY, SecurityMode.XTLS -> if (profile.overridesFingerprint()) DEFAULT else profile
        SecurityMode.TLS, SecurityMode.NONE, SecurityMode.UNKNOWN -> profile
    }

    /** Parse a single-node rawConfig to its SecurityMode; UNKNOWN on null/garbage/multi-node. */
    fun securityModeOf(rawConfig: String?): SecurityMode =
        rawConfig?.let { ConfigParserEngine.parseConfig(it).profile?.securityMode } ?: SecurityMode.UNKNOWN
```
Run cible -> GREEN. COMMIT - `feat(camouflage): realitySafe / overridesFingerprint / coerceForSecurity(5 modes) / securityModeOf (pure)`

- [ ] RED 2 (selector REALITY) - Etendre `CamouflageAdaptiveTest.kt` (ajouter dans la classe existante) :
```kotlin
    // --- REALITY fp-override floor in selectBestCamouflageProfile ---

    @Test fun `reality never selects an fp-override profile`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|chrome" to 9))
        val picked = AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY)
        assertTrue(picked.fingerprint.isBlank()); assertTrue(picked.id in setOf("auto", "frag_light", "frag_aggressive")) }
    @Test fun `reality falls back to auto when only an override has positive margin`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|chrome" to 9))
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY).id) }
    @Test fun `reality still selects a fragment with the best proven margin`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|frag_light" to 3))
        assertEquals("frag_light", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY).id) }
    @Test fun `non-reality keeps fp-override eligible`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|chrome" to 9))
        assertEquals("chrome", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.TLS).id) }
    @Test fun `default securityMode (UNKNOWN) keeps the legacy override-eligible behaviour`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|chrome" to 9))
        // No securityMode arg -> UNKNOWN default -> existing callers/tests stay green.
        assertEquals("chrome", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id) }
```
RED (param `securityMode` absent).

- [ ] GREEN 2 - `AdaptiveDecisionAgent.selectBestCamouflageProfile` :560-577 : ajouter `securityMode: SecurityMode = SecurityMode.UNKNOWN` (default => callers/tests existants verts), import `com.swimvpn.app.config.SecurityMode`, pre-filtre `realitySafe` quand REALITY/XTLS, et **corriger le KDoc :549 (le mot "chrome" -> "AUTO")** :
```kotlin
    // ... KDoc corrige : "...with no history it returns the default (AUTO) and a failing profile is..."
    fun selectBestCamouflageProfile(
        score: ServerQualityScore?,
        networkType: NetworkType,
        profiles: List<CamouflageProfile> = CamouflageProfileRepository.fallbackOrder,
        securityMode: SecurityMode = SecurityMode.UNKNOWN,
    ): CamouflageProfile {
        if (score == null || !isBucketedNetwork(networkType)) return CamouflageProfileRepository.DEFAULT
        // On REALITY/XTLS the wire fingerprint is fixed by the link; never let an fp-override win.
        val candidates = when (securityMode) {
            SecurityMode.REALITY, SecurityMode.XTLS -> CamouflageProfileRepository.realitySafe(profiles)
            else -> profiles
        }
        var best = CamouflageProfileRepository.DEFAULT
        var bestMargin = Int.MIN_VALUE
        for (profile in candidates) {
            val key = "${networkType.name}|${profile.id}"
            val margin = (score.profileSuccesses[key] ?: 0) - (score.profileFailures[key] ?: 0)
            if (margin > bestMargin) { bestMargin = margin; best = profile }
        }
        return best
    }
```
Run cible -> GREEN. COMMIT - `feat(adaptive): ban fp-override on REALITY/XTLS in selectBestCamouflageProfile (UNKNOWN default keeps callers)`

- [ ] RED 3 (verrou cascade MORPH, pur) - Etendre `CamouflageAdaptiveTest.kt` (GREEN une fois `coerceForSecurity` livre en GREEN 1) :
```kotlin
    @Test fun `every fp-override coerces to auto under reality`() {
        com.swimvpn.app.config.CamouflageProfileRepository.all().filter { it.fingerprint.isNotBlank() }.forEach { override ->
            val coerced = com.swimvpn.app.config.CamouflageProfileRepository.coerceForSecurity(override, com.swimvpn.app.config.SecurityMode.REALITY)
            assertTrue(coerced.fingerprint.isBlank()); assertEquals("auto", coerced.id) } }
```

- [ ] GREEN 3 (wiring MainViewModel - resolve + MORPH). **BLOCKER 3 - threading exact du securityMode :**
  - **(a) Ou parser** : la source du `securityMode` est le rawConfig SINGLE-NODE resolu, PAS `server.rawConfig` (qui peut etre une URL d'abonnement multi-node non parseable en un seul profil). Pour `toggleVpn`, utiliser `resolvedRuntimeConfig` (calcule a MainViewModel.kt:1646-1655, un seul noeud). Pour `restartActiveServerWithProfile`, le rawConfig resolu single-node est calcule a :316-319 (`resolved`).
  - **(b) Ou cacher pour ne pas re-parser** : `securityModeOf` parse a chaque appel. Pour eviter de re-parser a chaque cycle, on calcule le mode UNE fois par etablissement et on le memorise dans un champ `@Volatile private var activeSecurityMode: SecurityMode = SecurityMode.UNKNOWN` (a cote de `adaptiveActiveServerId` :163), pose dans `toggleVpn` apres resolution et dans `restartActiveServerWithProfile`. `resolveCamouflageProfile` et la cascade MORPH lisent ce champ (pas de re-parse). Le parse initial s'execute dans `viewModelScope.launch` (deja off-Main : toggleVpn :1642, restart est `suspend`).
  - **(c) Signature exacte** : `resolveCamouflageProfile(serverId: String?, agentEnabled: Boolean, securityMode: SecurityMode = SecurityMode.UNKNOWN)` (default => aucun appelant casse). `selectBestCamouflageProfile(..., securityMode = SecurityMode.UNKNOWN)` (deja livre GREEN 2).

  1. Champ : a cote de `adaptiveActiveServerId` :163 ajouter `@Volatile private var activeSecurityMode: SecurityMode = SecurityMode.UNKNOWN` (import `com.swimvpn.app.config.SecurityMode`).
  2. `resolveCamouflageProfile` :727-736 -> prend `securityMode`, coerce la branche manuelle, filtre la branche agent ; ecrit l'id EFFECTIF :
```kotlin
    private fun resolveCamouflageProfile(serverId: String?, agentEnabled: Boolean, securityMode: SecurityMode = SecurityMode.UNKNOWN): CamouflageProfile {
        val profile = if (!agentEnabled) {
            CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.byId(camouflageProfileId.value), securityMode)
        } else {
            val score = serverId?.let { serverScoreStore.loadScores()[it] }
            AdaptiveDecisionAgent.selectBestCamouflageProfile(score, currentNetworkType(), securityMode = securityMode)
        }
        _activeCamouflageProfileId.value = profile.id
        return profile
    }
```
  3. `toggleVpn` :1675 -> apres `resolvedRuntimeConfig` (deja calcule :1655), poser le mode puis resoudre :
```kotlin
                activeSecurityMode = CamouflageProfileRepository.securityModeOf(resolvedRuntimeConfig)
                val camouflage = resolveCamouflageProfile(
                    server.id,
                    (_state.value as? AppState.Success)?.agentEnabled ?: true,
                    securityMode = activeSecurityMode,
                )
```
  4. `restartActiveServerWithProfile` :314-331 -> apres le calcul de `resolved` (:319), poser `activeSecurityMode = CamouflageProfileRepository.securityModeOf(resolved)` (avant `startService`). Ainsi un restart (heal/morph/switch) reactualise le mode.
  5. SWITCH_SERVER :438 -> `resolveCamouflageProfile(targetServer.id, current.agentEnabled, activeSecurityMode)` (le mode aura ete reactualise par le restart suivant ; si l'on veut etre exact AVANT le restart on peut re-parser `targetServer.rawConfig` mais c'est potentiellement multi-node -> on s'en tient au champ, et `restartActiveServerWithProfile` re-coerce de toute facon via le mode pose en (4)). Note : `restartActiveServerWithProfile` ne re-resout PAS le profil (prend un id) ; la coercion s'applique donc en amont via `resolveCamouflageProfile`/le MORPH ci-dessous.
  6. Cascade MORPH :442-449 -> coercer le target AVANT `_activeCamouflageProfileId` et le restart (garder `incidentTriedProfiles` indexe par l'id BRUT pour ne pas re-piocher) :
```kotlin
                DecisionActionType.MORPH_PROFILE -> {
                    val rawTarget = action.targetProfileId ?: return
                    val target = CamouflageProfileRepository.coerceForSecurity(CamouflageProfileRepository.byId(rawTarget), activeSecurityMode).id
                    incidentTriedProfiles = incidentTriedProfiles + rawTarget // mark the RAW pick as tried (cascade progresses)
                    _activeCamouflageProfileId.value = target
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.adaptive_optimizing)))
                    delay(action.delayMs)
                    restartActiveServerWithProfile(activeServer, current, target)
                }
```

- [ ] VERIFY (suite adaptive+config ; `planAfterFailure`/MORPH existants :135-187 inchanges, defaults UNKNOWN => verts) + COMMIT - `fix(camouflage): coerce fp-override -> AUTO on REALITY/XTLS in resolve and morph cascade (cached securityMode)`

---

## PHASE A4 - Auto-guerison bornee (mutex-guardee) + garde geo-bypass

- [ ] RED - Creer `android/app/src/test/java/com/swimvpn/app/adaptive/AutoHealPolicyTest.kt` :
```kotlin
package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoHealPolicyTest {
    @Test fun `non-auto fails sustained egress on healthy net heals to auto`() {
        assertEquals(AutoHealPolicy.Decision.HEAL_TO_AUTO, AutoHealPolicy.decide("frag_light", egressProven = false, networkHealthy = true, autoHealAttempts = 0, maxAttempts = 1)) }
    @Test fun `auto never heals`() {
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("auto", egressProven = false, networkHealthy = true, autoHealAttempts = 0, maxAttempts = 1)) }
    @Test fun `proven egress is no-op`() {
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("chrome", egressProven = true, networkHealthy = true, autoHealAttempts = 0, maxAttempts = 1)) }
    @Test fun `unhealthy network is no-op`() {
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("frag_light", egressProven = false, networkHealthy = false, autoHealAttempts = 0, maxAttempts = 1)) }
    @Test fun `cap reached is no-op`() {
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("frag_aggressive", egressProven = false, networkHealthy = true, autoHealAttempts = 1, maxAttempts = 1)) }
    @Test fun `single heal then capped`() {
        assertEquals(AutoHealPolicy.Decision.HEAL_TO_AUTO, AutoHealPolicy.decide("frag_light", false, true, 0, 1))
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("frag_light", false, true, 1, 1)) }
    @Test fun `blank or unknown profile id is treated as auto and never heals`() {
        assertEquals(AutoHealPolicy.Decision.NO_OP, AutoHealPolicy.decide("", false, true, 0, 1)) }
}
```
RED.

- [ ] GREEN - Creer `android/app/src/main/java/com/swimvpn/app/adaptive/AutoHealPolicy.kt` :
```kotlin
package com.swimvpn.app.adaptive

import com.swimvpn.app.config.CamouflageProfileRepository

/**
 * Pure decision: should a non-AUTO profile that FAILED sustained egress on a HEALTHY tunnel link be
 * healed by restarting the same server in AUTO? Capped to avoid looping with the sentinel cascade.
 * AUTO is the fixed point (never healed). [networkHealthy] is the prober's link-liveness signal
 * (EgressOutcome.networkHealthy) - captive/echo-timeout/server-KO => false => NO_OP. Blank/unknown id is
 * treated as AUTO-equivalent (never heal a garbled id).
 */
object AutoHealPolicy {
    enum class Decision { HEAL_TO_AUTO, NO_OP }

    fun decide(activeProfileId: String, egressProven: Boolean, networkHealthy: Boolean, autoHealAttempts: Int, maxAttempts: Int): Decision {
        val isAuto = activeProfileId.isBlank() || activeProfileId == CamouflageProfileRepository.AUTO.id
        val heal = !isAuto && !egressProven && networkHealthy && autoHealAttempts < maxAttempts
        return if (heal) Decision.HEAL_TO_AUTO else Decision.NO_OP
    }
}
```
Run cible -> GREEN. COMMIT - `feat(adaptive): AutoHealPolicy - bounded heal to AUTO on proven egress failure`

- [ ] CABLAGE MainViewModel (non unit-teste ; verifie device). **BLOCKER 6 (mutex A4<->cascade sentinel via `handlingAdaptiveFailure`) + MAJOR 4 (garde geo-bypass) :**
  1. Champs pres de `incidentTriedProfiles` :167 : `private var autoHealAttempts = 0`, `private var autoHealServerId: String? = null` ; constante `private const val AUTO_HEAL_MAX_ATTEMPTS = 1` dans le `private companion object` :182.
  2. Reset dans `toggleVpn` a cote de `incidentTriedProfiles = emptySet()` :1644 : `autoHealAttempts = 0; autoHealServerId = server.id`.
  3. **Garde geo-bypass (MAJOR 4)** : helper pur sur les entrees user pour savoir si le host de preuve pourrait sortir EN DIRECT (ce qui falsifierait l'egress). On NE prouve alors PAS (degrade NEUTRAL : ni credit, ni heal) :
```kotlin
    /** True if geo-bypass is ON and a user direct-rule could route the echo host (api.swimvpn.pro)
     * OUTSIDE the tunnel -> the echo would exit DIRECT and fake a PROVEN. Default geoip:private never
     * matches the host, so OFF (or entries that are only IPs/private) returns false. Conservative:
     * any domain-like entry whose value is a substring of (or equal to) the echo host disables proof. */
    private fun egressProofWouldLeakDirect(): Boolean {
        if (!bypassGeoEnabled.value) return false
        val host = EgressTruthProber.ECHO_HOST
        val (directDomains, _) = XrayRoutingBuilder.partitionDirectEntries(bypassGeoEntries.value)
        return directDomains.any { entry ->
            val v = entry.substringAfter(':', entry).trim().lowercase() // strip domain:/full:/keyword: prefixes
            v.isNotEmpty() && (host == v || host.endsWith(".$v") || host.contains(v))
        }
    }
```
  4. Point d'application egress (declenche par `onAdaptiveRuntimeRunning` quand agentEnabled), avec le mutex `handlingAdaptiveFailure` partage avec `handleAdaptiveRuntimeFailure` :
```kotlin
    private fun launchEgressProof() {
        viewModelScope.launch {
            val serverId = adaptiveActiveServerId ?: return@launch
            if (VpnManager.state.value != VpnState.CONNECTED) return@launch
            // MAJOR 4: if a geo-bypass direct rule could route the echo host outside the tunnel, the
            // proof would be a false PROVEN. Degrade to NEUTRAL: do not credit, do not heal.
            if (egressProofWouldLeakDirect()) {
                AdaptiveEventLogger.log("egress_proof_skipped", mapOf("reason" to "geo_bypass_direct"))
                return@launch
            }
            val outcome = EgressTruthProber.probe(socksPort = TunnelRuntimeAdapter.DEFAULT_SOCKS_PORT)
            // Scope the result to THIS session/server (the suspend probe spans ~22s).
            if (VpnManager.state.value != VpnState.CONNECTED) return@launch
            if (autoHealServerId != serverId) return@launch
            val proven = outcome.result.verdict == EgressTruthProbe.Verdict.PROVEN
            // Credit uses the OS validated flag (BLOCKER 2: networkValidated, NOT networkHealthy).
            recordEgressCredit(serverId, outcome.result, networkValidated = networkValidated())
            if (proven && _activeCamouflageProfileId.value == CamouflageProfileRepository.AUTO.id) autoHealAttempts = 0
            // A4 heal uses the link-liveness flag (BLOCKER 2: networkHealthy). MUTEX with the sentinel
            // cascade (BLOCKER 6): only act when no failure handling is in flight, and reuse the SAME
            // guard so planAfterFailure and the heal restart can never run concurrently.
            if (handlingAdaptiveFailure) return@launch
            val decision = AutoHealPolicy.decide(_activeCamouflageProfileId.value, proven, outcome.networkHealthy, autoHealAttempts, AUTO_HEAL_MAX_ATTEMPTS)
            if (decision != AutoHealPolicy.Decision.HEAL_TO_AUTO) return@launch
            handlingAdaptiveFailure = true
            try {
                autoHealAttempts += 1
                AdaptiveEventLogger.log("auto_heal_to_auto", mapOf(
                    "serverId" to serverId, "breakingProfile" to _activeCamouflageProfileId.value, "exitCountry" to outcome.result.country))
                val current = _state.value as? AppState.Success ?: return@launch
                val server = current.activeServer ?: return@launch
                _activeCamouflageProfileId.value = CamouflageProfileRepository.AUTO.id
                restartActiveServerWithProfile(server, current, CamouflageProfileRepository.AUTO.id)
            } finally { handlingAdaptiveFailure = false }
        }
    }
```
  Note mutex (BLOCKER 6) : `handleAdaptiveRuntimeFailure` ouvre par `if (handlingAdaptiveFailure) return` (:334) ; `launchEgressProof` fait de meme avant le heal et pose/relache le meme flag en `try/finally`. Donc si le sentinel est en train de gerer un echec, le heal s'abstient ; et pendant le heal, `planAfterFailure` est bloque. Le restart heal re-declenche `onAdaptiveRuntimeRunning` (RUNNING) qui relance `launchEgressProof` ; `autoHealAttempts` (cape a 1) empeche la boucle.
  Imports requis dans MainViewModel : `com.swimvpn.app.data.network.EgressTruthProber`, `com.swimvpn.app.adaptive.EgressTruthProbe`, `com.swimvpn.app.adaptive.AutoHealPolicy`, `com.swimvpn.app.config.TunnelRuntimeAdapter`, `com.swimvpn.app.config.XrayRoutingBuilder` (deja importe `RoutingOptions`? verifier ; `XrayRoutingBuilder` n'est pas encore importe -> l'ajouter).

- [ ] Compile-check + VERIFY (suite adaptive+config incl. AdaptiveDecisionAgentTest, TunnelHealthSentinelTest, AgentDisabledFailurePolicyTest) + COMMIT - `feat(adaptive): wire bounded auto-heal to AUTO on proven egress failure (mutex-guarded, geo-bypass safe)`

---

## PHASE A5 - ServerScoreStore v6 -> v7 (purge selective)

- [ ] RED 1 - **Corriger d'abord** ServerScoreCodecTest.kt:274 (litteral `"v6"`) en `ServerScoreCodec.VERSION` (lignes 29/183 deja version-agnostiques, ne pas toucher). Puis etendre ServerScoreCodecTest.kt :
```kotlin
    @Test fun `current version round trip preserves profile maps`() {
        val score = ServerQualityScore("srv", profileSuccesses = mapOf("WIFI|frag_light" to 3), profileFailures = mapOf("WIFI|chrome" to 2))
        val encoded = ServerScoreCodec.encode(score)
        assertEquals(ServerScoreCodec.VERSION, encoded.split(ServerScoreCodec.SEPARATOR).first())
        assertEquals(score, ServerScoreCodec.decode(encoded)) }
    @Test fun `withProfileMapsPurged clears only profile maps and keeps all server history`() {
        val full = ServerQualityScore("srv", successCount = 5, failureCount = 3, consecutiveFailures = 1,
            lastSuccessAtMs = 111L, lastFailureAtMs = 222L, avoidUntilMs = 333L, manualSelectionCount = 2, lastManualSelectionAtMs = 444L,
            networkFailures = mapOf(NetworkType.CELLULAR to 2), networkSuccesses = mapOf(NetworkType.WIFI to 5),
            successByHour = mapOf(9 to 3), failureByHour = mapOf(3 to 1),
            profileSuccesses = mapOf("WIFI|frag_light" to 7), profileFailures = mapOf("WIFI|chrome" to 4))
        val purged = ServerScoreCodec.withProfileMapsPurged(full)
        assertEquals(emptyMap<String, Int>(), purged.profileSuccesses); assertEquals(emptyMap<String, Int>(), purged.profileFailures)
        assertEquals(full.copy(profileSuccesses = emptyMap(), profileFailures = emptyMap()), purged) }
    @Test fun `rowVersion reads leading token and defaults legacy rows`() {
        assertEquals(ServerScoreCodec.VERSION, ServerScoreCodec.rowVersion(ServerScoreCodec.encode(ServerQualityScore("s"))))
        val legacy = listOf("server-legacy", 1, 0, 0, 0L, 0L, 0L).joinToString(ServerScoreCodec.SEPARATOR)
        assertEquals(null, ServerScoreCodec.rowVersion(legacy)) }
    @Test fun `migrate rows purges only profile maps and is idempotent`() {
        val v6 = listOf("v6", "srv", 5, 3, 0, 111L, 222L, 333L, 2, 444L, "CELLULAR:2", "WIFI:5", "9:3", "3:1", "WIFI|frag_light:7", "WIFI|chrome:4").joinToString(ServerScoreCodec.SEPARATOR)
        val migrated = ServerScoreCodec.migrateRowsToCurrentVersion(setOf(v6))
        val decoded = migrated.mapNotNull(ServerScoreCodec::decode).single()
        assertEquals(emptyMap<String, Int>(), decoded.profileSuccesses); assertEquals(emptyMap<String, Int>(), decoded.profileFailures)
        assertEquals(5, decoded.successCount); assertEquals(mapOf(NetworkType.CELLULAR to 2), decoded.networkFailures); assertEquals(mapOf(9 to 3), decoded.successByHour)
        assertEquals(migrated, ServerScoreCodec.migrateRowsToCurrentVersion(migrated)) }
```
RED.

- [ ] GREEN - ServerScoreStore.kt : (1) :87 `const val VERSION = "v7"` ; (2) ajouter a `object ServerScoreCodec` (referencer `LEGACY_FIELD_COUNT` deja declare :88) :
```kotlin
    fun withProfileMapsPurged(score: ServerQualityScore): ServerQualityScore =
        score.copy(profileSuccesses = emptyMap(), profileFailures = emptyMap())

    fun rowVersion(raw: String): String? {
        val parts = raw.split(SEPARATOR)
        // A legacy row (exactly LEGACY_FIELD_COUNT fields, no token) -> null, even if serverId starts with "v".
        if (parts.size == LEGACY_FIELD_COUNT) return null
        return parts.firstOrNull()?.takeIf { it.startsWith("v") }
    }

    fun migrateRowsToCurrentVersion(rawRows: Set<String>): Set<String> =
        rawRows.mapNotNull(::decode).map(::withProfileMapsPurged).map(::encode).toSet()
```
(3) glue prefs NON unit-testee dans `class ServerScoreStore`, gatee par un pref GLOBAL de schema (pas par ligne) :
```kotlin
    init { migrateIfNeeded() }

    private fun migrateIfNeeded() {
        val stored = prefs.getString(KEY_SCHEMA_VERSION, null)
        if (stored == ServerScoreCodec.VERSION) return
        val rows = prefs.getStringSet(KEY_SCORES, emptySet()).orEmpty()
        val migrated = ServerScoreCodec.migrateRowsToCurrentVersion(rows)
        prefs.edit().putStringSet(KEY_SCORES, migrated).putString(KEY_SCHEMA_VERSION, ServerScoreCodec.VERSION).apply()
        cached = migrated.mapNotNull(ServerScoreCodec::decode).associateBy { it.serverId }
    }

    private companion object {
        const val PREFS_NAME = "swimvpn_adaptive_scores"
        const val KEY_SCORES = "server_scores"
        const val KEY_SCHEMA_VERSION = "schema_version"
    }
```
(le `private companion object` existant :69-72 est etendu avec `KEY_SCHEMA_VERSION` ; `rowVersion` n'est pas appele par la glue mais expose pour les tests / inspection). PAS de `ServerScoreStoreMigrationTest` (le classpath test ne peut instancier un Context).

- [ ] VERIFY (ServerScoreCodecTest puis suite adaptive+config ; v2..v6 round-trips OK ; le test :274 corrige passe avec VERSION="v7") + COMMIT - `feat(adaptive): ServerScoreStore v7 selective purge of polluted profile-margin maps (pure migration)`

---

## GATE - Acceptance device Couche A (AVANT Couche B)
Derouler la recette device (spec 7). Ne PAS demarrer B tant que : (a) profil cassant ne se reproduit plus sur REALITY (Redmi + Samsung) ; (b) un profil ratant l'egress sur reseau sain (networkValidated=true) => `early_death` (recordFailure) + `auto_heal_to_auto` < 30s ; (c) 4G/IPv6 v1.0.9 tient ; (d) logs `egress_proven` exit-IP/country prouvent la sortie via serveur ; (e) avec geo-bypass ON + une regle directe matchant `api.swimvpn.pro` => log `egress_proof_skipped reason=geo_bypass_direct` (pas de faux PROVEN). Coller les logs.

---

## PHASE B - Fragmentation = levier adaptatif prouve

**Definition UNIQUE de durabilite (MAJOR 7+8, anti-redondance) :** la SONDE `EgressTruthProbe.verdict` exige deja `>=2 echos coherents distincts` pour rendre PROVEN (un PROVEN soutenu DANS une preuve). `EgressDurabilityGate` n'EST PAS un 2e crediteur : c'est un latch fire-once qui gouverne la PROMOTION d'un profil non-AUTO pour la SELECTION. Concretement : on n'inflige PAS de re-credit a chaque cycle sentinel. Le gate compte les PREUVES PROVEN consecutives (probes, jamais wall-clock) et `shouldPromote()` ne tire QU'UNE fois par streak puis latche. A2 (`recordEgressCredit`) reste le SEUL chemin de credit ; B ajoute uniquement la garde de promotion + le logging soutenu. Reset comme `healthSentinel.reset()` (nouvelle session / restart heal).

- [ ] RED 1 (gate de durabilite) - Creer `android/app/src/test/java/com/swimvpn/app/adaptive/EgressDurabilityGateTest.kt` :
```kotlin
package com.swimvpn.app.adaptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EgressDurabilityGateTest {
    @Test fun `survival under threshold is not promotable`() {
        val g = EgressDurabilityGate(threshold = 2); g.onProvenProbe(); assertFalse(g.shouldPromote()) }
    @Test fun `two consecutive proven probes promote exactly once`() {
        val g = EgressDurabilityGate(threshold = 2); g.onProvenProbe(); g.onProvenProbe(); assertTrue(g.shouldPromote()); g.onProvenProbe(); assertFalse(g.shouldPromote()) }
    @Test fun `sustained probing after promotion never re-promotes`() {
        val g = EgressDurabilityGate(threshold = 2); g.onProvenProbe(); g.onProvenProbe(); assertTrue(g.shouldPromote()); repeat(3) { g.onProvenProbe(); assertFalse(g.shouldPromote()) } }
    @Test fun `a death between proofs resets the streak`() {
        val g = EgressDurabilityGate(threshold = 2); g.onProvenProbe(); g.onLostProbe(); g.onProvenProbe(); assertFalse(g.shouldPromote()) }
    @Test fun `reset clears streak and latch for a fresh session`() {
        val g = EgressDurabilityGate(threshold = 2); g.onProvenProbe(); g.onProvenProbe(); assertTrue(g.shouldPromote()); g.reset(); g.onProvenProbe(); g.onProvenProbe(); assertTrue(g.shouldPromote()) }
}
```
RED.

- [ ] GREEN - Creer `android/app/src/main/java/com/swimvpn/app/adaptive/EgressDurabilityGate.kt` (modele TunnelHealthSentinel, wall-clock-free) :
```kotlin
package com.swimvpn.app.adaptive

/**
 * Pure survival counter: a profile is promotable only after surviving [threshold] CONSECUTIVE proven
 * egress probes this session. [shouldPromote] fires exactly once per streak (then latches) so the 8s
 * sentinel cadence cannot re-credit every cycle and inflate the margin. Counts PROBES, never wall-clock.
 * No Android deps.
 * CONTRACT: onProvenProbe() is fed ONLY a SUSTAINED distinct-exit EGRESS_PROVEN verdict, never the 204
 * liveness fallback (NEUTRAL) and never RUNNING. This is the SOLE definition of "durability" in the plan;
 * the per-proof >=2-echo rule lives inside EgressTruthProbe and is NOT re-checked here.
 */
class EgressDurabilityGate(private val threshold: Int = 2) {
    private var consecutiveProven = 0
    private var latched = false

    fun onProvenProbe() { consecutiveProven += 1 }
    fun onLostProbe() { consecutiveProven = 0; latched = false }
    fun reset() { consecutiveProven = 0; latched = false }

    fun shouldPromote(): Boolean {
        if (consecutiveProven < threshold || latched) return false
        latched = true
        return true
    }
}
```
Run cible -> GREEN. COMMIT - `feat(adaptive): EgressDurabilityGate - promote only after sustained proven survival (fire-once latch)`

- [ ] RED 2 (logging PII-free, pur) - Creer `android/app/src/test/java/com/swimvpn/app/adaptive/AdaptiveEventLoggerFieldsTest.kt` (assert sur le MAP des field-builders, JAMAIS via `log()`/Log) :
```kotlin
package com.swimvpn.app.adaptive

import com.swimvpn.app.vpn.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdaptiveEventLoggerFieldsTest {
    @Test fun `egress proven fields are PII-free and complete`() {
        val f = AdaptiveEventLogger.egressProvenFields("frag_light", NetworkType.WIFI, "FI", 21000L, 2)
        assertEquals("frag_light", f["profileId"]); assertEquals("WIFI", f["network"]); assertEquals("FI", f["country"])
        assertEquals(21000L, f["ageMs"]); assertEquals(2, f["streak"]); assertFalse(f.containsKey("ip")); assertFalse(f.containsKey("baseline")) }
    @Test fun `egress proven fields drop a null country`() {
        val f = AdaptiveEventLogger.egressProvenFields("auto", NetworkType.CELLULAR, null, null, null)
        assertEquals("auto", f["profileId"]); assertFalse(f.containsKey("country")) }
    @Test fun `early death fields are PII-free`() {
        val f = AdaptiveEventLogger.earlyDeathFields("chrome", NetworkType.WIFI, 8000L)
        assertEquals("chrome", f["profileId"]); assertEquals("WIFI", f["network"]); assertEquals(8000L, f["ageMs"]); assertFalse(f.containsKey("ip")) }
}
```
GREEN immediat (les builders ont ete declares en A2) ; ce test FIGE le contrat. Si on souhaite un vrai RED, retarder la declaration A2 et la faire ici - mais l'ordre A2-d'abord est plus sur (le helper credit en a besoin). On documente : ce test fige le contrat des builders introduits en A2.

- [ ] (Builders deja livres en A2 - aucune nouvelle source ici.) Run cible -> GREEN. COMMIT - `test(adaptive): PII-free egress_proven / early_death log field contract`

- [ ] RED 3 (verrou contrat REALITY consomme par B) - Etendre CamouflageAdaptiveTest.kt (GREEN, logique livree en A3) :
```kotlin
    @Test fun `reality-filtered order promotes frag only with positive proven margin else auto`() {
        val withFrag = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|frag_light" to 2))
        assertEquals("frag_light", AdaptiveDecisionAgent.selectBestCamouflageProfile(withFrag, NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY).id)
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(ServerQualityScore("s"), NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY).id) }
```

- [ ] CABLAGE B (non unit-teste) - Instancier `private val egressDurabilityGate = EgressDurabilityGate(threshold = 2)` dans MainViewModel (a cote de `healthSentinel` :166). `reset()` sur nouvelle session (meme edge que `healthSentinel.reset()` :289, dans `launchHealthSentinel`) ET sur restart heal (dans la branche HEAL_TO_AUTO de `launchEgressProof`, avant le restart). Dans `launchEgressProof`, APRES le credit A2 :
  - si verdict == PROVEN : `egressDurabilityGate.onProvenProbe()` ; sinon `egressDurabilityGate.onLostProbe()`.
  - La PROMOTION (le credit per-profil deja effectue par A2) n'est PAS doublee : B se contente, pour les profils NON-AUTO, de loguer `egress_proven` avec `streak` via `egressDurabilityGate.shouldPromote()` (fire-once) pour rendre la durabilite INSPECTABLE. AUTO reste credite serveur-niveau via `onAdaptiveRuntimeRunning`. Concretement, remplacer dans `recordEgressCredit` l'appel log `egress_proven` par une variante portant `streak` quand le gate promeut :
```kotlin
            EgressCreditPolicy.Decision.CREDIT_SUCCESS -> {
                serverScoreStore.recordSuccess(serverId, networkType = currentNetworkType(), hourOfDay = currentHourOfDay(), profileId = profileId)
                BenchmarkCollector.record(SessionBenchmarkRecord(transport, profileId, currentNetworkType(), success = true))
                val promoted = profileId != CamouflageProfileRepository.AUTO.id && egressDurabilityGate.shouldPromote()
                AdaptiveEventLogger.log("egress_proven",
                    AdaptiveEventLogger.egressProvenFields(profileId, currentNetworkType(), result.country, null, if (promoted) 2 else null))
            }
```
  (Note: `egressDurabilityGate.onProvenProbe()/onLostProbe()` est appele dans `launchEgressProof` AVANT `recordEgressCredit`, pour que `shouldPromote()` voie le streak a jour.)

- [ ] VERIFY (suite adaptive+config) + COMMIT - `feat(adaptive): fragmentation as a proven-then-believed lever (durability gate + inspectable logging)`

---

## PHASE T - Tests transverses + recette device

- [ ] T1 - verrou cascade MORPH sur REALITY (hard test, catalog-robuste) - Dans CamouflageAdaptiveTest.kt :
```kotlin
    @Test fun `next untried profile coerced under reality is never an fp-override`() {
        var tried = setOf<String>()
        repeat(com.swimvpn.app.config.CamouflageProfileRepository.all().size) {
            val next = AdaptiveDecisionAgent.nextUntriedProfile(tried) ?: return@repeat
            val coerced = com.swimvpn.app.config.CamouflageProfileRepository.coerceForSecurity(next, com.swimvpn.app.config.SecurityMode.REALITY)
            assertTrue("REALITY must never apply an fp-override", coerced.fingerprint.isBlank())
            tried = tried + next.id } }
    @Test fun `reality wins auto at margin-zero tie`() {
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(ServerQualityScore("s"), NetworkType.WIFI, securityMode = com.swimvpn.app.config.SecurityMode.REALITY).id) }
```

- [ ] T2 - verrou anti-regression AUTO (BLOCKER/MINOR: AUTO jamais blackliste sous le nouveau chemin de credit) :
  1. Confirmer (sans modifier) que CamouflageAdaptiveTest.kt:14 (`defaults to auto`) et les tests record-by-profile restent verts.
  2. Reexecuter le test `auto-adds-no-sockopt` byte-identical dans XrayShapingTest (suite config).
  3. **Nouveau test de la policy de credit** (dans EgressCreditPolicyTest, deja en A2) : `egress failed behind captive (not validated) is neutral` PROUVE que AUTO derriere un portail n'est jamais credite FAILURE. Verifier qu'aucun chemin (`onAdaptiveRuntimeRunning` -> profileId=null ; `recordEgressCredit` -> NEUTRAL sur captif) ne peut blacklister AUTO.

- [ ] T3 - credit matrix bloquant (deja couvert par A2) - Verifier EgressCreditPolicyTest (PROVEN=>SUCCESS qq soit validated ; FAILED+validated=>FAILURE ; FAILED+captif=>NEUTRAL ; NEUTRAL=>NEUTRAL ; lock "only PROVEN credits success"). Test pivot spec 7.

- [ ] T4 - verdict matrix (deja couvert par A1) - Verifier EgressTruthProbeTest (PROVEN soutenu ; single/inconsistent/timeout/blank/mixed=>NEUTRAL ; exit==baseline avec baseline present=>FAILED ; baseline-null/CGNAT=>degraded PROVEN). Ne jamais inventer de difference (spec 3.1).

- [ ] T5 - v7 purge + idempotence (deja couvert par A5) - Verifier `migrateRowsToCurrentVersion` purge selective + idempotence + coexistence vieilles cles ; et que ServerScoreCodecTest:274 n'a plus de litteral version.

- [ ] VERIFY final (suite adaptive+config). Attendu : BUILD SUCCESSFUL, 0 echec. COMMIT - `test(egress): transverse guardrails - REALITY morph floor, credit matrix, verdict matrix, AUTO-never-blacklisted, v7 purge`

- [ ] NOTE - Mettre a jour WORKLOG.md / DECISIONS.md (amendements : EGRESS_PROVEN credite SUCCESS meme not-validated ; A5 store-level test abandonne pour fonctions pures ; logger teste via map-builders, jamais via log()/Log ; XTLS traite comme REALITY pour l'interdit fp ; durabilite = latch fire-once SANS re-credit ; garde geo-bypass = egress_proof_skipped). Derouler la recette device et coller les logs `egress_proven` / `early_death` / `auto_heal_to_auto` / `egress_proof_skipped`.

---

## Recapitulatif des fichiers touches
Crees (source) : `adaptive/EgressTruthProbe.kt`, `data/network/EgressTruthProber.kt`, `adaptive/EgressCreditPolicy.kt`, `adaptive/AutoHealPolicy.kt`, `adaptive/EgressDurabilityGate.kt`.
Modifies (source) : `config/TunnelRuntimeAdapter.kt` (DEFAULT_SOCKS_PORT private->internal), `config/CamouflageProfile.kt` (overridesFingerprint + realitySafe + coerceForSecurity(5 modes) + securityModeOf), `adaptive/AdaptiveDecisionAgent.kt` (param securityMode + KDoc chrome->AUTO), `adaptive/ServerScoreStore.kt` (v7 + withProfileMapsPurged/rowVersion/migrateRowsToCurrentVersion + migration glue + KEY_SCHEMA_VERSION), `adaptive/AdaptiveEventLogger.kt` (egressProvenFields/earlyDeathFields purs), `MainViewModel.kt` (retrait faux oracle ; recordEgressCredit ; networkValidated() ; activeSecurityMode + resolve/MORPH/restart coercion ; launchEgressProof mutex-guarde + garde geo-bypass ; auto-heal + reset compteurs ; egressDurabilityGate).
Crees/etendus (tests) : EgressTruthProbeTest, EgressCreditPolicyTest, AutoHealPolicyTest, EgressDurabilityGateTest, AdaptiveEventLoggerFieldsTest (crees) ; **CamouflageProfileRepositoryTest (EXISTANT - on AJOUTE les tests REALITY-floor)**, CamouflageAdaptiveTest (etendu), ServerScoreCodecTest (etendu + correction du litteral "v6" :274).
Interdits (NE PAS TOUCHER) : SwimVpnService.kt:615-622 (tun IPv4-only + ::/0 unreachable), :630 (addDisallowedApplication), awaitStartupHealthProof/canMarkRunning/probeTrafficThroughProxy (RUNNING reste un gate rapide, EGRESS_PROVEN est un 2e verdict post-RUNNING async - spec 3.6).
