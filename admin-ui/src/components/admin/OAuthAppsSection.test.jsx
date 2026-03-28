import { render, screen } from '@testing-library/react'
import OAuthAppsSection from './OAuthAppsSection'
import { translate } from '../../lib/i18n'

describe('OAuthAppsSection', () => {
  it('summarizes Google app configuration without suggesting shared mailbox consent', () => {
    render(
      <OAuthAppsSection
        collapsed={false}
        collapseLoading={false}
        oauthSettings={{
          googleClientId: 'google-client-id',
          googleClientSecretConfigured: true,
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

    expect(screen.getByText(/Administration only stores the shared Google OAuth client registration/i)).toBeInTheDocument()
    expect(screen.getByText('Google Client Secret')).toBeInTheDocument()
    expect(screen.getByText('Redirect URI')).toBeInTheDocument()
    expect(screen.queryByText('Google Refresh Token')).not.toBeInTheDocument()
    expect(screen.queryByText('Gmail API User')).not.toBeInTheDocument()
  })
})