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

/**
 * CUSTOM/LANGUAGE win outright when set. Otherwise: try the club's preferred languages in rank order (skipping any
 * that are also ignored), first one with a translation wins; failing that, use the original title unless its own
 * language is ignored, in which case fall back to any non-ignored translation, or the original title if there's
 * truly nothing better.
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
    if (club.ignoredLanguages.includes(lang)) continue
    const match = media.translations.find((t) => t.languageCode === lang)
    if (match) return match.title
  }

  if (!media.originalLanguage || !club.ignoredLanguages.includes(media.originalLanguage)) {
    return media.originalTitle
  }
  const fallback = media.translations.find((t) => !club.ignoredLanguages.includes(t.languageCode))
  return fallback ? fallback.title : media.originalTitle
}
