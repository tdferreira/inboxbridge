import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SecretManagementSection from './SecretManagementSection'
import { translate } from '@/lib/i18n'

function renderSection(overrides = {}) {
  const onExportSecretManagementReport = vi.fn().mockResolvedValue(true)
  const onReencryptStoredSecrets = vi.fn().mockResolvedValue(true)
  const onSecretReencryptOptionsChange = vi.fn()
  render(
    <SecretManagementSection
      collapsed={false}
      collapseLoading={false}
      locale="en"
      onCollapseToggle={vi.fn()}
      onExportSecretManagementReport={onExportSecretManagementReport}
      onReencryptStoredSecrets={onReencryptStoredSecrets}
      onVerifySecretManagementPassword={vi.fn()}
      onVerifySecretManagementPasskey={vi.fn()}
      onSecretReencryptOptionsChange={onSecretReencryptOptionsChange}
      session={{ hasPassword: true, passkeyCount: 1 }}
      secretManagementStatus={{
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v2',
        activeKeyId: 'LOCAL:v2',
        providerComponents: [
          {
            componentId: 'local-key',
            title: 'Local inner encryption key',
            detail: 'The local AES-GCM key path is configured and can protect InboxBridge-managed secrets.',
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY', 'SECURITY_TOKEN_ENCRYPTION_KEY_ID'],
            healthy: true,
            writable: true
          }
        ],
        rotationPlan: {
          planId: 'local-key-rotation',
          title: 'Local-key rotation is pending',
          summary: '3 stored records still depend on older or different encryption targets and must be rewritten to LOCAL:v2.',
          recommendedAction: 'Keep legacy keys and provider credentials available, run full re-encryption, then validate mailbox, destination, and OAuth flows before retiring the previous secret path.',
          targetKeyVersion: 'LOCAL:v2',
          affectedRecordCount: 3,
          unavailableRecordCount: 0,
          impactedAreas: ['destination-mailboxes', 'system-oauth'],
          rotationNeeded: true,
          requiresFullReencryption: true,
          metadataRewrapSupported: false
        },
        reencryptionPreview: {
          activeKeyVersion: 'LOCAL:v2',
          totalRecordsPendingUpdate: 3,
          totalSecretValuesPendingRewrite: 3,
          totalFullReencryptionCount: 3,
          totalMetadataRewrapCount: 0,
          areas: [
            {
              area: 'destination-mailboxes',
              recordsUpdated: 1,
              secretValuesReencrypted: 1,
              fullReencryptionCount: 1,
              metadataRewrapCount: 0
            },
            {
              area: 'system-oauth',
              recordsUpdated: 2,
              secretValuesReencrypted: 2,
              fullReencryptionCount: 2,
              metadataRewrapCount: 0
            }
          ]
        },
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
            remediationSteps: ['Verify the current provider endpoint from the server host.'],
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY'],
            actionTargetId: 'secret-management-provider-diagnostics',
            actionLabel: 'Review provider diagnostics',
            satisfied: true,
            blocking: true
          }
        ],
        reencryptionRequest: null,
        reencryptionCooldown: 'PT12H',
        immediateReencryptionOverrideAllowed: false,
        reauthenticationRequired: false,
        reauthenticationSatisfied: true,
        reauthenticationExpiresAt: null,
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
  return { onExportSecretManagementReport, onReencryptStoredSecrets, onSecretReencryptOptionsChange }
}

describe('SecretManagementSection', () => {
  it('exports the latest report from the actions card', async () => {
    const { onExportSecretManagementReport } = renderSection()

    fireEvent.click(screen.getByRole('button', { name: 'Export latest report' }))

    await waitFor(() => {
      expect(onExportSecretManagementReport).toHaveBeenCalledTimes(1)
    })
  })

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
            remediationSteps: ['Verify the current provider endpoint from the server host.'],
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY'],
            actionTargetId: 'secret-management-provider-diagnostics',
            actionLabel: 'Review provider diagnostics',
            satisfied: true,
            blocking: true
          },
          {
            requirementId: 'legacy-key-availability',
            title: 'Every stored secret is currently decryptable',
            detail: 'Some stored records already reference unavailable key material. Restore those keys before re-encrypting.',
            remediationSteps: [
              'Restore every missing legacy key or provider credential that still protects encrypted records.',
              'Do not start re-encryption until the unavailable-record counter returns to zero.'
            ],
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS'],
            actionTargetId: 'secret-management-key-usage',
            actionLabel: 'Review key usage',
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
    expect(screen.getByText('Backend dry-run preview')).toBeInTheDocument()
    expect(screen.getByText('Records that would be updated')).toBeInTheDocument()
    expect(screen.getByText('Stored secrets that would be rewritten')).toBeInTheDocument()
    expect(screen.getByText('Not satisfied')).toBeInTheDocument()
    expect(screen.getByText('Provider diagnostics')).toBeInTheDocument()
    expect(screen.getByText('Local inner encryption key')).toBeInTheDocument()
    expect(screen.getByText('Healthy and writable')).toBeInTheDocument()
    expect(screen.getByText('Rotation plan')).toBeInTheDocument()
    expect(screen.getByText('Local-key rotation is pending')).toBeInTheDocument()
    expect(screen.getByText('Action required')).toBeInTheDocument()
    expect(screen.getByText('Full re-encryption')).toBeInTheDocument()
    expect(screen.getByText('What you need to do')).toBeInTheDocument()
    expect(screen.getByText('Restore every missing legacy key or provider credential that still protects encrypted records.')).toBeInTheDocument()
    expect(screen.getByText('SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review key usage' })).toBeInTheDocument()

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

  it('renders metadata rewrap as the required method when the rotation plan supports it', () => {
    renderSection({
      secretManagementStatus: {
        rotationPlan: {
          planId: 'transit-key-rollover',
          title: 'Transit key rollover rewrap is pending',
          summary: '2 stored records already use the active target metadata but still carry older transit-provider key versions inside the ciphertext envelope.',
          recommendedAction: 'Run metadata rewrap so InboxBridge can refresh the outer transit ciphertext to the current provider key version without rewriting plaintext, then validate the provider before retiring older provider-side key versions.',
          targetKeyVersion: 'OPENBAO_TRANSIT:inboxbridge',
          affectedRecordCount: 2,
          unavailableRecordCount: 0,
          impactedAreas: ['oauth-credentials'],
          rotationNeeded: true,
          requiresFullReencryption: false,
          metadataRewrapSupported: true
        }
      }
    })

    expect(screen.getByText('Metadata rewrap')).toBeInTheDocument()
  })

  it('requires sensitive-session re-authentication before confirming when server policy demands it', async () => {
    const onVerifySecretManagementPassword = vi.fn().mockResolvedValue(true)
    render(
      <SecretManagementSection
        collapsed={false}
        collapseLoading={false}
        locale="en"
        onCollapseToggle={vi.fn()}
        onReencryptStoredSecrets={vi.fn().mockResolvedValue(true)}
        onSecretReencryptOptionsChange={vi.fn()}
        onVerifySecretManagementPassword={onVerifySecretManagementPassword}
        onVerifySecretManagementPasskey={vi.fn()}
        session={{ hasPassword: true, passkeyCount: 1 }}
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
          safeToRetireLegacyKeys: false,
          keyUsage: [],
          reencryptionReady: false,
          reencryptionRequirements: [
            {
              requirementId: 'provider-health',
              title: 'Active secret provider is healthy and writable',
              detail: 'Local secret provider is ready.',
              remediationSteps: ['Verify the current provider endpoint from the server host.'],
              configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY'],
            actionTargetId: 'secret-management-provider-diagnostics',
            actionLabel: 'Review provider diagnostics',
              satisfied: true,
              blocking: true
            },
            {
              requirementId: 'recent-reauthentication',
              title: 'This browser session was recently re-authenticated for sensitive actions',
              detail: 'Re-authenticate this browser session with the current password or a passkey before re-encrypting stored secrets.',
              remediationSteps: [
                'Use Verify with current password or Verify with passkey in this dialog before confirming re-encryption.'
              ],
              configReferences: ['inboxbridge.security.secret-management.reauthentication-ttl'],
              actionTargetId: 'secret-reencryption-reauthentication',
              actionLabel: 'Open session verification',
              satisfied: false,
              blocking: true
            }
          ],
          reencryptionRequest: null,
          reencryptionCooldown: 'PT12H',
          immediateReencryptionOverrideAllowed: false,
          reauthenticationRequired: true,
          reauthenticationSatisfied: false,
          reauthenticationExpiresAt: null
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

    expect(screen.getByText('Sensitive session verification')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verify with current password' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verify with passkey' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open session verification' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('checkbox', { name: 'I already configured and validated the new active key or secret-management provider.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I will keep legacy keys available until re-encryption finishes and the key-usage summary shows they are no longer needed.' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'I understand a failed or interrupted re-encryption can leave stored secrets unrecoverable until the missing key material is restored.' }))

    expect(screen.getAllByRole('button', { name: 'Re-encrypt stored secrets' }).at(-1)).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Current password'), { target: { value: 'Current1!' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verify with current password' }))

    await waitFor(() => {
      expect(onVerifySecretManagementPassword).toHaveBeenCalledWith('Current1!')
    })
  })

  it('shows verification output in the dialog after the backend completes re-encryption', async () => {
    const onReencryptStoredSecrets = vi.fn().mockResolvedValue({
      operationStatus: 'COMPLETED',
      message: 'Secret re-encryption completed and post-run verification passed.',
      executeAfter: '2026-04-15T14:00:00Z',
      totalFullReencryptionCount: 1,
      totalMetadataRewrapCount: 2,
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
              remediationSteps: ['Verify the current provider endpoint from the server host.'],
              configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY'],
              actionTargetId: 'secret-management-provider-diagnostics',
              actionLabel: 'Review provider diagnostics',
              satisfied: true,
              blocking: true
            }
          ],
          reencryptionRequest: null,
          reencryptionCooldown: 'PT12H',
          immediateReencryptionOverrideAllowed: false,
          reauthenticationRequired: false,
          reauthenticationSatisfied: true,
          reauthenticationExpiresAt: null
        }}
        secretReencryptOptions={{
          immediateExecutionOverride: false,
          revokeBrowserExtensionSessions: false,
          revokeRemoteSessions: false,
          clearCachedOAuthAccessTokens: false
        }}
        onVerifySecretManagementPassword={vi.fn()}
        onVerifySecretManagementPasskey={vi.fn()}
        session={{ hasPassword: true, passkeyCount: 1 }}
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
    const fullRow = screen.getByText('Secrets rewritten through full re-encryption').closest('div')
    const rewrapRow = screen.getByText('Secrets refreshed through metadata rewrap').closest('div')
    expect(fullRow).toHaveTextContent('1')
    expect(rewrapRow).toHaveTextContent('2')
  })

  it('renders the persisted queued-request snapshot from secret-management status', () => {
    renderSection({
      secretManagementStatus: {
        reencryptionRequest: {
          status: 'PENDING',
          executeAfter: '2026-04-16T10:00:00Z',
          message: 'Secret re-encryption is queued and will execute after the cooldown window.',
          verificationPassed: false,
          plannedPreview: {
            activeKeyVersion: 'LOCAL:v2',
            totalRecordsPendingUpdate: 2,
            totalSecretValuesPendingRewrite: 2,
            totalFullReencryptionCount: 2,
            totalMetadataRewrapCount: 0,
            areas: []
          },
          totalRecordsUpdated: 0,
          totalSecretValuesReencrypted: 0,
          totalFullReencryptionCount: 0,
          totalMetadataRewrapCount: 0,
          areas: [],
          followUp: { browserExtensionSessionsRevoked: 0, remoteSessionsRevoked: 0, cachedOAuthAccessTokensCleared: 0 },
          verification: null
        }
      }
    })

    expect(screen.getByText('Latest re-encryption request')).toBeInTheDocument()
    expect(screen.getByText('Records that would be updated')).toBeInTheDocument()
    expect(screen.getByText('Stored secrets that would be rewritten')).toBeInTheDocument()
  })
})
