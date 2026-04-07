import { render, screen, within } from '@testing-library/react'
import SystemOAuthAppsDialog from './SystemOAuthAppsDialog'
import { translate } from '@/lib/i18n'

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
    expect(screen.getAllByRole('button', { name: 'Copy' }).length).toBeGreaterThan(0)
    expect(screen.getByLabelText('Redirect URI')).toHaveAttribute('readonly')
    const clientIdField = screen.getByLabelText('Google Client ID')
    const clientSecretField = screen.getByLabelText('Google Client Secret')
    const redirectField = screen.getByLabelText('Redirect URI')
    expect(clientIdField.compareDocumentPosition(clientSecretField) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(clientSecretField.compareDocumentPosition(redirectField) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText(/Administration only stores the shared Google OAuth client registration/i)).toBeInTheDocument()
    expect(screen.getByText('How to create the Google OAuth client')).toBeInTheDocument()
    expect(screen.getByText(/Open Google Cloud and create a dedicated project for InboxBridge if one does not exist yet./i)).toBeInTheDocument()
    expect(screen.getByText(/Open Google Auth Platform > Clients, create a new OAuth client, and use a Web application client type./i)).toBeInTheDocument()
    expect(screen.getByText(/Add this redirect URI to the Google OAuth client:/i)).toBeInTheDocument()
    expect(screen.getByText('https://localhost:3000/api/google-oauth/callback')).toBeInTheDocument()
    expect(screen.getByText(/Create a client secret in Google Cloud for that client./i)).toBeInTheDocument()
    expect(screen.getByText(/Paste the Google Client ID and the newly created Client Secret into this InboxBridge dialog, then save./i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Google Cloud Console' })).toHaveAttribute('href', 'https://console.cloud.google.com/')
    expect(within(screen.getByText('https://localhost:3000/api/google-oauth/callback').closest('.oauth-setup-step-block')).getByRole('button', { name: 'Copy' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Google Refresh Token')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Gmail API User')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Connect Shared Gmail OAuth' })).not.toBeInTheDocument()
  })

  it('shows detailed Microsoft setup instructions in the admin dialog', () => {
    render(
      <SystemOAuthAppsDialog
        isDirty={false}
        oauthSettings={{
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: '',
          googleClientSecret: '',
          googleClientSecretConfigured: false,
          googleRefreshToken: '',
          googleRefreshTokenConfigured: false,
          microsoftClientId: 'client-id',
          microsoftClientSecret: '',
          microsoftClientSecretConfigured: true,
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          secureStorageConfigured: true
        }}
        oauthSettingsLoading={false}
        onClose={vi.fn()}
        onOauthSettingsChange={vi.fn()}
        onSave={vi.fn()}
        provider="microsoft"
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('How to create the Microsoft app registration')).toBeInTheDocument()
    expect(screen.getByText(/Create or sign in to the Microsoft Entra account that will own the InboxBridge app registration./i)).toBeInTheDocument()
    expect(screen.getByText(/Open App registrations and register a new application for InboxBridge./i)).toBeInTheDocument()
    expect(screen.getByText(/In Supported account types, choose/i)).toBeInTheDocument()
    expect(screen.getByText('Any Entra ID tenant + Personal Microsoft accounts')).toBeInTheDocument()
    expect(screen.getByText(/Add this redirect URI to the app registration:/i)).toBeInTheDocument()
    expect(screen.getByText('https://localhost:3000/api/microsoft-oauth/callback')).toBeInTheDocument()
    expect(screen.getByText(/Create a new client secret under Certificates & secrets./i)).toBeInTheDocument()
    expect(screen.getByText(/Paste the Application \(client\) ID and the new client secret value into this InboxBridge dialog, then save./i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Microsoft Entra' })).toHaveAttribute('href', 'https://entra.microsoft.com/')
    expect(screen.getByLabelText('Microsoft Redirect URI')).toHaveAttribute('readonly')
    expect(within(screen.getByText('https://localhost:3000/api/microsoft-oauth/callback').closest('.oauth-setup-step-block')).getByRole('button', { name: 'Copy' })).toBeInTheDocument()
  })
})
