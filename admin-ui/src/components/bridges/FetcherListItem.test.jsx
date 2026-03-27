import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import FetcherListItem from './FetcherListItem'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('FetcherListItem', () => {
  it('closes the contextual menu when clicking outside', () => {
    render(
      <div>
        <FetcherListItem
          fetcher={{
            bridgeId: 'fetcher-1',
            customLabel: '',
            managementSource: 'DATABASE',
            protocol: 'IMAP',
            host: 'imap.example.com',
            port: 993,
            authMethod: 'PASSWORD',
            oauthProvider: 'NONE',
            tls: true,
            folder: 'INBOX',
            tokenStorageMode: 'PASSWORD',
            oauthConnected: false,
            totalImportedMessages: 0,
            lastImportedAt: null,
            effectivePollEnabled: true,
            effectivePollInterval: '5m',
            effectiveFetchWindow: 50,
            pollingState: null,
            lastEvent: null,
            canEdit: true,
            canDelete: true,
            canConnectMicrosoft: false
          }}
          locale="en"
          onConnectMicrosoft={vi.fn()}
          onConfigurePolling={vi.fn()}
          onDelete={vi.fn()}
          onEdit={vi.fn()}
          onRunPoll={vi.fn()}
          t={t}
        />
        <button type="button">Outside</button>
      </div>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Expand section' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Collapse section' })).not.toBeInTheDocument()

    fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }))

    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })

  it('requests a refresh when the fetcher row is expanded', () => {
    const onToggleExpand = vi.fn()

    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'fetcher-1',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'PASSWORD',
          oauthConnected: false,
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: false
        }}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        onToggleExpand={onToggleExpand}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /fetcher-1/i }))

    expect(onToggleExpand).toHaveBeenCalledWith(expect.objectContaining({ bridgeId: 'fetcher-1' }), true)
  })

  it('renders translated fetcher labels in portuguese', async () => {
    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'fetcher-1',
          customLabel: '',
          managementSource: 'ENVIRONMENT',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'DATABASE',
          oauthConnected: false,
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: false
        }}
        locale="pt-PT"
          onConnectMicrosoft={vi.fn()}
          onConfigurePolling={vi.fn()}
          onDelete={vi.fn()}
          onEdit={vi.fn()}
          onRunPoll={vi.fn()}
          stats={{
            totalImportedMessages: 2,
            configuredMailFetchers: 1,
            enabledMailFetchers: 1,
            sourcesWithErrors: 0,
            importTimelines: {},
            duplicateTimelines: {},
            errorTimelines: {},
            health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
            providerBreakdown: [],
            manualRuns: 1,
            scheduledRuns: 0,
            averagePollDurationMillis: 1000
          }}
          t={(key, params) => translate('pt-PT', key, params)}
        />
      )

    fireEvent.click(screen.getByRole('button', { name: 'Ações da conta de email de origem' }))
    expect(screen.getByRole('button', { name: 'Executar polling agora' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Definições do poller' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apagar' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /fetcher-1/i }))

    expect(screen.getByText('.env')).toBeInTheDocument()
    expect(screen.getByText('Armazenamento do token')).toBeInTheDocument()
    expect(screen.getByText('BD encriptada')).toBeInTheDocument()
    expect(screen.getByText('Ainda não existe atividade de polling registada.')).toBeInTheDocument()
    expect(await screen.findByText('Estatísticas da conta de email: fetcher-1')).toBeInTheDocument()
  })

  it('shows running state and OAuth connection details for OAuth fetchers', () => {
    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'outlook-main',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'outlook.office365.com',
          port: 993,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'DATABASE',
          oauthConnected: true,
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: true
        }}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        pollLoading
        t={t}
      />
    )

    expect(screen.getByText('Running…')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /outlook-main/i }))

    expect(screen.getByText('Provider')).toBeInTheDocument()
    expect(screen.getByText('Microsoft')).toBeInTheDocument()
    expect(screen.getByText('OAuth connected')).toBeInTheDocument()
    expect(screen.getAllByText('Yes').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    expect(screen.getByRole('button', { name: 'Reconnect Microsoft OAuth' })).toBeInTheDocument()
  })

  it('does not show provider breakdown inside single-account statistics', async () => {
    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'gmail-source',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.gmail.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'PASSWORD',
          oauthConnected: false,
          totalImportedMessages: 12,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: false
        }}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onLoadCustomRange={vi.fn()}
        onRunPoll={vi.fn()}
        stats={{
          totalImportedMessages: 12,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          errorPolls: 1,
          importTimelines: {},
          duplicateTimelines: {},
          errorTimelines: {},
          manualRunTimelines: {},
          scheduledRunTimelines: {},
          providerBreakdown: [{ key: 'gmail', label: 'Gmail', count: 12 }],
          manualRuns: 2,
          scheduledRuns: 4,
          averagePollDurationMillis: 1000
        }}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /gmail-source/i }))

    expect(await screen.findByText('Provider')).toBeInTheDocument()
    expect(screen.getByText('Gmail')).toBeInTheDocument()
    expect(screen.queryByText('Provider breakdown')).not.toBeInTheDocument()
  })

  it('keeps the contextual menu attached to the trigger and flips above when needed', async () => {
    const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect
    HTMLElement.prototype.getBoundingClientRect = function patchedBoundingClientRect() {
      if (this.getAttribute?.('aria-label') === 'Source email account actions') {
        return { top: 660, bottom: 700, right: 1180, left: 1140, width: 40, height: 40 }
      }
      if (this.classList?.contains('fetcher-menu')) {
        return { top: 0, bottom: 180, left: 0, right: 220, width: 220, height: 180 }
      }
      return originalGetBoundingClientRect.call(this)
    }

    const originalInnerWidth = window.innerWidth
    const originalInnerHeight = window.innerHeight
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1280 })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 720 })

    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'fetcher-1',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'PASSWORD',
          oauthConnected: false,
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: false
        }}
        locale="en"
        onConnectMicrosoft={vi.fn()}
        onConfigurePolling={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))

    await waitFor(() => {
      const menu = screen.getByRole('button', { name: 'Edit' }).closest('.fetcher-menu')
      expect(menu).toHaveAttribute('data-placement', 'top')
      expect(menu.style.top).toBe('472px')
      expect(menu.style.left).toBe('960px')
    })

    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalInnerWidth })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalInnerHeight })
    HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect
  })

  it('starts the nested statistics section collapsed when the mail account has no meaningful stats', () => {
    render(
      <FetcherListItem
        fetcher={{
          bridgeId: 'fetcher-1',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'PASSWORD',
          oauthConnected: false,
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: null,
          canEdit: true,
          canDelete: true,
          canConnectMicrosoft: false
        }}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        stats={{
          totalImportedMessages: 0,
          configuredMailFetchers: 0,
          enabledMailFetchers: 0,
          sourcesWithErrors: 0,
          errorPolls: 0,
          importTimelines: {},
          duplicateTimelines: {},
          errorTimelines: {},
          manualRunTimelines: {},
          scheduledRunTimelines: {},
          health: { activeMailFetchers: 0, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
          providerBreakdown: [],
          manualRuns: 0,
          scheduledRuns: 0,
          averagePollDurationMillis: 0
        }}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /fetcher-1/i }))

    expect(screen.getByText('Mail Account Statistics: fetcher-1')).toBeInTheDocument()
    expect(screen.queryByText('Import activity over time')).not.toBeInTheDocument()
  })
})
