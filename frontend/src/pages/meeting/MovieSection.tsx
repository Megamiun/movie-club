import BookmarkAddIcon from '@mui/icons-material/BookmarkAdd'
import DeleteIcon from '@mui/icons-material/Delete'
import RefreshIcon from '@mui/icons-material/Refresh'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Select,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useState, type FormEvent } from 'react'
import { moviesApi } from '../../api/movies'
import { watchlistApi } from '../../api/watchlist'
import { ApiError } from '../../api/client'
import type { ClubMember, Movie, RatingScale, TmdbSearchResult } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { ImdbLink } from '../../components/ImdbLink'
import { LanguagePickerDialog } from '../../components/LanguagePickerDialog'
import { RatingForm } from '../../components/RatingForm'
import { ReviewsList } from '../../components/ReviewsList'
import { TmdbSearchAutocomplete } from '../../components/TmdbSearchAutocomplete'
import { useAsync } from '../../hooks/useAsync'
import { useSmartPolling } from '../../hooks/useSmartPolling'
import { memberName } from '../../utils/members'
import { ratingLabel } from '../../utils/rating'
import { resolveTitle, type LanguagePreferences } from '../../utils/title'

export function MovieSection({
  meetingId,
  clubId,
  scales,
  members,
  languagePrefs,
}: {
  meetingId: string
  clubId: string
  scales: RatingScale[]
  members: ClubMember[]
  languagePrefs: LanguagePreferences
}) {
  const { data: movies, loading, error, reload, silentReload } = useAsync(() => moviesApi.list(meetingId), [meetingId])
  useSmartPolling(silentReload, 15000)
  const [addMode, setAddMode] = useState<'search' | 'imdb'>('search')
  const [selectedResult, setSelectedResult] = useState<TmdbSearchResult | null>(null)
  const [imdbUrlOrId, setImdbUrlOrId] = useState('')
  const [watchLink, setWatchLink] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)
    try {
      if (addMode === 'search') {
        if (!selectedResult) return
        await moviesApi.addByTmdbId(meetingId, selectedResult.tmdbId, watchLink || undefined)
        setSelectedResult(null)
      } else {
        await moviesApi.add(meetingId, imdbUrlOrId, watchLink || undefined)
        setImdbUrlOrId('')
      }
      setWatchLink('')
      reload()
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Movies
      </Typography>

      <AsyncState loading={loading} error={error}>
        <Stack spacing={1}>
          {movies?.length === 0 && <Typography color="text.secondary">No movies picked yet.</Typography>}
          {movies?.map((movie) => (
            <MovieItem
              key={movie.id}
              movie={movie}
              clubId={clubId}
              scales={scales}
              members={members}
              languagePrefs={languagePrefs}
              onChange={reload}
            />
          ))}
        </Stack>
      </AsyncState>

      <Box component="form" onSubmit={handleAdd} sx={{ mt: 2 }}>
        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}
        <ToggleButtonGroup
          size="small"
          exclusive
          value={addMode}
          onChange={(_, mode) => mode && setAddMode(mode)}
          sx={{ mb: 1 }}
        >
          <ToggleButton value="search">Search by title</ToggleButton>
          <ToggleButton value="imdb">IMDB URL/ID</ToggleButton>
        </ToggleButtonGroup>
        <Stack direction="row" spacing={1}>
          {addMode === 'search' ? (
            <TmdbSearchAutocomplete
              search={moviesApi.search}
              value={selectedResult}
              onChange={setSelectedResult}
              label="Movie title"
            />
          ) : (
            <TextField
              label="IMDB URL or tt id"
              size="small"
              value={imdbUrlOrId}
              onChange={(e) => setImdbUrlOrId(e.target.value)}
              required
              fullWidth
            />
          )}
          <TextField
            label="Watch link (optional)"
            size="small"
            value={watchLink}
            onChange={(e) => setWatchLink(e.target.value)}
            fullWidth
          />
          <Button type="submit" variant="contained">
            Add
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}

