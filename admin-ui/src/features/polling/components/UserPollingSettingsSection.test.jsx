import { fireEvent, render, screen } from '@testing-library/react'
import UserPollingSettingsSection from './UserPollingSettingsSection'
import { translate } from '@/lib/i18n'

function renderSection(props = {}) {
  return render(
    <UserPollingSettingsSection
      collapsed={false}
      collapseLoading={false}
      hasFetchers
      onCollapseToggle={vi.fn()}
      onOpenEditor={vi.fn()}
      onRunPoll={vi.fn()}
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
      sectionLoading={false}
      t={(key, params) => translate('en', key, params)}
      {...props}
    />
  )
}

describe('UserPollingSettingsSection', () => {
  it('renders a summary and opens the editor flow', () => {
    const onOpenEditor = vi.fn()
    const onRunPoll = vi.fn()
    renderSection({ onOpenEditor, onRunPoll })

    expect(screen.getByText(/Effective polling:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective interval:/)).toBeInTheDocument()
    expect(screen.getByText(/Effective fetch window:/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Polling Settings' }))
    fireEvent.click(screen.getByRole('button', { name: 'Run Poll Now' }))
    expect(onRunPoll).toHaveBeenCalled()
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
        onRunPoll={vi.fn()}
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
        sectionLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('As minhas definições de verificação')).toBeInTheDocument()
    expect(screen.getByText(/Polling efetivo:/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar definições de verificação' })).toBeInTheDocument()
  })

  it('translates the live control buttons in portuguese', () => {
    const onPausePoll = vi.fn()
    const onStopPoll = vi.fn()

    render(
      <UserPollingSettingsSection
        collapsed={false}
        collapseLoading={false}
        hasFetchers
        livePoll={{
          running: true,
          state: 'RUNNING',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [{ sourceId: 'source-1', label: 'Inbox', state: 'RUNNING', actionable: false }]
        }}
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        onPausePoll={onPausePoll}
        onRunPoll={vi.fn()}
        onStopPoll={onStopPoll}
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
        sectionLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByRole('button', { name: 'Pausar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Parar' })).toBeInTheDocument()
  })

  it('keeps the edit action first and the run action as the primary button', () => {
    const { container } = renderSection()
    const actionButtons = container.querySelectorAll('.panel-header-actions button')

    expect(actionButtons).toHaveLength(2)
    expect(actionButtons[0]).toHaveTextContent('Edit Polling Settings')
    expect(actionButtons[0]).toHaveClass('secondary')
    expect(actionButtons[1]).toHaveTextContent('Run Poll Now')
    expect(actionButtons[1]).toHaveClass('primary')
  })

  it('shows pause and stop actions in the section header while a controllable live poll is running', () => {
    const onPausePoll = vi.fn()
    const onStopPoll = vi.fn()

    renderSection({
      livePoll: {
        running: true,
        state: 'RUNNING',
        viewerCanControl: true,
        activeSourceId: 'source-1',
        sources: [{ sourceId: 'source-1', label: 'Inbox', state: 'RUNNING', actionable: false }]
      },
      onPausePoll,
      onStopPoll
    })

    expect(screen.queryByText('Live Poll Progress')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    fireEvent.click(screen.getByRole('button', { name: 'Stop' }))

    expect(onPausePoll).toHaveBeenCalledTimes(1)
    expect(onStopPoll).toHaveBeenCalledTimes(1)
  })

  it('disables the broad run action while a live poll is already running', () => {
    renderSection({
      livePoll: {
        running: true,
        state: 'RUNNING',
        viewerCanControl: true,
        activeSourceId: 'source-1',
        sources: [{ sourceId: 'source-1', label: 'Inbox', state: 'RUNNING', actionable: false }]
      }
    })

    expect(screen.getByRole('button', { name: 'Run Poll Now' })).toBeDisabled()
  })

  it('shows a refresh indicator while loading the latest polling values', () => {
    renderSection({ sectionLoading: true })
    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })

  it('disables the broad run action when there are no runnable fetchers', () => {
    renderSection({ hasFetchers: false })
    expect(screen.getByRole('button', { name: 'Run Poll Now' })).toBeDisabled()
  })
})
