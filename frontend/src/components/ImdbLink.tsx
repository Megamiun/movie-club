import { IconButton, Link } from '@mui/material'
import type { ReactNode } from 'react'
import { ImdbIcon } from './icons/ImdbIcon'

export function ImdbLink({
  imdbId,
  kind = 'title',
  variant = 'icon',
  children,
}: {
  imdbId: string
  kind?: 'title' | 'name'
  variant?: 'icon' | 'text'
  children?: ReactNode
}) {
  const href = `https://www.imdb.com/${kind}/${imdbId}/`

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
        {children ?? 'IMDB'}
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
      <ImdbIcon fontSize="inherit" />
    </IconButton>
  )
}
