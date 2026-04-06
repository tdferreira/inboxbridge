import { describe, expect, it, vi } from 'vitest'
import { MAX_NOTIFICATION_HISTORY, normalizeNotificationHistory, notificationHistoriesEqual } from './notificationHistory'

describe('notificationHistory helpers', () => {
  it('keeps only the most recent bounded notification history entries', () => {
    const entries = Array.from({ length: MAX_NOTIFICATION_HISTORY + 5 }, (_, index) => ({
      id: `note-${index}`,
      message: `Message ${index}`,
      createdAt: index
    }))

    const normalized = normalizeNotificationHistory(entries)

    expect(normalized).toHaveLength(MAX_NOTIFICATION_HISTORY)
    expect(normalized[0].id).toBe('note-5')
    expect(normalized.at(-1).id).toBe(`note-${MAX_NOTIFICATION_HISTORY + 4}`)
  })

  it('hides stale auto-closing notifications when they are reloaded later', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-31T12:00:00Z'))

    const [normalized] = normalizeNotificationHistory([{
      id: 'stale',
      message: 'Signed in.',
      createdAt: Date.parse('2026-03-31T11:59:00Z'),
      autoCloseMs: 5_000,
      floatingVisible: true
    }])

    expect(normalized.floatingVisible).toBe(false)

    vi.useRealTimers()
  })

  it('compares normalized notification histories instead of raw object identity', () => {
    const left = [{
      id: 'note-1',
      message: 'Signed in.',
      createdAt: 1,
      tone: 'unknown'
    }]
    const right = [{
      id: 'note-1',
      message: 'Signed in.',
      createdAt: 1,
      tone: 'success'
    }]

    expect(notificationHistoriesEqual(left, right)).toBe(true)
  })

  it('normalizes grouped repeat counts and falls back to 1 for invalid values', () => {
    const [grouped, fallback] = normalizeNotificationHistory([
      { id: 'grouped', message: 'Load failed', createdAt: 1, repeatCount: 5 },
      { id: 'fallback', message: 'Saved', createdAt: 2, repeatCount: 0 }
    ])

    expect(grouped.repeatCount).toBe(5)
    expect(fallback.repeatCount).toBe(1)
  })
})
