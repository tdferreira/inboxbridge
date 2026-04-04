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

export function detectScheduledRunAnomaly(stats, scheduledRunAlertInterval, scheduledRunAlertSourceCount) {
  const intervalMinutes = parseIntervalMinutes(scheduledRunAlertInterval)
  const sourceCount = Number(scheduledRunAlertSourceCount || 0)
  if (!stats || !intervalMinutes || intervalMinutes <= 0 || sourceCount <= 0) {
    return null
  }

  const expectedRunsPerSourcePerHour = Math.max(1, 60 / intervalMinutes)
  const expectedRunsPerHour = Math.max(1, Math.ceil(expectedRunsPerSourcePerHour * sourceCount))
  const anomalyThreshold = Math.max(expectedRunsPerHour * 2, expectedRunsPerHour + sourceCount)
  const suspiciousPoints = Object.values(stats.scheduledRunTimelines || {})
    .flatMap((points) => Array.isArray(points) ? points : [])
    .filter((point) => isHourlyTimelineBucket(point?.bucketLabel))
    .map((point) => ({
      bucketLabel: point.bucketLabel,
      observedRuns: Number(point.importedMessages || 0)
    }))
    .filter((point) => point.observedRuns > anomalyThreshold)
    .sort((left, right) => right.observedRuns - left.observedRuns)

  if (suspiciousPoints.length === 0) {
    return null
  }

  const worstPoint = suspiciousPoints[0]
  return {
    bucketLabel: worstPoint.bucketLabel,
    observedRuns: worstPoint.observedRuns,
    expectedRunsPerHour,
    sourceCount
  }
}
