import { api } from './client'
import type { ImportMemberMapping, ImportResult, ImportType } from './types'

export const importApi = {
  run: (clubId: string, type: ImportType, files: File[], mappings: ImportMemberMapping[]) => {
    const form = new FormData()
    form.set('type', type)
    form.set('mappings', JSON.stringify(mappings))
    files.forEach((file) => form.append('file', file))
    return api.postForm<ImportResult>(`/clubs/${clubId}/import`, form)
  },
}
