export function ratingLabel(item: { imdbRating: string | null; tmdbRating: string | null }): string | null {
  if (item.imdbRating) return `IMDB ${item.imdbRating}`
  if (item.tmdbRating) return `TMDB ${item.tmdbRating}`
  return null
}
