import { act, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import ImportTimelineChart from './ImportTimelineChart'
import { translate } from '../../lib/i18n'

vi.mock('recharts', () => ({
  CartesianGrid: () => null,
  Legend: () => null,
  Line: () => null,
  LineChart: ({ children }) => <div data-testid="line-chart">{children}</div>,
  ResponsiveContainer: ({ children }) => <div data-testid="responsive-container">{children}</div>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null
}))

describe('ImportTimelineChart', () => {
  it('waits for a measurable frame before mounting the responsive chart', async () => {
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

    render(
      <ImportTimelineChart
        points={[
          { bucketLabel: '2026-03-30', importedMessages: 4 }
        ]}
        t={(key, params) => translate('en', key, params)}
        title="Import activity over time"
      />
    )

    const frame = document.querySelector('.timeline-chart-frame')
    expect(frame).not.toBeNull()
    expect(screen.queryByTestId('responsive-container')).not.toBeInTheDocument()

    Object.defineProperty(frame, 'clientWidth', {
      configurable: true,
      value: 640
    })
    Object.defineProperty(frame, 'clientHeight', {
      configurable: true,
      value: 288
    })

    await act(async () => {
      resizeObservers.forEach((observer) => observer.callback())
    })

    expect(screen.getByTestId('responsive-container')).toBeInTheDocument()
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()

    vi.unstubAllGlobals()
  })
})
