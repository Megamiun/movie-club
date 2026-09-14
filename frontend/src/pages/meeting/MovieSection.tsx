import BookmarkAddIcon from '@mui/icons-material/BookmarkAdd'
import DeleteIcon from '@mui/icons-material/Delete'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import LinkIcon from '@mui/icons-material/Link'
import RefreshIcon from '@mui/icons-material/Refresh'
import TranslateIcon from '@mui/icons-material/Translate'
import {
  Alert,
  Box,
  Button,
  Collapse,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useMediaQuery,
} from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { moviesApi } from '../../api/movies'
import { mediaItemsApi } from '../../api/mediaItems'
import { watchlistApi } from '../../api/watchlist'
import { ApiError } from '../../api/client'
import type { ClubMember, Movie, RatingScale, TmdbSearchResult } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { CountryFlags } from '../../components/CountryFlags'
import { ImdbLink } from '../../components/ImdbLink'
import { MemberBadge } from '../../components/MemberBadge'
import { ReviewsList } from '../../components/ReviewsList'
import { TmdbSearchAutocomplete } from '../../components/TmdbSearchAutocomplete'
import { useAuth } from '../../auth/AuthContext'
import { useAsync } from '../../hooks/useAsync'
import { useContainerWidth } from '../../hooks/useContainerWidth'
import { useSmartPolling } from '../../hooks/useSmartPolling'
import { formatDuration } from '../../utils/duration'
import { ratingLabel } from '../../utils/rating'
import { resolveTitle, type LanguagePreferences } from '../../utils/title'

const COLLAPSED_POSTER_WIDTH = 64
const EXPANDED_POSTER_WIDTH = 350

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
  // Hides the whole section (heading, divider included) once it's known there's nothing to show -- but the add
  // Dialog below stays mounted regardless, since that's the only way to add the meeting's *first* movie once the
  // heading that used to always announce "Movies" is gone.
  const isEmpty = !loading && !error && (movies?.length ?? 0) === 0
  // A movie starts pre-expanded when the list is wide enough to comfortably fit two expanded posters side by
  // side -- a simple, unit-based proxy for "this is a wide desktop view, not a cramped one" rather than an
  // arbitrary pixel breakpoint disconnected from the poster size actually in play.
  const [listRef, listWidth] = useContainerWidth<HTMLDivElement>()
  const defaultExpanded = listWidth >= 2 * EXPANDED_POSTER_WIDTH
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
      {!isEmpty && (
        <>
          <Divider sx={{ mb: 3 }} />
          <Typography variant="h6" gutterBottom>
            Movies
          </Typography>

          <AsyncState loading={loading} error={error}>
            <Stack ref={listRef} spacing={1}>
              {movies?.map((movie) => (
                <MovieItem
                  key={movie.id}
                  movie={movie}
                  clubId={clubId}
                  scales={scales}
                  members={members}
                  languagePrefs={languagePrefs}
                  onChange={reload}
                  defaultExpanded={defaultExpanded}
                />
              ))}
            </Stack>
          </AsyncState>
        </>
      )}

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

/** Encodes the merged title dialog's radio selection as a single string: 'ORIGINAL', 'CUSTOM', or
 * `LANGUAGE:<code>` for one specific exhibition-language translation -- RadioGroup values have to be strings, and
 * this is the one field (a translation's `languageCode`) that already uniquely identifies each radio option. */
type TitleMode = 'ORIGINAL' | 'CUSTOM' | `LANGUAGE:${string}`

function titleModeFor(movie: Movie): TitleMode {
  if (movie.displayTitlePreference === 'CUSTOM') return 'CUSTOM'
  if (movie.displayTitlePreference === 'LANGUAGE' && movie.displayLanguageCode) return `LANGUAGE:${movie.displayLanguageCode}`
  return 'ORIGINAL'
}

