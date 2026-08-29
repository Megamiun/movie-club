import { BASE_URL } from '../api/client'

const IMAGE_WIDTH = 1080
const IMAGE_HEIGHT = 1920
const PADDING = 60
const GAP = 24
const MAX_ROW_GAP = 150
const CORNER_RADIUS = 16
const HEADER_HEIGHT = 96

/** Renders a poster grid (no per-poster title/date text, no club branding -- just the month/year label at top and
 * the art) into a 1080x1920 PNG, Instagram Stories' own aspect ratio (9:16). Posters keep a consistent size and
 * outer margin (`PADDING`) regardless of how many there are; what changes is the *gap between rows* -- a sparse
 * grid (few rows relative to the available height) grows that gap (up to `MAX_ROW_GAP`) to use up the leftover
 * space instead of leaving it as blank margin above/below, so the frame reads consistently full whether a month
 * has 4 picks or 10. The cap matters most for a grid with very few rows (e.g. 2): uncapped, it would dump the
 * *entire* leftover into one absurdly wide gap instead of spreading it out -- whatever the cap leaves on the table
 * becomes symmetric top/bottom margin instead, same as the original centering behavior. Only falls back to
 * shrinking the tiles themselves (preserving aspect) if the grid doesn't fit *even at* the base gap. Both
 * directions center what's left over (a short last row centers on its own rather than trailing left-aligned).
 * Each poster is cover-fit (cropped, not stretched) into its tile, same as the on-screen `PosterCard`'s own
 * `object-fit: cover`. */
export async function generateMonthShareImage(posterUrls: string[], label: string): Promise<Blob> {
  if (posterUrls.length === 0) {
    throw new Error('Nothing with a poster to share this month')
  }

  const canvas = document.createElement('canvas')
  canvas.width = IMAGE_WIDTH
  canvas.height = IMAGE_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas is not supported in this browser')

  ctx.fillStyle = '#0a0a0a'
  ctx.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)

  ctx.fillStyle = '#f2f2f2'
  ctx.font = '600 52px system-ui, -apple-system, "Segoe UI", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(label, IMAGE_WIDTH / 2, PADDING + HEADER_HEIGHT / 2)

  const images = await Promise.all(posterUrls.map(loadImage))

  const columns = columnsFor(images.length)
  const gridTopPadding = PADDING + HEADER_HEIGHT
  const availableWidth = IMAGE_WIDTH - PADDING * 2
  const availableHeight = IMAGE_HEIGHT - gridTopPadding - PADDING
  let tileWidth = (availableWidth - GAP * (columns - 1)) / columns
  let tileHeight = tileWidth * 1.5
  const rows = Math.ceil(images.length / columns)
  const naturalGridHeight = rows * tileHeight + (rows - 1) * GAP

  let rowGap = GAP
  let gridHeight = naturalGridHeight
  if (naturalGridHeight > availableHeight) {
    // Doesn't fit even at the base gap -- shrink the tiles (and gap) proportionally instead.
    const scale = availableHeight / naturalGridHeight
    tileWidth *= scale
    tileHeight *= scale
    rowGap *= scale
    gridHeight = availableHeight
  } else if (rows > 1) {
    // Room to spare -- grow the row gap to absorb it rather than leaving blank margin top/bottom. Capped so a
    // grid with very few rows (e.g. 2) doesn't dump the *entire* leftover into a single, absurdly wide gap --
    // whatever growth the cap leaves on the table still ends up as symmetric top/bottom margin via `gridTop` below.
    const leftover = availableHeight - naturalGridHeight
    rowGap = GAP + Math.min(leftover / (rows - 1), MAX_ROW_GAP - GAP)
    gridHeight = rows * tileHeight + (rows - 1) * rowGap
  }

  const gridWidth = columns * tileWidth + (columns - 1) * GAP
  const gridTop = gridTopPadding + (availableHeight - gridHeight) / 2
  const gridLeft = PADDING + (availableWidth - gridWidth) / 2

  images.forEach((img, index) => {
    const row = Math.floor(index / columns)
    const col = index % columns
    const itemsInRow = Math.min(columns, images.length - row * columns)
    const rowWidth = itemsInRow * tileWidth + (itemsInRow - 1) * GAP
    const rowLeft = gridLeft + (gridWidth - rowWidth) / 2
    const x = rowLeft + col * (tileWidth + GAP)
    const y = gridTop + row * (tileHeight + rowGap)
    drawPoster(ctx, img, x, y, tileWidth, tileHeight)
  })

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('Failed to generate the image'))
    }, 'image/png')
  })
}

/** 1-2 posters get one row; beyond that, more columns as the count grows, so a busy month doesn't end up with one
 * absurdly long single-file column. */
function columnsFor(count: number): number {
  if (count <= 2) return count
  if (count <= 6) return 2
  if (count <= 12) return 3
  return 4
}

/** TMDB's own CDN doesn't send CORS headers, so a poster loaded directly from `image.tmdb.org` would taint the
 * canvas and block `toBlob` entirely (see `MediaItemRoutes.kt`'s doc comment on the backend route this proxies
 * through) -- routed back through our own origin instead, which does send one. */
function toProxiedPosterUrl(url: string): string {
  return `${BASE_URL}/media-items/image-proxy?url=${encodeURIComponent(url)}`
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('Failed to load a poster image'))
    img.src = toProxiedPosterUrl(url)
  })
}

function drawPoster(ctx: CanvasRenderingContext2D, img: HTMLImageElement, x: number, y: number, w: number, h: number) {
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(x + CORNER_RADIUS, y)
  ctx.arcTo(x + w, y, x + w, y + h, CORNER_RADIUS)
  ctx.arcTo(x + w, y + h, x, y + h, CORNER_RADIUS)
  ctx.arcTo(x, y + h, x, y, CORNER_RADIUS)
  ctx.arcTo(x, y, x + w, y, CORNER_RADIUS)
  ctx.closePath()
  ctx.clip()

  const imageAspect = img.width / img.height
  const tileAspect = w / h
  let sx = 0
  let sy = 0
  let sw = img.width
  let sh = img.height
  if (imageAspect > tileAspect) {
    sw = img.height * tileAspect
    sx = (img.width - sw) / 2
  } else {
    sh = img.width / tileAspect
    sy = (img.height - sh) / 2
  }
  ctx.drawImage(img, sx, sy, sw, sh, x, y, w, h)
  ctx.restore()
}
