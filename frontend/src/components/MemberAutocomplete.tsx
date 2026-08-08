import { Autocomplete, TextField } from '@mui/material'
import type { ClubMember } from '../api/types'

export function MemberAutocomplete({
  members,
  value,
  onChange,
  label,
  required = false,
  size = 'small',
  variant = 'outlined',
  fullWidth = false,
}: {
  members: ClubMember[]
  value: string | null
  onChange: (memberId: string | null) => void
  label?: string
  required?: boolean
  size?: 'small' | 'medium'
  variant?: 'outlined' | 'standard'
  fullWidth?: boolean
}) {
  const selected = members.find((m) => m.memberId === value) ?? null

  return (
    <Autocomplete
      size={size}
      fullWidth={fullWidth}
      options={members}
      getOptionLabel={(m) => m.name}
      isOptionEqualToValue={(a, b) => a.memberId === b.memberId}
      value={selected}
      onChange={(_, option) => onChange(option?.memberId ?? null)}
      renderInput={(params) => <TextField {...params} label={label} required={required} variant={variant} />}
      sx={{ minWidth: 200 }}
    />
  )
}
