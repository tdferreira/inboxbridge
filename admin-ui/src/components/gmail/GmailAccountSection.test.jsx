import { fireEvent, render, screen } from '@testing-library/react'
import GmailAccountSection from './GmailAccountSection'
import { translate } from '../../lib/i18n'

describe('GmailAccountSection', () => {
  it('shows the advanced Gmail override form to admins', () => {
    let gmailConfig = {
      destinationUser: 'me',
      clientId: '',
      clientSecret: '',
      refreshToken: '',
      redirectUri: 'https://mail.example.test/api/google-oauth/callback',
      createMissingLabels: true,
      neverMarkSpam: false,
      processForCalendar: false
    }

    const { rerender } = renderSection()

    expect(screen.getByText(/gmail oauth connected: no/i)).toBeInTheDocument()
    expect(screen.getByText(/shared google oauth client available/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Google Client ID')).toBeInTheDocument()
    expect(screen.getByText(/google setup/i)).toBeInTheDocument()
    expect(screen.getByRole('note', { name: /the gmail api user id that inboxbridge writes into/i })).toBeInTheDocument()
    expect(screen.getByRole('note', { name: /the oauth callback url registered in google cloud/i })).toBeInTheDocument()
    expect(screen.getByRole('note', { name: /the google cloud oauth client id/i })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Gmail API User'), { target: { value: 'alternate-user' } })

    expect(gmailConfig.destinationUser).toBe('alternate-user')

    function renderSection() {
      return render(
        <GmailAccountSection
          collapsed={false}
          collapseLoading={false}
          gmailConfig={gmailConfig}
          gmailMeta={{
            defaultRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
            sharedClientConfigured: true,
            clientIdConfigured: false,
            clientSecretConfigured: false,
            refreshTokenConfigured: false
          }}
          isAdmin
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onUnlinkOAuth={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
          locale="en"
          t={(key, params) => translate('en', key, params)}
        />
      )
    }

    function rerenderSection() {
      rerender(
        <GmailAccountSection
          collapsed={false}
          collapseLoading={false}
          gmailConfig={gmailConfig}
          gmailMeta={{
            defaultRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
            sharedClientConfigured: true,
            clientIdConfigured: false,
            clientSecretConfigured: false,
            refreshTokenConfigured: false
          }}
          isAdmin
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onUnlinkOAuth={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
          locale="en"
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('shows only Gmail OAuth status and connect controls to non-admin users', () => {
    render(
      <GmailAccountSection
        collapsed={false}
        collapseLoading={false}
        gmailConfig={{
          destinationUser: 'me',
          clientId: '',
          clientSecret: '',
          refreshToken: '',
          redirectUri: 'https://mail.example.test/api/google-oauth/callback',
          createMissingLabels: true,
          neverMarkSpam: false,
          processForCalendar: false
        }}
        gmailMeta={{
          defaultRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
          sharedClientConfigured: true,
          clientIdConfigured: false,
          clientSecretConfigured: false,
          refreshTokenConfigured: true
        }}
        isAdmin={false}
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn()}
        saveLoading={false}
        setGmailConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByRole('button', { name: 'Reconnect My Gmail OAuth' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unlink Gmail Account' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Gmail API User')).not.toBeInTheDocument()
    expect(screen.queryByText(/google setup/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/client id override stored for this user/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/saved credential status/i)).not.toBeInTheDocument()
    expect(screen.getByText(/your gmail connection is ready/i)).toBeInTheDocument()
  })

  it('renders Gmail section copy and labels in portuguese', () => {
    render(
      <GmailAccountSection
        collapsed={false}
        collapseLoading={false}
        gmailConfig={{
          destinationUser: 'me',
          clientId: '',
          clientSecret: '',
          refreshToken: '',
          redirectUri: 'https://mail.example.test/api/google-oauth/callback',
          createMissingLabels: true,
          neverMarkSpam: false,
          processForCalendar: false
        }}
        gmailMeta={{
          defaultRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
          sharedClientConfigured: true,
          clientIdConfigured: false,
          clientSecretConfigured: false,
          refreshTokenConfigured: false
        }}
        isAdmin
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        setGmailConfig={vi.fn()}
        locale="pt-PT"
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('A minha conta Gmail')).toBeInTheDocument()
    expect(screen.getByText('Estado das credenciais guardadas')).toBeInTheDocument()
    expect(screen.getByLabelText('Utilizador da API Gmail')).toBeInTheDocument()
    expect(screen.getByLabelText('URI de redirecionamento')).toBeInTheDocument()
    expect(screen.getByRole('note', { name: /identificador do utilizador da api gmail/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ligar o meu OAuth Gmail' })).toBeInTheDocument()
    expect(screen.getByText('Configuração Google')).toBeInTheDocument()
  })

  it('shows a refresh indicator while the Gmail section updates', () => {
    render(
      <GmailAccountSection
        collapsed={false}
        collapseLoading={false}
        gmailConfig={{
          destinationUser: 'me',
          clientId: '',
          clientSecret: '',
          refreshToken: '',
          redirectUri: 'https://mail.example.test/api/google-oauth/callback',
          createMissingLabels: true,
          neverMarkSpam: false,
          processForCalendar: false
        }}
        gmailMeta={{
          defaultRedirectUri: 'https://mail.example.test/api/google-oauth/callback',
          sharedClientConfigured: true,
          clientIdConfigured: false,
          clientSecretConfigured: false,
          refreshTokenConfigured: false
        }}
        isAdmin
        onCollapseToggle={vi.fn()}
        oauthLoading={false}
        onConnectOAuth={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        sectionLoading
        setGmailConfig={vi.fn()}
        locale="en"
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })
})
