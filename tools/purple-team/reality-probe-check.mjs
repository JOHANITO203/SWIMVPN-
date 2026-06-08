#!/usr/bin/env node
// reality-probe-check — purple-team dev/CI tool (NOT app code, NOT run through the app tunnel).
//
// WHAT IT MEASURES (honestly):
//   A correctly-configured VLESS+REALITY server is *probe-resistant*: when an UNAUTHENTICATED
//   client connects (no valid REALITY x25519/shortId handshake), the server transparently relays
//   the raw connection to its real "donor" site (the SNI domain). So an unauthenticated TLS
//   handshake to the server, with servername = the donor SNI, should return the donor's GENUINE
//   CA-issued certificate (chaining to a real CA, SAN covering the SNI, not expired, not self-signed).
//   That is exactly what defeats active probing (the attack that killed Shadowsocks/Trojan): the
//   prober sees a real site, not a proxy. A server that instead returns a self-signed cert, a
//   mismatched cert, or resets the connection is EXPOSING itself to a probe.
//
// WHAT IT DOES NOT MEASURE (no theater):
//   - It does NOT prove the server evades a specific censor's DPI. That is location-dependent and
//     needs an in-country vantage point. This tool tests the SERVER's relay behaviour, which is
//     location-independent, from wherever you run it.
//   - It does NOT touch the app, the VPN tun, or route live user traffic. It is a standalone TLS
//     client. (Contrast the abandoned in-session egress probe, which destabilised the live tunnel.)
//   - It needs NONE of the REALITY secrets (pbk/sid/uuid); it only uses host:port:sni. Secrets are
//     never read into the verdict and never printed.
//
// USAGE:
//   node tools/purple-team/reality-probe-check.mjs <subscription-url | file | -> [--verbose] [--json]
//     <->         read the subscription text from stdin
//     --verbose   print full host/sni (LOCAL ONLY — reveals infra; off by default = masked)
//     --json      machine-readable output
//
// Pure functions (parseRealityEndpoints / classifyProbe / mask*) are exported for unit tests; the
// network probe is the only side-effecting part.

import { connect as tlsConnect } from 'node:tls'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'

const PROBE_TIMEOUT_MS = 8000

