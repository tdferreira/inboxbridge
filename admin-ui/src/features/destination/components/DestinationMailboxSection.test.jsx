import { fireEvent, render, screen, within } from '@testing-library/react'
import DestinationMailboxSection from './DestinationMailboxSection'
import { translate } from '@/lib/i18n'

describe('DestinationMailboxSection', () => {
  function renderSection(overrides = {}) {
    let destinationConfig = overrides.destinationConfig || {
      provider: 'GMAIL_API',
      host: '',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      folder: 'INBOX'
    }
    const props = {
      collapsed: false,
      collapseLoading: false,
      destinationConfig,
      destinationFolders: [],
      destinationFoldersLoading: false,
      destinationMeta: {
        configured: false,
        provider: 'GMAIL_API',
        linked: false,
        sharedGoogleClientConfigured: true,
        oauthConnected: false,
        passwordConfigured: false,
        googleRedirectUri: 'https://mail.example.test/api/google-oauth/callback'
      },
      isAdmin: true,
      unlinkLoading: false,
      locale: 'en',
      onCollapseToggle: vi.fn(),
      onUnlinkOAuth: vi.fn(),
      onSave: vi.fn(async () => {}),
      onSaveAndAuthenticate: vi.fn(async () => {}),
      onTestConnection: vi.fn(async () => ({ success: true, message: 'Connection test succeeded.', protocol: 'IMAP', host: 'outlook.office365.com', port: 993, tls: true, authMethod: 'OAUTH2', oauthProvider: 'MICROSOFT', authenticated: true, folder: 'INBOX', folderAccessible: true })),
      saveLoading: false,
      sectionLoading: false,
      testConnectionLoading: false,
      setDestinationConfig: (updater) => {
        destinationConfig = typeof updater === 'function' ? updater(destinationConfig) : updater
        rerenderWithConfig(destinationConfig)
      },
      t: (key, params) => translate('en', key, params),
      ...overrides
    }

    const { rerender } = render(<DestinationMailboxSection {...props} />)

    function rerenderWithConfig(nextConfig) {
      rerender(<DestinationMailboxSection {...props} destinationConfig={nextConfig} />)
    }

    return { destinationConfig, props, rerenderWithConfig }
  }

  it('shows an Add button and opens the destination modal when no mailbox is configured', () => {
    renderSection()

    expect(screen.getByRole('button', { name: 'Add Destination Mailbox' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Connect Gmail Account' })).not.toBeInTheDocument()
    expect(screen.getByText('No destination mailbox configured yet.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Add Destination Mailbox' }))

    expect(screen.getByRole('dialog', { name: 'Add Destination Mailbox' })).toBeInTheDocument()
    expect(screen.getByLabelText('Destination Provider')).toBeInTheDocument()
  })

  it('shows only Edit in the section header and exposes IMAP and auth actions in the modal', () => {
    renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: false,
        passwordConfigured: false,
        oauthConnected: false,
        sharedGoogleClientConfigured: true
      }
    })

    expect(screen.getByRole('button', { name: 'Edit Destination Mailbox' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Connect Microsoft Account' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Unlink Account' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))

    expect(screen.getByRole('dialog', { name: 'Edit Destination Mailbox' })).toBeInTheDocument()
    expect(screen.getByLabelText('Server hostname')).toHaveValue('outlook.office365.com')
    expect(screen.getByLabelText('Username')).toHaveValue('me@example.com')
    expect(screen.queryByLabelText('Auth Method')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('OAuth Provider')).not.toBeInTheDocument()
    expect(screen.getByText('Outlook uses Microsoft OAuth2 for IMAP APPEND access. Save the mailbox details here, then use the Microsoft OAuth button to authorize this destination account.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save and Authenticate' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('shows Gmail as an authenticate-only destination flow inside the modal', () => {
    renderSection({
      destinationConfig: {
        provider: 'GMAIL_API',
        host: '',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'GOOGLE',
        username: '',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: false,
        provider: 'GMAIL_API',
        linked: false,
        sharedGoogleClientConfigured: true,
        oauthConnected: false,
        passwordConfigured: false
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Add Destination Mailbox' }))

    expect(within(screen.getByRole('dialog', { name: 'Add Destination Mailbox' })).getByText('Choose Gmail and then use Save and Authenticate. InboxBridge only treats Gmail as ready after the Google OAuth flow finishes successfully.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save and Authenticate' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('only enables plain Outlook save for folder-only destination changes', () => {
    renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: true,
        passwordConfigured: false,
        oauthConnected: true,
        sharedGoogleClientConfigured: true
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))

    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Folder'), { target: { value: 'Archive' } })

    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'other@example.com' } })

    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
  })

  it('keeps the selected draft provider while the parent rerenders server state underneath the open modal', () => {
    const { rerenderWithConfig } = renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: false,
        passwordConfigured: false,
        oauthConnected: false,
        sharedGoogleClientConfigured: true
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))
    fireEvent.change(screen.getByLabelText('Destination Provider'), { target: { value: 'YAHOO_IMAP' } })

    rerenderWithConfig({
      provider: 'OUTLOOK_IMAP',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      username: 'me@example.com',
      password: '',
      folder: 'INBOX'
    })

    expect(screen.getByLabelText('Destination Provider')).toHaveValue('YAHOO_IMAP')
    expect(screen.getByLabelText('Server hostname')).toHaveValue('imap.mail.yahoo.com')
  })

  it('shows unlink only inside the modal and only when the saved provider is actually linked', () => {
    renderSection({
      destinationConfig: {
        provider: 'GMAIL_API',
        host: '',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'GOOGLE',
        username: '',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'GMAIL_API',
        linked: false,
        sharedGoogleClientConfigured: true,
        oauthConnected: false,
        passwordConfigured: false
      }
    })

    expect(screen.queryByRole('button', { name: 'Unlink Account' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))

    expect(screen.queryByRole('button', { name: 'Unlink Account' })).not.toBeInTheDocument()
  })

  it('shows the detected folder dropdown in the edit modal and can switch back to manual entry', () => {
    renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: ''
      },
      destinationFolders: ['Archive', 'INBOX', 'Sent Items'],
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: true,
        passwordConfigured: false,
        oauthConnected: true,
        sharedGoogleClientConfigured: true
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))

    expect(screen.getByRole('combobox', { name: 'Folder' })).toHaveValue('INBOX')

    fireEvent.click(screen.getByRole('button', { name: 'Enter folder manually' }))

    expect(screen.getByRole('textbox', { name: 'Folder' })).toHaveValue('INBOX')
    expect(screen.getByRole('button', { name: 'Use detected folders' })).toBeInTheDocument()
  })

  it('runs destination connection testing from the modal when the mailbox can already authenticate', async () => {
    const onTestConnection = vi.fn(async () => ({
      success: true,
      message: 'Connection test succeeded.',
      protocol: 'IMAP',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      authenticated: true,
      folder: 'INBOX',
      folderAccessible: true,
      visibleMessageCount: 0
    }))

    renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: true,
        passwordConfigured: false,
        oauthConnected: true,
        sharedGoogleClientConfigured: true
      },
      onTestConnection
    })

    fireEvent.click(screen.getByRole('button', { name: 'Edit Destination Mailbox' }))
    fireEvent.click(screen.getByRole('button', { name: 'Test Connection' }))

    expect(onTestConnection).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Connection test succeeded.')).toBeInTheDocument()
  })

  it('renders localized destination preset copy and test action in portuguese', () => {
    const locale = 'pt-PT'
    const t = (key, params) => translate(locale, key, params)

    renderSection({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: 993,
        tls: true,
        authMethod: 'OAUTH2',
        oauthProvider: 'MICROSOFT',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'OUTLOOK_IMAP',
        linked: true,
        passwordConfigured: false,
        oauthConnected: true,
        sharedGoogleClientConfigured: true
      },
      locale,
      t
    })

    fireEvent.click(screen.getByRole('button', { name: t('destination.edit') }))

    expect(screen.getByText('Usa IMAP APPEND para o Outlook com Microsoft OAuth2 por defeito.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Testar ligação' })).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('Fornecedor: Outlook'))).toBeInTheDocument()
    expect(screen.getByLabelText('Endereço')).toHaveValue('outlook.office365.com')
    expect(screen.getByLabelText('Porto')).toHaveValue(993)
  })

  it('renders the generic IMAP destination provider label in portuguese', () => {
    const locale = 'pt-PT'
    const t = (key, params) => translate(locale, key, params)

    renderSection({
      destinationConfig: {
        provider: 'CUSTOM_IMAP',
        host: 'imap.example.com',
        port: 993,
        tls: true,
        authMethod: 'PASSWORD',
        oauthProvider: 'NONE',
        username: 'me@example.com',
        password: '',
        folder: 'INBOX'
      },
      destinationMeta: {
        configured: true,
        provider: 'CUSTOM_IMAP',
        linked: true,
        passwordConfigured: true,
        oauthConnected: false,
        sharedGoogleClientConfigured: true
      },
      locale,
      t
    })

    expect(screen.getByText((content) => content.includes('Fornecedor: IMAP genérico'))).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: t('destination.edit') }))

    expect(screen.getByRole('option', { name: 'IMAP genérico' })).toBeInTheDocument()
  })

  it('shows a refresh indicator while the destination section updates', () => {
    render(
      <DestinationMailboxSection
        collapsed={false}
        collapseLoading={false}
        destinationConfig={{
          provider: 'GMAIL_API',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: '',
          folder: 'INBOX'
        }}
        destinationMeta={{
          linked: false,
          sharedGoogleClientConfigured: true,
          oauthConnected: false,
          passwordConfigured: false
        }}
        isAdmin
        onCollapseToggle={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onSaveAndAuthenticate={vi.fn()}
        onTestConnection={vi.fn()}
        saveLoading={false}
        sectionLoading
        locale="en"
        t={(key, params) => translate('en', key, params)}
        testConnectionLoading={false}
        unlinkLoading={false}
      />
    )

    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })
})
