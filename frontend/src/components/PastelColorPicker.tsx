import { Box } from '@mui/material'
import { useRef, type PointerEvent as ReactPointerEvent } from 'react'
import { hueToPastelHex, pastelHexToHue } from '../utils/pastelColor'

const HUE_STOPS = Array.from({ length: 13 }, (_, i) => hueToPastelHex(i * 30))

/**
 * Replaces a native `<input type="color">` with a hue-only slider at a fixed pastel saturation/lightness -- every
 * color it can produce is pastel by construction, rather than relying on the picker's user to stay within a band.
 * `value` is still a plain hex string in/out (unchanged storage format); a pre-existing color that isn't itself on
 * the pastel band just positions the thumb at its closest hue until the user actually drags it.
 *
 * `onChange` fires continuously while dragging (matching a native color input's own `input` event); `onCommit`,
 * if given, fires once on release -- for callers that only want to persist on a deliberate stop, not every pixel
 * of drag (mirrors the `onChange`+`onBlur` split the native input previously used).
 */
export function PastelColorPicker({
  value,
  onChange,
  onCommit,
  width = 140,
  height = 32,
}: {
  value: string
  onChange: (hex: string) => void
  onCommit?: (hex: string) => void
  width?: number
  height?: number
}) {
  const trackRef = useRef<HTMLDivElement>(null)
  const hue = pastelHexToHue(value)

  const hueFromClientX = (clientX: number) => {
    const rect = trackRef.current?.getBoundingClientRect()
    if (!rect) return hue
    const fraction = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
    return Math.round(fraction * 360)
  }

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    onChange(hueToPastelHex(hueFromClientX(event.clientX)))
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.buttons !== 1) return
    onChange(hueToPastelHex(hueFromClientX(event.clientX)))
  }

  const handlePointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    onCommit?.(hueToPastelHex(hueFromClientX(event.clientX)))
  }

  return (
    <Box
      ref={trackRef}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      sx={{
        position: 'relative',
        width,
        height,
        borderRadius: 1,
        cursor: 'pointer',
        touchAction: 'none',
        background: `linear-gradient(to right, ${HUE_STOPS.join(', ')})`,
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: -2,
          left: `${(hue / 360) * 100}%`,
          transform: 'translateX(-50%)',
          width: 10,
          height: height + 4,
          borderRadius: 0.5,
          border: '2px solid white',
          outline: '1px solid rgba(0, 0, 0, 0.35)',
          bgcolor: value,
          pointerEvents: 'none',
        }}
      />
    </Box>
  )
}
