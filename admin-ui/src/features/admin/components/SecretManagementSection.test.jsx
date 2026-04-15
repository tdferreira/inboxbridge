import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SecretManagementSection from './SecretManagementSection'
import { translate } from '@/lib/i18n'

function renderSection(overrides = {}) {
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
        unavailableKeyRecordCount: 0,
        envManagedMailboxSecretsAllowed: false,
        configuredEnvManagedSourceCount: 2,
        envManagedGoogleRefreshTokenConfigured: true,
        safeToRetireLegacyKeys: false,
        keyUsage: [],
        reencryptionReady: true,
        reencryptionRequirements: [
          {
            requirementId: 'provider-health',
            title: 'Active secret provider is healthy and writable',
            detail: 'Local secret provider is ready.',
            satisfied: true,
            blocking: true
          }
        ],
        reencryptionRequest: null,
        reencryptionCooldown: 'PT12H',
        immediateReencryptionOverrideAllowed: false,
        ...overrides.secretManagementStatus
      }}
      secretReencryptOptions={{
        immediateExecutionOverride: false,
        revokeBrowserExtensionSessions: false,
        revokeRemoteSessions: false,
        clearCachedOAuthAccessTokens: false,
        ...overrides.secretReencryptOptions
      }}
      t={(key, params) => translate('en', key, params)}
    />
  )
  return { onReencryptStoredSecrets, onSecretReencryptOptionsChange }
}

describe('SecretManagementSection', () => {
  it('keeps the confirm action disabled when backend requirements are not satisfied', () => {
    const { onSecretReencryptOptionsChange } = renderSection({
      secretManagementStatus: {
        unavailableKeyRecordCount: 1,
        reencryptionReady: false,
        reencryptionRequirements: [
          {
            requirementId: 'provider-health',
            title: 'Active secret provider is healthy and writable',
            detail: 'Local secret provider is ready.',
            satisfied: true,
            blocking: true
          },
          {
            requirementId: 'legacy-key-availability',
            title: 'Every stored secret is currently decryptable',
            detail: 'Some stored records already reference unavailable key material. Restore those keys before re-encrypting.',
            satisfied: false,
            blocking: true
          }
        ],
        keyUsage: [
          {
            keyVersion: 'LOCAL:v1',
            recordCount: 3,
            areas: 'destination-mailboxes',
            availableForDecryption: false
          }
        ]
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Re-encrypt stored secrets' }))

    expect(screen.getByText('Backend-verified requirements')).toBeInTheDocument()
    expect(screen.getByText('Not satisfied')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Revoke browser extension sessions after re-encryption' }))
    expect(onSecretReencryptOptionsChange).toHaveBeenCalled()

    fireEvent.click(screen.getByRole('checkbox', { name: 'I already configured and validated the new active key or secret-management provider.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I will keep legacy keys available until re-encryption finishes and the key-usage summary shows they are no longer needed.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I understand a failed or interrupted re-encryption can leave stored secrets unrecoverable until the missing key material is restored.' }))

    expect(screen.getAllByRole('button', { name: 'Re-encrypt stored secrets' }).at(-1)).toBeDisabled()
  })

  it('allows re-encryption when requirements and acknowledgements are complete', async () => {
    const { onReencryptStoredSecrets } = renderSection()

    fireEvent.click(screen.getByRole('button', { name: 'Re-encrypt stored secrets' }))

    fireEvent.click(screen.getByRole('checkbox', { name: 'I already configured and validated the new active key or secret-management provider.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I will keep legacy keys available until re-encryption finishes and the key-usage summary shows they are no longer needed.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I understand a failed or interrupted re-encryption can leave stored secrets unrecoverable until the missing key material is restored.' }))

    const confirmButton = screen.getAllByRole('button', { name: 'Re-encrypt stored secrets' }).at(-1)
    expect(confirmButton).not.toBeDisabled()

    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(onReencryptStoredSecrets).toHaveBeenCalledTimes(1)
    })
  })

  it('shows verification output in the dialog after the backend completes re-encryption', async () => {
    const onReencryptStoredSecrets = vi.fn().mockResolvedValue({
      operationStatus: 'COMPLETED',
      message: 'Secret re-encryption completed and post-run verification passed.',
      executeAfter: '2026-04-15T14:00:00Z',
      verification: {
        passed: true,
        messages: ['All stored secret records now use the active key version.'],
        operatorSaveItems: ['Save the active key version and the current legacy-key list in your recovery notes.']
      }
    })

    render(
      <SecretManagementSection
        collapsed={false}
        collapseLoading={false}
        locale="en"
        onCollapseToggle={vi.fn()}
        onReencryptStoredSecrets={onReencryptStoredSecrets}
        onSecretReencryptOptionsChange={vi.fn()}
        secretManagementStatus={{
          secureStorageConfigured: true,
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: ['LOCAL:v1'],
          protectedRecordCount: 14,
          nonActiveKeyRecordCount: 0,
          unavailableKeyRecordCount: 0,
          envManagedMailboxSecretsAllowed: false,
          configuredEnvManagedSourceCount: 2,
          envManagedGoogleRefreshTokenConfigured: true,
          safeToRetireLegacyKeys: true,
          keyUsage: [],
          reencryptionReady: true,
          reencryptionRequirements: [
            {
              requirementId: 'provider-health',
              title: 'Active secret provider is healthy and writable',
              detail: 'Local secret provider is ready.',
              satisfied: true,
              blocking: true
            }
          ],
          reencryptionRequest: null,
          reencryptionCooldown: 'PT12H',
          immediateReencryptionOverrideAllowed: false
        }}
        secretReencryptOptions={{
          immediateExecutionOverride: false,
          revokeBrowserExtensionSessions: false,
          revokeRemoteSessions: false,
          clearCachedOAuthAccessTokens: false
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Re-encrypt stored secrets' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I already configured and validated the new active key or secret-management provider.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I will keep legacy keys available until re-encryption finishes and the key-usage summary shows they are no longer needed.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I understand a failed or interrupted re-encryption can leave stored secrets unrecoverable until the missing key material is restored.' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Re-encrypt stored secrets' }).at(-1))

    await waitFor(() => {
      expect(screen.getByText('All stored secret records now use the active key version.')).toBeInTheDocument()
    })
    expect(screen.getByText('Save before retiring legacy keys')).toBeInTheDocument()
    expect(screen.getByText('Save the active key version and the current legacy-key list in your recovery notes.')).toBeInTheDocument()
  })
})
