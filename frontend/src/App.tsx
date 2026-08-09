import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './auth/RequireAuth'
import { RatingDisplayProvider } from './settings/RatingDisplayContext'
import { AppLayout } from './layout/AppLayout'
import { ClubLayout } from './layout/ClubLayout'
import { AdminPage } from './pages/AdminPage'
import { ClubOverviewPage } from './pages/ClubOverviewPage'
import { ClubsPage } from './pages/ClubsPage'
import { ImportPage } from './pages/ImportPage'
import { InvitePage } from './pages/InvitePage'
import { LoginPage } from './pages/LoginPage'
import { MeetingDetailPage } from './pages/MeetingDetailPage'
import { MeetingsPage } from './pages/MeetingsPage'
import { MoviesPage } from './pages/MoviesPage'
import { RegisterPage } from './pages/RegisterPage'
import { SeasonDetailPage } from './pages/SeasonDetailPage'
import { SeriesDetailPage } from './pages/SeriesDetailPage'
import { SeriesListPage } from './pages/SeriesListPage'
import { WatchlistPage } from './pages/WatchlistPage'
import { theme } from './theme'

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>
          <RatingDisplayProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              <Route
                element={
                  <RequireAuth>
                    <AppLayout />
                  </RequireAuth>
                }
              >
                <Route index element={<Navigate to="/clubs" replace />} />
                <Route path="/clubs" element={<ClubsPage />} />
                <Route path="/invite" element={<InvitePage />} />
                <Route path="/admin" element={<AdminPage />} />
                <Route path="/meetings/:meetingId" element={<MeetingDetailPage />} />
                <Route path="/series/:seriesId" element={<SeriesDetailPage />} />
                <Route path="/seasons/:seasonId" element={<SeasonDetailPage />} />

                <Route path="/clubs/:clubId" element={<ClubLayout />}>
                  <Route index element={<MeetingsPage />} />
                  <Route path="movies" element={<MoviesPage />} />
                  <Route path="series" element={<SeriesListPage />} />
                  <Route path="watchlist" element={<WatchlistPage />} />
                  <Route path="import" element={<ImportPage />} />
                  <Route path="overview" element={<ClubOverviewPage />} />
                </Route>
              </Route>

              <Route path="*" element={<Navigate to="/clubs" replace />} />
            </Routes>
          </RatingDisplayProvider>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}

export default App
