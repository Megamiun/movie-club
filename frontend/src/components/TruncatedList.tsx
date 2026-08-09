import { Tooltip, Typography } from '@mui/material'

/**
 * [maxChars], if given, caps the *joined* length of the shown items (beyond just their count via [limit]) so a
 * long second item doesn't wrap the row -- it's dropped into the "+N" count instead of being shown truncated or
 * overflowing. The first item is always shown in full even if it alone exceeds [maxChars], so there's never
 * nothing to show.
 */
export function TruncatedList({
  items,
  limit = 2,
  maxChars,
}: {
  items: string[]
  limit?: number
  maxChars?: number
}) {
  if (items.length === 0) return <>—</>

  let shownItems = items.slice(0, limit)
  if (maxChars !== undefined) {
    const fitted: string[] = []
    let length = 0
    for (const item of shownItems) {
      const nextLength = length === 0 ? item.length : length + 2 + item.length
      if (fitted.length > 0 && nextLength > maxChars) break
      fitted.push(item)
      length = nextLength
    }
    shownItems = fitted.length > 0 ? fitted : shownItems.slice(0, 1)
  }

  if (shownItems.length === items.length) return <>{items.join(', ')}</>

  const shown = shownItems.join(', ')
  const hiddenCount = items.length - shownItems.length

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
