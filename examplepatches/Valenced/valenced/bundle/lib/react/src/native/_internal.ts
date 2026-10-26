import type { RunApplicationCallback } from '@valenced/react/types'

export const sBeforeRunApplication = new Set<RunApplicationCallback>()
export const sAfterRunApplication = new Set<RunApplicationCallback>()
