import EditIcon from '@mui/icons-material/Edit'
import LogoutIcon from '@mui/icons-material/Logout'
import {
  Alert,
  AppBar,
  Avatar,
  Box,
  Button,
  Container,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Snackbar,
  Stack,
  Switch,
  Toolbar,
  Typography,
} from '@mui/material'
import { useColorScheme } from '@mui/material/styles'
import { useRef, useState } from 'react'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import { membersApi } from '../api/members'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useDateDisplay } from '../settings/DateDisplayContext'
import { useMemberPhotos } from '../settings/MemberPhotoContext'
import { initials } from '../utils/members'

/** Every personal display preference (theme, member photos vs. initials, date format) used to be its own always-
 * visible toolbar icon -- now a single Edit icon opens this dialog with all three together, so the toolbar isn't
 * a growing row of one-off toggle icons as more preferences get added. */
function SettingsDialog() {
  const [open, setOpen] = useState(false)
  const { mode, setMode } = useColorScheme()
  const { showPhotos, setShowPhotos } = useMemberPhotos()
  const { dateStyle, setDateStyle } = useDateDisplay()
  const isDark = mode === 'dark'
  const isIso = dateStyle === 'iso'

  return (
    <>
      <IconButton color="inherit" onClick={() => setOpen(true)} title="Display settings">
        <EditIcon />
      </IconButton>
      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Display settings</DialogTitle>
        <DialogContent>
          <Stack spacing={1} sx={{ mt: 1 }}>
            <FormControlLabel
              control={<Switch checked={isDark} onChange={(e) => setMode(e.target.checked ? 'dark' : 'light')} />}
              label="Dark mode"
            />
            <FormControlLabel
              control={<Switch checked={showPhotos} onChange={(e) => setShowPhotos(e.target.checked)} />}
              label="Show member photos instead of initials"
            />
            <FormControlLabel
              control={<Switch checked={isIso} onChange={(e) => setDateStyle(e.target.checked ? 'iso' : 'compact')} />}
              label='Show dates as "2026-09-13" instead of "13 Sep 2026"'
            />
          </Stack>
        </DialogContent>
      </Dialog>
    </>
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
          <SettingsDialog />
          {member && (
            <>
              <OwnPhotoUploader />
              {member.isSiteAdmin && (
                <Button color="inherit" component={RouterLink} to="/admin" size="small" sx={{ mr: 1, ml: 1 }}>
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
