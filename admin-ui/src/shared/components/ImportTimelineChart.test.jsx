import { act, fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import ImportTimelineChart from './ImportTimelineChart'
import { DATE_FORMAT_YMD_24, resetCurrentFormattingDateFormat, setCurrentFormattingDateFormat } from '@/lib/formatters'
import { translate } from '@/lib/i18n'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from '@/lib/timeZonePreferences'

vi.mock('recharts', () => ({
  CartesianGrid: () => null,
  Curve: (props) => (
    <div
      data-clip-path={props.clipPath || ''}
      data-testid={props.className || 'curve'}
    />
  ),
  Legend: () => null,
  Line: (props) => (
    <div
      data-active-dot={String(props.activeDot)}
      data-class-name={props.className || ''}
      data-has-shape={String(Boolean(props.shape))}
      data-stroke={props.stroke}
      data-testid={props.className || `line-${props.dataKey}`}
    />
  ),
  LineChart: ({ children }) => <div data-testid="line-chart">{children}</div>,
  ReferenceDot: (props) => (
    <svg
      data-testid="reference-dot"
      data-label={props.x}
      data-value={props.y}
      onClick={props.onClick}
      onMouseEnter={props.onMouseEnter}
      onMouseLeave={props.onMouseLeave}
    >
      {props.shape?.({
        cx: 32,
        cy: 24,
        payload: props.payload
      })}
    </svg>
  ),
  ResponsiveContainer: ({ children }) => <div data-testid="responsive-container">{children}</div>,
  Tooltip: (props) => <div data-cursor={String(props.cursor)} data-testid="tooltip-props" />,
  XAxis: () => null,
  YAxis: () => null
}))

function installResizeObserver() {
  const resizeObservers = []

  class ResizeObserverMock {
    constructor(callback) {
      this.callback = callback
      resizeObservers.push(this)
    }

    observe() {}

    disconnect() {}
  }

  vi.stubGlobal('ResizeObserver', ResizeObserverMock)
  return resizeObservers
}

async function mountFrame(resizeObservers) {
  const frame = document.querySelector('.timeline-chart-frame')
  Object.defineProperty(frame, 'clientWidth', { configurable: true, value: 640 })
  Object.defineProperty(frame, 'clientHeight', { configurable: true, value: 288 })

  await act(async () => {
    resizeObservers.forEach((observer) => observer.callback())
  })
}

describe('ImportTimelineChart', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
    resetCurrentFormattingTimeZone()
    if (typeof window.sessionStorage?.clear === 'function') {
      window.sessionStorage.clear()
    }
    vi.unstubAllGlobals()
  })

  it('waits for a measurable frame before mounting the responsive chart', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        points={[
          { bucketLabel: '2026-03-30', importedMessages: 4 }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    expect(screen.queryByTestId('responsive-container')).not.toBeInTheDocument()

    await mountFrame(resizeObservers)

    expect(screen.getByTestId('responsive-container')).toBeInTheDocument()
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('shows the anomaly warning message on hover and click', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        anomalyMarkers={[
          {
            bucketLabel: '10:00',
            message: 'Scheduled polling looks unusually high for this hour.',
            seriesKey: 'scheduledRuns'
          }
        ]}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '09:00', importedMessages: 3 },
                { bucketLabel: '10:00', importedMessages: 720 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.mouseEnter(screen.getByTestId('reference-dot'))

    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-label', '10:00')
    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-value', '720')
    expect(screen.getByRole('status')).toHaveTextContent('Scheduled polling looks unusually high for this hour.')
    expect(screen.getByTestId('tooltip-props')).toHaveAttribute('data-cursor', 'false')
    expect(screen.getByTestId('line-scheduledRuns')).toHaveAttribute('data-active-dot', 'false')

    fireEvent.click(screen.getByTestId('reference-dot'))
    expect(screen.getByRole('status')).toHaveTextContent('Scheduled polling looks unusually high for this hour.')

    fireEvent.click(screen.getByTestId('reference-dot'))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('anchors anomaly markers to the aggregated bucket for the selected resolution', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        anomalyMarkers={[
          {
            bucketLabel: '2026-04-02T10:00',
            message: 'Scheduled polling looks unusually high for this hour.',
            seriesKey: 'scheduledRuns'
          }
        ]}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              pastWeek: [
                { bucketLabel: '2026-04-01T09:00', importedMessages: 2 },
                { bucketLabel: '2026-04-02T10:00', importedMessages: 720 },
                { bucketLabel: '2026-04-02T11:00', importedMessages: 10 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('day')
    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-label', '2026-04-02')
    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-value', '730')
  })

  it('does not render an anomaly marker for a different selected range that happens to share the same hour label', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        anomalyMarkers={[
          {
            bucketLabel: '07:00',
            message: 'Scheduled polling looks unusually high for this hour.',
            rangeKey: 'yesterday',
            seriesKey: 'scheduledRuns'
          }
        ]}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 },
                { bucketLabel: '08:00', importedMessages: 2 }
              ],
              yesterday: [
                { bucketLabel: '07:00', importedMessages: 720 },
                { bucketLabel: '08:00', importedMessages: 2 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')
    expect(screen.queryByTestId('reference-dot')).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), {
      target: { value: 'yesterday' }
    })

    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-label', '07:00')
    expect(screen.getByTestId('reference-dot')).toHaveAttribute('data-value', '720')
  })

  it('switches to a requested focus range when an external anomaly action targets the chart', async () => {
    const resizeObservers = installResizeObserver()

    const { rerender } = render(
      <ImportTimelineChart
        focusedRangeKey="yesterday"
        focusedRangeToken={0}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ],
              yesterday: [
                { bucketLabel: '07:00', importedMessages: 720 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')

    rerender(
      <ImportTimelineChart
        focusedRangeKey="yesterday"
        focusedRangeToken={1}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ],
              yesterday: [
                { bucketLabel: '07:00', importedMessages: 720 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('yesterday')
  })

  it('focuses the chart card when requested by an external anomaly action', async () => {
    const resizeObservers = installResizeObserver()

    const { rerender } = render(
      <ImportTimelineChart
        focusChartToken={0}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    rerender(
      <ImportTimelineChart
        focusChartToken={1}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    expect(document.activeElement).toHaveClass('timeline-chart-card')
  })

  it('does not keep forcing the anomaly focus range after the targeted jump has already been handled once', async () => {
    const resizeObservers = installResizeObserver()

    const { rerender } = render(
      <ImportTimelineChart
        focusedRangeKey="pastWeek"
        focusedRangeToken={1}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ],
              pastWeek: [
                { bucketLabel: '2026-04-01T07:00', importedMessages: 720 }
              ],
              pastMonth: [
                { bucketLabel: '2026-04-01', importedMessages: 720 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastWeek')

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), {
      target: { value: 'pastMonth' }
    })
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastMonth')

    rerender(
      <ImportTimelineChart
        focusedRangeKey="pastWeek"
        focusedRangeToken={1}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              today: [
                { bucketLabel: '07:00', importedMessages: 1 }
              ],
              pastWeek: [
                { bucketLabel: '2026-04-01T07:00', importedMessages: 720 }
              ],
              pastMonth: [
                { bucketLabel: '2026-04-01', importedMessages: 720 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastMonth')
  })

  it('draws a highlighted anomaly segment over the scheduled-runs line for the anomaly window', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        anomalyMarkers={[
          {
            bucketLabel: '2026-04-02T10:00',
            endBucketLabel: '2026-04-02T11:00',
            message: 'Scheduled polling looks unusually high for this window.',
            seriesKey: 'scheduledRuns'
          }
        ]}
        series={[
          {
            key: 'scheduledRuns',
            label: 'Scheduled runs',
            timelines: {
              pastWeek: [
                { bucketLabel: '2026-04-02T09:00', importedMessages: 20 },
                { bucketLabel: '2026-04-02T10:00', importedMessages: 720 },
                { bucketLabel: '2026-04-02T11:00', importedMessages: 680 },
                { bucketLabel: '2026-04-02T12:00', importedMessages: 10 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Poll activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByTestId('line-scheduledRuns')).toHaveAttribute('data-stroke', '#4f46e5')
    expect(screen.getByTestId('timeline-chart-anomaly-line-scheduledRuns')).toHaveAttribute('data-stroke', '#dc2626')
    expect(screen.getByTestId('timeline-chart-anomaly-line-scheduledRuns')).toHaveAttribute('data-has-shape', 'true')
  })

  it('keeps the chart focused on range and resolution without rendering brush controls', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              pastMonth: Array.from({ length: 12 }, (_, index) => ({
                bucketLabel: `2026-03-${String(index + 1).padStart(2, '0')}`,
                importedMessages: index + 1
              }))
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('day')
    expect(screen.queryByRole('combobox', { name: 'Range' })).not.toBeInTheDocument()
    expect(screen.queryByText('Drag the brush handles below to focus on a denser slice of time.')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show full range' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: 'Resolution' }), {
      target: { value: 'week' }
    })

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('week')
  })

  it('uses range-aware resolution options for short and medium presets', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: Array.from({ length: 6 }, (_, index) => ({
                bucketLabel: `${String(index).padStart(2, '0')}:00`,
                importedMessages: index + 1
              })),
              pastWeek: Array.from({ length: 12 }, (_, index) => ({
                bucketLabel: `2026-04-0${String((index % 3) + 1)}T${String(index).padStart(2, '0')}:00`,
                importedMessages: index + 1
              })),
              pastTrimester: Array.from({ length: 20 }, (_, index) => ({
                bucketLabel: `2026-W${String(index + 1).padStart(2, '0')}`,
                importedMessages: index + 1
              }))
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveTextContent('Past day')
    expect(screen.getByText('Resolution: Hour')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Resolution' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), {
      target: { value: 'pastWeek' }
    })

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('day')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Hour')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Day')

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), {
      target: { value: 'pastTrimester' }
    })

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('week')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Week')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Month')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).not.toHaveTextContent('Day')
  })

  it('persists the selected range and resolution for the same stats card', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        chartStateKey="test:imports:storage"
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: Array.from({ length: 6 }, (_, index) => ({
                bucketLabel: `${String(index).padStart(2, '0')}:00`,
                importedMessages: index + 1
              })),
              pastWeek: Array.from({ length: 12 }, (_, index) => ({
                bucketLabel: `2026-04-0${String((index % 3) + 1)}T${String(index).padStart(2, '0')}:00`,
                importedMessages: index + 1
              }))
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), {
      target: { value: 'pastWeek' }
    })
    fireEvent.change(screen.getByRole('combobox', { name: 'Resolution' }), {
      target: { value: 'hour' }
    })

    const storedState = JSON.parse(window.sessionStorage.getItem('inboxbridge.chartViewState.test:imports:storage'))
    expect(storedState.selectedRange).toBe('pastWeek')
    expect(storedState.selectedResolution).toBe('hour')
  })

  it('falls back from a persisted custom range after a hard refresh when the custom dataset is not available anymore', async () => {
    const resizeObservers = installResizeObserver()
    window.sessionStorage.setItem('inboxbridge.chartViewState.test:custom-refresh', JSON.stringify({
      selectedRange: 'custom',
      selectedResolution: 'day'
    }))

    render(
      <ImportTimelineChart
        chartStateKey="test:custom-refresh"
        customRangeLoader={vi.fn(async () => ({ imports: [] }))}
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: [
                { bucketLabel: '08:00', importedMessages: 2 },
                { bucketLabel: '09:00', importedMessages: 4 }
              ],
              pastWeek: [
                { bucketLabel: '2026-04-01T08:00', importedMessages: 2 },
                { bucketLabel: '2026-04-02T09:00', importedMessages: 4 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.queryByText('No imported-message history is available yet.')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('falls back from a persisted stale preset range after a hard refresh when that range is no longer available', async () => {
    const resizeObservers = installResizeObserver()
    window.sessionStorage.setItem('inboxbridge.chartViewState.test:stale-preset-refresh', JSON.stringify({
      selectedRange: 'pastMonth',
      selectedResolution: 'day'
    }))

    render(
      <ImportTimelineChart
        chartStateKey="test:stale-preset-refresh"
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: [
                { bucketLabel: '08:00', importedMessages: 2 },
                { bucketLabel: '09:00', importedMessages: 4 }
              ],
              pastWeek: [
                { bucketLabel: '2026-04-01T08:00', importedMessages: 2 },
                { bucketLabel: '2026-04-02T09:00', importedMessages: 4 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.queryByText('No imported-message history is available yet.')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('falls back to the first preset with actual data after refresh instead of landing on an empty preset', async () => {
    const resizeObservers = installResizeObserver()
    window.sessionStorage.setItem('inboxbridge.chartViewState.test:custom-refresh-empty-today', JSON.stringify({
      selectedRange: 'custom',
      selectedResolution: 'day'
    }))

    render(
      <ImportTimelineChart
        chartStateKey="test:custom-refresh-empty-today"
        customRangeLoader={vi.fn(async () => ({ imports: [] }))}
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: [],
              pastWeek: [
                { bucketLabel: '2026-04-01T08:00', importedMessages: 2 },
                { bucketLabel: '2026-04-02T09:00', importedMessages: 4 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.queryByText('No imported-message history is available yet.')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastWeek')
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('resets the resolution back to the native level when the range changes', async () => {
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              today: Array.from({ length: 6 }, (_, index) => ({
                bucketLabel: `${String(index).padStart(2, '0')}:00`,
                importedMessages: index + 1
              })),
              pastWeek: Array.from({ length: 10 }, (_, index) => ({
                bucketLabel: `2026-04-${String(index + 1).padStart(2, '0')}T00:00`,
                importedMessages: index + 1
              }))
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('today')
    expect(screen.getByText('Resolution: Hour')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), { target: { value: 'pastWeek' } })

    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastWeek')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('day')
  })

  it('adapts custom-range resolutions to the selected span and keeps the dates editable', async () => {
    const resizeObservers = installResizeObserver()

    const customRangeLoader = vi.fn(async () => ({
      imports: Array.from({ length: 14 }, (_, index) => ({
        bucketLabel: `2026-03-${String(index + 1).padStart(2, '0')}`,
        importedMessages: index + 1
      }))
    }))

    render(
      <ImportTimelineChart
        customRangeLoader={customRangeLoader}
        locale="en-GB"
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              pastMonth: [
                { bucketLabel: '2026-03-01', importedMessages: 1 },
                { bucketLabel: '2026-03-02', importedMessages: 2 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), { target: { value: 'custom' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Custom range type' }), { target: { value: 'absolute' } })
    fireEvent.change(screen.getByLabelText('Date-time from'), { target: { value: '2026-03-01T00:00' } })
    fireEvent.change(screen.getByLabelText('Date-time to'), { target: { value: '2026-03-20T00:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    await act(async () => {})

    expect(screen.getByText('Custom range: 01/03/2026, 00:00:00 to 20/03/2026, 00:00:00')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit dates' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('day')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Day')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Week')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).not.toHaveTextContent('Month')

    fireEvent.click(screen.getByRole('button', { name: 'Edit dates' }))
    expect(screen.getByRole('combobox', { name: 'Custom range type' })).toHaveValue('absolute')
    expect(screen.getByLabelText('Date-time from')).toHaveValue('2026-03-01T00:00')
    expect(screen.getByLabelText('Date-time to')).toHaveValue('2026-03-20T00:00')
  })

  it('supports relative custom ranges and keeps hour resolution for windows shorter than one day', async () => {
    const resizeObservers = installResizeObserver()

    const customRangeLoader = vi.fn(async () => ({
      imports: Array.from({ length: 24 }, (_, index) => ({
        bucketLabel: `2026-03-01T${String(index % 24).padStart(2, '0')}:00`,
        importedMessages: index + 1
      }))
    }))

    render(
      <ImportTimelineChart
        customRangeLoader={customRangeLoader}
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              pastMonth: [
                { bucketLabel: '2026-03-01', importedMessages: 1 },
                { bucketLabel: '2026-03-02', importedMessages: 2 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), { target: { value: 'custom' } })
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Past amount' }), { target: { value: '12' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Past unit' }), { target: { value: 'hour' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    await act(async () => {})

    expect(screen.queryByRole('combobox', { name: 'Resolution' })).not.toBeInTheDocument()
    expect(screen.getByText('Resolution: Hour')).toBeInTheDocument()
    expect(screen.getByText(/Custom range: past 12 Hours \(/)).toBeInTheDocument()
  })

  it('renders custom range summaries with the active manual date format', async () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    setCurrentFormattingTimeZone('UTC')
    const resizeObservers = installResizeObserver()

    render(
      <ImportTimelineChart
        customRangeLoader={vi.fn(async () => ({
          imports: [
            { bucketLabel: '2026-03-01', importedMessages: 1 },
            { bucketLabel: '2026-03-20', importedMessages: 2 }
          ]
        }))}
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              pastMonth: [
                { bucketLabel: '2026-03-01', importedMessages: 1 },
                { bucketLabel: '2026-03-02', importedMessages: 2 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), { target: { value: 'custom' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Custom range type' }), { target: { value: 'absolute' } })
    fireEvent.change(screen.getByLabelText('Date-time from'), { target: { value: '2026-03-01T00:00' } })
    fireEvent.change(screen.getByLabelText('Date-time to'), { target: { value: '2026-03-20T00:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    expect(await screen.findByText('Custom range: 2026-03-01 00:00:00 to 2026-03-20 00:00:00')).toBeInTheDocument()
  })

  it('offers broader custom-range resolutions as the relative window grows', async () => {
    const resizeObservers = installResizeObserver()

    const customRangeLoader = vi.fn(async ({ from, to }) => {
      const durationDays = (Date.parse(to) - Date.parse(from)) / (1000 * 60 * 60 * 24)
      if (durationDays >= 700) {
        return {
          imports: Array.from({ length: 36 }, (_, index) => ({
            bucketLabel: `2023-${String((index % 12) + 1).padStart(2, '0')}`,
            importedMessages: index + 1
          }))
        }
      }
      if (durationDays >= 20) {
        return {
          imports: Array.from({ length: 21 }, (_, index) => ({
            bucketLabel: `2026-03-${String(index + 1).padStart(2, '0')}`,
            importedMessages: index + 1
          }))
        }
      }
      return { imports: [] }
    })

    render(
      <ImportTimelineChart
        customRangeLoader={customRangeLoader}
        series={[
          {
            key: 'imports',
            label: 'Imported',
            timelines: {
              pastMonth: [
                { bucketLabel: '2026-03-01', importedMessages: 1 }
              ]
            }
          }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    await mountFrame(resizeObservers)

    fireEvent.change(screen.getByRole('combobox', { name: 'Range' }), { target: { value: 'custom' } })
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Past amount' }), { target: { value: '3' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Past unit' }), { target: { value: 'week' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    await act(async () => {})

    expect(screen.queryByText('No imported-message history is available yet.')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Day')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Week')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).not.toHaveTextContent('Month')

    fireEvent.click(screen.getByRole('button', { name: 'Edit dates' }))
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Past amount' }), { target: { value: '3' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Past unit' }), { target: { value: 'year' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    await act(async () => {})

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Month')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Trimester')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Semester')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Year')
  })
})
