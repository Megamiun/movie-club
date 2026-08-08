import LogoutIcon from '@mui/icons-material/Logout'
import { AppBar, Box, Button, Container, IconButton, Toolbar, Typography } from '@mui/material'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function AppLayout() {
  const { member, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <Box display="flex" flexDirection="column" minHeight="100vh">
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
          {member && (
            <>
              <Typography variant="body2" sx={{ mr: 2 }}>
                {member.name}
              </Typography>
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
      <Container component="main" maxWidth="lg" sx={{ py: 3, flexGrow: 1 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
