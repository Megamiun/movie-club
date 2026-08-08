import { Autocomplete, TextField } from '@mui/material'
import { useEffect, useState } from 'react'
import type { TmdbSearchResult } from '../api/types'

const MIN_QUERY_LENGTH = 2

export function TmdbSearchAutocomplete({
  search,
  value,
  onChange,
  label = 'Search by title',
}: {
  search: (query: string) => Promise<TmdbSearchResult[]>
  value: TmdbSearchResult | null
  onChange: (item: TmdbSearchResult | null) => void
  label?: string
}) {
  const [inputValue, setInputValue] = useState('')
  const [options, setOptions] = useState<TmdbSearchResult[]>([])

  useEffect(() => {
    if (inputValue.trim().length < MIN_QUERY_LENGTH) {
      setOptions([])
      return
    }
    let active = true
    const timeout = setTimeout(() => {
      search(inputValue.trim()).then((results) => {
        if (active) setOptions(results)
      })
    }, 300)
    return () => {
      active = false
      clearTimeout(timeout)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inputValue])

  return (
    <Autocomplete
      size="small"
      options={options}
      filterOptions={(x) => x}
      getOptionLabel={(item) => `${item.title}${item.year ? ` (${item.year})` : ''}`}
      isOptionEqualToValue={(a, b) => a.tmdbId === b.tmdbId}
      value={value}
      onChange={(_, option) => onChange(option)}
      inputValue={inputValue}
      onInputChange={(_, newValue) => setInputValue(newValue)}
      noOptionsText={inputValue.trim().length < MIN_QUERY_LENGTH ? 'Type to search' : 'No results found'}
      renderOption={({ key: _key, ...props }, option) => (
        <li key={option.tmdbId} {...props}>
          {option.title}
          {option.year ? ` (${option.year})` : ''}
          {option.originalTitle !== option.title ? ` — ${option.originalTitle}` : ''}
        </li>
      )}
      renderInput={(params) => <TextField {...params} label={label} />}
      sx={{ minWidth: 280 }}
    />
  )
}
