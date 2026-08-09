/** ISO 639-1 language codes -- the base option list `LanguagePreferencesSection`'s autocomplete suggests from.
 * A region-qualified preference (e.g. "pt-BR") is still accepted via free text, see `parseLanguageCode` -- this
 * list only needs to cover the un-qualified, most-common case for suggestions to be useful. */
export const ISO_639_1_CODES = [
  'aa', 'ab', 'ae', 'af', 'ak', 'am', 'an', 'ar', 'as', 'av', 'ay', 'az',
  'ba', 'be', 'bg', 'bh', 'bi', 'bm', 'bn', 'bo', 'br', 'bs',
  'ca', 'ce', 'ch', 'co', 'cr', 'cs', 'cu', 'cv', 'cy',
  'da', 'de', 'dv', 'dz',
  'ee', 'el', 'en', 'eo', 'es', 'et', 'eu',
  'fa', 'ff', 'fi', 'fj', 'fo', 'fr', 'fy',
  'ga', 'gd', 'gl', 'gn', 'gu', 'gv',
  'ha', 'he', 'hi', 'ho', 'hr', 'ht', 'hu', 'hy', 'hz',
  'ia', 'id', 'ie', 'ig', 'ii', 'ik', 'io', 'is', 'it', 'iu',
  'ja', 'jv',
  'ka', 'kg', 'ki', 'kj', 'kk', 'kl', 'km', 'kn', 'ko', 'kr', 'ks', 'ku', 'kv', 'kw', 'ky',
  'la', 'lb', 'lg', 'li', 'ln', 'lo', 'lt', 'lu', 'lv',
  'mg', 'mh', 'mi', 'mk', 'ml', 'mn', 'mr', 'ms', 'mt', 'my',
  'na', 'nb', 'nd', 'ne', 'ng', 'nl', 'nn', 'no', 'nr', 'nv', 'ny',
  'oc', 'oj', 'om', 'or', 'os',
  'pa', 'pi', 'pl', 'ps', 'pt',
  'qu',
  'rm', 'rn', 'ro', 'ru', 'rw',
  'sa', 'sc', 'sd', 'se', 'sg', 'si', 'sk', 'sl', 'sm', 'sn', 'so', 'sq', 'sr', 'ss', 'st', 'su', 'sv', 'sw',
  'ta', 'te', 'tg', 'th', 'ti', 'tk', 'tl', 'tn', 'to', 'tr', 'ts', 'tt', 'tw', 'ty',
  'ug', 'uk', 'ur', 'uz',
  've', 'vi', 'vo',
  'wa', 'wo',
  'xh',
  'yi', 'yo',
  'za', 'zh', 'zu',
]

const languageNames = typeof Intl !== 'undefined' && 'DisplayNames' in Intl ? new Intl.DisplayNames(['en'], { type: 'language' }) : null
const regionNames = typeof Intl !== 'undefined' && 'DisplayNames' in Intl ? new Intl.DisplayNames(['en'], { type: 'region' }) : null

/** Splits a language-preference code into its ISO 639-1 language part and optional ISO 3166-1 region part --
 * accepts both a bare language ("pt") and a language-region form ("pt-BR", "pt-PT"). Returns null when the code
 * isn't even shaped like one. */
export function parseLanguageCode(code: string): { language: string; region: string | null } | null {
  const match = /^([a-z]{2,3})(?:-([a-z]{2}))?$/i.exec(code.trim())
  if (!match) return null
  return { language: match[1].toLowerCase(), region: match[2] ? match[2].toUpperCase() : null }
}

/** Re-joins a parsed code back into its canonical form (lowercase language, uppercase region) -- used to normalize
 * user input before it's persisted, since the raw input's casing shouldn't matter. */
export function normalizeLanguageCode(code: string): string | null {
  const parsed = parseLanguageCode(code)
  if (!parsed) return null
  return parsed.region ? `${parsed.language}-${parsed.region}` : parsed.language
}

/** Human-readable name for a language-preference code (e.g. "pt" -> "Portuguese", "pt-BR" -> "Brazilian
 * Portuguese") via Intl.DisplayNames -- falls back to the raw code when Intl isn't available or the code isn't
 * recognized. */
export function languageName(code: string): string {
  const parsed = parseLanguageCode(code)
  if (!parsed || !languageNames) return code
  try {
    return languageNames.of(parsed.region ? `${parsed.language}-${parsed.region}` : parsed.language) ?? code
  } catch {
    return code
  }
}

/** Whether [code] is a real, recognized language (optionally with a real region) -- an unrecognized subtag comes
 * back unchanged (case-insensitively) from `Intl.DisplayNames.of`, which is how this tells "not a real one" from
 * a genuine match. When Intl isn't available at all, falls back to just checking the code is shaped like a
 * language tag, rather than blocking input outright. */
export function isValidLanguageCode(code: string): boolean {
  const parsed = parseLanguageCode(code)
  if (!parsed) return false
  if (!languageNames) return true
  try {
    if (languageNames.of(parsed.language)?.toLowerCase() === parsed.language) return false
    if (parsed.region && regionNames && regionNames.of(parsed.region)?.toLowerCase() === parsed.region.toLowerCase()) return false
    return true
  } catch {
    return false
  }
}
