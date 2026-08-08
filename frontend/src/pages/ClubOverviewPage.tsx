import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import type { ClubDetail } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function ClubOverviewPage() {
  const { club, reload } = useOutletContext<ClubOutletContext>()

  return (
    <Stack spacing={4}>
      <MembersSection club={club} refresh={reload} />
      <RotationSection club={club} />
      <RatingScalesSection clubId={club.id} />
    </Stack>
  )
}

function MembersSection({ club, refresh }: { club: ClubDetail; refresh: () => void }) {
  const [memberId, setMemberId] = useState('')
  const [role, setRole] = useState('MEMBER')
  const [error, setError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await clubsApi.addMember(club.id, memberId, role)
      setMemberId('')
      refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRoleChange = async (targetMemberId: string, newRole: string) => {
    try {
      await clubsApi.changeRole(club.id, targetMemberId, newRole)
      refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleRemove = async (targetMemberId: string) => {
    try {
      await clubsApi.removeMember(club.id, targetMemberId)
      refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Members
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Member ID</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Rotation position</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {club.members.map((m) => (
              <TableRow key={m.memberId}>
                <TableCell sx={{ fontFamily: 'monospace' }}>{m.memberId}</TableCell>
                <TableCell>
                  <Select size="small" value={m.role} onChange={(e) => handleRoleChange(m.memberId, e.target.value)}>
                    <MenuItem value="ADMIN">ADMIN</MenuItem>
                    <MenuItem value="MEMBER">MEMBER</MenuItem>
                  </Select>
                </TableCell>
                <TableCell>{m.rotationOrder}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => handleRemove(m.memberId)} title="Remove member">
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <Box component="form" onSubmit={handleAdd} display="flex" gap={1} mt={2}>
        <TextField
          label="Member ID"
          size="small"
          value={memberId}
          onChange={(e) => setMemberId(e.target.value)}
          required
        />
        <Select size="small" value={role} onChange={(e) => setRole(e.target.value)}>
          <MenuItem value="ADMIN">ADMIN</MenuItem>
          <MenuItem value="MEMBER">MEMBER</MenuItem>
        </Select>
        <Button type="submit" variant="outlined">
          Add member
        </Button>
      </Box>
    </Box>
  )
}

function RotationSection({ club }: { club: ClubDetail }) {
  const memberIds = club.members.map((m) => m.memberId).sort().join(',')
  const [order, setOrder] = useState(() => [...club.members].sort((a, b) => a.rotationOrder - b.rotationOrder).map((m) => m.memberId))
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    setOrder([...club.members].sort((a, b) => a.rotationOrder - b.rotationOrder).map((m) => m.memberId))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberIds])

  const move = (index: number, direction: -1 | 1) => {
    const next = [...order]
    const target = index + direction
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]
    setOrder(next)
    setSaved(false)
  }

  const save = async () => {
    setError(null)
    try {
      await clubsApi.updateRotation(club.id, order)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Rotation order
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {saved && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSaved(false)}>
          Rotation order saved.
        </Alert>
      )}
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1}>
          {order.map((memberId, index) => (
            <Stack key={memberId} direction="row" alignItems="center" spacing={1}>
              <Chip label={index + 1} size="small" />
              <Typography sx={{ fontFamily: 'monospace', flexGrow: 1 }}>{memberId}</Typography>
              <IconButton size="small" onClick={() => move(index, -1)} disabled={index === 0}>
                <ArrowUpwardIcon fontSize="small" />
              </IconButton>
              <IconButton size="small" onClick={() => move(index, 1)} disabled={index === order.length - 1}>
                <ArrowDownwardIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
        </Stack>
        <Button variant="outlined" sx={{ mt: 2 }} onClick={save} disabled={order.length === 0}>
          Save order
        </Button>
      </Paper>
    </Box>
  )
}

function RatingScalesSection({ clubId }: { clubId: string }) {
  const { data: scales, loading, error } = useAsync(() => clubsApi.getRatingScales(clubId), [clubId])

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Rating scales
      </Typography>
      <AsyncState loading={loading} error={error}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          {scales?.map((scale) => (
            <Paper key={scale.id} sx={{ p: 2, flex: 1 }}>
              <Typography variant="subtitle2" gutterBottom>
                {scale.type}
              </Typography>
              <Stack direction="row" flexWrap="wrap" gap={1}>
                {scale.options
                  .sort((a, b) => a.position - b.position)
                  .map((option) => (
                    <Chip
                      key={option.id}
                      label={option.label}
                      sx={{ bgcolor: option.color, color: '#fff' }}
                      size="small"
                    />
                  ))}
              </Stack>
            </Paper>
          ))}
        </Stack>
      </AsyncState>
    </Box>
  )
}
