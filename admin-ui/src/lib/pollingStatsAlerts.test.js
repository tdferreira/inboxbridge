import {
  detectScheduledRunAnomaly,
  GLOBAL_STATS_ANOMALY_NOTIFICATION_MAX_AGE_MS,
  GLOBAL_STATS_ANOMALY_WARNING_MAX_AGE_MS
} from './pollingStatsAlerts'

describe('pollingStatsAlerts', () => {
  it('collapses contiguous suspicious hourly buckets into a single anomaly span anchored at the start', () => {
    const now = Date.parse('2026-04-05T12:00:00Z')
    const anomaly = detectScheduledRunAnomaly({
      scheduledRunTimelines: {
        yesterday: [
          { bucketLabel: '07:00', importedMessages: 1147 },
          { bucketLabel: '08:00', importedMessages: 900 },
          { bucketLabel: '09:00', importedMessages: 720 }
        ]
      }
    }, '5m', 2, now)

    expect(anomaly.rangeKey).toBe('yesterday')
    expect(anomaly.startBucketLabel).toBe('07:00')
    expect(anomaly.endBucketLabel).toBe('09:00')
    expect(anomaly.bucketLabel).toBe('07:00')
    expect(anomaly.observedRuns).toBe(1147)
  })

  it('keeps anomaly notifications visible only for the first 24 hours', () => {
    const now = Date.parse('2026-04-04T12:00:00Z')
    const anomaly = detectScheduledRunAnomaly({
      scheduledRunTimelines: {
        custom: [
          { bucketLabel: '2026-04-04T10:00:00Z', importedMessages: 720 }
        ]
      }
    }, '5m', 1, now)

    expect(anomaly.notificationVisible).toBe(true)
    expect(anomaly.warningVisible).toBe(true)
    expect(anomaly.ageMs).toBe(now - Date.parse('2026-04-04T10:00:00Z'))
    expect(anomaly.ageMs).toBeLessThan(GLOBAL_STATS_ANOMALY_NOTIFICATION_MAX_AGE_MS)
  })

  it('keeps the section warning longer than the notification but expires it after one week', () => {
    const recentEnoughForWarning = detectScheduledRunAnomaly({
      scheduledRunTimelines: {
        custom: [
          { bucketLabel: '2026-04-04T09:00:00Z', importedMessages: 720 }
        ]
      }
    }, '5m', 1, Date.parse('2026-04-10T08:30:00Z'))

    expect(recentEnoughForWarning.notificationVisible).toBe(false)
    expect(recentEnoughForWarning.warningVisible).toBe(true)

    const expiredWarning = detectScheduledRunAnomaly({
      scheduledRunTimelines: {
        custom: [
          { bucketLabel: '2026-04-04T09:00:00Z', importedMessages: 720 }
        ]
      }
    }, '5m', 1, Date.parse('2026-04-12T09:00:01Z'))

    expect(expiredWarning.warningVisible).toBe(false)
    expect(expiredWarning.ageMs).toBeGreaterThan(GLOBAL_STATS_ANOMALY_WARNING_MAX_AGE_MS)
  })

  it('still keeps a five-day-old anomaly warning visible for the section while hiding the notification', () => {
    const anomaly = detectScheduledRunAnomaly({
      scheduledRunTimelines: {
        pastWeek: [
          { bucketLabel: '2026-04-01T07:00:00Z', importedMessages: 900 },
          { bucketLabel: '2026-04-01T08:00:00Z', importedMessages: 1147 }
        ]
      }
    }, '5m', 2, Date.parse('2026-04-06T09:00:00Z'))

    expect(anomaly.notificationVisible).toBe(false)
    expect(anomaly.warningVisible).toBe(true)
    expect(anomaly.startBucketLabel).toBe('2026-04-01T07:00:00Z')
    expect(anomaly.endBucketLabel).toBe('2026-04-01T08:00:00Z')
  })
})
