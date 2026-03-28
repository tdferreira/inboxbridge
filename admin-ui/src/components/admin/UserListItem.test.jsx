import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import UserListItem from './UserListItem'
import { translate } from '../../lib/i18n'

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
      bridgeCount: 1
    },
    gmailConfig: {
      destinationUser: 'me',
      redirectUri: 'https://localhost:3000/api/google-oauth/callback',
      sharedClientConfigured: true,
      clientIdConfigured: false,
      clientSecretConfigured: false,
      refreshTokenConfigured: true
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
    bridges: [{
      bridgeId: 'outlook-main',
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
    expect(screen.getByText('Destino Gmail')).toBeInTheDocument()
    expect(screen.getByText('Definições de verificação')).toBeInTheDocument()
    expect(screen.getByText('Passkeys')).toBeInTheDocument()
    const mailFetchersSectionTitle = screen.getByText('Contas de email de origem')
    expect(await screen.findByText('Estatísticas do utilizador: admin')).toBeInTheDocument()
    expect(screen.getByText(/Utilizador da API Gmail: me/)).toBeInTheDocument()
    expect(screen.getByText(/armazenamento do token:/i)).toBeInTheDocument()
    expect(mailFetchersSectionTitle.closest('section')).toHaveTextContent('BD encriptada')
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

    expect(screen.getByText('User Statistics: admin')).toBeInTheDocument()
    expect(screen.queryByText('Import activity over time')).not.toBeInTheDocument()
  })
})
