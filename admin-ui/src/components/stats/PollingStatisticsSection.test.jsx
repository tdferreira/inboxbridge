import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import PollingStatisticsSection from './PollingStatisticsSection'
import { translate } from '../../lib/i18n'
import { colorForSeries } from '../common/ImportTimelineChart'
import * as pollingStatsAlerts from '../../lib/pollingStatsAlerts'

vi.mock('recharts', async () => {
  const actual = await vi.importActual('recharts')
  return {
    ...actual,
    ResponsiveContainer: ({ children }) => (
      <div style={{ height: '288px', width: '100%' }}>
        {children}
      </div>
    )
  }
})

function renderSection(props = {}) {
  return render(
    <PollingStatisticsSection
      copy="Track the service."
      id="stats-section"
      stats={{
        totalImportedMessages: 14,
        configuredMailFetchers: 3,
        enabledMailFetchers: 2,
        sourcesWithErrors: 1,
        errorPolls: 2,
        importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 5 }],
        importTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 1 },
            { bucketLabel: '10:00', importedMessages: 4 }
          ],
          pastWeek: [
            { bucketLabel: '2026-03-26', importedMessages: 5 },
            { bucketLabel: '2026-03-27', importedMessages: 9 }
          ]
        },
        duplicateTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 0 },
            { bucketLabel: '10:00', importedMessages: 2 }
          ],
          pastWeek: [
            { bucketLabel: '2026-03-26', importedMessages: 2 },
            { bucketLabel: '2026-03-27', importedMessages: 3 }
          ]
        },
        errorTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 0 },
            { bucketLabel: '10:00', importedMessages: 1 }
          ],
          pastWeek: [
            { bucketLabel: '2026-03-26', importedMessages: 1 },
            { bucketLabel: '2026-03-27', importedMessages: 0 }
          ]
        },
        manualRunTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 1 },
            { bucketLabel: '10:00', importedMessages: 1 }
          ]
        },
        scheduledRunTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 0 },
            { bucketLabel: '10:00', importedMessages: 2 }
          ]
        },
        health: {
          activeMailFetchers: 1,
          coolingDownMailFetchers: 1,
          failingMailFetchers: 1,
          disabledMailFetchers: 0
        },
        providerBreakdown: [
          { key: 'microsoft', label: 'Microsoft', count: 2 },
          { key: 'generic-imap', label: 'Generic IMAP', count: 1 }
        ],
        manualRuns: 3,
        scheduledRuns: 8,
        averagePollDurationMillis: 2100
      }}
      t={(key, params) => translate('en', key, params)}
      title="My Statistics"
      {...props}
    />
  )
}

