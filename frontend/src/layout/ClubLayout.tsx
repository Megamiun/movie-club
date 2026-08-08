import { Box, Tab, Tabs, Typography } from '@mui/material'
import { useMemo } from 'react'
import { Link as RouterLink, Outlet, useLocation, useParams } from 'react-router-dom'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import { clubsApi } from '../api/clubs'

const TABS = [
  { path: '', label: 'Overview' },
  { path: 'meetings', label: 'Meetings' },
  { path: 'series', label: 'Series' },
  { path: 'watchlist', label: 'Watchlist' },
  { path: 'import', label: 'Import' },
]

export function ClubLayout() {
  const { clubId } = useParams<{ clubId: string }>()
  const location = useLocation()
  const { data: club, loading, error, reload } = useAsync(() => clubsApi.get(clubId!), [clubId])

  const activeTab = useMemo(() => {
    const suffix = location.pathname.split(`/clubs/${clubId}/`)[1] ?? ''
    const segment = suffix.split('/')[0] ?? ''
    return TABS.some((tab) => tab.path === segment) ? segment : ''
  }, [location.pathname, clubId])

  return (
    <Box>
      <AsyncState loading={loading} error={error}>
        <Typography variant="h4" gutterBottom>
          {club?.name}
        </Typography>
        <Tabs value={activeTab} sx={{ mb: 3 }}>
          {TABS.map((tab) => (
            <Tab
              key={tab.path}
              value={tab.path}
              label={tab.label}
              component={RouterLink}
              to={tab.path ? `/clubs/${clubId}/${tab.path}` : `/clubs/${clubId}`}
            />
          ))}
        </Tabs>
        <Outlet context={{ club, reload }} />
      </AsyncState>
    </Box>
  )
}
