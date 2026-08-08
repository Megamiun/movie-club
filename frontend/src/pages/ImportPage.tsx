import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import UploadFileIcon from '@mui/icons-material/UploadFile'
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
import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { importApi } from '../api/importApi'
import { ApiError } from '../api/client'
import type { ImportMemberMapping, ImportResult, ImportType } from '../api/types'
import type { ClubOutletContext } from '../layout/ClubOutletContext'

export function ImportPage() {
  const { club } = useOutletContext<ClubOutletContext>()
  const [type, setType] = useState<ImportType>('movies')
  const [files, setFiles] = useState<File[]>([])
  const [mappings, setMappings] = useState<ImportMemberMapping[]>([])
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const addMapping = () => setMappings([...mappings, { choiceInitial: '', csvDisplayName: '', memberId: '' }])
  const updateMapping = (index: number, patch: Partial<ImportMemberMapping>) =>
    setMappings(mappings.map((m, i) => (i === index ? { ...m, ...patch } : m)))
  const removeMapping = (index: number) => setMappings(mappings.filter((_, i) => i !== index))

  const handleSubmit = async () => {
    if (files.length === 0) return
    setSubmitting(true)
    setError(null)
    setResult(null)
    try {
      const response = await importApi.run(club.id, type, files, mappings)
      setResult(response)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Import CSV
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Stack spacing={3} maxWidth={640}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Select size="small" value={type} onChange={(e) => setType(e.target.value as ImportType)}>
            <MenuItem value="movies">Movies</MenuItem>
            <MenuItem value="series">Series</MenuItem>
            <MenuItem value="reserve">Reserve (watchlist)</MenuItem>
          </Select>
          <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
            {files.length > 0 ? `${files.length} file(s) selected` : 'Choose CSV file(s)'}
            <input
              type="file"
              accept=".csv"
              multiple
              hidden
              onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
            />
          </Button>
        </Stack>

        <Box>
          <Typography variant="subtitle2" gutterBottom>
            Member mappings ({'Choice'} initial → member)
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Choice initial</TableCell>
                <TableCell>CSV display name</TableCell>
                <TableCell>Member ID</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {mappings.map((mapping, index) => (
                <TableRow key={index}>
                  <TableCell>
                    <TextField
                      size="small"
                      variant="standard"
                      value={mapping.choiceInitial}
                      onChange={(e) => updateMapping(index, { choiceInitial: e.target.value })}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      variant="standard"
                      value={mapping.csvDisplayName}
                      onChange={(e) => updateMapping(index, { csvDisplayName: e.target.value })}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      variant="standard"
                      value={mapping.memberId}
                      onChange={(e) => updateMapping(index, { memberId: e.target.value })}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => removeMapping(index)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Button size="small" startIcon={<AddIcon />} onClick={addMapping} sx={{ mt: 1 }}>
            Add mapping
          </Button>
        </Box>

        <Button variant="contained" onClick={handleSubmit} disabled={submitting || files.length === 0}>
          Import
        </Button>
      </Stack>

      {result && (
        <Paper sx={{ p: 3, mt: 3, maxWidth: 640 }}>
          <Typography variant="subtitle1" gutterBottom>
            Result
          </Typography>
          <Stack direction="row" spacing={1} mb={2}>
            <Chip color="success" label={`Created: ${result.created}`} />
            <Chip color="info" label={`Updated: ${result.updated}`} />
            <Chip color="warning" label={`Skipped: ${result.skipped.length}`} />
            <Chip label={`Warnings: ${result.warnings.length}`} />
          </Stack>
          {result.skipped.length > 0 && (
            <Box mb={2}>
              <Typography variant="body2" fontWeight="bold">
                Skipped rows
              </Typography>
              {result.skipped.map((issue, i) => (
                <Typography variant="body2" key={i}>
                  Row {issue.row}: {issue.reason}
                </Typography>
              ))}
            </Box>
          )}
          {result.warnings.length > 0 && (
            <Box>
              <Typography variant="body2" fontWeight="bold">
                Warnings
              </Typography>
              {result.warnings.map((issue, i) => (
                <Typography variant="body2" key={i}>
                  Row {issue.row}: {issue.reason}
                </Typography>
              ))}
            </Box>
          )}
        </Paper>
      )}
    </Box>
  )
}
