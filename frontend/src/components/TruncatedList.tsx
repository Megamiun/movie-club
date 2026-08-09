import { Tooltip, Typography } from '@mui/material'

export function TruncatedList({ items, limit = 2 }: { items: string[]; limit?: number }) {
  if (items.length === 0) return <>—</>
  if (items.length <= limit) return <>{items.join(', ')}</>

  const shown = items.slice(0, limit).join(', ')
  const hiddenCount = items.length - limit

  return (
    <Tooltip title={items.join(', ')}>
      <span>
        {shown}{' '}
        <Typography component="span" variant="body2" color="text.secondary">
          +{hiddenCount}
        </Typography>
      </span>
    </Tooltip>
  )
}
