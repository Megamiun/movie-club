import { Autocomplete, TextField } from '@mui/material'
import { useEffect, useState } from 'react'
import { membersApi } from '../api/members'
import type { MemberSummary } from '../api/types'

const MIN_QUERY_LENGTH = 2

export function MemberSearchAutocomplete({
  value,
  onChange,
  excludeMemberIds = [],
  label = 'Search by name or email',
  required = false,
}: {
  value: MemberSummary | null
  onChange: (member: MemberSummary | null) => void
  excludeMemberIds?: string[]
  label?: string
  required?: boolean
}) {
  const [inputValue, setInputValue] = useState('')
  const [options, setOptions] = useState<MemberSummary[]>([])

  useEffect(() => {
    if (inputValue.trim().length < MIN_QUERY_LENGTH) {
      setOptions([])
      return
    }
    let active = true
    const timeout = setTimeout(() => {
      membersApi.search(inputValue.trim()).then((results) => {
        if (active) setOptions(results.filter((m) => !excludeMemberIds.includes(m.id)))
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
      getOptionLabel={(m) => `${m.name} (${m.email})`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      value={value}
      onChange={(_, option) => onChange(option)}
      inputValue={inputValue}
      onInputChange={(_, newValue) => setInputValue(newValue)}
      noOptionsText={inputValue.trim().length < MIN_QUERY_LENGTH ? 'Type to search' : 'No members found'}
      renderInput={(params) => <TextField {...params} label={label} required={required} />}
      sx={{ minWidth: 280 }}
    />
  )
}
