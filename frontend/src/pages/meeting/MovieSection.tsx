import BookmarkAddIcon from '@mui/icons-material/BookmarkAdd'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import LinkIcon from '@mui/icons-material/Link'
import RefreshIcon from '@mui/icons-material/Refresh'
import StarIcon from '@mui/icons-material/Star'
import StarBorderIcon from '@mui/icons-material/StarBorder'
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Popover,
  Select,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { moviesApi } from '../../api/movies'
import { mediaItemsApi } from '../../api/mediaItems'
import { watchlistApi } from '../../api/watchlist'
import { ApiError } from '../../api/client'
import type { ClubMember, Movie, RatingScale, TmdbSearchResult } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { CountryFlags } from '../../components/CountryFlags'
import { ImdbLink } from '../../components/ImdbLink'
import { LanguagePickerDialog } from '../../components/LanguagePickerDialog'
import { MemberBadge } from '../../components/MemberBadge'
import { RatingForm } from '../../components/RatingForm'
import { ReviewsList } from '../../components/ReviewsList'
import { TmdbSearchAutocomplete } from '../../components/TmdbSearchAutocomplete'
import { useAuth } from '../../auth/AuthContext'
import { useAsync } from '../../hooks/useAsync'
import { useSmartPolling } from '../../hooks/useSmartPolling'
import { formatDuration } from '../../utils/duration'
import { ratingLabel } from '../../utils/rating'
import { resolveTitle, type LanguagePreferences } from '../../utils/title'

export function MovieSection({
  meetingId,
  clubId,
  scales,
  members,
  languagePrefs,
  showAddForm,
  onCloseAddForm,
}: {
  meetingId: string
  clubId: string
  scales: RatingScale[]
  members: ClubMember[]
  languagePrefs: LanguagePreferences
  showAddForm: boolean
  onCloseAddForm: () => void
}) {
  const { data: movies, loading, error, reload, silentReload } = useAsync(() => moviesApi.list(meetingId), [meetingId])
  useSmartPolling(silentReload, 7500)
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
      onCloseAddForm()
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

      <Dialog open={showAddForm} onClose={onCloseAddForm} fullWidth maxWidth="sm">
        <DialogTitle>Add a movie</DialogTitle>
        <DialogContent>
          <Box component="form" onSubmit={handleAdd} sx={{ mt: 1 }}>
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
            <Stack spacing={1}>
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
        </DialogContent>
      </Dialog>
    </Box>
  )
}

