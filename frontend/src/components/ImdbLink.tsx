import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import { IconButton } from '@mui/material'

export function ImdbLink({ imdbId }: { imdbId: string }) {
  return (
    <IconButton
      size="small"
      component="a"
      href={`https://www.imdb.com/title/${imdbId}/`}
      target="_blank"
      rel="noreferrer"
      title="Open on IMDB"
      onClick={(e) => e.stopPropagation()}
    >
      <OpenInNewIcon fontSize="inherit" />
    </IconButton>
  )
}
