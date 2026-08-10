import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import { AppBar, Box, Button, Container, IconButton, Toolbar, Typography } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function ThemeModeToggle() {
  const { mode, setMode } = useColorScheme()
  const isDark = mode === 'dark'

  return (
    <IconButton
      color="inherit"
      onClick={() => setMode(isDark ? 'light' : 'dark')}
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {isDark ? <LightModeIcon /> : <DarkModeIcon />}
    </IconButton>
  )
}

export function AppLayout() {
  const { member, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/clubs"
            sx={{ flexGrow: 1, color: 'inherit', textDecoration: 'none' }}
          >
            Movie Club
          </Typography>
          <ThemeModeToggle />
          {member && (
            <>
              <Typography variant="body2" sx={{ mr: 2, ml: 1 }}>
                {member.name} (@{member.username})
              </Typography>
              {member.isSiteAdmin && (
                <Button color="inherit" component={RouterLink} to="/admin" size="small" sx={{ mr: 1 }}>
                  Admin
                </Button>
              )}
              <Button color="inherit" component={RouterLink} to="/invite" size="small" sx={{ mr: 1 }}>
                Invite
              </Button>
              <IconButton color="inherit" onClick={handleLogout} title="Log out">
                <LogoutIcon />
              </IconButton>
            </>
          )}
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="xl" sx={{ py: 3, flexGrow: 1 }}>
        <Outlet />
      </Container>
      <Box component="footer" sx={{ py: 2, textAlign: 'center' }}>
        <Typography variant="caption" color="text.secondary">
          This product uses the TMDB API but is not endorsed or certified by TMDB.
        </Typography>
      </Box>
    </Box>
  )
}