const COLLAPSED_POSTER_WIDTH = 64
const EXPANDED_POSTER_WIDTH = 220

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
  const { member: viewer } = useAuth()
  const { data: reviews, reload: reloadReviews, silentReload: silentReloadReviews } = useAsync(() => moviesApi.listReviews(movie.id), [movie.id])
  useSmartPolling(silentReloadReviews, 7500)
  const [expanded, setExpanded] = useState(false)
  const [customTitle, setCustomTitle] = useState(movie.customTitle ?? '')
  const [preference, setPreference] = useState<'ORIGINAL' | 'CUSTOM'>(
    movie.displayTitlePreference === 'CUSTOM' ? 'CUSTOM' : 'ORIGINAL',
  )
  const [watchLink, setWatchLink] = useState(movie.watchLink ?? '')
  const [titleDialogOpen, setTitleDialogOpen] = useState(false)
  const [watchLinkDialogOpen, setWatchLinkDialogOpen] = useState(false)
  const [ratingAnchorEl, setRatingAnchorEl] = useState<HTMLElement | null>(null)
  const [error, setError] = useState<string | null>(null)

  const title = resolveTitle(movie, languagePrefs)
  const chooser = members.find((m) => m.memberId === movie.chosenById)
  const viewerMember = members.find((m) => m.memberId === viewer?.id)
  const myReview = reviews?.find((r) => r.memberId === viewer?.id)
  const haveIRated = Boolean(myReview?.qualityOptionId || myReview?.sentimentOptionId)

  const handleSaveDetails = async () => {
    setError(null)
    try {
      await moviesApi.update(movie.id, { customTitle: customTitle || undefined, preference, watchLink: watchLink || undefined })
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleSaveTitle = async () => {
    await handleSaveDetails()
    setTitleDialogOpen(false)
  }

  const handleSaveWatchLink = async () => {
    await handleSaveDetails()
    setWatchLinkDialogOpen(false)
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
    if (!movie.mediaItemId) return
    setError(null)
    try {
      await mediaItemsApi.refreshMetadata(movie.mediaItemId)
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
    <Box sx={{ pb: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
      {error && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {error}
        </Alert>
      )}

      {/* Action icons sit above the poster, only shown once expanded -- same "click to reveal" shape the old
       * Accordion had, just reordered so these no longer compete with the poster/text for space below it. */}
      <Collapse in={expanded}>
        <Stack direction="row" spacing={0.5} sx={{ mb: 1, flexWrap: 'wrap' }}>
          <IconButton size="small" onClick={() => setTitleDialogOpen(true)} title="Edit title">
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={() => setWatchLinkDialogOpen(true)} title="Edit watch link">
            <LinkIcon fontSize="small" />
          </IconButton>
          <LanguagePickerDialog
            translations={movie.translations}
            selectedLanguageCode={movie.displayTitlePreference === 'LANGUAGE' ? movie.displayLanguageCode : null}
            onSelect={handlePickLanguage}
          />
          <IconButton size="small" onClick={handleRefresh} disabled={!movie.mediaItemId} title="Refresh metadata">
            <RefreshIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={handleMoveToWatchlist} disabled={!movie.tmdbId} title="Move to watchlist">
            <BookmarkAddIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={handleDelete} title="Delete pick">
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Stack>
      </Collapse>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        {/* One poster element total -- its width just grows on expand, rather than a second, separate image
         * rendered inside the expanded details (the old Accordion summary/details split did exactly that). */}
        <Stack spacing={0.5} sx={{ alignItems: 'center', flexShrink: 0 }}>
          {movie.posterUrl ? (
            <Box
              component="img"
              src={movie.posterUrl}
              alt=""
              onClick={() => setExpanded((prev) => !prev)}
              sx={{
                width: expanded ? EXPANDED_POSTER_WIDTH : COLLAPSED_POSTER_WIDTH,
                aspectRatio: '2 / 3',
                objectFit: 'cover',
                borderRadius: 1,
                cursor: 'pointer',
                transition: 'width 0.2s ease-in-out',
              }}
            />
          ) : (
            <Box
              onClick={() => setExpanded((prev) => !prev)}
              sx={{
                width: expanded ? EXPANDED_POSTER_WIDTH : COLLAPSED_POSTER_WIDTH,
                aspectRatio: '2 / 3',
                borderRadius: 1,
                bgcolor: 'action.hover',
                cursor: 'pointer',
                transition: 'width 0.2s ease-in-out',
              }}
            />
          )}
          {/* Below the poster: the viewer's own photo plus a rating icon -- always visible (not gated behind
           * expanding the block), and it stays exactly the same icon whether or not a rating exists yet, so it
           * always doubles as the way to go back and edit one already given. */}
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <MemberBadge member={viewerMember} size={22} />
            <IconButton
              size="small"
              onClick={(e) => setRatingAnchorEl(e.currentTarget)}
              title={haveIRated ? 'Edit your rating' : 'Rate this movie'}
            >
              {haveIRated ? <StarIcon fontSize="small" color="primary" /> : <StarBorderIcon fontSize="small" />}
            </IconButton>
          </Stack>
        </Stack>

        <Stack spacing={0.5} sx={{ flexGrow: 1, minWidth: 0 }}>
          <Stack
            direction="row"
            spacing={1}
            sx={{ alignItems: 'center', flexWrap: 'wrap', cursor: 'pointer' }}
            onClick={() => setExpanded((prev) => !prev)}
          >
            <MemberBadge member={chooser} />
            <Typography sx={{ fontWeight: 500 }}>{title}</Typography>
            <CountryFlags codes={movie.originCountry} />
            {movie.displayTitlePreference === 'LANGUAGE' && movie.displayLanguageCode && (
              <Chip size="small" label={movie.displayLanguageCode} />
            )}
            <ImdbLink imdbId={movie.imdbId} />
            <ExpandMoreIcon
              fontSize="small"
              color="action"
              sx={{ ml: 'auto', ...(expanded && { transform: 'rotate(180deg)' }), transition: 'transform 0.2s' }}
            />
          </Stack>

          <Collapse in={expanded}>
            <Stack spacing={0.25} sx={{ mt: 0.5 }}>
              <Typography variant="body2" color="text.secondary">
                Year: {movie.year ?? '—'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                IMDb rating: {ratingLabel(movie) ?? '—'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Director:{' '}
                {movie.director ? (
                  movie.directorImdbId ? (
                    <ImdbLink imdbId={movie.directorImdbId} kind="name" variant="text">
                      {movie.director}
                    </ImdbLink>
                  ) : (
                    movie.director
                  )
                ) : (
                  '—'
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Runtime: {movie.runtimeMinutes ? formatDuration(movie.runtimeMinutes) : '—'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Genre: {movie.genre && movie.genre.length > 0 ? movie.genre.join(', ') : '—'}
              </Typography>

              <ReviewsList reviews={reviews ?? []} scales={scales} members={members} />
            </Stack>
          </Collapse>
        </Stack>
      </Stack>

      <Dialog open={titleDialogOpen} onClose={() => setTitleDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Edit title</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <TextField
              label="Custom title"
              size="small"
              value={customTitle}
              onChange={(e) => setCustomTitle(e.target.value)}
              fullWidth
            />
            <Select size="small" value={preference} onChange={(e) => setPreference(e.target.value as 'ORIGINAL' | 'CUSTOM')}>
              <MenuItem value="ORIGINAL">ORIGINAL</MenuItem>
              <MenuItem value="CUSTOM">CUSTOM</MenuItem>
            </Select>
            <Button variant="contained" onClick={handleSaveTitle}>
              Save
            </Button>
          </Stack>
        </DialogContent>
      </Dialog>

      <Dialog open={watchLinkDialogOpen} onClose={() => setWatchLinkDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Edit watch link</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <TextField
              label="Watch link"
              size="small"
              value={watchLink}
              onChange={(e) => setWatchLink(e.target.value)}
              fullWidth
            />
            <Button variant="contained" onClick={handleSaveWatchLink}>
              Save
            </Button>
          </Stack>
        </DialogContent>
      </Dialog>

      <Popover
        open={Boolean(ratingAnchorEl)}
        anchorEl={ratingAnchorEl}
        onClose={() => setRatingAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Box sx={{ p: 1.5, minWidth: 280 }}>
          <RatingForm
            scales={scales}
            initialQualityOptionId={myReview?.qualityOptionId}
            initialSentimentOptionId={myReview?.sentimentOptionId}
            initialComment={myReview?.comment}
            onSave={async (quality, sentiment, comment) => {
              await handleRate(quality, sentiment, comment)
              setRatingAnchorEl(null)
            }}
          />
        </Box>
      </Popover>
    </Box>
  )
}
