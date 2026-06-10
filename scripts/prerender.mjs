// Post-build prerender: turns the CSR shell (dist/index.html) into per-locale static HTML with real
// content + correct <head>, so crawlers (esp. Yandex) index a full page. Runs in Node only (no browser).
//
//   default locale -> dist/index.html      (overwritten in place)
//   other locales  -> dist/<locale>/index.html   (served by nginx try_files $uri $uri/)
//
// The client bundle is untouched: on load it createRoot-renders over this markup (no hydration).
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const DIST = resolve('dist')
const SSR_ENTRY = pathToFileURL(resolve('dist-ssr/entry-prerender.js')).href

const { render, seo, locales, defaultLocale } = await import(SSR_ENTRY)

const template = readFileSync(resolve(DIST, 'index.html'), 'utf8')
if (!template.includes('<div id="root"></div>')) {
  throw new Error('prerender: expected an empty <div id="root"></div> in dist/index.html — aborting')
}

const escapeHtml = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// Replace a whole <meta name="X" ...> / <meta property="X" ...> tag (attributes may span lines) with a
// clean single-line tag carrying the new content. [^>]* matches newlines, so multi-line tags are covered.
const replaceMeta = (html, attr, name, content) => {
  const re = new RegExp(`<meta[^>]*\\b${attr}="${name}"[^>]*>`, 'i')
  return html.replace(re, `<meta ${attr}="${name}" content="${escapeHtml(content)}" />`)
}

function buildHtml(locale) {
  const s = seo[locale]
  if (!s) throw new Error(`prerender: no seo data for locale "${locale}"`)
  const markup = render(locale)

  let html = template
  html = html.replace('<div id="root"></div>', `<div id="root">${markup}</div>`)
  html = html.replace(/<html lang="[^"]*"/i, `<html lang="${s.htmlLang}"`)
  html = html.replace(/<title>[\s\S]*?<\/title>/i, `<title>${escapeHtml(s.title)}</title>`)
  html = replaceMeta(html, 'name', 'description', s.description)
  html = replaceMeta(html, 'name', 'twitter:title', s.ogTitle)
  html = replaceMeta(html, 'name', 'twitter:description', s.ogDescription)
  html = replaceMeta(html, 'property', 'og:title', s.ogTitle)
  html = replaceMeta(html, 'property', 'og:description', s.ogDescription)
  html = replaceMeta(html, 'property', 'og:locale', s.ogLocale)
  html = replaceMeta(html, 'property', 'og:url', s.url)
  html = html.replace(/(<link rel="canonical" href=")[^"]*(")/i, `$1${s.url}$2`)
  // JSON-LD structured-data description -> per-locale (the only "description": key in the doc is the
  // ld+json one; meta tags use content="..."). JSON.stringify gives a correctly-escaped string body.
  const jsonLdDesc = JSON.stringify(s.description).slice(1, -1)
  html = html.replace(/("description":\s*")(?:\\.|[^"\\])*(")/, `$1${jsonLdDesc}$2`)
  return html
}

let count = 0
for (const locale of locales) {
  const html = buildHtml(locale)
  if (locale === defaultLocale) {
    writeFileSync(resolve(DIST, 'index.html'), html)
  } else {
    mkdirSync(resolve(DIST, locale), { recursive: true })
    writeFileSync(resolve(DIST, locale, 'index.html'), html)
  }
  count++
  const markupLen = (html.split('<div id="root">')[1]?.split('<script type="module"')[0] ?? '').length
  console.log(`prerender: ${locale.padEnd(3)} -> ${locale === defaultLocale ? 'index.html' : locale + '/index.html'}  (rendered ~${markupLen} chars of content)`)
}
console.log(`prerender: ${count} locale(s) written.`)
