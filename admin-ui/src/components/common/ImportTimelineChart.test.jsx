import { act, fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import ImportTimelineChart from './ImportTimelineChart'
import { translate } from '../../lib/i18n'

vi.mock('recharts', () => ({
  CartesianGrid: () => null,
  Legend: () => null,
  Line: (props) => <div data-active-dot={String(props.activeDot)} data-testid={`line-${props.dataKey}`} />,
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
    expect(screen.getByLabelText('Date-time from')).toHaveValue('2026-03-01T00:00')
    expect(screen.getByLabelText('Date-time to')).toHaveValue('2026-03-20T00:00')
  })

  it('allows hourly resolution for custom ranges up to one week', async () => {
    const resizeObservers = installResizeObserver()

    const customRangeLoader = vi.fn(async () => ({
      imports: Array.from({ length: 24 }, (_, index) => ({
        bucketLabel: `2026-03-0${String((index % 7) + 1)}T${String(index % 24).padStart(2, '0')}:00`,
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
    fireEvent.change(screen.getByLabelText('Date-time from'), { target: { value: '2026-03-01T00:00' } })
    fireEvent.change(screen.getByLabelText('Date-time to'), { target: { value: '2026-03-06T00:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply custom range' }))

    await act(async () => {})

    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveValue('hour')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Hour')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Day')
    expect(screen.getByRole('combobox', { name: 'Resolution' })).toHaveTextContent('Week')
  })
})
