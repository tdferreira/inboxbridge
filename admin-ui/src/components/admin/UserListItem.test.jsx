import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import UserListItem from './UserListItem'
import { DATE_FORMAT_YMD_24, resetCurrentFormattingDateFormat, setCurrentFormattingDateFormat } from '../../lib/formatters'
import { translate } from '../../lib/i18n'
import { resolveNotificationContent } from '../../lib/notifications'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from '../../lib/timeZonePreferences'

function buildConfig() {
  return {
    user: {
      id: 7,
      username: 'admin',
      role: 'ADMIN',
      approved: true,
      active: true,
      gmailConfigured: true,
      passwordConfigured: true,
      mustChangePassword: false,
      passkeyCount: 1,
      emailAccountCount: 1
    },
    destinationConfig: {
      provider: 'OUTLOOK_IMAP',
      deliveryMode: 'IMAP_APPEND',
      linked: true,
      host: 'outlook.office365.com',
      port: 993,
      authMethod: 'OAUTH2',
      username: 'admin@example.com',
      folder: 'INBOX'
    },
    pollingSettings: {
      effectivePollEnabled: true,
      effectivePollInterval: '5m',
      effectiveFetchWindow: 50,
      pollEnabledOverride: null,
      pollIntervalOverride: null,
      fetchWindowOverride: null
    },
    pollingStats: {
      totalImportedMessages: 8,
      configuredMailFetchers: 1,
      enabledMailFetchers: 1,
      sourcesWithErrors: 0,
      importTimelines: {},
      duplicateTimelines: {},
      errorTimelines: {},
      health: {
        activeMailFetchers: 1,
        coolingDownMailFetchers: 0,
        failingMailFetchers: 0,
        disabledMailFetchers: 0
      },
      providerBreakdown: [],
      manualRuns: 2,
      scheduledRuns: 3,
      averagePollDurationMillis: 1400
    },
    passkeys: [{
      id: 1,
      label: 'Portatil',
      discoverable: true,
      backedUp: false,
      createdAt: '2026-03-27T09:00:00Z',
      lastUsedAt: '2026-03-27T10:00:00Z'
    }],
    emailAccounts: [{
      emailAccountId: 'outlook-main',
      protocol: 'IMAP',
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      host: 'outlook.office365.com',
      port: 993,
      tokenStorageMode: 'DATABASE',
      effectivePollInterval: '5m',
      effectiveFetchWindow: 50,
      pollingState: null,
      lastEvent: null
    }]
  }
}

