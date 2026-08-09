import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import { IconButton, Link } from '@mui/material'

export function ImdbLink({ imdbId, variant = 'icon' }: { imdbId: string; variant?: 'icon' | 'text' }) {
  const href = `https://www.imdb.com/title/${imdbId}/`

  if (variant === 'text') {
    return (
      <Link
        href={href}
        target="_blank"
        rel="noreferrer"
        underline="always"
        variant="body2"
        onClick={(e) => e.stopPropagation()}
      >
        IMDB
      </Link>
    )
  }

  return (
    <IconButton
      size="small"
      component="a"
      href={href}
      target="_blank"
      rel="noreferrer"
      title="Open on IMDB"
      onClick={(e) => e.stopPropagation()}
    >
      <OpenInNewIcon fontSize="inherit" />
    </IconButton>
  )
}
