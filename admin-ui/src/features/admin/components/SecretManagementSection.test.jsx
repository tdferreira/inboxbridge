import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SecretManagementSection from './SecretManagementSection'
import { translate } from '@/lib/i18n'

describe('SecretManagementSection', () => {
  it('renders the secret-management summary and requires acknowledgements before re-encryption', async () => {
    const onReencryptStoredSecrets = vi.fn().mockResolvedValue(true)
    const onSecretReencryptOptionsChange = vi.fn()

    render(
      <SecretManagementSection
        collapsed={false}
        collapseLoading={false}
        locale="en"
        onCollapseToggle={vi.fn()}
        onReencryptStoredSecrets={onReencryptStoredSecrets}
        onSecretReencryptOptionsChange={onSecretReencryptOptionsChange}
        secretManagementStatus={{
          secureStorageConfigured: true,
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: ['LOCAL:v1'],
          protectedRecordCount: 14,
          nonActiveKeyRecordCount: 3,
          unavailableKeyRecordCount: 1,
          envManagedMailboxSecretsAllowed: false,
          configuredEnvManagedSourceCount: 2,
          envManagedGoogleRefreshTokenConfigured: true,
          safeToRetireLegacyKeys: false,
          keyUsage: [
            {
              keyVersion: 'LOCAL:v2',
              recordCount: 11,
              areas: 'oauth-credentials,source-mailboxes',
              availableForDecryption: true
            },
            {
              keyVersion: 'LOCAL:v1',
              recordCount: 3,
              areas: 'destination-mailboxes',
              availableForDecryption: false
            }
          ]
        }}
        secretReencryptOptions={{
          revokeBrowserExtensionSessions: false,
          revokeRemoteSessions: false,
          clearCachedOAuthAccessTokens: false
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Secret management')).toBeInTheDocument()
    expect(screen.getByText('Key and policy summary')).toBeInTheDocument()
    expect(screen.getByText('LOCAL:v2')).toBeInTheDocument()
    expect(screen.getAllByText('LOCAL:v1').length).toBeGreaterThan(0)
    expect(screen.getByText('Blocked by policy')).toBeInTheDocument()
    expect(screen.getByText('Configured')).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('Available') && content.includes('11 records'))).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('Unavailable for decryption') && content.includes('3 records'))).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Re-encrypt stored secrets' }))

    expect(screen.getByText('High-risk operation')).toBeInTheDocument()
    const confirmButton = screen.getAllByRole('button', { name: 'Re-encrypt stored secrets' }).at(-1)
    expect(confirmButton).toBeDisabled()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Revoke browser extension sessions after re-encryption' }))
    expect(onSecretReencryptOptionsChange).toHaveBeenCalled()

    fireEvent.click(screen.getByRole('checkbox', { name: 'I already configured and validated the new active key or secret-management provider.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I will keep legacy keys available until re-encryption finishes and the key-usage summary shows they are no longer needed.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I understand a failed or interrupted re-encryption can leave stored secrets unrecoverable until the missing key material is restored.' }))

    expect(confirmButton).not.toBeDisabled()

    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onReencryptStoredSecrets).toHaveBeenCalledTimes(1)
    })
    await waitFor(() => {
      expect(screen.queryByText('High-risk operation')).not.toBeInTheDocument()
    })
  })
})
