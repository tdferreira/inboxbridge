import { fireEvent, render, screen } from '@testing-library/react'
import SystemDashboardSection from './SystemDashboardSection'
import { translate } from '../../lib/i18n'

function renderSection(props = {}) {
  return render(
    <SystemDashboardSection
      collapsed={false}
      collapseLoading={false}
      dashboard={{
        overall: {
          configuredSources: 1,
          enabledSources: 1,
          totalImportedMessages: 4,
          sourcesWithErrors: 0,
          pollInterval: '2m',
          fetchWindow: 25
        },
        stats: {
          totalImportedMessages: 4,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 4 }],
          importTimelines: {
            pastWeek: [{ bucketLabel: '2026-03-26', importedMessages: 4 }],
            pastMonth: [{ bucketLabel: '2026-03', importedMessages: 4 }]
          },
          duplicateTimelines: {},
          errorTimelines: {},
          health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
          providerBreakdown: [],
          manualRuns: 0,
          scheduledRuns: 1,
          averagePollDurationMillis: 1200
        },
        polling: {
          defaultPollEnabled: true,
          pollEnabledOverride: false,
          effectivePollEnabled: false,
          defaultPollInterval: '5m',
          pollIntervalOverride: '2m',
          effectivePollInterval: '2m',
          defaultFetchWindow: 50,
          fetchWindowOverride: 25,
          effectiveFetchWindow: 25
        },
        destination: { tokenStorageMode: 'DATABASE' },
        bridges: [],
        recentEvents: []
      }}
      onCollapseToggle={vi.fn()}
      onOpenEditor={vi.fn()}
      onRunPoll={vi.fn()}
      runningPoll={false}
      locale="en"
      t={(key, params) => translate('en', key, params)}
      {...props}
    />
  )
}

describe('SystemDashboardSection', () => {
  it('renders a summary and forwards edit and run actions', () => {
    const onOpenEditor = vi.fn()
    const onRunPoll = vi.fn()
    renderSection({ onOpenEditor, onRunPoll })

    expect(screen.getByText(/Effective polling:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective interval:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective fetch window:/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Polling Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Run Poll Now' }))

    expect(onOpenEditor).toHaveBeenCalled()
    expect(onRunPoll).toHaveBeenCalled()
  })

  it('renders translated system polling labels in portuguese', () => {
    render(
      <SystemDashboardSection
        collapsed={false}
        collapseLoading={false}
        dashboard={{
          overall: {
            configuredSources: 1,
            enabledSources: 1,
            totalImportedMessages: 4,
            sourcesWithErrors: 0,
            pollInterval: '2m',
            fetchWindow: 25
          },
          stats: {
            totalImportedMessages: 4,
            configuredMailFetchers: 1,
            enabledMailFetchers: 1,
            sourcesWithErrors: 0,
            importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 4 }],
            importTimelines: {
              pastWeek: [{ bucketLabel: '2026-03-26', importedMessages: 4 }]
            },
            duplicateTimelines: {},
            errorTimelines: {},
            health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
            providerBreakdown: [],
            manualRuns: 0,
            scheduledRuns: 1,
            averagePollDurationMillis: 1200
          },
          polling: {
            defaultPollEnabled: true,
            pollEnabledOverride: false,
            effectivePollEnabled: false,
            defaultPollInterval: '5m',
            pollIntervalOverride: '2m',
            effectivePollInterval: '2m',
            defaultFetchWindow: 50,
            fetchWindowOverride: 25,
            effectiveFetchWindow: 25
          },
          destination: { tokenStorageMode: 'DATABASE' },
          bridges: [],
          recentEvents: []
        }}
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        onRunPoll={vi.fn()}
        runningPoll={false}
        locale="pt-PT"
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Definições globais de verificação')).toBeInTheDocument()
    expect(screen.getByText(/Polling efetivo:/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar definições de verificação' })).toBeInTheDocument()
  })
})
