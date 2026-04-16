import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SecretManagementSection from './SecretManagementSection'
import { translate } from '@/lib/i18n'

function renderSection(overrides = {}) {
  const onExportSecretManagementReport = vi.fn().mockResolvedValue(true)
  const onRecordSecretManagementRetirementReview = vi.fn().mockResolvedValue(true)
  const onVerifySecretManagementRetirementCompletion = vi.fn().mockResolvedValue(true)
  const onReencryptStoredSecrets = vi.fn().mockResolvedValue(true)
  const onLoadSecretManagementMigrationGuide = vi.fn().mockResolvedValue({
    title: 'Migrate to OpenBao transit mode',
    summary: 'Prepare the target mode, switch the server configuration, restart InboxBridge, and then re-encrypt stored secrets.',
    executionMethod: 'Changing the active secret-management target requires a full stored-secret re-encryption after the server starts on the new mode.',
    checks: [{ checkId: 'target-ready', title: 'Target mode is configured and writable', satisfied: false, detail: 'Missing configuration.', configReferences: ['SECRET_PROVIDER_OPENBAO_URL'] }],
    beforeSwitchSteps: ['Confirm the target mode is writable.'],
    switchSteps: ['Set SECRET_PROVIDER_MODE=OPENBAO_TRANSIT in the server environment.'],
    afterSwitchSteps: ['Run stored-secret re-encryption.']
  })
  const onLoadSecretManagementRecoveryGuide = vi.fn().mockResolvedValue({
    title: 'Secret-management recovery checklist',
    summary: 'The last stored-secret re-encryption run failed or finished with warnings. Stabilize the provider state before retrying.',
    triggerReason: 'The latest re-encryption request failed while the current provider was no longer writable.',
    currentMode: 'OPENBAO_TRANSIT',
    providerId: 'OPENBAO_TRANSIT',
    latestRequestStatus: 'FAILED',
    latestRequestMessage: 'The OpenBao transit token was rejected during verification.',
    rollbackRecommended: true,
    containmentSteps: ['Preserve the previous provider credentials and all legacy key material until recovery is complete.'],
    rollbackSteps: ['If you recently switched SECRET_PROVIDER_MODE, revert it to the last known-good mode recorded in your operator runbook.'],
    validationSteps: ['Refresh the Secret management status and confirm the provider is healthy and writable again.'],
    evidenceItems: ['The last-known-good provider mode and key references saved in your operator runbook.']
  })
  const onRecordSecretManagementRecoveryReview = vi.fn().mockResolvedValue(true)
  const onSecretReencryptOptionsChange = vi.fn()
  render(
    <SecretManagementSection
      collapsed={false}
      collapseLoading={false}
      locale="en"
      onCollapseToggle={vi.fn()}
      onExportSecretManagementReport={onExportSecretManagementReport}
      onRecordSecretManagementRecoveryReview={onRecordSecretManagementRecoveryReview}
      onRecordSecretManagementRetirementReview={onRecordSecretManagementRetirementReview}
      onVerifySecretManagementRetirementCompletion={onVerifySecretManagementRetirementCompletion}
      onReencryptStoredSecrets={onReencryptStoredSecrets}
      onLoadSecretManagementMigrationGuide={onLoadSecretManagementMigrationGuide}
      onLoadSecretManagementRecoveryGuide={onLoadSecretManagementRecoveryGuide}
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
        modeAssessments: [
          {
            mode: 'LOCAL',
            providerId: 'LOCAL',
            current: true,
            healthy: true,
            writable: true,
            statusMessage: 'Local secret provider is ready.',
            activeKeyVersion: 'LOCAL:v2',
            activeKeyId: 'v2',
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_KEY'],
            remediationSteps: ['Keep this active provider path available while you finish re-encryption, validation, and any later retirement review.']
          },
          {
            mode: 'OPENBAO_TRANSIT',
            providerId: 'OPENBAO_TRANSIT',
            current: false,
            healthy: false,
            writable: false,
            statusMessage: 'Secret provider OPENBAO_TRANSIT requires SECRET_PROVIDER_OPENBAO_URL.',
            activeKeyVersion: null,
            activeKeyId: null,
            configReferences: ['SECRET_PROVIDER_MODE', 'SECRET_PROVIDER_OPENBAO_URL'],
            remediationSteps: ['Configure SECRET_PROVIDER_MODE=OPENBAO_TRANSIT together with the OpenBao transit URL, token, mount, and key name.']
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
        legacyKeyRetirementReady: false,
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
        retirementRequirements: [
          {
            requirementId: 'rotation-complete',
            title: 'No encrypted records still depend on a legacy rotation target',
            detail: '3 stored records still depend on older or different encryption targets and must be rewritten to LOCAL:v2.',
            remediationSteps: ['Finish the pending re-encryption or metadata rewrap first.'],
            configReferences: ['active provider / key settings'],
            actionTargetId: 'secret-management-rotation-plan',
            actionLabel: 'Review rotation plan',
            satisfied: false,
            blocking: true
          }
        ],
        latestRetirementReview: null,
        recentRetirementReviews: [],
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
  return {
    onExportSecretManagementReport,
    onLoadSecretManagementMigrationGuide,
    onLoadSecretManagementRecoveryGuide,
    onRecordSecretManagementRecoveryReview,
    onRecordSecretManagementRetirementReview,
    onVerifySecretManagementRetirementCompletion,
    onReencryptStoredSecrets,
    onSecretReencryptOptionsChange
  }
}

describe('SecretManagementSection', () => {
  it('opens the legacy-key retirement review dialog', async () => {
    renderSection({
      secretManagementStatus: {
        safeToRetireLegacyKeys: true,
        legacyKeyRetirementReady: true,
        configuredLegacyKeyIds: ['LOCAL:v1'],
        nonActiveKeyRecordCount: 0,
        unavailableKeyRecordCount: 0,
        retirementRequirements: [
          {
            requirementId: 'rotation-complete',
            title: 'No encrypted records still depend on a legacy rotation target',
            detail: 'InboxBridge does not currently see any encrypted records that still need rotation or rewrap.',
            remediationSteps: ['The current key-usage summary shows no records left on non-active targets.'],
            configReferences: ['active provider / key settings'],
            actionTargetId: 'secret-management-rotation-plan',
            actionLabel: 'Review rotation plan',
            satisfied: true,
            blocking: true
          }
        ]
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Review legacy-key retirement' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
    expect(screen.getByText('InboxBridge no longer sees any encrypted records that still require legacy key material.')).toBeInTheDocument()
    expect(screen.getByText('No encrypted records still depend on a legacy rotation target')).toBeInTheDocument()
    expect(screen.getAllByText('Safe to retire').length).toBeGreaterThan(0)
  })

  it('records a retirement review from the dialog', async () => {
    const { onRecordSecretManagementRetirementReview } = renderSection()

    fireEvent.click(screen.getByRole('button', { name: 'Review legacy-key retirement' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: 'Record review snapshot' }))

    await waitFor(() => {
      expect(onRecordSecretManagementRetirementReview).toHaveBeenCalledTimes(1)
    })
  })

  it('verifies post-cleanup retirement completion from the dialog', async () => {
    const { onVerifySecretManagementRetirementCompletion } = renderSection({
      secretManagementStatus: {
        latestRetirementReview: {
          reviewId: 9,
          reviewedAt: '2026-04-15T14:00:00Z',
          reviewedByUsername: 'admin',
          legacyKeyRetirementReady: true,
          blockingRequirementsRemaining: 0,
          completion: null
        }
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Review legacy-key retirement' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: 'Verify post-cleanup completion' }))

    await waitFor(() => {
      expect(onVerifySecretManagementRetirementCompletion).toHaveBeenCalledTimes(1)
    })
  })

  it('exports the latest report from the actions card', async () => {
    const { onExportSecretManagementReport } = renderSection()

    fireEvent.click(screen.getByRole('button', { name: 'Export latest report' }))

    await waitFor(() => {
      expect(onExportSecretManagementReport).toHaveBeenCalledTimes(1)
    })
  })

  it('renders backend-assessed secret-management migration targets', () => {
    renderSection()

    expect(screen.getByText('Available migration targets')).toBeInTheDocument()
    expect(screen.getByText('Local key mode')).toBeInTheDocument()
    expect(screen.getByText('OpenBao transit mode')).toBeInTheDocument()
    expect(screen.getAllByText('Target key or provider path').length).toBeGreaterThan(0)
    expect(screen.getByText('Secret provider OPENBAO_TRANSIT requires SECRET_PROVIDER_OPENBAO_URL.')).toBeInTheDocument()
  })

  it('opens the backend-generated migration checklist for a target mode', async () => {
    const { onLoadSecretManagementMigrationGuide } = renderSection()

    fireEvent.click(screen.getAllByRole('button', { name: 'Open migration checklist' })[1])

    await waitFor(() => expect(onLoadSecretManagementMigrationGuide).toHaveBeenCalledWith('OPENBAO_TRANSIT'))
    expect(screen.getByText('Migrate to OpenBao transit mode')).toBeInTheDocument()
    expect(screen.getByText('Backend-validated preflight checks')).toBeInTheDocument()
    expect(screen.getByText('Set SECRET_PROVIDER_MODE=OPENBAO_TRANSIT in the server environment.')).toBeInTheDocument()
  })

  it('opens the backend-generated recovery checklist when the latest request needs operator follow-up', async () => {
    const { onLoadSecretManagementRecoveryGuide, onRecordSecretManagementRecoveryReview } = renderSection({
      secretManagementStatus: {
        reencryptionRequest: {
          requestId: 42,
          requestFingerprint: 'FAILED|2026-04-16T08:00:00Z|2026-04-16T08:10:00Z|attention',
          status: 'FAILED',
          requestedAt: '2026-04-16T08:00:00Z',
          requestedByUsername: 'admin',
          summary: 'Run failed',
          verificationPassed: false
        }
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Open recovery checklist' }))

    await waitFor(() => expect(onLoadSecretManagementRecoveryGuide).toHaveBeenCalledTimes(1))
    expect(screen.getByText('Secret-management recovery checklist')).toBeInTheDocument()
    expect(screen.getByText('The OpenBao transit token was rejected during verification.')).toBeInTheDocument()
    expect(screen.getByText('Preserve the previous provider credentials and all legacy key material until recovery is complete.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Record recovery snapshot' }))

    await waitFor(() => expect(onRecordSecretManagementRecoveryReview).toHaveBeenCalledTimes(1))
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
    expect(screen.getAllByText('What you need to do').length).toBeGreaterThan(0)
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
