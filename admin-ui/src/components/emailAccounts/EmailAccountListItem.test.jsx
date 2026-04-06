import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import EmailAccountListItem from './EmailAccountListItem'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('EmailAccountListItem', () => {
  it('closes the contextual menu when clicking outside', () => {
    render(
      <div>
        <EmailAccountListItem
          fetcher={{
            emailAccountId: 'fetcher-1',
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
            markReadAfterPoll: false,
            postPollAction: 'NONE',
            postPollTargetFolder: '',
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
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'fetcher-1',
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
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
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

    expect(onToggleExpand).toHaveBeenCalledWith(expect.objectContaining({ emailAccountId: 'fetcher-1' }), true)
  })

  it('renders translated fetcher labels in portuguese', async () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'fetcher-1',
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
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
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
    expect(screen.getAllByRole('button', { name: 'Executar polling agora' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Definições de verificação' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apagar' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /fetcher-1/i }))

    expect(screen.getAllByText('.env').length).toBeGreaterThan(0)
    expect(screen.getByText('Armazenamento do token')).toBeInTheDocument()
    expect(screen.getByText('Armazenamento encriptado')).toBeInTheDocument()
    expect(screen.getByText('Ainda não existe atividade de polling registada.')).toBeInTheDocument()
  })

  it('shows running state and OAuth connection details for OAuth fetchers', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'outlook-main',
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
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
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

  it('renders diagnostics, checkpoints, idle watchers, and poll audit details', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'idle-source',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX, Projects/2026',
          fetchMode: 'IDLE',
          tokenStorageMode: 'PASSWORD',
          oauthConnected: false,
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          diagnostics: {
            destinationIdentityKey: 'imap-append:abc123',
            popLastSeenUidl: null,
            imapCheckpoints: [
              { folderName: 'INBOX', uidValidity: 44, lastSeenUid: 101 },
              { folderName: 'Projects/2026', uidValidity: 77, lastSeenUid: 205 }
            ],
            sourceThrottle: { adaptiveMultiplier: 2, nextAllowedAt: '2026-04-06T10:06:00Z' },
            destinationThrottle: { adaptiveMultiplier: 3, nextAllowedAt: '2026-04-06T10:07:00Z' },
            idleHealthy: false,
            idleSchedulerFallback: true,
            idleWatches: [
              { folderName: 'INBOX', status: 'CONNECTED' },
              { folderName: 'Projects/2026', status: 'DISCONNECTED' }
            ]
          },
          lastEvent: {
            status: 'ERROR',
            finishedAt: '2026-04-06T10:00:00Z',
            trigger: 'scheduler',
            fetched: 0,
            imported: 0,
            duplicates: 0,
            failureCategory: 'RATE_LIMIT',
            cooldownBackoffMillis: 900000,
            sourceThrottleWaitMillis: 2500,
            destinationThrottleWaitMillis: 1000,
            error: '429 too many requests'
          },
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
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /idle-source/i }))

    expect(screen.getByText('Diagnostics')).toBeInTheDocument()
    expect(screen.getByText('imap-append:abc123')).toBeInTheDocument()
    expect(screen.getByText('INBOX · UIDVALIDITY 44 · UID 101')).toBeInTheDocument()
    expect(screen.getByText('Projects/2026 · UIDVALIDITY 77 · UID 205')).toBeInTheDocument()
    expect(screen.getByText('INBOX · Connected')).toBeInTheDocument()
    expect(screen.getByText('Projects/2026 · Disconnected')).toBeInTheDocument()
    expect(screen.getByText(/Failure category/)).toBeInTheDocument()
    expect(screen.getByText(/Rate Limit/)).toBeInTheDocument()
    expect(screen.getByText(/Cooldown backoff/)).toBeInTheDocument()
  })

  it('explains manual runs for IMAP IDLE sources', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'idle-source',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          fetchMode: 'IDLE',
          tokenStorageMode: 'DATABASE',
          oauthConnected: false,
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
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
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /idle-source/i }))

    expect(screen.getByText('IMAP IDLE (real-time)')).toBeInTheDocument()
    expect(screen.getByText('Run Poll Now still starts an immediate one-off sync for this source. It does not disable IMAP IDLE, and scheduled polling still skips this mailbox while the watcher stays active.')).toBeInTheDocument()
  })

  it('shows determinate live progress for a running source', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'inbox-main',
          customLabel: 'Primary Inbox',
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
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
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
        liveSource={{
          sourceId: 'inbox-main',
          label: 'Primary Inbox',
          state: 'RUNNING',
          actionable: true,
          position: 1,
          totalMessages: 50,
          processedMessages: 3,
          totalBytes: 6 * 1024 * 1024,
          processedBytes: 1536 * 1024,
          fetched: 3,
          imported: 2,
          duplicates: 1
        }}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        t={t}
      />
    )

    expect(screen.getByText('Processing 3 / 50 emails (1.5 MB / 6 MB)')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Processing 3 / 50 emails' })).toHaveClass('status-pill-progress')
  })

  it('shows disabled status for disabled fetchers even when the last event failed', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'disabled-fetcher',
          enabled: false,
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
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: '',
          totalImportedMessages: 0,
          lastImportedAt: null,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          pollingState: null,
          lastEvent: {
            status: 'ERROR',
            finishedAt: '2026-03-26T12:00:00Z',
            trigger: 'manual',
            fetched: 0,
            imported: 0,
            duplicates: 0,
            error: 'boom'
          },
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
        t={t}
      />
    )

    expect(screen.getByText('Disabled')).toBeInTheDocument()
    expect(screen.queryByText('Error')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run Poll Now' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    expect(screen.getAllByRole('button', { name: 'Run Poll Now' })[0]).toBeDisabled()
  })

  it('passes the clicked fetcher object when running a manual poll', () => {
    const onRunPoll = vi.fn()
    const fetcher = {
      emailAccountId: 'outlook-main',
      customLabel: '',
      managementSource: 'ENVIRONMENT',
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
      canEdit: false,
      canDelete: false,
      canConnectOAuth: true
    }

    render(
      <EmailAccountListItem
        fetcher={fetcher}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={onRunPoll}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Run Poll Now' })[1])

    expect(onRunPoll).toHaveBeenCalledWith(fetcher)
  })

  it('shows a disable action for enabled fetchers and an enable action for disabled fetchers', () => {
    const onToggleEnabled = vi.fn()
    const enabledFetcher = {
      emailAccountId: 'enabled-fetcher',
      enabled: true,
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
      canConnectOAuth: false
    }
    const disabledFetcher = {
      ...enabledFetcher,
      emailAccountId: 'disabled-fetcher',
      enabled: false
    }

    const { rerender } = render(
      <EmailAccountListItem
        fetcher={enabledFetcher}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        onToggleEnabled={onToggleEnabled}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    fireEvent.click(screen.getByRole('button', { name: 'Disable' }))
    expect(onToggleEnabled).toHaveBeenCalledWith(enabledFetcher)

    rerender(
      <EmailAccountListItem
        fetcher={disabledFetcher}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={vi.fn()}
        onToggleEnabled={onToggleEnabled}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Source email account actions' }))
    expect(screen.getByRole('button', { name: 'Enable' })).toBeInTheDocument()
  })

  it('shows the stored spam or junk message count in the expanded last run summary', () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'spam-aware-fetcher',
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
          lastEvent: {
            status: 'SUCCESS',
            finishedAt: '2026-03-26T12:00:00Z',
            trigger: 'admin-fetcher',
            fetched: 10,
            imported: 4,
            importedBytes: 3 * 1024 * 1024,
            duplicates: 6,
            spamJunkMessageCount: 6,
            actorUsername: 'admin',
            executionSurface: 'ADMINISTRATION',
            error: ''
          },
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
        t={t}
        viewerUsername="alice"
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /spam-aware-fetcher/i }))

    expect(screen.getByText(/Executed at .* by admin via Administration/)).toBeInTheDocument()
    expect(screen.getByText('Imported size: 3 MB')).toBeInTheDocument()
    expect(screen.getByText('Spam / Junk: 6')).toBeInTheDocument()
    expect(screen.queryByText('Spam/Junk folders currently contain 6 messages.')).not.toBeInTheDocument()
  })

  it('shows a quick-run icon button in the fetcher row', () => {
    const onRunPoll = vi.fn()
    const fetcher = {
      emailAccountId: 'quick-run-source',
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
      canConnectOAuth: false
    }

    render(
      <EmailAccountListItem
        fetcher={fetcher}
        locale="en"
        onConfigurePolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        onRunPoll={onRunPoll}
        t={t}
      />
    )

    fireEvent.click(screen.getAllByRole('button', { name: 'Run Poll Now' })[0])

    expect(onRunPoll).toHaveBeenCalledWith(fetcher)
  })

  it('does not show provider breakdown inside single-account statistics', async () => {
    render(
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'gmail-source',
          customLabel: '',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.gmail.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          fetchMode: 'IDLE',
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
          idleRunTimelines: {
            today: [{ bucketLabel: '09:00', importedMessages: 3 }]
          },
          providerBreakdown: [{ key: 'gmail', label: 'Gmail', count: 12 }],
          manualRuns: 2,
          scheduledRuns: 4,
          idleRuns: 3,
          averagePollDurationMillis: 1000
        }}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: /gmail-source/i }))

    expect(await screen.findByText('Provider')).toBeInTheDocument()
    expect(screen.getByText('Gmail')).toBeInTheDocument()
    expect(screen.queryByText('Provider breakdown')).not.toBeInTheDocument()
    expect(await screen.findByText('Source activity over time')).toBeInTheDocument()
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
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'fetcher-1',
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
      <EmailAccountListItem
        fetcher={{
          emailAccountId: 'fetcher-1',
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

    expect(screen.queryByText('Import activity over time')).not.toBeInTheDocument()
  })
})
