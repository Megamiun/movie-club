export interface Member {
  id: string
  name: string
  email: string
}

export interface AuthResponse {
  token: string
  member: Member
}

export interface Club {
  id: string
  name: string
}

export interface ClubMember {
  memberId: string
  name: string
  role: string
  rotationOrder: number
}

export interface MemberSummary {
  id: string
  name: string
  email: string
}

export interface ClubDetail {
  id: string
  name: string
  members: ClubMember[]
}

export interface RatingOption {
  id: string
  label: string
  position: number
  color: string
}

export interface RatingScale {
  id: string
  type: string
  options: RatingOption[]
}

export interface Meeting {
  id: string
  clubId: string
  date: string
  assignedMemberId: string | null
}

export interface AlternativeTitle {
  isoCode: string
  title: string
  type: string | null
}

export interface Movie {
  id: string
  meetingId: string
  chosenById: string
  imdbId: string
  tmdbId: string | null
  originalTitle: string
  alternativeTitles: AlternativeTitle[]
  customTitle: string | null
  displayTitlePreference: string
  year: number | null
  director: string | null
  runtimeMinutes: number | null
  genre: string[] | null
  originCountry: string[] | null
  productionCountries: string[] | null
  tmdbRating: string | null
  posterS3Key: string | null
  watchLink: string | null
}

export interface MovieReview {
  movieId: string
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
}

export interface Series {
  id: string
  clubId: string
  chosenById: string
  imdbId: string
  tmdbId: string | null
  originalTitle: string
  alternativeTitles: AlternativeTitle[]
  customTitle: string | null
  displayTitlePreference: string
  year: number | null
  genre: string[] | null
  originCountry: string[] | null
  productionCountries: string[] | null
  tmdbRating: string | null
  creator: string | null
  posterS3Key: string | null
}

export interface Season {
  id: string
  seriesId: string
  number: number
  title: string | null
}

export interface Episode {
  id: string
  seasonId: string
  number: number
  title: string | null
  airDate: string | null
  overview: string | null
  runtimeMinutes: number | null
  director: string | null
  tmdbRating: string | null
}

export interface SeriesReview {
  seriesId: string
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
}

export interface SeasonReview {
  seasonId: string
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
}

export interface EpisodeReview {
  episodeId: string
  memberId: string
  qualityOptionId: string | null
  sentimentOptionId: string | null
  comment: string | null
}

export interface WatchlistEntry {
  id: string
  clubId: string
  memberId: string
  title: string
  imdbUrl: string | null
  notes: string | null
}

export interface ImportRowIssue {
  row: number
  reason: string
}

export interface ImportResult {
  clubId: string
  type: string
  created: number
  updated: number
  skipped: ImportRowIssue[]
  warnings: ImportRowIssue[]
}

export type ImportType = 'movies' | 'series' | 'reserve'

export interface ImportMemberMapping {
  choiceInitial: string
  csvDisplayName: string
  memberId: string
}