// ---------------------------------------------------------------------------------------------
// Pure: parse REALITY endpoints from a subscription (raw or base64). Only host/port/sni — never
// the REALITY secrets. A vless URI looks like: vless://<uuid>@<host>:<port>?security=reality&sni=<d>&...#<tag>
// ---------------------------------------------------------------------------------------------
export function parseRealityEndpoints(subscriptionText) {
  const decoded = maybeBase64Decode(subscriptionText)
  const lines = decoded.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  const endpoints = []
  for (const line of lines) {
    if (!line.startsWith('vless://')) continue
    let url
    try {
      url = new URL(line)
    } catch {
      continue
    }
    const security = (url.searchParams.get('security') || '').toLowerCase()
    if (security !== 'reality') continue
    const host = url.hostname
    const port = Number(url.port) || 443
    // SNI = the donor domain REALITY borrows; fall back to host if the link omits it.
    const sni = url.searchParams.get('sni') || url.searchParams.get('serverName') || host
    if (!host || !sni) continue
    // Tag is the #fragment label (decoded); keep only for human ordering, it is not a secret key.
    const tag = safeDecode(url.hash.replace(/^#/, '')) || host
    endpoints.push({ tag, host, port, sni })
  }
  return endpoints
}

// A subscription body is often base64 of newline-separated URIs. Decode iff it looks like base64
// AND decoding yields a vless:// line; otherwise treat the text as already-decoded.
function maybeBase64Decode(text) {
  const t = (text || '').trim()
  if (t.includes('vless://')) return t
  if (!/^[A-Za-z0-9+/=\r\n_-]+$/.test(t)) return t
  try {
    const norm = t.replace(/-/g, '+').replace(/_/g, '/')
    const out = Buffer.from(norm, 'base64').toString('utf8')
    return out.includes('vless://') ? out : t
  } catch {
    return t
  }
}

function safeDecode(s) {
  try {
    return decodeURIComponent(s)
  } catch {
    return s
  }
}

// ---------------------------------------------------------------------------------------------
// Pure: classify a probe result into a verdict. `result` shape:
//   { reachable: bool, handshakeOk: bool, cert: NormalizedCert | null, error?: string, sni: string }
// NormalizedCert: { selfSigned: bool, sanMatchesSni: bool, expired: bool, issuer: string, daysToExpiry: number|null }
// ---------------------------------------------------------------------------------------------
export function classifyProbe(result) {
  if (!result.reachable) {
    return {
      level: 'UNREACHABLE',
      reasons: [`could not connect from this vantage (${result.error || 'timeout'}) — inconclusive, not a server verdict; re-run from your own network`],
    }
  }
  if (!result.handshakeOk || !result.cert) {
    return {
      level: 'SUSPICIOUS',
      reasons: [`no valid TLS handshake (${result.error || 'reset/closed'}) — a genuine donor relay completes TLS; a reset on an unauth probe is a proxy tell`],
    }
  }
  const c = result.cert
  const reasons = []
  if (c.selfSigned) reasons.push('certificate is self-signed — the server is presenting its own cert, not relaying to a real donor (probe tell)')
  if (c.expired) reasons.push('certificate is expired — not a healthy donor relay')
  if (!c.sanMatchesSni) reasons.push(`certificate does not cover the SNI — the server is not serving the donor it claims (SAN mismatch)`)
  if (reasons.length === 0) {
    return {
      level: 'RESISTANT',
      reasons: [`relayed a genuine CA cert (issuer="${c.issuer}", SAN covers SNI${c.daysToExpiry != null ? `, expires in ${c.daysToExpiry}d` : ''}) — looks like the real donor to an unauthenticated probe`],
    }
  }
  return { level: 'SUSPICIOUS', reasons }
}

// ---------------------------------------------------------------------------------------------
// Pure: masking for shareable output (infra is sensitive). Default shows only the public suffix
// and a short stable hash, never the full host/SNI.
// ---------------------------------------------------------------------------------------------
export function maskHost(host) {
  if (!host) return '(none)'
  const h = createHash('sha256').update(host).digest('hex').slice(0, 6)
  const parts = host.split('.')
  const suffix = parts.length >= 2 ? parts.slice(-2).join('.') : host
  return `***.${suffix}#${h}`
}

export function maskSni(sni) {
  return maskHost(sni)
}

// Pure: normalise a Node peer certificate into the minimal shape classifyProbe needs.
export function normalizeCert(peerCert, sni, nowMs) {
  if (!peerCert || Object.keys(peerCert).length === 0) return null
  const subject = peerCert.subject || {}
  const issuer = peerCert.issuer || {}
  const selfSigned =
    JSON.stringify(subject) === JSON.stringify(issuer) ||
    (peerCert.fingerprint256 && peerCert.issuerCertificate && peerCert.fingerprint256 === peerCert.issuerCertificate.fingerprint256)
  const names = collectCertNames(peerCert)
  const sanMatchesSni = names.some((n) => hostMatches(n, sni))
  const validTo = peerCert.valid_to ? Date.parse(peerCert.valid_to) : NaN
  const expired = Number.isFinite(validTo) ? validTo < nowMs : false
  const daysToExpiry = Number.isFinite(validTo) ? Math.round((validTo - nowMs) / 86400000) : null
  const issuerStr = issuer.O || issuer.CN || issuer.OU || 'unknown'
  return { selfSigned: Boolean(selfSigned), sanMatchesSni, expired, issuer: issuerStr, daysToExpiry }
}

export function collectCertNames(peerCert) {
  const names = []
  if (peerCert.subject && peerCert.subject.CN) names.push(peerCert.subject.CN)
  if (peerCert.subjectaltname) {
    for (const entry of peerCert.subjectaltname.split(',')) {
      const m = entry.trim().match(/^DNS:(.+)$/i)
      if (m) names.push(m[1])
    }
  }
  return names
}

// Pure: hostname match supporting a single leading wildcard label (RFC 6125 style).
export function hostMatches(certName, sni) {
  if (!certName || !sni) return false
  const cn = certName.toLowerCase()
  const s = sni.toLowerCase()
  if (cn === s) return true
  if (cn.startsWith('*.')) {
    const base = cn.slice(2)
    const idx = s.indexOf('.')
    return idx > 0 && s.slice(idx + 1) === base
  }
  return false
}

// ---------------------------------------------------------------------------------------------
// Side-effecting: one unauthenticated TLS handshake to host:port with servername = sni.
// ---------------------------------------------------------------------------------------------
function probeEndpoint({ host, port, sni }, timeoutMs = PROBE_TIMEOUT_MS, nowMs = Date.now()) {
  return new Promise((resolve) => {
    let settled = false
    const done = (r) => {
      if (settled) return
      settled = true
      try { socket.destroy() } catch {}
      resolve(r)
    }
    const socket = tlsConnect(
      { host, port, servername: sni, rejectUnauthorized: false, timeout: timeoutMs, ALPNProtocols: ['h2', 'http/1.1'] },
      () => {
        const peer = socket.getPeerCertificate(true)
        done({ reachable: true, handshakeOk: true, cert: normalizeCert(peer, sni, nowMs), sni })
      },
    )
    socket.on('timeout', () => done({ reachable: false, handshakeOk: false, cert: null, error: 'timeout', sni }))
    socket.on('error', (e) => {
      // A connect-level error = unreachable; a TLS-level error = reachable but handshake failed.
      const reachable = !['ECONNREFUSED', 'ETIMEDOUT', 'EHOSTUNREACH', 'ENETUNREACH', 'ENOTFOUND', 'EAI_AGAIN'].includes(e.code)
      done({ reachable, handshakeOk: false, cert: null, error: e.code || e.message, sni })
    })
  })
}

// ---------------------------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------------------------
async function readInput(arg) {
  if (arg === '-') return readFileSync(0, 'utf8')
  if (/^https?:\/\//i.test(arg)) {
    const res = await fetch(arg, { headers: { 'User-Agent': 'v2rayNG/1.9.5' } })
    if (!res.ok) throw new Error(`fetch ${arg} -> HTTP ${res.status}`)
    return await res.text()
  }
  return readFileSync(arg, 'utf8')
}

async function main() {
  const args = process.argv.slice(2)
  const verbose = args.includes('--verbose')
  const json = args.includes('--json')
  const inputArg = args.find((a) => !a.startsWith('--'))
  if (!inputArg) {
    console.error('usage: node reality-probe-check.mjs <subscription-url | file | -> [--verbose] [--json]')
    process.exit(2)
  }

  const text = await readInput(inputArg)
  const endpoints = parseRealityEndpoints(text)
  if (endpoints.length === 0) {
    console.error('no VLESS+REALITY endpoints found in input')
    process.exit(1)
  }

  // De-duplicate identical host:port:sni so we probe each distinct server once.
  const seen = new Set()
  const unique = endpoints.filter((e) => {
    const k = `${e.host}:${e.port}:${e.sni}`
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })

  const results = []
  for (let i = 0; i < unique.length; i++) {
    const ep = unique[i]
    const probe = await probeEndpoint(ep)
    const verdict = classifyProbe(probe)
    results.push({ ep, verdict, cert: probe.cert })
  }

  if (json) {
    const payload = results.map(({ ep, verdict, cert }, i) => ({
      index: i + 1,
      server: verbose ? `${ep.host}:${ep.port}` : maskHost(ep.host),
      sni: verbose ? ep.sni : maskSni(ep.sni),
      level: verdict.level,
      reasons: verdict.reasons,
      issuer: cert?.issuer ?? null,
    }))
    console.log(JSON.stringify(payload, null, 2))
  } else {
    const counts = { RESISTANT: 0, SUSPICIOUS: 0, UNREACHABLE: 0 }
    console.log(`reality-probe-check — ${unique.length} distinct REALITY server(s)\n`)
    results.forEach(({ ep, verdict }, i) => {
      counts[verdict.level]++
      const id = String(i + 1).padStart(2, '0')
      const where = verbose ? `${ep.host}:${ep.port} (sni=${ep.sni})` : `${maskHost(ep.host)} :${ep.port}`
      const mark = verdict.level === 'RESISTANT' ? 'OK ' : verdict.level === 'SUSPICIOUS' ? '!! ' : '?? '
      console.log(`[${id}] ${mark}${verdict.level.padEnd(11)} ${where}`)
      verdict.reasons.forEach((r) => console.log(`      └ ${r}`))
    })
    console.log(`\nsummary: ${counts.RESISTANT} resistant · ${counts.SUSPICIOUS} suspicious · ${counts.UNREACHABLE} unreachable(inconclusive)`)
    console.log('note: tests SERVER relay behaviour (location-independent), NOT evasion of a specific censor.')
  }
}

// Run main only as a script, not when imported by tests.
if (import.meta.url === `file://${process.argv[1]}` || import.meta.url.endsWith(process.argv[1]?.replace(/\\/g, '/'))) {
  main().catch((e) => {
    console.error('error:', e.message)
    process.exit(1)
  })
}
