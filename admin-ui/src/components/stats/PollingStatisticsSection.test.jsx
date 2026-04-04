import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import PollingStatisticsSection from './PollingStatisticsSection'
import { translate } from '../../lib/i18n'
import { colorForSeries } from '../common/ImportTimelineChart'

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
    expect(screen.getByLabelText('Date-time from')).toHaveValue('')
    expect(screen.getByLabelText('Date-time to')).toHaveValue('')

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
    expect(screen.getByText(/At 03:00, InboxBridge recorded 720 scheduled runs/)).toBeInTheDocument()
    expect(document.querySelector('.polling-statistics-section-alerting')).toBeInTheDocument()
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
})
