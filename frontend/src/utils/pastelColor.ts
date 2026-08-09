/** Fixed saturation/lightness band that reads as "pastel" regardless of hue -- soft and light, never fully
 * saturated or dark. `PastelColorPicker` only ever lets a user move along the hue axis within this band. */
const PASTEL_SATURATION = 60
const PASTEL_LIGHTNESS = 82

/** Saturation/lightness band for `strongPastelHex` -- same hue as the pastel band above, but bold/dark enough to
 * read as text on top of it, instead of a generic black/white contrast pick. */
const STRONG_SATURATION = 70
const STRONG_LIGHTNESS = 32

function hslToHex(h: number, s: number, l: number): string {
  const sat = s / 100
  const light = l / 100
  const k = (n: number) => (n + h / 30) % 12
  const a = sat * Math.min(light, 1 - light)
  const f = (n: number) => light - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))
  const toHex = (x: number) =>
    Math.round(x * 255)
      .toString(16)
      .padStart(2, '0')
  return `#${toHex(f(0))}${toHex(f(8))}${toHex(f(4))}`
}

/** Approximates a hex color's hue -- used to position the picker's thumb (including for a pre-existing custom color
 * that isn't itself on the pastel band, left alone until the user actually touches the picker) and by
 * `strongPastelHex` to derive a same-hue text/accent color. */
function hexToHue(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16) / 255
  const g = parseInt(hex.slice(3, 5), 16) / 255
  const b = parseInt(hex.slice(5, 7), 16) / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  if (max === min) return 0

  const d = max - min
  let h: number
  if (max === r) h = ((g - b) / d) % 6
  else if (max === g) h = (b - r) / d + 2
  else h = (r - g) / d + 4

  h *= 60
  return h < 0 ? h + 360 : h
}

export function hueToPastelHex(hue: number): string {
  return hslToHex(((hue % 360) + 360) % 360, PASTEL_SATURATION, PASTEL_LIGHTNESS)
}

export function pastelHexToHue(hex: string): number {
  return hexToHue(hex)
}

/** A bolder, more saturated same-hue variant of [hex] -- for text/accents wherever a pastel-picker color (member
 * color, rating option color) is otherwise only ever used as a background, instead of a generic black/white
 * contrast pick. Only meaningful for an actual pastel hex; a gray/desaturated input has no real hue to preserve. */
export function strongPastelHex(hex: string): string {
  return hslToHex(hexToHue(hex), STRONG_SATURATION, STRONG_LIGHTNESS)
}
