import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import PersonIcon from '@mui/icons-material/Person'
import PersonOutlineIcon from '@mui/icons-material/PersonOutlineOutlined'
import { Alert, AppBar, Avatar, Box, Button, Container, IconButton, Snackbar, Toolbar, Typography } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'
import { useRef, useState } from 'react'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import { membersApi } from '../api/members'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useMemberPhotos } from '../settings/MemberPhotoContext'
import { initials } from '../utils/members'

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

function MemberPhotoToggle() {
  const { showPhotos, setShowPhotos } = useMemberPhotos()

  return (
    <IconButton
      color="inherit"
      onClick={() => setShowPhotos(!showPhotos)}
      title={showPhotos ? 'Show colored initials instead of photos' : 'Show member photos instead of initials'}
    >
      {showPhotos ? <PersonIcon /> : <PersonOutlineIcon />}
    </IconButton>
  )
}

/** Clicking the viewer's own avatar opens a file picker and uploads straight away -- no separate profile page
 * exists yet for this, and a photo is the only thing about a member's own account that's ever user-editable here
 * (name/username/email all come from registration). Self-service only (see MemberService.uploadPhoto). */
function OwnPhotoUploader() {
  const { member, updateMember } = useAuth()
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!member) return null

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setUploading(true)
    setError(null)
    try {
      const updated = await membersApi.uploadPhoto(member.id, file)
      updateMember(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong uploading your photo')
    } finally {
      setUploading(false)
    }
  }

  return (
    <>
      <input ref={inputRef} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={handleFileChange} />
      <IconButton
        onClick={() => inputRef.current?.click()}
        disabled={uploading}
        title="Change your photo"
        sx={{ p: 0.25 }}
      >
        <Avatar src={member.photoUrl ?? undefined} sx={{ width: 28, height: 28, fontSize: '0.7rem' }}>
          {initials(member.name)}
        </Avatar>
      </IconButton>
      <Snackbar open={Boolean(error)} autoHideDuration={5000} onClose={() => setError(null)}>
        <Alert severity="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      </Snackbar>
    </>
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
          <MemberPhotoToggle />
          {member && (
            <>
              <OwnPhotoUploader />
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