function MovieItem({
  movie,
  clubId,
  scales,
  members,
  languagePrefs,
  onChange,
  defaultExpanded,
}: {
  movie: Movie
  clubId: string
  scales: RatingScale[]
  members: ClubMember[]
  languagePrefs: LanguagePreferences
  onChange: () => void
  defaultExpanded: boolean
}) {
  const { member: viewer } = useAuth()
  const { data: reviews, reload: reloadReviews, silentReload: silentReloadReviews } = useAsync(() => moviesApi.listReviews(movie.id), [movie.id])
  useSmartPolling(silentReloadReviews, 7500)
  const [expanded, setExpanded] = useState(defaultExpanded)
  useEffect(() => {
    if (defaultExpanded) setExpanded(true)
  }, [defaultExpanded])
  // Small screen *and* portrait, together -- a wide phone held landscape (or a small-but-landscape window) still
  // has room to keep the poster beside the info, so orientation alone or width alone isn't the right signal.
  const isNarrowPortrait = useMediaQuery('(max-width: 600px) and (orientation: portrait)')

  const [titleMode, setTitleMode] = useState<TitleMode>(() => titleModeFor(movie))
  const [customTitle, setCustomTitle] = useState(movie.customTitle ?? '')
  const [watchLink, setWatchLink] = useState(movie.watchLink ?? '')
  const [titleDialogOpen, setTitleDialogOpen] = useState(false)
  const [watchLinkDialogOpen, setWatchLinkDialogOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const title = resolveTitle(movie, languagePrefs)
  const chooser = members.find((m) => m.memberId === movie.chosenById)
  const myReview = reviews?.find((r) => r.memberId === viewer?.id)

  const handleOpenTitleDialog = () => {
    setTitleMode(titleModeFor(movie))
    setCustomTitle(movie.customTitle ?? '')
    setTitleDialogOpen(true)
  }

  const handleSaveTitle = async () => {
    setError(null)
    try {
      if (titleMode === 'CUSTOM') {
        await moviesApi.update(movie.id, { customTitle: customTitle || undefined, preference: 'CUSTOM' })
      } else if (titleMode.startsWith('LANGUAGE:')) {
        await moviesApi.update(movie.id, { preference: 'LANGUAGE', languageCode: titleMode.slice('LANGUAGE:'.length) })
      } else {
        await moviesApi.update(movie.id, { preference: 'ORIGINAL' })
      }
      onChange()
      setTitleDialogOpen(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleSaveWatchLink = async () => {
    setError(null)
    try {
      await moviesApi.update(movie.id, { watchLink: watchLink || undefined })
      onChange()
      setWatchLinkDialogOpen(false)
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

  const handleSaveQuality = async (optionId: string | null) => {
    await moviesApi.rateQuality(movie.id, optionId)
    reloadReviews()
  }

  const handleSaveSentiment = async (optionId: string | null) => {
    await moviesApi.rateSentiment(movie.id, optionId)
    reloadReviews()
  }

  // The combined PUT is the only endpoint that can touch a comment at all (no comment-only PATCH exists), so this
  // has to pass the viewer's *current* quality/sentiment through unchanged -- otherwise saving just a comment
  // would silently wipe out whatever rating handleSaveQuality/handleSaveSentiment already saved.
  const handleSaveComment = async (comment: string | null) => {
    await moviesApi.rate(movie.id, myReview?.qualityOptionId ?? undefined, myReview?.sentimentOptionId ?? undefined, comment ?? undefined)
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
          <IconButton size="small" onClick={handleOpenTitleDialog} title="Title">
            <TranslateIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" onClick={() => setWatchLinkDialogOpen(true)} title="Edit watch link">
            <LinkIcon fontSize="small" />
          </IconButton>
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

      {(() => {
        // On a small, portrait screen, an expanded poster occupies most of the row's width and the info moves
        // below it instead of beside it -- 350px (EXPANDED_POSTER_WIDTH) alongside any meaningful text simply
        // doesn't fit a ~390px-wide phone. Collapsed (64px) always fits fine beside text, on any screen.
        const stackedPortrait = expanded && isNarrowPortrait
        const posterWidth = expanded ? (stackedPortrait ? '100%' : EXPANDED_POSTER_WIDTH) : COLLAPSED_POSTER_WIDTH
        return (
          <Stack direction={stackedPortrait ? 'column' : 'row'} spacing={2}>
            {/* One poster element total -- its width just grows on expand, rather than a second, separate image
             * rendered inside the expanded details (the old Accordion summary/details split did exactly that). */}
            <Stack spacing={0.5} sx={{ alignItems: 'center', flexShrink: 0, width: stackedPortrait ? '100%' : 'auto' }}>
              {movie.posterUrl ? (
                <Box
                  component="img"
                  src={movie.posterUrl}
                  alt=""
                  onClick={() => setExpanded((prev) => !prev)}
                  sx={{
                    width: posterWidth,
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
                    width: posterWidth,
                    aspectRatio: '2 / 3',
                    borderRadius: 1,
                    bgcolor: 'action.hover',
                    cursor: 'pointer',
                    transition: 'width 0.2s ease-in-out',
                  }}
                />
              )}
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
                <ImdbLink imdbId={movie.imdbId} />
                <ExpandMoreIcon
                  fontSize="small"
                  color="action"
                  sx={{ ml: 'auto', ...(expanded && { transform: 'rotate(180deg)' }), transition: 'transform 0.2s' }}
                />
              </Stack>

              <Collapse in={expanded}>
                <Stack spacing={1.25} sx={{ mt: 1 }}>
                  <Typography variant="body2" color="text.secondary">
                    <Box component="span" sx={{ fontWeight: 700 }}>Year:</Box> {movie.year ?? '—'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <Box component="span" sx={{ fontWeight: 700 }}>IMDb rating:</Box> {ratingLabel(movie) ?? '—'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <Box component="span" sx={{ fontWeight: 700 }}>Director:</Box>{' '}
                    {movie.director ? (
                      movie.directorImdbId ? (
                        <ImdbLink imdbId={movie.directorImdbId} kind="name" variant="text">
                          {movie.director}
                        </ImdbLink>
                      ) : movie.director
                    ) : '—'
                    }
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <Box component="span" sx={{ fontWeight: 700 }}>Runtime:</Box>{' '}
                    {movie.runtimeMinutes ? formatDuration(movie.runtimeMinutes) : '—'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    <Box component="span" sx={{ fontWeight: 700 }}>Genre:</Box>{' '}
                    {movie.genre && movie.genre.length > 0 ? movie.genre.join(', ') : '—'}
                  </Typography>
                  <ReviewsList
                    reviews={reviews ?? []}
                    scales={scales}
                    members={members}
                    viewerMemberId={viewer?.id}
                    onSaveQuality={handleSaveQuality}
                    onSaveSentiment={handleSaveSentiment}
                    onSaveComment={handleSaveComment}
                  />
                </Stack>
              </Collapse>
            </Stack>
          </Stack>
        )
      })()}

      <Dialog open={titleDialogOpen} onClose={() => setTitleDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Title</DialogTitle>
        <DialogContent>
          <RadioGroup value={titleMode} onChange={(e) => setTitleMode(e.target.value as TitleMode)} sx={{ mt: 1 }}>
            <FormControlLabel value="ORIGINAL" control={<Radio />} label="Default" />
            <FormControlLabel value="CUSTOM" control={<Radio />} label="Custom" />
            {movie.translations.map((t) => (
              <FormControlLabel
                key={`${t.languageCode}-${t.countryCode}`}
                value={`LANGUAGE:${t.languageCode}`}
                control={<Radio />}
                label={`${t.title} (${t.englishName})`}
              />
            ))}
          </RadioGroup>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            {titleMode === 'CUSTOM' && (
              <TextField
                label="Custom title"
                size="small"
                value={customTitle}
                onChange={(e) => setCustomTitle(e.target.value)}
                fullWidth
                autoFocus
              />
            )}
            <Button variant="contained" onClick={handleSaveTitle} sx={{ alignSelf: 'flex-start' }}>
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
    </Box>
  )
}