function MovieItem({
  movie,
  clubId,
  scales,
  members,
  languagePrefs,
  onChange,
}: {
  movie: Movie
  clubId: string
  scales: RatingScale[]
  members: ClubMember[]
  languagePrefs: LanguagePreferences
  onChange: () => void
}) {
  const { data: reviews, reload: reloadReviews, silentReload: silentReloadReviews } = useAsync(() => moviesApi.listReviews(movie.id), [movie.id])
  useSmartPolling(silentReloadReviews, 15000)
  const [customTitle, setCustomTitle] = useState(movie.customTitle ?? '')
  const [preference, setPreference] = useState<'ORIGINAL' | 'CUSTOM'>(
    movie.displayTitlePreference === 'CUSTOM' ? 'CUSTOM' : 'ORIGINAL',
  )
  const [watchLink, setWatchLink] = useState(movie.watchLink ?? '')
  const [error, setError] = useState<string | null>(null)

  const title = resolveTitle(movie, languagePrefs)

  const handleSaveDetails = async () => {
    setError(null)
    try {
      await moviesApi.update(movie.id, { customTitle: customTitle || undefined, preference, watchLink: watchLink || undefined })
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handlePickLanguage = async (languageCode: string) => {
    setError(null)
    try {
      await moviesApi.update(movie.id, { preference: 'LANGUAGE', languageCode })
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRefresh = async () => {
    setError(null)
    try {
      await moviesApi.refreshMetadata(movie.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleDelete = async () => {
    setError(null)
    try {
      await moviesApi.remove(movie.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleMoveToWatchlist = async () => {
    if (!movie.tmdbId) return
    setError(null)
    try {
      await watchlistApi.add(clubId, 'MOVIE', movie.tmdbId)
      await moviesApi.remove(movie.id)
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRate = async (qualityOptionId?: string, sentimentOptionId?: string, comment?: string) => {
    await moviesApi.rate(movie.id, qualityOptionId, sentimentOptionId, comment)
    reloadReviews()
  }

  return (
    <Accordion>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexGrow: 1 }}>
          <Typography sx={{ flexGrow: 1 }}>{title}</Typography>
          {movie.year && <Chip size="small" label={movie.year} />}
          {ratingLabel(movie) && <Chip size="small" label={ratingLabel(movie)} />}
          {movie.displayTitlePreference === 'LANGUAGE' && movie.displayLanguageCode && (
            <Chip size="small" label={movie.displayLanguageCode} />
          )}
          <ImdbLink imdbId={movie.imdbId} />
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            Chosen by {memberName(members, movie.chosenById)} &middot; Director: {movie.director ?? '—'} &middot;
            Runtime:{' '}
            {movie.runtimeMinutes ? `${movie.runtimeMinutes}min` : '—'}
            {movie.genre && movie.genre.length > 0 ? ` · Genre: ${movie.genre.join(', ')}` : ''}
            {movie.productionCountries && movie.productionCountries.length > 0
              ? ` · Country: ${movie.productionCountries.join(', ')}`
              : ''}
          </Typography>

          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
            <TextField
              label="Custom title"
              size="small"
              value={customTitle}
              onChange={(e) => setCustomTitle(e.target.value)}
            />
            <Select
              size="small"
              value={preference}
              onChange={(e) => setPreference(e.target.value as 'ORIGINAL' | 'CUSTOM')}
            >
              <MenuItem value="ORIGINAL">ORIGINAL</MenuItem>
              <MenuItem value="CUSTOM">CUSTOM</MenuItem>
            </Select>
            <LanguagePickerDialog
              translations={movie.translations}
              selectedLanguageCode={movie.displayTitlePreference === 'LANGUAGE' ? movie.displayLanguageCode : null}
              onSelect={handlePickLanguage}
            />
            <TextField label="Watch link" size="small" value={watchLink} onChange={(e) => setWatchLink(e.target.value)} />
            <Button size="small" variant="outlined" onClick={handleSaveDetails}>
              Save
            </Button>
            <IconButton size="small" onClick={handleRefresh} title="Refresh metadata">
              <RefreshIcon fontSize="small" />
            </IconButton>
            <IconButton
              size="small"
              onClick={handleMoveToWatchlist}
              disabled={!movie.tmdbId}
              title="Move to watchlist"
            >
              <BookmarkAddIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" onClick={handleDelete} title="Delete pick">
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>

          <RatingForm
            scales={scales}
            onSave={handleRate}
          />

          <ReviewsList reviews={reviews ?? []} scales={scales} members={members} />
        </Stack>
      </AccordionDetails>
    </Accordion>
  )
}
