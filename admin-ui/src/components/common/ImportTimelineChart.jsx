import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'

import ModalDialog from './ModalDialog'
import './ImportTimelineChart.css'

const PRESET_ORDER = ['today', 'yesterday', 'pastWeek', 'pastMonth', 'pastTrimester', 'pastSemester', 'pastYear']
const SERIES_COLORS = ['#b45309', '#2563eb', '#dc2626']

function formatBucketLabel(rangeKey, bucketLabel) {
  if (rangeKey === 'today' || rangeKey === 'yesterday') return bucketLabel
  if (rangeKey === 'pastWeek' || rangeKey === 'pastMonth') return bucketLabel.slice(5)
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

function ImportTimelineChart({ customRangeLoader = null, points = [], timelines = null, series = null, t, title }) {
  const chartFrameRef = useRef(null)
  const resolvedSeries = useMemo(() => {
    if (Array.isArray(series) && series.length > 0) {
      return series
    }
    const singleTimelines = timelines && Object.keys(timelines).length > 0 ? timelines : { pastMonth: points }
    return [{ key: 'imports', label: t('pollingStats.seriesImports'), timelines: singleTimelines }]
  }, [points, series, t, timelines])

  const selectableRanges = useMemo(() => {
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

  const [selectedRange, setSelectedRange] = useState(selectableRanges[0] || 'pastMonth')
  const [customRangeDialogOpen, setCustomRangeDialogOpen] = useState(false)
  const [customRangeLoading, setCustomRangeLoading] = useState(false)
  const [customRangeError, setCustomRangeError] = useState('')
  const [customRangeData, setCustomRangeData] = useState(null)
  const [customRangeForm, setCustomRangeForm] = useState({ from: '', to: '' })
  const [chartFrameReady, setChartFrameReady] = useState(false)

  useEffect(() => {
    if (!selectableRanges.includes(selectedRange)) {
      setSelectedRange(selectableRanges[0] || 'pastMonth')
    }
  }, [selectedRange, selectableRanges])

  async function applyCustomRange() {
    if (!customRangeLoader) return
    setCustomRangeLoading(true)
    setCustomRangeError('')
    try {
      const fromDate = fromDateTimeLocalValue(customRangeForm.from)
      const toDate = customRangeForm.to ? fromDateTimeLocalValue(customRangeForm.to) : new Date()
      if (!fromDate) {
        throw new Error(t('pollingStats.customRangeFromRequired'))
      }
      if (!toDate) {
        throw new Error(t('pollingStats.customRangeToInvalid'))
      }
      if (toDate <= fromDate) {
        throw new Error(t('pollingStats.customRangeInvalid'))
      }
      const payload = await customRangeLoader({
        from: fromDate.toISOString(),
        to: toDate.toISOString()
      })
      setCustomRangeData(payload)
      setSelectedRange('custom')
      setCustomRangeDialogOpen(false)
    } catch (error) {
      setCustomRangeError(error.message || t('pollingStats.customRangeLoadError'))
    } finally {
      setCustomRangeLoading(false)
    }
  }

  const visibleSeries = useMemo(() => resolvedSeries.map((entry) => ({
    ...entry,
    points: selectedRange === 'custom'
      ? customRangeData?.[entry.key] || []
      : entry.timelines?.[selectedRange] || []
  })), [customRangeData, resolvedSeries, selectedRange])

  const chartRows = useMemo(() => buildChartRows(visibleSeries, selectedRange), [selectedRange, visibleSeries])

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
    <div className="timeline-chart-card surface-card">
      <div className="timeline-chart-header">
        <div className="timeline-chart-title">{title}</div>
        {selectableRanges.length > 1 ? (
          <label className="timeline-chart-select-label">
            <span>{t('pollingStats.range')}</span>
            <select
              onChange={(event) => {
                const nextValue = event.target.value
                if (nextValue === 'custom') {
                  setCustomRangeError('')
                  setCustomRangeForm({ from: '', to: '' })
                  setCustomRangeDialogOpen(true)
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
          <div className="timeline-chart-frame" ref={chartFrameRef}>
            {chartFrameReady ? (
              <ResponsiveContainer height="100%" minHeight={240} minWidth={320} width="100%">
                <LineChart data={chartRows} margin={{ top: 8, right: 16, bottom: 6, left: 0 }}>
                  <CartesianGrid stroke="rgba(15, 23, 42, 0.08)" strokeDasharray="3 4" vertical={false} />
                  <XAxis
                    axisLine={false}
                    dataKey="label"
                    minTickGap={18}
                    tick={{ fill: 'var(--muted-text)', fontSize: 12 }}
                    tickLine={false}
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
                    cursor={{ stroke: 'rgba(37, 99, 235, 0.35)', strokeDasharray: '3 4' }}
                    labelFormatter={(_, payload) => payload?.[0]?.payload?.bucketLabel || ''}
                  />
                  <Legend
                    formatter={(value) => <span className="timeline-chart-legend-text">{value}</span>}
                    wrapperStyle={{ paddingTop: '4px' }}
                  />
                  {visibleSeries.map((entry, index) => (
                    <Line
                      activeDot={{ r: 5 }}
                      dataKey={entry.key}
                      dot={false}
                      key={entry.key}
                      name={entry.label}
                      stroke={SERIES_COLORS[index % SERIES_COLORS.length]}
                      strokeWidth={2.5}
                      type="monotone"
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
