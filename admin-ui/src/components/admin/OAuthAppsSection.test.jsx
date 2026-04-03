import { fireEvent, render, screen } from '@testing-library/react'
import OAuthAppsSection from './OAuthAppsSection'
import { translate } from '../../lib/i18n'

describe('OAuthAppsSection', () => {
  it('shows stacked expandable provider cards for shared OAuth apps', () => {
    render(
      <OAuthAppsSection
        collapsed={false}
        collapseLoading={false}
        oauthSettings={{
          googleClientId: 'google-client-id',
          googleClientSecretConfigured: true,
          microsoftClientSecretConfigured: true,
          googleRefreshTokenConfigured: false,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback'
        }}
        onCollapseToggle={vi.fn()}
        onEditGoogle={vi.fn()}
        onEditMicrosoft={vi.fn()}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText(/Use this shared Google OAuth app when InboxBridge will import mail into Gmail as the destination mailbox./i)).toBeInTheDocument()
    expect(screen.queryByText(/Administration only stores the shared Google OAuth client registration/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Google Client Secret')).not.toBeInTheDocument()
    expect(screen.queryByText('Google Refresh Token')).not.toBeInTheDocument()
    expect(screen.queryByText('Gmail API User')).not.toBeInTheDocument()
    expect(screen.getByText(/Use this shared Microsoft app when InboxBridge will append mail into Outlook as the destination mailbox./i)).toBeInTheDocument()
    fireEvent.click(screen.getByTitle('Show or hide Google OAuth details'))
    expect(screen.getByText(/Administration only stores the shared Google OAuth client registration/i)).toBeInTheDocument()
    expect(screen.getByText('Google Client Secret')).toBeInTheDocument()
    expect(screen.getByText('Redirect URI')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Google OAuth' })).toBeInTheDocument()
    fireEvent.click(screen.getByTitle('Show or hide Google OAuth details'))
    expect(screen.queryByRole('button', { name: 'Edit Google OAuth' })).not.toBeInTheDocument()
  })

  it('collapses cards by default when both providers are fully configured and expands incomplete ones', () => {
    const { rerender } = render(
      <OAuthAppsSection
        collapsed={false}
        collapseLoading={false}
        oauthSettings={{
          googleClientId: 'google-client-id',
          googleClientSecretConfigured: true,
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          microsoftClientId: 'microsoft-client-id',
          microsoftClientSecretConfigured: true,
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback'
        }}
        onCollapseToggle={vi.fn()}
        onEditGoogle={vi.fn()}
        onEditMicrosoft={vi.fn()}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByRole('button', { name: 'Edit Google OAuth' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Microsoft OAuth' })).not.toBeInTheDocument()

    rerender(
      <OAuthAppsSection
        collapsed={false}
        collapseLoading={false}
        oauthSettings={{
          googleClientId: '',
          googleClientSecretConfigured: false,
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          microsoftClientId: 'microsoft-client-id',
          microsoftClientSecretConfigured: true,
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback'
        }}
        onCollapseToggle={vi.fn()}
        onEditGoogle={vi.fn()}
        onEditMicrosoft={vi.fn()}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByRole('button', { name: 'Edit Google OAuth' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Microsoft OAuth' })).not.toBeInTheDocument()
  })
})
