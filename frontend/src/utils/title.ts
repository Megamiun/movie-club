import type { DisplayTitlePreference, Translation } from '../api/types'

export interface TitledMedia {
  originalTitle: string
  originalLanguage: string | null
  translations: Translation[]
  customTitle: string | null
  displayTitlePreference: DisplayTitlePreference
  displayLanguageCode: string | null
}

export interface LanguagePreferences {
  preferredLanguages: string[]
  ignoredLanguages: string[]
}

/** A preferred/ignored-language entry is either a bare ISO 639-1 code ("pt") or a language-region form
 * ("pt-BR") -- splits on the first "-" so callers can match each part against a `Translation`'s own separate
 * `languageCode`/`countryCode` fields. Case-insensitive on both parts: `languageName`/`isValidLanguageCode`
 * (see `utils/language.ts`) normalize new entries to lowercase-language/uppercase-region on save, but an entry
 * saved before that normalization existed may still be stored some other way, and should still match. */
function splitLanguagePreference(entry: string): { language: string; region: string | null } {
  const [language, region] = entry.split('-')
  return { language: language.toLowerCase(), region: region ? region.toUpperCase() : null }
}

function matchesTranslation(translation: Translation, preference: string): boolean {
  const { language, region } = splitLanguagePreference(preference)
  return translation.languageCode.toLowerCase() === language && (region === null || translation.countryCode.toUpperCase() === region)
}

/** Whether [languageCode] (a bare ISO 639-1 code -- the only form a `Translation.languageCode` or a Movie/Series'
 * own `originalLanguage` ever takes) is covered by any entry in [ignoredLanguages]. A region-qualified ignored
 * entry (e.g. "pt-BR") still matches by its language part alone here, since neither side of this comparison ever
 * carries a region to narrow against. */
function isIgnoredLanguage(languageCode: string, ignoredLanguages: string[]): boolean {
  return ignoredLanguages.some((entry) => splitLanguagePreference(entry).language === languageCode.toLowerCase())
}

/**
 * CUSTOM/LANGUAGE win outright when set. Otherwise: try the club's preferred languages in rank order (skipping any
 * that are also ignored), first one with a translation wins; failing that, use the original title unless its own
 * language is ignored, in which case fall back to any non-ignored translation, or the original title if there's
 * truly nothing better. A preferred/ignored entry may be region-qualified ("pt-BR") to match a specific
 * `Translation.countryCode`, or bare ("pt") to match any region of that language.
 */
export function resolveTitle(media: TitledMedia, club: LanguagePreferences): string {
  if (media.displayTitlePreference === 'CUSTOM' && media.customTitle) {
    return media.customTitle
  }
  if (media.displayTitlePreference === 'LANGUAGE' && media.displayLanguageCode) {
    const picked = media.translations.find((t) => t.languageCode === media.displayLanguageCode)
    if (picked) return picked.title
  }

  for (const lang of club.preferredLanguages) {
    if (isIgnoredLanguage(splitLanguagePreference(lang).language, club.ignoredLanguages)) continue
    const match = media.translations.find((t) => matchesTranslation(t, lang))
    if (match) return match.title
  }

  if (!media.originalLanguage || !isIgnoredLanguage(media.originalLanguage, club.ignoredLanguages)) {
    return media.originalTitle
  }
  const fallback = media.translations.find((t) => !isIgnoredLanguage(t.languageCode, club.ignoredLanguages))
  return fallback ? fallback.title : media.originalTitle
}
