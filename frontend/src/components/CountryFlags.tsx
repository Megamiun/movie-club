import { Stack, Tooltip } from '@mui/material'
import { countryFlag, countryName } from '../utils/country'

/** Renders one flag emoji per country code, each with a tooltip naming the country -- shared by the Meetings table
 * (movie/series origin country) and the meeting detail page's movie block (see MovieSection). */
export function CountryFlags({ codes }: { codes: string[] | null | undefined }) {
  if (!codes || codes.length === 0) return <>—</>
  return (
    <Stack direction="row" spacing={0.5} component="span">
      {codes.map((code) => (
        <Tooltip key={code} title={countryName(code)}>
          <span>{countryFlag(code)}</span>
        </Tooltip>
      ))}
    </Stack>
  )
}
