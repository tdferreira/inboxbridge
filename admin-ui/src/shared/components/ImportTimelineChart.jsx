import { Fragment, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import {
  CartesianGrid,
  Curve,
  Legend,
  Line,
  LineChart,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'

import ModalDialog from './ModalDialog'
import { formatDate } from '@/lib/formatters'
import './ImportTimelineChart.css'

const PRESET_ORDER = ['today', 'yesterday', 'pastWeek', 'pastMonth', 'pastTrimester', 'pastSemester', 'pastYear']
const SERIES_COLORS_BY_KEY = {
  imports: '#16a34a',
  duplicates: '#2563eb',
  errors: '#dc2626',
  manualRuns: '#b45309',
  scheduledRuns: '#4f46e5',
  idleRuns: '#0f766e'
}
const FALLBACK_SERIES_COLORS = ['#16a34a', '#2563eb', '#dc2626', '#b45309', '#4f46e5', '#0f766e']
const CHART_VIEW_STATE = new Map()
const CHART_VIEW_STATE_STORAGE_PREFIX = 'inboxbridge.chartViewState.'

function formatBucketLabel(rangeKey, bucketLabel) {
  if (rangeKey === 'today') {
    return /^\d{4}-\d{2}-\d{2}T/.test(bucketLabel) ? bucketLabel.slice(5).replace('T', ' ') : bucketLabel
  }
  if (rangeKey === 'yesterday') return bucketLabel
  if (rangeKey === 'pastWeek') {
    return /^\d{4}-\d{2}-\d{2}T/.test(bucketLabel) ? bucketLabel.slice(5).replace('T', ' ') : bucketLabel
  }
  if (rangeKey === 'pastMonth') return bucketLabel.slice(5)
  return bucketLabel
}

function buildChartRows(entries, rangeKey) {
  const buckets = new Map()
  entries.forEach((entry) => {
    ;(entry.points || []).forEach((point) => {
      const current = buckets.get(point.bucketLabel) || { bucketLabel: point.bucketLabel }
      current[entry.key] = point.importedMessages || 0
      buckets.set(point.bucketLabel, current)
    })
  })
  return Array.from(buckets.values()).map((row) => ({
    ...row,
    label: formatBucketLabel(rangeKey, row.bucketLabel)
  }))
}

function seriesHasKnownRange(seriesEntries, rangeKey) {
  return seriesEntries.some((entry) => Array.isArray(entry.timelines?.[rangeKey]))
}

function seriesHasPointsForRange(seriesEntries, rangeKey) {
  return seriesEntries.some((entry) => Array.isArray(entry.timelines?.[rangeKey]) && entry.timelines[rangeKey].length > 0)
}

function buildVisibleMarkers(chartRows, anomalyMarkers, nativeResolution, targetResolution, rangeKey, t) {
  const rowsByBucket = new Map(chartRows.map((row) => [row.bucketLabel, row]))
  return (Array.isArray(anomalyMarkers) ? anomalyMarkers : [])
    .map((marker) => {
      if (marker?.rangeKey && marker.rangeKey !== rangeKey) {
        return null
      }
      const markerBucketLabel = aggregateBucketKey(
        marker?.bucketLabel,
        nativeResolution,
        targetResolution,
        rangeKey,
        t
      )
      const endMarkerBucketLabel = marker?.endBucketLabel
        ? aggregateBucketKey(
          marker.endBucketLabel,
          nativeResolution,
          targetResolution,
          rangeKey,
          t
        )
        : markerBucketLabel
      const row = rowsByBucket.get(markerBucketLabel)
      if (!row) {
        return null
      }
      const value = row[marker.seriesKey]
      if (!Number.isFinite(value)) {
        return null
      }
      return {
        ...marker,
        endMarkerBucketLabel,
        markerBucketLabel,
        value
      }
    })
    .filter(Boolean)
}

function renderAnomalyCurveShape(props) {
  const {
    clipId,
    markers = [],
    points = [],
    ...rest
  } = props || {}

  if (!clipId || !Array.isArray(points) || points.length === 0 || !Array.isArray(markers) || markers.length === 0) {
    return null
  }

  const pointIndexByBucket = new Map(points.map((point, index) => [point?.payload?.bucketLabel, index]))
  const clipRects = markers.map((marker) => {
    const startIndex = pointIndexByBucket.get(marker.markerBucketLabel)
    const endIndex = pointIndexByBucket.get(marker.endMarkerBucketLabel)
    if (startIndex == null || endIndex == null) {
      return null
    }
    const startPoint = points[Math.min(startIndex, endIndex)]
    const endPoint = points[Math.max(startIndex, endIndex)]
    const fromX = Number(startPoint?.x)
    const toX = Number(endPoint?.x)
    if (!Number.isFinite(fromX) || !Number.isFinite(toX)) {
      return null
    }
    return {
      key: `${marker.markerBucketLabel}:${marker.endMarkerBucketLabel}`,
      width: Math.max(1, Math.abs(toX - fromX)),
      x: Math.min(fromX, toX)
    }
  }).filter(Boolean)

  if (clipRects.length === 0) {
    return null
  }

  return (
    <>
      <defs>
        <clipPath id={clipId}>
          {clipRects.map((rect) => (
            <rect
              height="2000"
              key={rect.key}
              width={rect.width}
              x={rect.x}
              y="-1000"
            />
          ))}
        </clipPath>
      </defs>
      <Curve
        {...rest}
        clipPath={`url(#${clipId})`}
        points={points}
      />
    </>
  )
}

function renderAnomalyMarkerShape(props) {
  const { cx = 0, cy = 0, payload } = props || {}

  return (
    <g className="timeline-chart-anomaly-marker" data-marker-bucket={payload?.bucketLabel || ''}>
      <circle cx={cx} cy={cy} fill="#b91c1c" r={10} stroke="#ffffff" strokeWidth={2} />
      <text
        dominantBaseline="central"
        fill="#ffffff"
        fontSize="12"
        fontWeight="700"
        textAnchor="middle"
        x={cx}
        y={cy}
      >
        !
      </text>
    </g>
  )
}

export function colorForSeries(entryKey, index) {
  return SERIES_COLORS_BY_KEY[entryKey] || FALLBACK_SERIES_COLORS[index % FALLBACK_SERIES_COLORS.length]
}

function TimelineTooltip({ active, label, payload, t }) {
  if (!active || !payload?.length) return null
  return (
    <div className="timeline-chart-tooltip">
      <div className="timeline-chart-tooltip-title">{label}</div>
      <div className="timeline-chart-tooltip-body">
        {payload.map((entry) => (
          <div className="timeline-chart-tooltip-row" key={entry.dataKey}>
            <span className="timeline-chart-tooltip-key">
              <span
                aria-hidden="true"
                className="timeline-chart-legend-swatch"
                style={{ '--series-color': entry.color }}
              />
              {entry.name}
            </span>
            <strong>{entry.value ?? 0}</strong>
          </div>
        ))}
      </div>
    </div>
  )
}

function toDateTimeLocalValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function fromDateTimeLocalValue(value) {
  if (!value) return null
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return null
  return parsed
}

function subtractRelativeWindow(baseDate, amount, unit) {
  const value = new Date(baseDate)
  if (!Number.isFinite(amount) || amount <= 0) {
    return null
  }
  if (unit === 'hour') {
    value.setHours(value.getHours() - amount)
    return value
  }
  if (unit === 'day') {
    value.setDate(value.getDate() - amount)
    return value
  }
  if (unit === 'week') {
    value.setDate(value.getDate() - (amount * 7))
    return value
  }
  if (unit === 'month') {
    value.setMonth(value.getMonth() - amount)
    return value
  }
  if (unit === 'year') {
    value.setFullYear(value.getFullYear() - amount)
    return value
  }
  return null
}

function detectResolution(rangeKey, chartRows) {
  if (rangeKey === 'today' || rangeKey === 'yesterday') {
    return 'hour'
  }
  const sampleBucketLabel = chartRows[0]?.bucketLabel || ''
  if (/^\d{4}-\d{2}-\d{2}T/.test(sampleBucketLabel) || /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(sampleBucketLabel) || /^\d{2}:\d{2}$/.test(sampleBucketLabel)) {
    return 'hour'
  }
  if (/^\d{4}-W\d{2}$/.test(sampleBucketLabel)) {
    return 'week'
  }
  if (/^\d{4}-\d{2}$/.test(sampleBucketLabel)) {
    return 'month'
  }
  return 'day'
}

function customRangeDurationDays(customRangeBounds) {
  if (!customRangeBounds?.from || !customRangeBounds?.to) {
    return null
  }
  const from = Date.parse(customRangeBounds.from)
  const to = Date.parse(customRangeBounds.to)
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
    return null
  }
  return (to - from) / (1000 * 60 * 60 * 24)
}

function customResolutionScaleOrder(resolution) {
  const ranks = {
    hour: 0,
    day: 1,
    week: 2,
    month: 3,
    trimester: 4,
    semester: 5,
    year: 6
  }
  return ranks[resolution] ?? 999
}

function filterResolutionsAtOrAboveNative(nativeResolution, resolutions) {
  const nativeRank = customResolutionScaleOrder(nativeResolution)
  return resolutions.filter((resolution) => customResolutionScaleOrder(resolution) >= nativeRank)
}

function customRangeResolutionsFor(durationDays, nativeResolution) {
  if (durationDays == null) {
    return filterResolutionsAtOrAboveNative(nativeResolution, ['hour', 'day', 'week', 'month', 'trimester', 'semester', 'year'])
  }
  if (durationDays < 1) {
    return filterResolutionsAtOrAboveNative(nativeResolution, ['hour'])
  }
  if (durationDays < 14) {
    return filterResolutionsAtOrAboveNative(nativeResolution, ['hour', 'day'])
  }
  if (durationDays < 60) {
    return filterResolutionsAtOrAboveNative(nativeResolution, ['day', 'week'])
  }
  if (durationDays < 730) {
    return filterResolutionsAtOrAboveNative(nativeResolution, ['week', 'month'])
  }
  return filterResolutionsAtOrAboveNative(nativeResolution, ['month', 'trimester', 'semester', 'year'])
}

function defaultResolutionFor(rangeKey, nativeResolution, customRangeBounds) {
  if (rangeKey === 'today' || rangeKey === 'yesterday') {
    return 'hour'
  }
  if (rangeKey === 'pastWeek') {
    return nativeResolution === 'hour' ? 'day' : nativeResolution
  }
  if (rangeKey === 'pastMonth') {
    return nativeResolution === 'month' ? 'month' : 'day'
  }
  if (rangeKey === 'pastTrimester') {
    return nativeResolution === 'month' ? 'month' : 'week'
  }
  if (rangeKey === 'pastSemester' || rangeKey === 'pastYear') {
    return nativeResolution === 'month' ? 'month' : 'week'
  }
  if (rangeKey === 'custom') {
    const durationDays = customRangeDurationDays(customRangeBounds)
    return customRangeResolutionsFor(durationDays, nativeResolution)[0] || nativeResolution
  }
  return nativeResolution
}

function availableResolutionsFor(rangeKey, nativeResolution, customRangeBounds) {
  if (rangeKey === 'today' || rangeKey === 'yesterday') {
    return ['hour']
  }
  if (rangeKey === 'pastWeek') {
    return nativeResolution === 'hour' ? ['hour', 'day'] : ['day']
  }
  if (rangeKey === 'pastMonth') {
    return nativeResolution === 'month' ? ['month'] : ['day', 'week']
  }
  if (rangeKey === 'pastTrimester') {
    return nativeResolution === 'month' ? ['month'] : ['week', 'month']
  }
  if (rangeKey === 'pastSemester' || rangeKey === 'pastYear') {
    return nativeResolution === 'month' ? ['month'] : ['week', 'month']
  }
  if (rangeKey === 'custom') {
    const durationDays = customRangeDurationDays(customRangeBounds)
    return customRangeResolutionsFor(durationDays, nativeResolution)
  }
  return [nativeResolution]
}

function getWeekBucketKey(date) {
  const value = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()))
  const day = value.getUTCDay() || 7
  value.setUTCDate(value.getUTCDate() + 4 - day)
  const yearStart = new Date(Date.UTC(value.getUTCFullYear(), 0, 1))
  const week = Math.ceil((((value - yearStart) / 86400000) + 1) / 7)
  return `${value.getUTCFullYear()}-W${String(week).padStart(2, '0')}`
}

