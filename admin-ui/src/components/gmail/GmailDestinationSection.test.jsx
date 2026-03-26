import { fireEvent, render, screen } from '@testing-library/react'
import GmailDestinationSection from './GmailDestinationSection'

describe('GmailDestinationSection', () => {
  it('surfaces shared-client guidance and callback defaults', () => {
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

    expect(screen.getByText(/shared google oauth client available/i)).toBeInTheDocument()
    expect(screen.getAllByText(/https:\/\/mail\.example\.test\/api\/google-oauth\/callback/i)).toHaveLength(2)
    expect(screen.getByText(/client id stored for this user: no/i)).toBeInTheDocument()
    expect(screen.getByText(/refresh token stored for this user: no/i)).toBeInTheDocument()

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
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onPasswordChange={vi.fn()}
          onPasswordFormChange={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          passwordForm={{ currentPassword: '', newPassword: '', confirmNewPassword: '' }}
          passwordLoading={false}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
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
          onCollapseToggle={vi.fn()}
          oauthLoading={false}
          onConnectOAuth={vi.fn()}
          onPasswordChange={vi.fn()}
          onPasswordFormChange={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          passwordForm={{ currentPassword: '', newPassword: '', confirmNewPassword: '' }}
          passwordLoading={false}
          saveLoading={false}
          setGmailConfig={(updater) => {
            gmailConfig = typeof updater === 'function' ? updater(gmailConfig) : updater
            rerenderSection()
          }}
        />
      )
    }
  })
})
