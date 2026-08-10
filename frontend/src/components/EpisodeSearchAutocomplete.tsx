import { Autocomplete, TextField } from '@mui/material'
import { useEffect, useState } from 'react'
import { episodesApi } from '../api/series'
import type { EpisodeSearchResult } from '../api/types'
import { resolveTitle, type LanguagePreferences } from '../utils/title'

const MIN_QUERY_LENGTH = 2

function episodeLabel(item: EpisodeSearchResult, languagePrefs: LanguagePreferences): string {
  const title = item.episodeTitle ? ` — ${item.episodeTitle}` : ''
  return `${resolveTitle(item.series, languagePrefs)} — S${item.seasonNumber}E${item.episodeNumber}${title}`
}

export function EpisodeSearchAutocomplete({
  clubId,
  value,
  onChange,
  languagePrefs,
  label = 'Search by episode or series title',
}: {
  clubId: string
  value: EpisodeSearchResult | null
  onChange: (item: EpisodeSearchResult | null) => void
  languagePrefs: LanguagePreferences
  label?: string
}) {
  const [inputValue, setInputValue] = useState('')
  const [options, setOptions] = useState<EpisodeSearchResult[]>([])

  useEffect(() => {
    if (inputValue.trim().length < MIN_QUERY_LENGTH) {
      setOptions([])
      return
    }
    let active = true
    const timeout = setTimeout(() => {
      episodesApi.search(clubId, inputValue.trim()).then((results) => {
        if (active) setOptions(results)
      })
    }, 300)
    return () => {
      active = false
      clearTimeout(timeout)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inputValue, clubId])

  return (
    <Autocomplete
      size="small"
      fullWidth
      options={options}
      filterOptions={(x) => x}
      getOptionLabel={(item) => episodeLabel(item, languagePrefs)}
      isOptionEqualToValue={(a, b) => a.episodeId === b.episodeId}
      value={value}
      onChange={(_, option) => onChange(option)}
      inputValue={inputValue}
      onInputChange={(_, newValue) => setInputValue(newValue)}
      noOptionsText={inputValue.trim().length < MIN_QUERY_LENGTH ? 'Type to search' : 'No episodes found'}
      renderInput={(params) => <TextField {...params} label={label} />}
      sx={{ minWidth: 320 }}
    />
  )
}
