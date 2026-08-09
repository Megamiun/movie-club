import type { ClubDetail } from '../api/types'

export interface ClubOutletContext {
  club: ClubDetail
  reload: () => void
  silentReload: () => void
}