describe('PollingStatisticsSection', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders the richer statistics cards and breakdowns', () => {
    renderSection()

    expect(screen.getByText('Import activity over time')).toBeInTheDocument()
    expect(screen.getByText('Healthy accounts')).toBeInTheDocument()
    expect(screen.getByText('Provider breakdown')).toBeInTheDocument()
    expect(screen.getByText('Outlook')).toBeInTheDocument()
    expect(screen.getByText('Generic IMAP')).toBeInTheDocument()
    expect(screen.getByText('Scheduled runs')).toBeInTheDocument()
    expect(screen.getByText('Poll activity over time')).toBeInTheDocument()
    expect(colorForSeries('imports', 0)).toBe('#16a34a')
  })

  it('renders translated statistics labels in portuguese', () => {
    renderSection({
      copy: 'Acompanhe o serviço.',
      t: (key, params) => translate('pt-PT', key, params),
      title: 'As minhas estatísticas'
    })

    expect(screen.getByText('Atividade de importação ao longo do tempo')).toBeInTheDocument()
    expect(screen.getByText('Distribuição por fornecedor')).toBeInTheDocument()
    expect(screen.getByText('Contas saudáveis')).toBeInTheDocument()
    expect(screen.getByText('Outlook')).toBeInTheDocument()
    expect(screen.getByText('IMAP genérico')).toBeInTheDocument()
  })

  it('uses source-specific metrics for mail account statistics', () => {
    renderSection({ title: 'Mail Account Statistics', variant: 'source' })

    expect(screen.getAllByText('Error polls').length).toBeGreaterThan(0)
    expect(screen.queryByText('Healthy accounts')).not.toBeInTheDocument()
    expect(screen.queryByText('Configured email accounts')).not.toBeInTheDocument()
  })

  it('shows source activity instead of poll activity for IMAP IDLE sources', () => {
    renderSection({
      title: 'Mail Account Statistics',
      variant: 'source',
      sourceFetchMode: 'IDLE',
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 1,
        enabledMailFetchers: 1,
        sourcesWithErrors: 0,
        errorPolls: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {},
        idleRunTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 2 }
          ]
        },
        providerBreakdown: [],
        manualRuns: 1,
        scheduledRuns: 0,
        idleRuns: 2,
        averagePollDurationMillis: 800
      }
    })

    expect(screen.getByText('Source activity over time')).toBeInTheDocument()
    expect(screen.getAllByText('Realtime source activations').length).toBeGreaterThan(0)
    expect(screen.getByText('Realtime source activations count the initial IMAP IDLE sync and later activations caused by newly arrived mail. They do not measure connection time.')).toBeInTheDocument()
  })

  it('shows realtime source activations in non-source statistics when present', () => {
    renderSection({
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 3,
        enabledMailFetchers: 2,
        sourcesWithErrors: 1,
        errorPolls: 2,
        importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 5 }],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {},
        idleRunTimelines: {
          today: [
            { bucketLabel: '09:00', importedMessages: 4 }
          ]
        },
        health: {
          activeMailFetchers: 1,
          coolingDownMailFetchers: 1,
          failingMailFetchers: 1,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 3,
        scheduledRuns: 8,
        idleRuns: 4,
        averagePollDurationMillis: 2100
      }
    })

    expect(screen.getAllByText('Realtime source activations').length).toBeGreaterThan(0)
    expect(screen.getByText('Poll activity over time')).toBeInTheDocument()
  })

  it('opens the custom range dialog with empty fields and closes without discard confirmation when untouched', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderSection({
      customRangeLoader: vi.fn(async () => ({
        imports: [],
        duplicates: [],
        errors: [],
        manualRuns: [],
        scheduledRuns: []
      }))
    })

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'custom' } })

    expect(screen.getByRole('dialog', { name: 'Custom date-time range' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Custom range type' })).toHaveValue('relative')
    expect(screen.getByRole('spinbutton', { name: 'Past amount' })).toHaveValue(6)
    expect(screen.getByRole('combobox', { name: 'Past unit' })).toHaveValue('hour')

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog', { name: 'Custom date-time range' })).not.toBeInTheDocument()

    confirmSpy.mockRestore()
  })

  it('shows a persistent warning when scheduled run activity is far above the configured interval capacity', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-03-26T03:30:00Z'))

    renderSection({
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 1,
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 1,
        enabledMailFetchers: 1,
        sourcesWithErrors: 1,
        errorPolls: 2,
        importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 5 }],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {
          today: [
            { bucketLabel: '03:00', importedMessages: 720 }
          ]
        },
        health: {
          activeMailFetchers: 1,
          coolingDownMailFetchers: 0,
          failingMailFetchers: 1,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 720,
        averagePollDurationMillis: 2100
      }
    })

    expect(screen.getByText('Scheduled polling activity looks unusually high.')).toBeInTheDocument()
    expect(screen.getByText(/During Today at 03:00, InboxBridge recorded a peak of 720 scheduled runs/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show on chart' })).toBeInTheDocument()
    expect(document.querySelector('.polling-statistics-section-alerting')).toBeInTheDocument()
  })

  it('keeps yesterday anomalies out of the today chart and labels the warning with a full anomaly window', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-05T10:00:00Z'))

    renderSection({
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 2,
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 2,
        enabledMailFetchers: 2,
        sourcesWithErrors: 0,
        errorPolls: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {
          today: [
            { bucketLabel: '07:00', importedMessages: 2 }
          ],
          yesterday: [
            { bucketLabel: '07:00', importedMessages: 1147 },
            { bucketLabel: '08:00', importedMessages: 960 },
            { bucketLabel: '09:00', importedMessages: 800 }
          ]
        },
        health: {
          activeMailFetchers: 2,
          coolingDownMailFetchers: 0,
          failingMailFetchers: 0,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 1149,
        averagePollDurationMillis: 2100
      }
    })

    expect(screen.getByText(/During Yesterday from 07:00 until 09:00, InboxBridge recorded a peak of 1147 scheduled runs/)).toBeInTheDocument()
    expect(document.querySelector('.polling-statistics-section-alerting')).toBeInTheDocument()
  })

  it('moves the run chart to the anomaly range when the warning action is clicked', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-05T10:00:00Z'))

    renderSection({
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 2,
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 2,
        enabledMailFetchers: 2,
        sourcesWithErrors: 0,
        errorPolls: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {
          today: [
            { bucketLabel: '07:00', importedMessages: 2 }
          ],
          yesterday: [
            { bucketLabel: '07:00', importedMessages: 1147 }
          ]
        },
        health: {
          activeMailFetchers: 2,
          coolingDownMailFetchers: 0,
          failingMailFetchers: 0,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 1149,
        averagePollDurationMillis: 2100
      }
    })

    const runChartRangeSelect = screen.getByRole('combobox', { name: 'Range' })
    expect(runChartRangeSelect).toHaveValue('today')

    fireEvent.click(screen.getByRole('button', { name: 'Show on chart' }))

    expect(runChartRangeSelect).toHaveValue('yesterday')
    expect(document.activeElement).toHaveClass('timeline-chart-card')
  })

  it('hides stale scheduled-run anomaly warnings after one week', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-12T12:00:00Z'))

    renderSection({
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 1,
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 1,
        enabledMailFetchers: 1,
        sourcesWithErrors: 0,
        errorPolls: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {
          custom: [
            { bucketLabel: '2026-04-04T09:00:00Z', importedMessages: 720 }
          ]
        },
        health: {
          activeMailFetchers: 1,
          coolingDownMailFetchers: 0,
          failingMailFetchers: 0,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 720,
        averagePollDurationMillis: 2100
      }
    })

    expect(screen.queryByText('Scheduled polling activity looks unusually high.')).not.toBeInTheDocument()
    expect(document.querySelector('.polling-statistics-section-alerting')).not.toBeInTheDocument()
  })

  it('falls back to bucket labels instead of rendering unavailable timestamps when anomaly times are invalid', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-05T10:00:00Z'))
    vi.spyOn(pollingStatsAlerts, 'detectScheduledRunAnomaly').mockReturnValue({
      ageMs: 1_000,
      bucketLabel: '2026-04-04 07:00',
      endBucketLabel: '2026-04-04 08:00',
      endOccurredAt: Number.MAX_SAFE_INTEGER,
      expectedRunsPerHour: 24,
      notificationVisible: true,
      observedRuns: 1440,
      occurredAt: Number.MAX_SAFE_INTEGER,
      rangeKey: 'custom',
      sourceCount: 2,
      startBucketLabel: '2026-04-04 07:00',
      startOccurredAt: Number.MAX_SAFE_INTEGER,
      warningVisible: true
    })

    renderSection({
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 2,
      stats: {
        totalImportedMessages: 14,
        configuredMailFetchers: 2,
        enabledMailFetchers: 2,
        sourcesWithErrors: 0,
        errorPolls: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        manualRunTimelines: {},
        scheduledRunTimelines: {
          custom: [
            { bucketLabel: 'not-a-date 07:00', importedMessages: 1440 },
            { bucketLabel: 'not-a-date 08:00', importedMessages: 1200 }
          ]
        },
        health: {
          activeMailFetchers: 2,
          coolingDownMailFetchers: 0,
          failingMailFetchers: 0,
          disabledMailFetchers: 0
        },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 2640,
        averagePollDurationMillis: 2100
      }
    })

    expect(screen.getByText(/During 2026-04-04 07:00, InboxBridge recorded a peak of 1440 scheduled runs/)).toBeInTheDocument()
    expect(screen.queryByText(/Unavailable/)).not.toBeInTheDocument()
  })

  it('formats parseable anomaly bucket labels with the user locale instead of raw ISO timestamps', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-05T10:00:00Z'))
    vi.spyOn(pollingStatsAlerts, 'detectScheduledRunAnomaly').mockReturnValue({
      ageMs: 1_000,
      bucketLabel: '2026-04-03T21:00:00Z',
      endBucketLabel: '2026-04-04T07:00:00Z',
      endOccurredAt: null,
      expectedRunsPerHour: 24,
      notificationVisible: true,
      observedRuns: 1440,
      occurredAt: null,
      rangeKey: 'custom',
      sourceCount: 2,
      startBucketLabel: '2026-04-03T21:00:00Z',
      startOccurredAt: null,
      warningVisible: true
    })

    renderSection({
      locale: 'pt-PT',
      scheduledRunAlertInterval: '5m',
      scheduledRunAlertSourceCount: 2,
      t: (key, params) => translate('pt-PT', key, params)
    })

    const alertText = screen.getByRole('alert').textContent
    expect(alertText).not.toContain('2026-04-03T21:00')
    expect(alertText).not.toContain('2026-04-04T07:00')
    expect(alertText).not.toContain('Unavailable')
    expect(alertText).not.toContain('Durante de ')
    expect(alertText).toContain('03/')
    expect(alertText).toContain('04/')
  })
})
