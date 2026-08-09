import VerifiedUserIcon from '@mui/icons-material/VerifiedUser'
import {
  Box,
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { adminApi } from '../api/admin'
import { AsyncState } from '../components/AsyncState'
import { ImdbLink } from '../components/ImdbLink'
import { useAsync } from '../hooks/useAsync'

export function AdminPage() {
  return (
    <Stack spacing={4}>
      <Typography variant="h4" gutterBottom>
        Site Admin
      </Typography>
      <UsersSection />
      <MediaItemsSection />
    </Stack>
  )
}

function UsersSection() {
  const { data: users, loading, error } = useAsync(() => adminApi.listUsers(), [])

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Users
      </Typography>
      <AsyncState loading={loading} error={error}>
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Username</TableCell>
                <TableCell>Email</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {users?.map((user) => (
                <TableRow key={user.id}>
                  <TableCell>{user.name}</TableCell>
                  <TableCell>@{user.username}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>
                    {user.isSiteAdmin && (
                      <Chip size="small" icon={<VerifiedUserIcon fontSize="small" />} label="Site admin" />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </AsyncState>
    </Box>
  )
}

function MediaItemsSection() {
  const { data: mediaItems, loading, error } = useAsync(() => adminApi.listMediaItems(), [])

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Movies &amp; Series
      </Typography>
      <AsyncState loading={loading} error={error}>
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Title</TableCell>
                <TableCell>Year</TableCell>
                <TableCell>IMDB</TableCell>
                <TableCell>Rating</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {mediaItems?.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.type}</TableCell>
                  <TableCell>{item.title}</TableCell>
                  <TableCell>{item.year ?? '—'}</TableCell>
                  <TableCell>
                    <ImdbLink imdbId={item.imdbId} />
                  </TableCell>
                  <TableCell>{item.imdbRating ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </AsyncState>
    </Box>
  )
}
