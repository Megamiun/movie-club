/** Rating option colors range from pale pastels to solid/dark hues (see samples/img_1.png, img_2.png) -- a single
 * hardcoded text color would be unreadable on one end or the other, so pick white/dark text by relative luminance. */
export function contrastTextColor(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  return luminance > 0.6 ? 'rgba(0, 0, 0, 0.87)' : '#fff'
}
