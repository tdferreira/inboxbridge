import { useEffect, useMemo, useState } from 'react'

import './ImportTimelineChart.css'

const PRESET_ORDER = ['today', 'yesterday', 'pastWeek', 'pastMonth', 'pastTrimester', 'pastSemester', 'pastYear']

function formatBucketLabel(rangeKey, bucketLabel) {
  if (rangeKey === 'today' || rangeKey === 'yesterday') return bucketLabel
  if (rangeKey === 'pastWeek' || rangeKey === 'pastMonth') return bucketLabel.slice(5)
  return bucketLabel
}

function ImportTimelineChart({ points = [], timelines = null, t, title }) {
  const availableTimelines = useMemo(() => {
    if (timelines && Object.keys(timelines).length > 0) {
      return timelines
    }
    return { pastMonth: points }
  }, [points, timelines])
  const selectableRanges = useMemo(
    () => PRESET_ORDER.filter((key) => Array.isArray(availableTimelines[key])),
    [availableTimelines]
  )
  const [selectedRange, setSelectedRange] = useState(selectableRanges[0] || 'pastMonth')

  useEffect(() => {
    if (!selectableRanges.includes(selectedRange)) {
      setSelectedRange(selectableRanges[0] || 'pastMonth')
    }
  }, [selectedRange, selectableRanges])

  const resolvedPoints = availableTimelines[selectedRange] || []
  const maxValue = Math.max(1, ...resolvedPoints.map((entry) => entry.importedMessages || 0))
  const width = 100
  const height = 180
  const pointsPath = resolvedPoints.map((point, index) => {
    const x = resolvedPoints.length === 1 ? width / 2 : (index / (resolvedPoints.length - 1)) * width
    const y = height - ((point.importedMessages || 0) / maxValue) * (height - 16) - 8
    return `${x},${Math.max(8, y)}`
  }).join(' ')
  return (
    <div className="timeline-chart-card surface-card">
      <div className="timeline-chart-header">
        <div className="timeline-chart-title">{title}</div>
        {selectableRanges.length > 1 ? (
          <label className="timeline-chart-select-label">
            <span>{t('pollingStats.range')}</span>
            <select onChange={(event) => setSelectedRange(event.target.value)} value={selectedRange}>
              {selectableRanges.map((option) => (
                <option key={option} value={option}>{t(`pollingStats.${option}`)}</option>
              ))}
            </select>
          </label>
        ) : null}
      </div>
      {resolvedPoints.length === 0 ? (
        <div className="muted-box">{t('pollingStats.noImportTimeline')}</div>
      ) : (
        <div aria-label={title} className="timeline-chart timeline-chart-line" role="img">
          <svg className="timeline-chart-svg" preserveAspectRatio="none" viewBox={`0 0 ${width} ${height}`}>
            <polyline className="timeline-chart-polyline" fill="none" points={pointsPath} />
            {resolvedPoints.map((point, index) => {
              const x = resolvedPoints.length === 1 ? width / 2 : (index / (resolvedPoints.length - 1)) * width
              const y = height - ((point.importedMessages || 0) / maxValue) * (height - 16) - 8
              return <circle className="timeline-chart-dot" cx={x} cy={Math.max(8, y)} key={point.bucketLabel} r="1.7" />
            })}
          </svg>
          {resolvedPoints.map((point) => {
            const count = point.importedMessages || 0
            return (
              <div className="timeline-chart-column" key={point.bucketLabel}>
                <div className="timeline-chart-count">{count}</div>
                <div className="timeline-chart-label" title={t('pollingStats.timelineBarTitle', { date: point.bucketLabel, count })}>
                  {formatBucketLabel(selectedRange, point.bucketLabel)}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default ImportTimelineChart
