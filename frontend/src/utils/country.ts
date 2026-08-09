/** Converts an ISO 3166-1 alpha-2 code (e.g. "US") into its flag emoji via the regional-indicator-symbol trick --
 * each letter maps to the Unicode regional indicator letter at the same offset from its base code point. */
export function countryFlag(isoCode: string): string {
  return isoCode
    .toUpperCase()
    .replace(/./g, (char) => String.fromCodePoint(127397 + char.charCodeAt(0)))
}

const regionNames = typeof Intl !== 'undefined' && 'DisplayNames' in Intl ? new Intl.DisplayNames(['en'], { type: 'region' }) : null

export function countryName(isoCode: string): string {
  return regionNames?.of(isoCode.toUpperCase()) ?? isoCode
}
