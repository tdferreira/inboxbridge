import { fireEvent, render, screen } from '@testing-library/react'
import GmailDestinationSection from './GmailDestinationSection'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('GmailDestinationSection', () => {
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

    fireEvent.change(screen.getByLabelText('Destination User'), { target: { value: 'alternate-user' } })

    expect(gmailConfig.destinationUser).toBe('alternate-user')

    function renderSection() {
      return render(
        <GmailDestinationSection
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
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
          locale="en"
          t={t}
        />
      )
    }

    function rerenderSection() {
      rerender(
        <GmailDestinationSection
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
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
          locale="en"
          t={t}
        />
      )
    }
  })

  it('shows only Gmail OAuth status and connect controls to non-admin users', () => {
    render(
      <GmailDestinationSection
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
        onSave={vi.fn()}
        saveLoading={false}
        setGmailConfig={vi.fn()}
        locale="en"
        t={t}
      />
    )

    expect(screen.getByText(/gmail oauth connected: yes/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reconnect My Gmail OAuth' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Destination User')).not.toBeInTheDocument()
    expect(screen.queryByText(/google setup/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/client id override stored for this user/i)).not.toBeInTheDocument()
    expect(screen.getByText(/your gmail connection is ready/i)).toBeInTheDocument()
  })
})
