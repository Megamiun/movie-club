import { Alert, Box, CircularProgress } from '@mui/material'
import type { ReactNode } from 'react'

interface Props {
  loading: boolean
  error: string | null
  children: ReactNode
}

export function AsyncState({ loading, error, children }: Props) {
  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    )
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>
  }

  return <>{children}</>
}