function getTrimesterBucketKey(date) {
  const month = date.getUTCMonth()
  return `${date.getUTCFullYear()}-Q${Math.floor(month / 3) + 1}`
}

function getSemesterBucketKey(date) {
  const month = date.getUTCMonth()
  return `${date.getUTCFullYear()}-S${month < 6 ? 1 : 2}`
}

function parseBucketDate(bucketLabel, nativeResolution) {
  if (nativeResolution === 'hour' && (/^\d{4}-\d{2}-\d{2}T/.test(bucketLabel) || /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(bucketLabel))) {
    const normalized = bucketLabel.includes(' ') ? bucketLabel.replace(' ', 'T') : bucketLabel
    const parsed = new Date(normalized)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  if (nativeResolution === 'week' && /^\d{4}-W\d{2}$/.test(bucketLabel)) {
    const [yearPart, weekPart] = bucketLabel.split('-W')
    const year = Number.parseInt(yearPart, 10)
    const week = Number.parseInt(weekPart, 10)
    if (!Number.isFinite(year) || !Number.isFinite(week)) {
      return null
    }
    const firstThursday = new Date(Date.UTC(year, 0, 4))
    const day = firstThursday.getUTCDay() || 7
    const weekStart = new Date(firstThursday)
    weekStart.setUTCDate(firstThursday.getUTCDate() - day + 1 + ((week - 1) * 7))
    return weekStart
  }
  if ((nativeResolution === 'day' || nativeResolution === 'hour') && /^\d{4}-\d{2}-\d{2}$/.test(bucketLabel)) {
    const parsed = new Date(`${bucketLabel}T00:00:00Z`)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  if (nativeResolution === 'month' && /^\d{4}-\d{2}$/.test(bucketLabel)) {
    const parsed = new Date(`${bucketLabel}-01T00:00:00Z`)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  if (nativeResolution === 'trimester' && /^\d{4}-Q[1-4]$/.test(bucketLabel)) {
    const [yearPart, quarterPart] = bucketLabel.split('-Q')
    const year = Number.parseInt(yearPart, 10)
    const quarter = Number.parseInt(quarterPart, 10)
    if (!Number.isFinite(year) || !Number.isFinite(quarter)) {
      return null
    }
    return new Date(Date.UTC(year, (quarter - 1) * 3, 1))
  }
  if (nativeResolution === 'semester' && /^\d{4}-S[12]$/.test(bucketLabel)) {
    const [yearPart, semesterPart] = bucketLabel.split('-S')
    const year = Number.parseInt(yearPart, 10)
    const semester = Number.parseInt(semesterPart, 10)
    if (!Number.isFinite(year) || !Number.isFinite(semester)) {
      return null
    }
    return new Date(Date.UTC(year, semester === 1 ? 0 : 6, 1))
  }
  return null
}

function aggregateBucketKey(bucketLabel, nativeResolution, targetResolution, rangeKey, t) {
  if (nativeResolution === targetResolution) {
    return bucketLabel
  }

  if (nativeResolution === 'hour') {
    if (/^\d{4}-\d{2}-\d{2}T/.test(bucketLabel)) {
      if (targetResolution === 'day') return bucketLabel.slice(0, 10)
      if (targetResolution === 'week') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getWeekBucketKey(parsed) : bucketLabel
      }
      if (targetResolution === 'month') return bucketLabel.slice(0, 7)
      if (targetResolution === 'trimester') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getTrimesterBucketKey(parsed) : bucketLabel
      }
      if (targetResolution === 'semester') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getSemesterBucketKey(parsed) : bucketLabel
      }
      return bucketLabel.slice(0, 4)
    }
  }

  if (nativeResolution === 'day') {
    if (/^\d{4}-\d{2}-\d{2}/.test(bucketLabel)) {
      if (targetResolution === 'week') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getWeekBucketKey(parsed) : bucketLabel
      }
      if (targetResolution === 'month') return bucketLabel.slice(0, 7)
      if (targetResolution === 'trimester') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getTrimesterBucketKey(parsed) : bucketLabel
      }
      if (targetResolution === 'semester') {
        const parsed = parseBucketDate(bucketLabel, nativeResolution)
        return parsed ? getSemesterBucketKey(parsed) : bucketLabel
      }
      if (targetResolution === 'year') return bucketLabel.slice(0, 4)
    }
  }

  if (nativeResolution === 'month' && /^\d{4}-\d{2}/.test(bucketLabel)) {
    if (targetResolution === 'trimester') {
      const parsed = parseBucketDate(bucketLabel, nativeResolution)
      return parsed ? getTrimesterBucketKey(parsed) : bucketLabel
    }
    if (targetResolution === 'semester') {
      const parsed = parseBucketDate(bucketLabel, nativeResolution)
      return parsed ? getSemesterBucketKey(parsed) : bucketLabel
    }
    return bucketLabel.slice(0, 4)
  }
  if (nativeResolution === 'week' && /^\d{4}-W\d{2}$/.test(bucketLabel)) {
    const parsed = parseBucketDate(bucketLabel, nativeResolution)
    if (!parsed) {
      return bucketLabel
    }
    if (targetResolution === 'month') return parsed.toISOString().slice(0, 7)
    if (targetResolution === 'trimester') return getTrimesterBucketKey(parsed)
    if (targetResolution === 'semester') return getSemesterBucketKey(parsed)
    if (targetResolution === 'year') return parsed.toISOString().slice(0, 4)
  }
  if (nativeResolution === 'trimester' && /^\d{4}-Q[1-4]$/.test(bucketLabel)) {
    const parsed = parseBucketDate(bucketLabel, nativeResolution)
    if (!parsed) {
      return bucketLabel
    }
    if (targetResolution === 'semester') return getSemesterBucketKey(parsed)
    if (targetResolution === 'year') return parsed.toISOString().slice(0, 4)
  }
  if (nativeResolution === 'semester' && /^\d{4}-S[12]$/.test(bucketLabel)) {
    const parsed = parseBucketDate(bucketLabel, nativeResolution)
    if (!parsed) {
      return bucketLabel
    }
    if (targetResolution === 'year') return parsed.toISOString().slice(0, 4)
  }

  return bucketLabel
}

