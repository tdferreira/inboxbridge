import { fireEvent, render, screen } from '@testing-library/react'
import UserPollingSettingsSection from './UserPollingSettingsSection'
import { translate } from '../../lib/i18n'

function renderSection(props = {}) {
  return render(
    <UserPollingSettingsSection
      collapsed={false}
      collapseLoading={false}
      hasFetchers
      onCollapseToggle={vi.fn()}
      onOpenEditor={vi.fn()}
      pollingSettings={{
        defaultPollEnabled: true,
        pollEnabledOverride: null,
        effectivePollEnabled: true,
        defaultPollInterval: '5m',
        pollIntervalOverride: null,
        effectivePollInterval: '2m',
        defaultFetchWindow: 50,
        fetchWindowOverride: null,
        effectiveFetchWindow: 50
      }}
      pollingStats={{
        totalImportedMessages: 2,
        configuredMailFetchers: 1,
        enabledMailFetchers: 1,
        sourcesWithErrors: 0,
        importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 2 }],
        importTimelines: {
          day: [{ bucketLabel: '2026-03-26', importedMessages: 2 }],
          month: [{ bucketLabel: '2026-03', importedMessages: 2 }]
        }
      }}
      sectionLoading={false}
      t={(key, params) => translate('en', key, params)}
      {...props}
    />
  )
}

describe('UserPollingSettingsSection', () => {
  it('renders a summary and opens the editor flow', () => {
    const onOpenEditor = vi.fn()
    renderSection({ onOpenEditor })

    expect(screen.getByText(/Effective polling:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective interval:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective fetch window:/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Poller Settings' }))
    expect(onOpenEditor).toHaveBeenCalled()
  })

  it('renders translated summary labels in portuguese', () => {
    render(
      <UserPollingSettingsSection
        collapsed={false}
        collapseLoading={false}
        hasFetchers
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        pollingSettings={{
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '2m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        }}
        pollingStats={{
          totalImportedMessages: 2,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [{ bucketLabel: '2026-03-26', importedMessages: 2 }],
          importTimelines: {
            day: [{ bucketLabel: '2026-03-26', importedMessages: 2 }]
          }
        }}
        sectionLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('As minhas definições do poller')).toBeInTheDocument()
    expect(screen.getByText(/Polling efetivo:/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar definições do poller' })).toBeInTheDocument()
  })

  it('shows a refresh indicator while loading the latest polling values', () => {
    renderSection({ sectionLoading: true })
    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })
})
