function parseIntervalMinutes(value) {
  if (!value) return null
  const text = String(value).trim()
  if (!text) return null

  const shorthandMatch = text.match(/^(\d+(?:\.\d+)?)(ms|s|m|h|d)$/i)
  if (shorthandMatch) {
    const amount = Number(shorthandMatch[1])
    const unit = shorthandMatch[2].toLowerCase()
    if (!Number.isFinite(amount) || amount <= 0) return null
    const minutesByUnit = {
      ms: 1 / 60_000,
      s: 1 / 60,
      m: 1,
      h: 60,
      d: 1440
    }
    return amount * minutesByUnit[unit]
  }

  const isoMatch = text.match(/^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i)
  if (!isoMatch) return null
  const days = Number(isoMatch[1] || 0)
  const hours = Number(isoMatch[2] || 0)
  const minutes = Number(isoMatch[3] || 0)
  const seconds = Number(isoMatch[4] || 0)
  const totalMinutes = (days * 1440) + (hours * 60) + minutes + (seconds / 60)
  return totalMinutes > 0 ? totalMinutes : null
}

function isHourlyTimelineBucket(bucketLabel) {
  return typeof bucketLabel === 'string' && bucketLabel.includes(':')
}

export const GLOBAL_STATS_ANOMALY_NOTIFICATION_MAX_AGE_MS = 24 * 60 * 60 * 1000
export const GLOBAL_STATS_ANOMALY_WARNING_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000

function bucketOccurredAt(rangeKey, bucketLabel, nowMs) {
  if (typeof bucketLabel !== 'string') {
    return null
  }
  const hourlyMatch = bucketLabel.match(/^(\d{2}):(\d{2})$/)
  if (hourlyMatch && (rangeKey === 'today' || rangeKey === 'yesterday')) {
    const now = new Date(nowMs)
    const occurredAt = new Date(now)
    occurredAt.setSeconds(0, 0)
    occurredAt.setHours(Number(hourlyMatch[1]), Number(hourlyMatch[2]), 0, 0)
    if (rangeKey === 'yesterday') {
      occurredAt.setDate(occurredAt.getDate() - 1)
    }
    return occurredAt.getTime()
  }
  const parsed = Date.parse(bucketLabel)
  return Number.isNaN(parsed) ? null : parsed
}

export function detectScheduledRunAnomaly(stats, scheduledRunAlertInterval, scheduledRunAlertSourceCount, nowMs = Date.now()) {
  const intervalMinutes = parseIntervalMinutes(scheduledRunAlertInterval)
  const sourceCount = Number(scheduledRunAlertSourceCount || 0)
  if (!stats || !intervalMinutes || intervalMinutes <= 0 || sourceCount <= 0) {
    return null
  }

  const expectedRunsPerSourcePerHour = Math.max(1, 60 / intervalMinutes)
  const expectedRunsPerHour = Math.max(1, Math.ceil(expectedRunsPerSourcePerHour * sourceCount))
  const anomalyThreshold = Math.max(expectedRunsPerHour * 2, expectedRunsPerHour + sourceCount)
  const suspiciousPoints = Object.entries(stats.scheduledRunTimelines || {})
    .flatMap(([rangeKey, points]) => (Array.isArray(points) ? points : []).map((point) => ({ ...point, rangeKey })))
    .filter((point) => isHourlyTimelineBucket(point?.bucketLabel))
    .map((point) => ({
      rangeKey: point.rangeKey,
      bucketLabel: point.bucketLabel,
      observedRuns: Number(point.importedMessages || 0),
      occurredAt: bucketOccurredAt(point.rangeKey, point.bucketLabel, nowMs)
    }))
    .filter((point) => point.observedRuns > anomalyThreshold)
    .sort((left, right) => {
      if (left.occurredAt != null && right.occurredAt != null && left.occurredAt !== right.occurredAt) {
        return right.occurredAt - left.occurredAt
      }
      if (left.occurredAt != null && right.occurredAt == null) {
        return -1
      }
      if (left.occurredAt == null && right.occurredAt != null) {
        return 1
      }
      return right.observedRuns - left.observedRuns
    })

  if (suspiciousPoints.length === 0) {
    return null
  }

  const worstPoint = suspiciousPoints[0]
  const ageMs = worstPoint.occurredAt == null ? null : Math.max(0, nowMs - worstPoint.occurredAt)
  return {
    bucketLabel: worstPoint.bucketLabel,
    occurredAt: worstPoint.occurredAt,
    ageMs,
    observedRuns: worstPoint.observedRuns,
    expectedRunsPerHour,
    sourceCount,
    notificationVisible: ageMs == null || ageMs <= GLOBAL_STATS_ANOMALY_NOTIFICATION_MAX_AGE_MS,
    warningVisible: ageMs == null || ageMs <= GLOBAL_STATS_ANOMALY_WARNING_MAX_AGE_MS
  }
}
