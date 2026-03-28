import { render, screen } from '@testing-library/react'
import SystemOAuthAppsDialog from './SystemOAuthAppsDialog'
import { translate } from '../../lib/i18n'

describe('SystemOAuthAppsDialog', () => {
  it('shows the Google admin dialog as shared client configuration only', () => {
    render(
      <SystemOAuthAppsDialog
        isDirty={false}
        oauthSettings={{
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: 'client-id',
          googleClientSecret: '',
          googleClientSecretConfigured: true,
          googleRefreshToken: '',
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftClientSecret: '',
          microsoftClientSecretConfigured: false,
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          secureStorageConfigured: true
        }}
        oauthSettingsLoading={false}
        onClose={vi.fn()}
        onOauthSettingsChange={vi.fn()}
        onSave={vi.fn()}
        provider="google"
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText('Google Client ID')).toBeInTheDocument()
    expect(screen.getByLabelText('Google Client Secret')).toBeInTheDocument()
    expect(screen.getByLabelText('Redirect URI')).toBeInTheDocument()
    expect(screen.getByText(/Administration only stores the shared Google OAuth client registration/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Google Refresh Token')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Gmail API User')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Connect Shared Gmail OAuth' })).not.toBeInTheDocument()
  })
})