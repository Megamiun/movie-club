import SvgIcon, { type SvgIconProps } from '@mui/material/SvgIcon'

/** IMDB has no icon in @mui/icons-material (a Material Symbols set, not brand marks), and pulling in a whole
 * brand-icon package (e.g. simple-icons) for this one glyph wasn't worth the added dependency -- this reproduces
 * IMDB's yellow badge/wordmark directly as an inline SVG, matching every other icon's `fontSize`/`sx` props via
 * `SvgIcon` (see `ImdbLink`, which is the only place this is used). */
export function ImdbIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props} viewBox="0 0 24 24">
      <rect x="1" y="4" width="22" height="16" rx="3" fill="#F5C518" />
      <text
        x="12"
        y="15.5"
        textAnchor="middle"
        fontFamily="Arial, Helvetica, sans-serif"
        fontWeight="bold"
        fontSize="8.5"
        fill="#000000"
      >
        IMDb
      </text>
    </SvgIcon>
  )
}
