import { fireEvent, render, screen } from '@testing-library/react'
import DestinationMailboxSection from './DestinationMailboxSection'
import { translate } from '../../lib/i18n'

describe('DestinationMailboxSection', () => {
  it('shows provider-neutral destination controls with Gmail linking', () => {
    let destinationConfig = {
      provider: 'GMAIL_API',
      destinationUser: 'me',
      host: '',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      folder: 'INBOX'
    }

    const { rerender } = renderSection()

    expect(screen.getByText(/saved destination status/i)).toBeInTheDocument()
    expect(screen.getByText(/provider: gmail/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Connect Gmail Account' })).toBeInTheDocument()
    expect(screen.getByText(/google setup/i)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Destination Provider'), { target: { value: 'OUTLOOK_IMAP' } })

    expect(destinationConfig.provider).toBe('OUTLOOK_IMAP')
    expect(destinationConfig.host).toBe('outlook.office365.com')

    function renderSection() {
      return render(
        <DestinationMailboxSection
          collapsed={false}
          collapseLoading={false}
          destinationConfig={destinationConfig}
          destinationMeta={{
            googleRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
            sharedGoogleClientConfigured: true,
            linked: false,
            passwordConfigured: false,
            oauthConnected: false
          }}
          isAdmin
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onUnlinkOAuth={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setDestinationConfig={(updater) => {
            destinationConfig = typeof updater === 'function' ? updater(destinationConfig) : updater
            rerenderSection()
          }}
          locale="en"
          t={(key, params) => translate('en', key, params)}
          unlinkLoading={false}
        />
      )
    }

    function rerenderSection() {
      rerender(
        <DestinationMailboxSection
          collapsed={false}
          collapseLoading={false}
          destinationConfig={destinationConfig}
          destinationMeta={{
            googleRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
            sharedGoogleClientConfigured: true,
            linked: false,
            passwordConfigured: false,
            oauthConnected: false
          }}
          isAdmin
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onUnlinkOAuth={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setDestinationConfig={(updater) => {
            destinationConfig = typeof updater === 'function' ? updater(destinationConfig) : updater
          }}
          locale="en"
          t={(key, params) => translate('en', key, params)}
          unlinkLoading={false}
        />
      )
    }
  })

  it('shows Microsoft OAuth controls for an Outlook destination', () => {
    render(
      <DestinationMailboxSection
        collapsed={false}
        collapseLoading={false}
        destinationConfig={{
          provider: 'OUTLOOK_IMAP',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'me@example.com',
          password: '',
          folder: 'INBOX'
        }}
        destinationMeta={{
          provider: 'OUTLOOK_IMAP',
          linked: true,
          passwordConfigured: false,
          oauthConnected: true
        }}
        isAdmin
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn()}
        saveLoading={false}
        setDestinationConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
        unlinkLoading={false}
      />
    )

    expect(screen.getByRole('button', { name: 'Reconnect Microsoft Account' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unlink Destination' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Host')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Auth Method')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Folder')).toHaveValue('INBOX')
    expect(screen.getByText(/use the microsoft oauth button above/i)).toBeInTheDocument()
  })

  it('keeps the saved status on the linked provider while the user stages a different provider', () => {
    render(
      <DestinationMailboxSection
        collapsed={false}
        collapseLoading={false}
        destinationConfig={{
          provider: 'GMAIL_API',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'GOOGLE',
          username: '',
          password: '',
          folder: 'INBOX'
        }}
        destinationMeta={{
          provider: 'OUTLOOK_IMAP',
          linked: true,
          passwordConfigured: false,
          oauthConnected: true,
          sharedGoogleClientConfigured: true
        }}
        isAdmin
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn()}
        saveLoading={false}
        setDestinationConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
        unlinkLoading={false}
      />
    )

    expect(screen.getByText((value) => value.includes('Provider: Outlook / Hotmail / Live'))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Connect Gmail Account' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reconnect Gmail Account' })).not.toBeInTheDocument()
    expect(screen.getByText('Gmail still needs to be connected.')).toBeInTheDocument()
  })

  it('shows simplified Gmail status for non-admin users', () => {
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
        isAdmin={false}
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        setDestinationConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
        unlinkLoading={false}
      />
    )

    expect(screen.getByText('Gmail still needs to be connected.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Connect Gmail Account' })).toBeInTheDocument()
    expect(screen.queryByText(/google setup/i)).not.toBeInTheDocument()
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
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        sectionLoading
        setDestinationConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
        unlinkLoading={false}
      />
    )

    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })
})