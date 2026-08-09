import LanguageIcon from '@mui/icons-material/Language'
import { Dialog, DialogContent, DialogTitle, IconButton, List, ListItemButton, ListItemText, Typography } from '@mui/material'
import { useState } from 'react'
import type { Translation } from '../api/types'

export function LanguagePickerDialog({
  translations,
  selectedLanguageCode,
  onSelect,
}: {
  translations: Translation[]
  selectedLanguageCode: string | null
  onSelect: (languageCode: string) => void
}) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <IconButton size="small" onClick={() => setOpen(true)} title="Choose exhibition language">
        <LanguageIcon fontSize="small" />
      </IconButton>
      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Choose exhibition language</DialogTitle>
        <DialogContent>
          {translations.length === 0 && (
            <Typography color="text.secondary">No translated titles available from TMDB.</Typography>
          )}
          <List>
            {translations.map((t) => (
              <ListItemButton
                key={`${t.languageCode}-${t.countryCode}`}
                selected={t.languageCode === selectedLanguageCode}
                onClick={() => {
                  onSelect(t.languageCode)
                  setOpen(false)
                }}
              >
                <ListItemText primary={t.title} secondary={`${t.englishName} (${t.languageCode})`} />
              </ListItemButton>
            ))}
          </List>
        </DialogContent>
      </Dialog>
    </>
  )
}