function formatResolutionLabel(bucketKey, targetResolution, rangeKey, t) {
  if (targetResolution === 'day' && /^\d{4}-\d{2}-\d{2}/.test(bucketKey)) {
    return rangeKey === 'pastWeek' || rangeKey === 'pastMonth' ? bucketKey.slice(5) : bucketKey
  }
  if (targetResolution === 'hour' && /^\d{4}-\d{2}-\d{2}T/.test(bucketKey)) {
    return rangeKey === 'pastWeek' ? bucketKey.slice(5).replace('T', ' ') : bucketKey.replace('T', ' ')
  }
  if (targetResolution === 'week' && /^\d{4}-W\d{2}$/.test(bucketKey)) {
    return bucketKey
  }
  if (targetResolution === 'month' && /^\d{4}-\d{2}/.test(bucketKey)) {
    return bucketKey
  }
  if (targetResolution === 'trimester' && /^\d{4}-Q[1-4]$/.test(bucketKey)) {
    return bucketKey.replace('-Q', ' Q')
  }
  if (targetResolution === 'semester' && /^\d{4}-S[12]$/.test(bucketKey)) {
    return bucketKey.replace('-S', ' S')
  }
  if (targetResolution === 'year' && /^\d{4}$/.test(bucketKey)) {
    return bucketKey
  }
  return bucketKey
}

