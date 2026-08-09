import AddIcon from '@mui/icons-material/Add'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import { authApi } from '../api/auth'
import { clubsApi } from '../api/clubs'
import { ApiError } from '../api/client'
import type { ClubDetail, ClubMember, MemberSummary, RatingOption, RatingScale } from '../api/types'
import { AsyncState } from '../components/AsyncState'
import { MemberSearchAutocomplete } from '../components/MemberSearchAutocomplete'
import { PastelColorPicker } from '../components/PastelColorPicker'
import { hueToPastelHex } from '../utils/pastelColor'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import type { ClubOutletContext } from '../layout/ClubOutletContext'
import { memberName } from '../utils/members'

export function ClubOverviewPage() {
  const { club, reload } = useOutletContext<ClubOutletContext>()

  return (
    <Stack spacing={4}>
      <MembersSection club={club} refresh={reload} />
      <RotationSection club={club} />
      <RatingScalesSection clubId={club.id} />
      <LanguagePreferencesSection club={club} />
    </Stack>
  )
}

function MembersSection({ club, refresh }: { club: ClubDetail; refresh: () => void }) {
  const { member: me } = useAuth()
  const myMembership = club.members.find((m) => m.memberId === me?.id)
  const isAdmin = myMembership?.role === 'ADMIN'
  const [selectedMember, setSelectedMember] = useState<MemberSummary | null>(null)
  const [role, setRole] = useState('MEMBER')
  const [inviteEmail, setInviteEmail] = useState('')
  const [error, setError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedMember) return
    setError(null)
    try {
      await clubsApi.addMember(club.id, selectedMember.id, role)
      setSelectedMember(null)
      refresh()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleInviteAndAdd = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const invited = await authApi.invite(inviteEmail)
      await clubsApi.addMember(club.id, invited.memberId, role)
      setInviteEmail('')
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
              <TableCell>Color</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Rotation position</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {club.members.map((m) => (
              <TableRow key={m.memberId}>
                <TableCell>
                  <MemberColorEditor
                    clubId={club.id}
                    member={m}
                    editable={isAdmin || m.memberId === me?.id}
                    onChange={refresh}
                  />
                </TableCell>
                <TableCell>{m.name}</TableCell>
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
      <Box component="form" onSubmit={handleAdd} sx={{ display: 'flex', gap: 1, mt: 2 }}>
        <MemberSearchAutocomplete
          value={selectedMember}
          onChange={setSelectedMember}
          excludeMemberIds={club.members.map((m) => m.memberId)}
          label="Add member"
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
      <Box component="form" onSubmit={handleInviteAndAdd} sx={{ display: 'flex', gap: 1, mt: 1, alignItems: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Not on the platform yet?
        </Typography>
        <TextField
          label="Email to invite"
          type="email"
          size="small"
          value={inviteEmail}
          onChange={(e) => setInviteEmail(e.target.value)}
          required
        />
        <Button type="submit" variant="text">
          Invite &amp; add
        </Button>
      </Box>
    </Box>
  )
}

function MemberColorEditor({
  clubId,
  member,
  editable,
  onChange,
}: {
  clubId: string
  member: ClubMember
  editable: boolean
  onChange: () => void
}) {
  const [color, setColor] = useState(member.color ?? '#9E9E9E')

  const handleChange = async (value: string) => {
    setColor(value)
    try {
      await clubsApi.updateColor(clubId, member.memberId, value)
      onChange()
    } catch {
      setColor(member.color ?? '#9E9E9E')
    }
  }

  if (!editable) {
    return <Box sx={{ width: 24, height: 24, borderRadius: '50%', bgcolor: color }} />
  }

  return <PastelColorPicker value={color} onChange={setColor} onCommit={handleChange} width={100} height={24} />
}

function RotationSection({ club }: { club: ClubDetail }) {
  const memberIds = club.members.map((m) => m.memberId).sort().join(',')
  const [order, setOrder] = useState(() => [...club.members].sort((a, b) => a.rotationOrder - b.rotationOrder).map((m) => m.memberId))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setOrder([...club.members].sort((a, b) => a.rotationOrder - b.rotationOrder).map((m) => m.memberId))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberIds])

  const move = async (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= order.length) return
    const previous = order
    const next = [...order]
    ;[next[index], next[target]] = [next[target], next[index]]
    setOrder(next)
    setError(null)
    try {
      await clubsApi.updateRotation(club.id, next)
    } catch (err) {
      setOrder(previous)
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
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1}>
          {order.map((memberId, index) => (
            <Stack key={memberId} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip label={index + 1} size="small" />
              <Typography sx={{ flexGrow: 1 }}>{memberName(club.members, memberId)}</Typography>
              <IconButton size="small" onClick={() => move(index, -1)} disabled={index === 0}>
                <ArrowUpwardIcon fontSize="small" />
              </IconButton>
              <IconButton size="small" onClick={() => move(index, 1)} disabled={index === order.length - 1}>
                <ArrowDownwardIcon fontSize="small" />
              </IconButton>
            </Stack>
          ))}
        </Stack>
      </Paper>
    </Box>
  )
}

function RatingScalesSection({ clubId }: { clubId: string }) {
  const { data: scales, loading, error, reload } = useAsync(() => clubsApi.getRatingScales(clubId), [clubId])

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Rating scales
      </Typography>
      <AsyncState loading={loading} error={error}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          {scales?.map((scale) => (
            <RatingScaleCard key={scale.id} clubId={clubId} scale={scale} onChange={reload} />
          ))}
        </Stack>
      </AsyncState>
    </Box>
  )
}

function RatingScaleCard({
  clubId,
  scale,
  onChange,
}: {
  clubId: string
  scale: RatingScale
  onChange: () => void
}) {
  const sortedOptions = [...scale.options].sort((a, b) => a.position - b.position)
  const [error, setError] = useState<string | null>(null)
  const [newLabel, setNewLabel] = useState('')
  const [newColor, setNewColor] = useState(hueToPastelHex(0))

  const handleMove = async (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= sortedOptions.length) return
    const reordered = [...sortedOptions]
    ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
    setError(null)
    try {
      await clubsApi.updateRatingOptionOrder(
        clubId,
        scale.id,
        reordered.map((o) => o.id),
      )
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await clubsApi.createRatingOption(clubId, scale.id, newLabel, newColor)
      setNewLabel('')
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Paper sx={{ p: 2, flex: 1 }}>
      <Typography variant="subtitle2" gutterBottom>
        {scale.type}
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {error}
        </Alert>
      )}
      <Stack spacing={1}>
        {sortedOptions.map((option, index) => (
          <RatingOptionEditor
            key={option.id}
            clubId={clubId}
            option={option}
            otherOptions={sortedOptions.filter((o) => o.id !== option.id)}
            canMoveUp={index > 0}
            canMoveDown={index < sortedOptions.length - 1}
            canDelete={sortedOptions.length > 1}
            onMove={(direction) => handleMove(index, direction)}
            onChange={onChange}
          />
        ))}
      </Stack>
      <Box component="form" onSubmit={handleAdd} sx={{ mt: 1.5 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <PastelColorPicker value={newColor} onChange={setNewColor} width={80} height={24} />
          <TextField
            size="small"
            variant="standard"
            placeholder="New option"
            value={newLabel}
            onChange={(e) => setNewLabel(e.target.value)}
            sx={{ flexGrow: 1 }}
          />
          <IconButton size="small" type="submit" disabled={!newLabel.trim()} title="Add option">
            <AddIcon fontSize="small" />
          </IconButton>
        </Stack>
      </Box>
    </Paper>
  )
}

function LanguagePreferencesSection({ club }: { club: ClubDetail }) {
  const langKey = `${club.preferredLanguages.join(',')}|${club.ignoredLanguages.join(',')}`
  const [preferred, setPreferred] = useState(club.preferredLanguages)
  const [ignored, setIgnored] = useState(club.ignoredLanguages)
  const [newPreferred, setNewPreferred] = useState('')
  const [newIgnored, setNewIgnored] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setPreferred(club.preferredLanguages)
    setIgnored(club.ignoredLanguages)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [langKey])

  const persist = async (nextPreferred: string[], nextIgnored: string[]) => {
    const previousPreferred = preferred
    const previousIgnored = ignored
    setPreferred(nextPreferred)
    setIgnored(nextIgnored)
    setError(null)
    try {
      await clubsApi.updateLanguagePreferences(club.id, nextPreferred, nextIgnored)
    } catch (err) {
      setPreferred(previousPreferred)
      setIgnored(previousIgnored)
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  const movePreferred = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= preferred.length) return
    const next = [...preferred]
    ;[next[index], next[target]] = [next[target], next[index]]
    persist(next, ignored)
  }

  const addPreferred = (event: FormEvent) => {
    event.preventDefault()
    const code = newPreferred.trim().toLowerCase()
    if (!code || preferred.includes(code)) return
    setNewPreferred('')
    persist([...preferred, code], ignored)
  }

  const removePreferred = (code: string) => {
    persist(
      preferred.filter((c) => c !== code),
      ignored,
    )
  }

  const addIgnored = (event: FormEvent) => {
    event.preventDefault()
    const code = newIgnored.trim().toLowerCase()
    if (!code || ignored.includes(code)) return
    setNewIgnored('')
    persist(preferred, [...ignored, code])
  }

  const removeIgnored = (code: string) => {
    persist(
      preferred,
      ignored.filter((c) => c !== code),
    )
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Language preferences
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        ISO 639-1 codes (e.g. "en", "pt") used to pick a display title for movies/series that don't have a custom
        title or a specific language chosen.
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <Paper sx={{ p: 2, flex: 1 }}>
          <Typography variant="subtitle2" gutterBottom>
            Preferred (ranked, most preferred first)
          </Typography>
          <Stack spacing={1} sx={{ mb: 1 }}>
            {preferred.length === 0 && (
              <Typography color="text.secondary" variant="body2">
                None set.
              </Typography>
            )}
            {preferred.map((code, index) => (
              <Stack key={code} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Chip label={code} size="small" />
                <Box sx={{ flexGrow: 1 }} />
                <IconButton size="small" onClick={() => movePreferred(index, -1)} disabled={index === 0}>
                  <ArrowUpwardIcon fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  onClick={() => movePreferred(index, 1)}
                  disabled={index === preferred.length - 1}
                >
                  <ArrowDownwardIcon fontSize="small" />
                </IconButton>
                <IconButton size="small" onClick={() => removePreferred(code)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
          </Stack>
          <Box component="form" onSubmit={addPreferred} sx={{ display: 'flex', gap: 1 }}>
            <TextField
              size="small"
              label="Language code"
              value={newPreferred}
              onChange={(e) => setNewPreferred(e.target.value)}
            />
            <Button type="submit" variant="outlined">
              Add
            </Button>
          </Box>
        </Paper>
        <Paper sx={{ p: 2, flex: 1 }}>
          <Typography variant="subtitle2" gutterBottom>
            Ignored (never default to these)
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
            {ignored.length === 0 && (
              <Typography color="text.secondary" variant="body2">
                None set.
              </Typography>
            )}
            {ignored.map((code) => (
              <Chip key={code} label={code} size="small" onDelete={() => removeIgnored(code)} />
            ))}
          </Stack>
          <Box component="form" onSubmit={addIgnored} sx={{ display: 'flex', gap: 1 }}>
            <TextField
              size="small"
              label="Language code"
              value={newIgnored}
              onChange={(e) => setNewIgnored(e.target.value)}
            />
            <Button type="submit" variant="outlined">
              Add
            </Button>
          </Box>
        </Paper>
      </Stack>
    </Box>
  )
}

function RatingOptionEditor({
  clubId,
  option,
  otherOptions,
  canMoveUp,
  canMoveDown,
  canDelete,
  onMove,
  onChange,
}: {
  clubId: string
  option: RatingOption
  otherOptions: RatingOption[]
  canMoveUp: boolean
  canMoveDown: boolean
  canDelete: boolean
  onMove: (direction: -1 | 1) => void
  onChange: () => void
}) {
  const [label, setLabel] = useState(option.label)
  const [color, setColor] = useState(option.color)
  const [error, setError] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const handleBlurSave = async (colorOverride?: string) => {
    const savedColor = colorOverride ?? color
    if (label === option.label && savedColor === option.color) return
    setError(null)
    try {
      await clubsApi.updateRatingOption(clubId, option.id, { label, color: savedColor })
      onChange()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <PastelColorPicker value={color} onChange={setColor} onCommit={handleBlurSave} width={100} height={24} />
      <TextField
        size="small"
        variant="standard"
        value={label}
        onChange={(e) => setLabel(e.target.value)}
        onBlur={() => handleBlurSave()}
        sx={{ flexGrow: 1 }}
      />
      <IconButton size="small" onClick={() => onMove(-1)} disabled={!canMoveUp}>
        <ArrowUpwardIcon fontSize="small" />
      </IconButton>
      <IconButton size="small" onClick={() => onMove(1)} disabled={!canMoveDown}>
        <ArrowDownwardIcon fontSize="small" />
      </IconButton>
      <IconButton size="small" onClick={() => setDeleteOpen(true)} disabled={!canDelete} title="Delete option">
        <DeleteIcon fontSize="small" />
      </IconButton>
      {error && <Alert severity="error">{error}</Alert>}
      {deleteOpen && (
        <DeleteRatingOptionDialog
          clubId={clubId}
          option={option}
          otherOptions={otherOptions}
          onClose={() => setDeleteOpen(false)}
          onDeleted={onChange}
        />
      )}
    </Stack>
  )
}

function DeleteRatingOptionDialog({
  clubId,
  option,
  otherOptions,
  onClose,
  onDeleted,
}: {
  clubId: string
  option: RatingOption
  otherOptions: RatingOption[]
  onClose: () => void
  onDeleted: () => void
}) {
  const [reassignTo, setReassignTo] = useState(otherOptions[0]?.id ?? '')
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setError(null)
    try {
      await clubsApi.deleteRatingOption(clubId, option.id, reassignTo)
      onDeleted()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    }
  }

  return (
    <Dialog open onClose={onClose}>
      <DialogTitle>Delete &ldquo;{option.label}&rdquo;?</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Any existing rating using this option will be moved to the option you pick below.
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Select size="small" fullWidth value={reassignTo} onChange={(e) => setReassignTo(e.target.value)}>
          {otherOptions.map((o) => (
            <MenuItem key={o.id} value={o.id}>
              {o.label}
            </MenuItem>
          ))}
        </Select>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button color="error" onClick={handleConfirm} disabled={!reassignTo}>
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  )
}
