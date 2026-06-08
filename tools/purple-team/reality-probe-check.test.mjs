// Unit tests for the PURE parts of reality-probe-check (no network). Run: node --test
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  parseRealityEndpoints,
  classifyProbe,
  hostMatches,
  normalizeCert,
  maskHost,
} from './reality-probe-check.mjs'

const FAR_FUTURE = 'Dec 31 23:59:59 2099 GMT'
const PAST = 'Jan 1 00:00:00 2000 GMT'
const NOW = Date.parse('2026-06-09T00:00:00Z')

test('parseRealityEndpoints extracts host/port/sni from a REALITY vless URI, ignores secrets', () => {
  const uri =
    'vless://11111111-1111-1111-1111-111111111111@cdn.example.com:8443' +
    '?security=reality&sni=donor.example.org&pbk=SECRETKEY&sid=ab12&type=tcp&flow=xtls-rprx-vision&fp=chrome#My%20Server'
  const eps = parseRealityEndpoints(uri)
  assert.equal(eps.length, 1)
  assert.deepEqual(
    { host: eps[0].host, port: eps[0].port, sni: eps[0].sni, tag: eps[0].tag },
    { host: 'cdn.example.com', port: 8443, sni: 'donor.example.org', tag: 'My Server' },
  )
  // The object must not carry any REALITY secret.
  assert.equal(JSON.stringify(eps[0]).includes('SECRETKEY'), false)
  assert.equal(JSON.stringify(eps[0]).includes('ab12'), false)
})

test('parseRealityEndpoints ignores non-REALITY and non-vless lines', () => {
  const sub = [
    'vless://uuid@a.com:443?security=tls&sni=x.com#tls-not-reality',
    'vmess://eyJ2IjoiMiJ9',
    'trojan://pass@b.com:443#trojan',
    'vless://uuid@c.com:443?security=reality&sni=donor.com#ok',
    'not a uri',
  ].join('\n')
  const eps = parseRealityEndpoints(sub)
  assert.equal(eps.length, 1)
  assert.equal(eps[0].host, 'c.com')
})

test('parseRealityEndpoints decodes a base64 subscription body', () => {
  const plain = 'vless://uuid@d.com:443?security=reality&sni=donor.io&type=tcp#node'
  const b64 = Buffer.from(plain, 'utf8').toString('base64')
  const eps = parseRealityEndpoints(b64)
  assert.equal(eps.length, 1)
  assert.equal(eps[0].host, 'd.com')
  assert.equal(eps[0].sni, 'donor.io')
})

test('parseRealityEndpoints defaults port to 443 and sni to host when omitted', () => {
  const eps = parseRealityEndpoints('vless://uuid@e.com?security=reality&type=tcp#n')
  assert.equal(eps.length, 1)
  assert.equal(eps[0].port, 443)
  assert.equal(eps[0].sni, 'e.com')
})

test('hostMatches: exact, wildcard, and non-match', () => {
  assert.equal(hostMatches('donor.com', 'donor.com'), true)
  assert.equal(hostMatches('*.donor.com', 'a.donor.com'), true)
  assert.equal(hostMatches('*.donor.com', 'donor.com'), false) // bare apex not covered by *.
  assert.equal(hostMatches('*.donor.com', 'a.b.donor.com'), false) // single label only
  assert.equal(hostMatches('other.com', 'donor.com'), false)
})

test('normalizeCert flags a self-signed cert (subject == issuer)', () => {
  const cert = normalizeCert(
    { subject: { CN: 'proxy' }, issuer: { CN: 'proxy' }, subjectaltname: 'DNS:donor.com', valid_to: FAR_FUTURE },
    'donor.com',
    NOW,
  )
  assert.equal(cert.selfSigned, true)
})

test('normalizeCert: genuine donor cert is not self-signed, SAN matches, not expired', () => {
  const cert = normalizeCert(
    { subject: { CN: 'donor.com' }, issuer: { O: "Let's Encrypt", CN: 'R3' }, subjectaltname: 'DNS:donor.com, DNS:www.donor.com', valid_to: FAR_FUTURE },
    'donor.com',
    NOW,
  )
  assert.equal(cert.selfSigned, false)
  assert.equal(cert.sanMatchesSni, true)
  assert.equal(cert.expired, false)
  assert.equal(cert.issuer, "Let's Encrypt")
})

test('normalizeCert: SAN mismatch and expiry detected', () => {
  const mismatch = normalizeCert(
    { subject: { CN: 'somethingelse.com' }, issuer: { O: 'CA' }, subjectaltname: 'DNS:somethingelse.com', valid_to: FAR_FUTURE },
    'donor.com',
    NOW,
  )
  assert.equal(mismatch.sanMatchesSni, false)
  const expired = normalizeCert(
    { subject: { CN: 'donor.com' }, issuer: { O: 'CA' }, subjectaltname: 'DNS:donor.com', valid_to: PAST },
    'donor.com',
    NOW,
  )
  assert.equal(expired.expired, true)
})

test('classifyProbe: RESISTANT only for a valid CA cert covering the SNI', () => {
  const v = classifyProbe({
    reachable: true,
    handshakeOk: true,
    sni: 'donor.com',
    cert: { selfSigned: false, sanMatchesSni: true, expired: false, issuer: "Let's Encrypt", daysToExpiry: 60 },
  })
  assert.equal(v.level, 'RESISTANT')
})

test('classifyProbe: self-signed / mismatch / expired => SUSPICIOUS', () => {
  for (const cert of [
    { selfSigned: true, sanMatchesSni: true, expired: false, issuer: 'self', daysToExpiry: 100 },
    { selfSigned: false, sanMatchesSni: false, expired: false, issuer: 'CA', daysToExpiry: 100 },
    { selfSigned: false, sanMatchesSni: true, expired: true, issuer: 'CA', daysToExpiry: -1 },
  ]) {
    const v = classifyProbe({ reachable: true, handshakeOk: true, sni: 'donor.com', cert })
    assert.equal(v.level, 'SUSPICIOUS')
  }
})

test('classifyProbe: handshake failure => SUSPICIOUS, unreachable => UNREACHABLE(inconclusive)', () => {
  assert.equal(classifyProbe({ reachable: true, handshakeOk: false, cert: null, error: 'ECONNRESET', sni: 'd' }).level, 'SUSPICIOUS')
  const u = classifyProbe({ reachable: false, handshakeOk: false, cert: null, error: 'timeout', sni: 'd' })
  assert.equal(u.level, 'UNREACHABLE')
})

test('maskHost never leaks the full host and is stable', () => {
  const host = 'secret-node-42.example.com'
  const m = maskHost(host)
  assert.equal(m.includes('secret-node-42'), false)
  assert.equal(m.includes('example.com'), true) // public suffix is acceptable
  assert.equal(m, maskHost(host)) // stable
})