function aggregateChartRows(chartRows, nativeResolution, targetResolution, rangeKey, t) {
  if (nativeResolution === targetResolution) {
    return chartRows
  }

  const buckets = new Map()
  chartRows.forEach((row) => {
    const bucketKey = aggregateBucketKey(row.bucketLabel, nativeResolution, targetResolution, rangeKey, t)
    const current = buckets.get(bucketKey) || { bucketLabel: bucketKey }
    Object.entries(row).forEach(([key, value]) => {
      if (key === 'bucketLabel' || key === 'label') {
        return
      }
      if (Number.isFinite(value)) {
        current[key] = (current[key] || 0) + value
      }
    })
    buckets.set(bucketKey, current)
  })

  return Array.from(buckets.values()).map((row) => ({
    ...row,
    label: formatResolutionLabel(row.bucketLabel, targetResolution, rangeKey, t)
  }))
}

function readChartViewState(chartStateKey) {
  if (!chartStateKey) {
    return null
  }
  const inMemoryState = CHART_VIEW_STATE.get(chartStateKey)
  if (inMemoryState) {
    return inMemoryState
  }
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return null
  }
  try {
    const rawValue = window.sessionStorage.getItem(`${CHART_VIEW_STATE_STORAGE_PREFIX}${chartStateKey}`)
    if (!rawValue) {
      return null
    }
    const parsedValue = JSON.parse(rawValue)
    return parsedValue && typeof parsedValue === 'object' ? parsedValue : null
  } catch {
    return null
  }
}