describe('UserListItem', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
    resetCurrentFormattingTimeZone()
  })

  it('renders translated subsection details in portuguese', async () => {
    render(
      <UserListItem
        config={buildConfig()}
        isExpanded
        isLoading={false}
        locale="pt-PT"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('pt-PT', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    expect(screen.getByText('Configuração do utilizador')).toBeInTheDocument()
    expect(screen.getByText('Caixa de destino')).toBeInTheDocument()
    expect(screen.getByText('Definições de verificação')).toBeInTheDocument()
    expect(screen.getByText('Passkeys')).toBeInTheDocument()
    const mailFetchersSectionTitle = screen.getByText('Contas de email de origem')
    expect(screen.getByText(/Provider: OUTLOOK_IMAP/i)).toBeInTheDocument()
    expect(screen.getByText(/armazenamento do token:/i)).toBeInTheDocument()
    expect(mailFetchersSectionTitle.closest('section')).toHaveTextContent('Armazenamento encriptado')
  })

  it('renders fallback detail values without crashing when the admin payload is partial', () => {
    render(
      <UserListItem
        config={{
          user: {
            id: 7,
            username: 'admin'
          }
        }}
        isExpanded
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    expect(screen.getByText('Destination Mailbox')).toBeInTheDocument()
    expect(screen.getByText('No passkeys registered for this user.')).toBeInTheDocument()
    expect(screen.getByText('No mail fetchers configured for this user.')).toBeInTheDocument()
    expect(screen.getByText(/Provider:\s*Not set/i)).toBeInTheDocument()
  })

  it('renders translated contextual menu actions in portuguese', () => {
    render(
      <UserListItem
        config={buildConfig()}
        isExpanded={false}
        isLoading={false}
        locale="pt-PT"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('pt-PT', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Ações do utilizador' }))
    const menu = screen.getByRole('button', { name: 'Ações do utilizador' }).parentElement
    const menuQueries = within(menu)

    expect(menuQueries.getByRole('button', { name: 'Tornar utilizador regular' })).toBeInTheDocument()
    expect(menuQueries.getByRole('button', { name: 'Forçar alteração da palavra-passe' })).toBeInTheDocument()
    expect(menuQueries.getByRole('button', { name: 'Repor palavra-passe' })).toBeInTheDocument()
    expect(menuQueries.getByRole('button', { name: 'Repor passkeys' })).toBeInTheDocument()
    expect(menuQueries.getByRole('button', { name: 'Eliminar utilizador' })).toBeInTheDocument()
  })

  it('closes the contextual menu when the trigger scrolls out of view', async () => {
    const originalGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect
    const originalInnerWidth = window.innerWidth
    const originalInnerHeight = window.innerHeight
    let triggerOutOfView = false
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1280 })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 720 })
    HTMLElement.prototype.getBoundingClientRect = function patchedBoundingClientRect() {
      if (this.getAttribute?.('aria-label') === 'User actions') {
        return triggerOutOfView
          ? { top: -80, bottom: -40, right: 1180, left: 1140, width: 40, height: 40 }
          : { top: 220, bottom: 260, right: 1180, left: 1140, width: 40, height: 40 }
      }
      if (this.classList?.contains('fetcher-menu')) {
        return { top: 0, bottom: 180, left: 0, right: 220, width: 220, height: 180 }
      }
      return originalGetBoundingClientRect.call(this)
    }

    render(
      <UserListItem
        config={buildConfig()}
        isExpanded={false}
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))
    expect(screen.getByRole('button', { name: 'Suspend user' })).toBeInTheDocument()

    triggerOutOfView = true
    fireEvent.scroll(window)

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Suspend user' })).not.toBeInTheDocument()
    })

    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalInnerWidth })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalInnerHeight })
    HTMLElement.prototype.getBoundingClientRect = originalGetBoundingClientRect
  })

  it('uses specific administration permission notifications when changing roles', () => {
    const onUpdateUser = vi.fn()

    const adminConfig = buildConfig()
    const regularConfig = {
      ...buildConfig(),
      user: {
        ...buildConfig().user,
        id: 8,
        username: 'operator',
        role: 'USER'
      }
    }

    const { rerender } = render(
      <UserListItem
        config={adminConfig}
        isExpanded
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={onUpdateUser}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Make regular user' }))

    expect(onUpdateUser).toHaveBeenCalledWith(
      7,
      { role: 'USER' },
      expect.objectContaining({
        key: 'notifications.userAdminRevoked',
        kind: 'translation',
        params: { username: 'admin' }
      })
    )
    expect(resolveNotificationContent(onUpdateUser.mock.calls[0][2], 'en')).toBe('admin has now the administration permissions revoked.')

    rerender(
      <UserListItem
        config={regularConfig}
        isExpanded
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={onUpdateUser}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Grant admin rights' }))

    expect(onUpdateUser).toHaveBeenLastCalledWith(
      8,
      { role: 'ADMIN' },
      expect.objectContaining({
        key: 'notifications.userAdminGranted',
        kind: 'translation',
        params: { username: 'operator' }
      })
    )
    expect(resolveNotificationContent(onUpdateUser.mock.calls[1][2], 'en')).toBe('operator is now granted administration permissions.')
  })

  it('starts the nested statistics section collapsed when the user has no meaningful stats', async () => {
    const config = buildConfig()
    config.pollingStats = {
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
    }

    render(
      <UserListItem
        config={config}
        isExpanded
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    expect(screen.queryByText('Import activity over time')).not.toBeInTheDocument()
  })

  it('renders admin detail timestamps with the active manual date format', () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    setCurrentFormattingTimeZone('UTC')

    render(
      <UserListItem
        config={buildConfig()}
        isExpanded
        isLoading={false}
        locale="en"
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpand={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        session={{ id: 99, role: 'ADMIN' }}
        t={(key, params) => translate('en', key, params)}
        updatingPasskeysReset={false}
        updatingUser={false}
      />
    )

    expect(screen.getByText((value) => value.includes('2026-03-27 09:00:00'))).toBeInTheDocument()
    expect(screen.getByText((value) => value.includes('2026-03-27 10:00:00'))).toBeInTheDocument()
  })
})
