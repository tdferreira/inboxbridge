import { describe, expect, it, vi, afterEach } from 'vitest'
import { statsTimezoneHeader } from '@/lib/statsTimezone'

describe('statsTimezoneHeader', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the resolved browser timezone header when available', () => {
    vi.stubGlobal('Intl', {
      DateTimeFormat: () => ({
        resolvedOptions: () => ({ timeZone: 'Europe/Lisbon' })
      })
    })

    expect(statsTimezoneHeader()).toEqual({ 'X-InboxBridge-Timezone': 'Europe/Lisbon' })
  })

  it('falls back to UTC when the browser timezone is unavailable', () => {
    vi.stubGlobal('Intl', {
      DateTimeFormat: () => ({
        resolvedOptions: () => ({})
      })
    })

    expect(statsTimezoneHeader()).toEqual({ 'X-InboxBridge-Timezone': 'UTC' })
  })
})