function writeChartViewState(chartStateKey, value) {
  if (!chartStateKey) {
    return
  }
  CHART_VIEW_STATE.set(chartStateKey, value)
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return
  }
  try {
    window.sessionStorage.setItem(`${CHART_VIEW_STATE_STORAGE_PREFIX}${chartStateKey}`, JSON.stringify(value))
  } catch {
    // Ignore storage failures and keep the in-memory fallback.
  }
}

function defaultSelectableRange(selectableRanges, resolvedSeries = []) {
  return (
    selectableRanges.find((rangeKey) => rangeKey !== 'custom' && seriesHasPointsForRange(resolvedSeries, rangeKey))
    || selectableRanges.find((rangeKey) => rangeKey !== 'custom')
    || selectableRanges[0]
    || 'pastMonth'
  )
}

function ImportTimelineChart({
  anomalyMarkers = [],
  chartStateKey = null,
  customRangeLoader = null,
  focusChartToken = 0,
  focusedRangeKey = null,
  focusedRangeToken = 0,
  locale = 'en',
  points = [],
  timelines = null,
  series = null,
  t,
  title
}) {
  const chartCardRef = useRef(null)
  const chartFrameRef = useRef(null)
  const anomalyClipId = useId().replace(/:/g, '')
  const hasMountedViewStateRef = useRef(false)
  const lastHandledChartFocusTokenRef = useRef(0)
  const lastHandledFocusTokenRef = useRef(0)
  const previousRangeRef = useRef(null)
  const previousResolutionRef = useRef(null)
  const persistedViewState = readChartViewState(chartStateKey)
  const [customRangeDisplay, setCustomRangeDisplay] = useState(null)
  const resolvedSeries = useMemo(() => {
    if (Array.isArray(series) && series.length > 0) {
      return series
    }
    const singleTimelines = timelines && Object.keys(timelines).length > 0 ? timelines : { pastMonth: points }
    return [{ key: 'imports', label: t('pollingStats.seriesImports'), timelines: singleTimelines }]
  }, [points, series, t, timelines])

  const actualSelectableRanges = useMemo(() => {
    const keys = new Set()
    resolvedSeries.forEach((entry) => {
      Object.keys(entry.timelines || {}).forEach((key) => {
        if (Array.isArray(entry.timelines[key])) keys.add(key)
      })
    })
    const options = PRESET_ORDER.filter((key) => keys.has(key))
    if (customRangeLoader) {
      options.push('custom')
    }
    return options
  }, [customRangeLoader, resolvedSeries])

  const [selectedRange, setSelectedRange] = useState(
    persistedViewState?.selectedRange
      && persistedViewState.selectedRange !== 'custom'
      && actualSelectableRanges.includes(persistedViewState.selectedRange)
      ? persistedViewState.selectedRange
      : defaultSelectableRange(actualSelectableRanges, resolvedSeries)
  )
  const selectableRanges = useMemo(() => {
    if (
      hasMountedViewStateRef.current
      && selectedRange
      && !actualSelectableRanges.includes(selectedRange)
    ) {
      return [...actualSelectableRanges, selectedRange]
    }
    return actualSelectableRanges
  }, [actualSelectableRanges, selectedRange])
  const [customRangeDialogOpen, setCustomRangeDialogOpen] = useState(false)
  const [customRangeLoading, setCustomRangeLoading] = useState(false)
  const [customRangeError, setCustomRangeError] = useState('')
  const [customRangeData, setCustomRangeData] = useState(null)
  const [customRangeForm, setCustomRangeForm] = useState({
    amount: '6',
    from: '',
    mode: 'relative',
    to: '',
    unit: 'hour'
  })
  const [customRangeBounds, setCustomRangeBounds] = useState(null)
  const [chartFrameReady, setChartFrameReady] = useState(false)
  const [hoveredMarker, setHoveredMarker] = useState(null)
  const [selectedMarker, setSelectedMarker] = useState(null)

  useEffect(() => {
    if (selectedRange === 'custom' && !customRangeData) {
      setSelectedRange(defaultSelectableRange(actualSelectableRanges, resolvedSeries))
      return
    }
    if (selectableRanges.includes(selectedRange)) {
      return
    }
    if (selectedRange && (selectedRange === 'custom' ? Boolean(customRangeLoader) : seriesHasKnownRange(resolvedSeries, selectedRange))) {
      return
    }
    if (persistedViewState?.selectedRange && selectedRange === persistedViewState.selectedRange) {
      return
    }
    if (!selectableRanges.includes(selectedRange)) {
      setSelectedRange(defaultSelectableRange(actualSelectableRanges, resolvedSeries))
    }
  }, [actualSelectableRanges, customRangeData, customRangeLoader, persistedViewState?.selectedRange, resolvedSeries, selectedRange, selectableRanges])

  async function applyCustomRange() {
    if (!customRangeLoader) return
    setCustomRangeLoading(true)
    setCustomRangeError('')
    try {
      let fromDate
      let toDate
      if (customRangeForm.mode === 'relative') {
        const amount = Number.parseInt(customRangeForm.amount, 10)
        if (!Number.isFinite(amount) || amount <= 0) {
          throw new Error(t('pollingStats.customRangeRelativeInvalid'))
        }
        toDate = new Date()
        fromDate = subtractRelativeWindow(toDate, amount, customRangeForm.unit)
        if (!fromDate) {
          throw new Error(t('pollingStats.customRangeRelativeInvalid'))
        }
      } else {
        fromDate = fromDateTimeLocalValue(customRangeForm.from)
        toDate = customRangeForm.to ? fromDateTimeLocalValue(customRangeForm.to) : new Date()
        if (!fromDate) {
          throw new Error(t('pollingStats.customRangeFromRequired'))
        }
        if (!toDate) {
          throw new Error(t('pollingStats.customRangeToInvalid'))
        }
        if (toDate <= fromDate) {
          throw new Error(t('pollingStats.customRangeInvalid'))
        }
      }
      const payload = await customRangeLoader({
        from: fromDate.toISOString(),
        to: toDate.toISOString()
      })
      setCustomRangeData(payload)
      setCustomRangeBounds({ from: fromDate.toISOString(), to: toDate.toISOString() })
      setCustomRangeDisplay({
        amount: customRangeForm.mode === 'relative' ? customRangeForm.amount : '',
        from: toDateTimeLocalValue(fromDate),
        mode: customRangeForm.mode,
        to: toDateTimeLocalValue(toDate),
        unit: customRangeForm.mode === 'relative' ? customRangeForm.unit : ''
      })
      setSelectedRange('custom')
      setCustomRangeDialogOpen(false)
    } catch (error) {
      setCustomRangeError(error.message || t('pollingStats.customRangeLoadError'))
    } finally {
      setCustomRangeLoading(false)
    }
  }

  function openCustomRangeDialog() {
    setCustomRangeError('')
    setCustomRangeForm({
      amount: customRangeDisplay?.amount || '6',
      from: customRangeDisplay?.mode === 'absolute' ? (customRangeDisplay?.from || '') : '',
      mode: customRangeDisplay?.mode || 'relative',
      to: customRangeDisplay?.mode === 'absolute' ? (customRangeDisplay?.to || '') : '',
      unit: customRangeDisplay?.unit || 'hour'
    })
    setCustomRangeDialogOpen(true)
  }

  const visibleSeries = useMemo(() => resolvedSeries.map((entry) => ({
    ...entry,
    points: selectedRange === 'custom'
      ? customRangeData?.[entry.key] || []
      : entry.timelines?.[selectedRange] || []
  })), [customRangeData, resolvedSeries, selectedRange])

  const chartRows = useMemo(() => buildChartRows(visibleSeries, selectedRange), [selectedRange, visibleSeries])
  const nativeResolution = useMemo(() => detectResolution(selectedRange, chartRows), [chartRows, selectedRange])
  const defaultResolution = useMemo(
    () => defaultResolutionFor(selectedRange, nativeResolution, customRangeBounds),
    [customRangeBounds, nativeResolution, selectedRange]
  )
  const resolutionOptions = useMemo(
    () => availableResolutionsFor(selectedRange, nativeResolution, customRangeBounds),
    [customRangeBounds, nativeResolution, selectedRange]
  )
  const [selectedResolution, setSelectedResolution] = useState(
    persistedViewState?.selectedResolution || defaultResolution
  )
  const resolvedResolution = resolutionOptions.includes(selectedResolution) ? selectedResolution : defaultResolution
  const resolvedChartRows = useMemo(
    () => aggregateChartRows(chartRows, nativeResolution, resolvedResolution, selectedRange, t),
    [chartRows, nativeResolution, resolvedResolution, selectedRange, t]
  )
  const visibleMarkers = useMemo(
    () => buildVisibleMarkers(
      resolvedChartRows,
      anomalyMarkers,
      nativeResolution,
      resolvedResolution,
      selectedRange,
      t
    ),
    [anomalyMarkers, nativeResolution, resolvedChartRows, resolvedResolution, selectedRange, t]
  )
  const activeMarker = selectedMarker || hoveredMarker
  const customRangeSummary = useMemo(() => {
    if (selectedRange !== 'custom' || !customRangeDisplay) {
      return ''
    }
    if (customRangeDisplay.mode === 'relative') {
      return t('pollingStats.customRangeRelativeSummary', {
        amount: customRangeDisplay.amount,
        from: formatDate(customRangeDisplay.from, locale),
        to: formatDate(customRangeDisplay.to, locale),
        unit: t(`pollingStats.relative${customRangeDisplay.unit[0].toUpperCase()}${customRangeDisplay.unit.slice(1)}s`)
      })
    }
    return t('pollingStats.customRangeSummary', {
      from: formatDate(customRangeDisplay.from, locale),
      to: formatDate(customRangeDisplay.to, locale)
    })
  }, [customRangeDisplay, locale, selectedRange, t])

  useEffect(() => {
    const previousRange = previousRangeRef.current
    setSelectedResolution((current) => (
      previousRange && previousRange !== selectedRange
        ? defaultResolution
        : (resolutionOptions.includes(current) ? current : defaultResolution)
    ))
  }, [defaultResolution, resolutionOptions, selectedRange])

  useEffect(() => {
    setHoveredMarker(null)
    setSelectedMarker(null)
  }, [selectedRange, resolvedResolution])

  useEffect(() => {
    if (!hasMountedViewStateRef.current) {
      hasMountedViewStateRef.current = true
      previousRangeRef.current = selectedRange
      previousResolutionRef.current = resolvedResolution
      return
    }
    if (previousRangeRef.current === selectedRange && previousResolutionRef.current === resolvedResolution) {
      return
    }
    previousRangeRef.current = selectedRange
    previousResolutionRef.current = resolvedResolution
  }, [selectedRange, resolvedResolution])

  useEffect(() => {
    if (!chartStateKey) {
      return
    }
    writeChartViewState(chartStateKey, {
      selectedRange,
      selectedResolution
    })
  }, [chartStateKey, selectedRange, selectedResolution])

  useEffect(() => {
    if (!focusedRangeKey || focusedRangeToken <= 0) {
      return
    }
    if (focusedRangeToken === lastHandledFocusTokenRef.current) {
      return
    }
    if (!selectableRanges.includes(focusedRangeKey)) {
      return
    }
    lastHandledFocusTokenRef.current = focusedRangeToken
    setSelectedRange(focusedRangeKey)
  }, [focusedRangeKey, focusedRangeToken, selectableRanges])

  useEffect(() => {
    if (focusChartToken <= 0 || focusChartToken === lastHandledChartFocusTokenRef.current) {
      return
    }
    lastHandledChartFocusTokenRef.current = focusChartToken
    const target = chartCardRef.current
    if (!target) {
      return
    }
    if (typeof target.scrollIntoView === 'function') {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    target.focus()
  }, [focusChartToken])

  function handleMarkerEnter(marker) {
    if (!selectedMarker) {
      setHoveredMarker(marker)
    }
  }

  function handleMarkerLeave() {
    if (!selectedMarker) {
      setHoveredMarker(null)
    }
  }

  function handleMarkerClick(marker) {
    setSelectedMarker((current) => (current?.bucketLabel === marker?.bucketLabel && current?.seriesKey === marker?.seriesKey ? null : marker))
    setHoveredMarker(null)
  }

  useLayoutEffect(() => {
    const frame = chartFrameRef.current
    if (!frame) {
      setChartFrameReady(false)
      return undefined
    }

    let animationFrame = null

    function updateFrameReadiness() {
      const nextReady = frame.clientWidth > 0 && frame.clientHeight > 0
      setChartFrameReady((current) => (current === nextReady ? current : nextReady))
    }

    updateFrameReadiness()

    const observer = typeof ResizeObserver === 'function'
      ? new ResizeObserver(() => {
        updateFrameReadiness()
      })
      : null

    if (observer) {
      observer.observe(frame)
    } else {
      animationFrame = window.requestAnimationFrame(updateFrameReadiness)
    }

    return () => {
      if (animationFrame !== null) {
        window.cancelAnimationFrame(animationFrame)
      }
      observer?.disconnect()
    }
  }, [selectedRange, visibleSeries.length])

  return (
    <div className="timeline-chart-card surface-card" ref={chartCardRef} tabIndex="-1">
      <div className="timeline-chart-header">
        <div className="timeline-chart-title">{title}</div>
        {selectableRanges.length > 1 ? (
          <label className="timeline-chart-select-label">
            <span>{t('pollingStats.range')}</span>
            <select
              onChange={(event) => {
                const nextValue = event.target.value
                if (nextValue === 'custom') {
                  openCustomRangeDialog()
                  return
                }
                setSelectedRange(nextValue)
              }}
              value={selectedRange}
            >
              {selectableRanges.map((option) => (
                <option key={option} value={option}>{t(`pollingStats.${option}`)}</option>
              ))}
            </select>
          </label>
        ) : null}
      </div>
      {chartRows.length === 0 ? (
        <div className="muted-box">{t('pollingStats.noImportTimeline')}</div>
      ) : (
        <div className="timeline-chart-shell">
          <div className="timeline-chart-toolbar">
            <div className="timeline-chart-toolbar-meta">
              {selectedRange === 'custom' && customRangeDisplay ? (
                <>
                  <span className="timeline-chart-meta-pill">
                    {customRangeSummary}
                  </span>
                  <button className="secondary ghost" onClick={openCustomRangeDialog} type="button">
                    {t('pollingStats.editCustomRange')}
                  </button>
                </>
              ) : null}
              {resolutionOptions.length > 1 ? (
                <label className="timeline-chart-select-label">
                  <span>{t('pollingStats.granularity')}</span>
                  <select onChange={(event) => setSelectedResolution(event.target.value)} value={resolvedResolution}>
                    {resolutionOptions.map((resolutionOption) => (
                      <option key={resolutionOption} value={resolutionOption}>
                        {t(`pollingStats.${resolutionOption}`)}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <span className="timeline-chart-meta-pill">
                  {t('pollingStats.granularity')}: {t(`pollingStats.${resolvedResolution}`)}
                </span>
              )}
            </div>
          </div>
          <div className="timeline-chart-frame" ref={chartFrameRef}>
            {activeMarker?.message ? (
              <div className="timeline-chart-anomaly-callout" role="status">
                {activeMarker.message}
              </div>
            ) : null}
            {chartFrameReady ? (
              <ResponsiveContainer height="100%" minHeight={240} width="100%">
                <LineChart data={resolvedChartRows} margin={{ top: 8, right: 16, bottom: 6, left: 0 }}>
                  <CartesianGrid stroke="rgba(15, 23, 42, 0.08)" strokeDasharray="3 4" vertical={false} />
                  <XAxis
                    axisLine={false}
                    dataKey="bucketLabel"
                    minTickGap={18}
                    tick={{ fill: 'var(--muted-text)', fontSize: 12 }}
                    tickLine={false}
                    tickFormatter={(_, index) => resolvedChartRows[index]?.label || ''}
                  />
                  <YAxis
                    allowDecimals={false}
                    axisLine={false}
                    tick={{ fill: 'var(--muted-text)', fontSize: 12 }}
                    tickLine={false}
                    width={34}
                  />
                  <Tooltip
                    content={<TimelineTooltip t={t} />}
                    cursor={false}
                    labelFormatter={(_, payload) => payload?.[0]?.payload?.bucketLabel || ''}
                  />
                  <Legend
                    formatter={(value) => <span className="timeline-chart-legend-text">{value}</span>}
                    wrapperStyle={{ paddingTop: '4px' }}
                  />
                  {visibleSeries.map((entry, index) => (
                    <Fragment key={entry.key}>
                      <Line
                        activeDot={false}
                        dataKey={entry.key}
                        dot={false}
                        key={entry.key}
                        name={entry.label}
                        stroke={colorForSeries(entry.key, index)}
                        strokeWidth={2.5}
                        type="monotone"
                      />
                      {entry.key === 'scheduledRuns' && visibleMarkers.some((marker) => marker.seriesKey === entry.key) ? (
                        <Line
                          activeDot={false}
                          className={`timeline-chart-anomaly-line-${entry.key}`}
                          dataKey={entry.key}
                          dot={false}
                          key={`anomaly:${entry.key}`}
                          legendType="none"
                          name={`${entry.label} anomaly`}
                          shape={(shapeProps) => renderAnomalyCurveShape({
                            ...shapeProps,
                            clipId: `${anomalyClipId}-${entry.key}`,
                            markers: visibleMarkers.filter((marker) => marker.seriesKey === entry.key)
                          })}
                          stroke="#dc2626"
                          strokeWidth={3.5}
                          type="monotone"
                        />
                      ) : null}
                    </Fragment>
                  ))}
                  {visibleMarkers.map((marker, index) => (
                    <ReferenceDot
                      fill="#b91c1c"
                      ifOverflow="visible"
                      isFront={true}
                      key={`${marker.seriesKey}:${marker.bucketLabel}:${index}`}
                      onClick={() => handleMarkerClick(marker)}
                      onMouseEnter={() => handleMarkerEnter(marker)}
                      onMouseLeave={handleMarkerLeave}
                      payload={marker}
                      r={5}
                      shape={renderAnomalyMarkerShape}
                      stroke="#ffffff"
                      strokeWidth={2}
                      x={marker.markerBucketLabel}
                      y={marker.value}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            ) : null}
          </div>
        </div>
      )}
      {customRangeDialogOpen ? (
        <ModalDialog
          closeLabel={t('common.closeDialog')}
          isDirty={Boolean(customRangeForm.from || customRangeForm.to)}
          onClose={() => setCustomRangeDialogOpen(false)}
          title={t('pollingStats.customRangeTitle')}
          unsavedChangesMessage={t('common.unsavedChangesConfirm')}
        >
          <div className="form-grid">
            <label>
              <span>{t('pollingStats.customRangeMode')}</span>
              <select
                onChange={(event) => setCustomRangeForm((current) => ({ ...current, mode: event.target.value }))}
                value={customRangeForm.mode}
              >
                <option value="relative">{t('pollingStats.customRangeModeRelative')}</option>
                <option value="absolute">{t('pollingStats.customRangeModeAbsolute')}</option>
              </select>
            </label>
            {customRangeForm.mode === 'relative' ? (
              <>
                <label>
                  <span>{t('pollingStats.customRangeAmount')}</span>
                  <input
                    min="1"
                    onChange={(event) => setCustomRangeForm((current) => ({ ...current, amount: event.target.value }))}
                    step="1"
                    type="number"
                    value={customRangeForm.amount}
                  />
                </label>
                <label>
                  <span>{t('pollingStats.customRangeUnit')}</span>
                  <select
                    onChange={(event) => setCustomRangeForm((current) => ({ ...current, unit: event.target.value }))}
                    value={customRangeForm.unit}
                  >
                    <option value="hour">{t('pollingStats.relativeHours')}</option>
                    <option value="day">{t('pollingStats.relativeDays')}</option>
                    <option value="week">{t('pollingStats.relativeWeeks')}</option>
                    <option value="month">{t('pollingStats.relativeMonths')}</option>
                    <option value="year">{t('pollingStats.relativeYears')}</option>
                  </select>
                </label>
              </>
            ) : (
              <>
            <label>
              <span>{t('pollingStats.customRangeFrom')}</span>
              <input
                onChange={(event) => setCustomRangeForm((current) => ({ ...current, from: event.target.value }))}
                required
                type="datetime-local"
                value={customRangeForm.from}
              />
            </label>
            <label>
              <span>{t('pollingStats.customRangeTo')}</span>
              <input
                onChange={(event) => setCustomRangeForm((current) => ({ ...current, to: event.target.value }))}
                type="datetime-local"
                value={customRangeForm.to}
              />
            </label>
              </>
            )}
          </div>
          {customRangeError ? <div className="form-error">{customRangeError}</div> : null}
          <div className="modal-actions">
            <button className="secondary" onClick={() => setCustomRangeDialogOpen(false)} type="button">
              {t('common.cancel')}
            </button>
            <button className="primary" disabled={customRangeLoading} onClick={applyCustomRange} type="button">
              {customRangeLoading ? t('common.refreshingSection') : t('pollingStats.applyCustomRange')}
            </button>
          </div>
        </ModalDialog>
      ) : null}
    </div>
  )
}

export default ImportTimelineChart
