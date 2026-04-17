import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SecretManagementSection from './SecretManagementSection'
import { translate } from '@/lib/i18n'

function renderSection(overrides = {}) {
  const onExportSecretManagementReport = vi.fn().mockResolvedValue(true)
  const onRecordSecretManagementRetirementReview = vi.fn().mockResolvedValue(true)
  const onVerifySecretManagementRetirementCompletion = vi.fn().mockResolvedValue(true)
  const onReencryptStoredSecrets = vi.fn().mockResolvedValue(true)
  const onLoadSecretManagementMigrationGuide = overrides.onLoadSecretManagementMigrationGuide || vi.fn().mockResolvedValue({
    title: 'Migrate to OpenBao transit mode',
    summary: 'Prepare the target mode, switch the server configuration, restart InboxBridge, and then re-encrypt stored secrets.',
    executionMethod: 'Changing the active secret-management target requires a full stored-secret re-encryption after the server starts on the new mode.',
    continueReady: false,
    checks: [{ checkId: 'target-ready', title: 'Target mode is configured and writable', satisfied: false, detail: 'Missing configuration.', configReferences: ['SECRET_PROVIDER_OPENBAO_URL'] }],
    beforeSwitchSteps: ['Confirm the target mode is writable.'],
    switchSteps: ['Set SECRET_PROVIDER_MODE=OPENBAO_TRANSIT in the server environment.'],
    afterSwitchSteps: ['Run stored-secret re-encryption.'],
    postSwitchRequirements: []
  })
  const onLoadSecretManagementRecoveryGuide = overrides.onLoadSecretManagementRecoveryGuide || vi.fn().mockResolvedValue({
    title: 'Secret-management recovery checklist',
    summary: 'The last stored-secret re-encryption run failed or finished with warnings. Stabilize the provider state before retrying.',
    triggerReason: 'The latest re-encryption request failed while the current provider was no longer writable.',
    currentMode: 'OPENBAO_TRANSIT',
    providerId: 'OPENBAO_TRANSIT',
    currentTarget: {
      mode: 'OPENBAO_TRANSIT',
      providerId: 'OPENBAO_TRANSIT',
      activeKeyVersion: 'OPENBAO_TRANSIT:inboxbridge',
      activeKeyId: 'inboxbridge'
    },
    latestRequestStatus: 'FAILED',
    latestRequestMessage: 'The OpenBao transit token was rejected during verification.',
    latestRequestTarget: {
      mode: 'LOCAL',
      providerId: 'LOCAL',
      activeKeyVersion: 'LOCAL:v2',
      activeKeyId: 'v2'
    },
    retryReady: false,
    retryRequirements: [
      {
        requirementId: 'latest-recovery-review',
        title: 'The latest failed or warning-state request was reviewed before retrying',
        detail: 'Record the recovery snapshot for the latest failed or warning-state request before retrying re-encryption.',
        satisfied: false,
        blocking: true
      }
    ],
    rollbackRecommended: true,
    containmentSteps: ['Preserve the previous provider credentials and all legacy key material until recovery is complete.'],
    rollbackSteps: ['If you recently switched SECRET_PROVIDER_MODE, revert it to the last known-good mode recorded in your operator runbook.'],
    validationSteps: ['Refresh the Secret management status and confirm the provider is healthy and writable again.'],
    evidenceItems: ['The last-known-good provider mode and key references saved in your operator runbook.']
  })
  const onRecordSecretManagementRecoveryReview = vi.fn().mockResolvedValue(true)
  const onApproveSecretManagementReencryption = vi.fn().mockResolvedValue(true)
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
      onApproveSecretManagementReencryption={onApproveSecretManagementReencryption}
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
    onApproveSecretManagementReencryption,
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
  it('starts migration targets collapsed and lets ready targets expand on demand', () => {
    renderSection({
      secretManagementStatus: {
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
            remediationSteps: ['Keep this active provider path available.']
          },
          {
            mode: 'OPENBAO_TRANSIT',
            providerId: 'OPENBAO_TRANSIT',
            current: false,
            healthy: true,
            writable: true,
            statusMessage: 'OpenBao transit mode is fully configured and can be used as the next active encryption target.',
            activeKeyVersion: 'OPENBAO_TRANSIT:inboxbridge',
            activeKeyId: 'inboxbridge',
            configReferences: ['SECRET_PROVIDER_MODE', 'SECRET_PROVIDER_OPENBAO_URL'],
            remediationSteps: ['Switch the deployment to OPENBAO_TRANSIT only after finishing the migration checklist.']
          }
        ]
      }
    })

    const targetsCard = document.getElementById('secret-management-mode-assessments')
    expect(targetsCard?.closest('.secret-management-grid')).not.toBeNull()
    expect(screen.queryByText('Switch the deployment to OPENBAO_TRANSIT only after finishing the migration checklist.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Local key modeCurrent mode/i })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('button', { name: /OpenBao transit modeReady to switch/i })).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(screen.getByRole('button', { name: /OpenBao transit modeReady to switch/i }))

    expect(screen.getByText('Switch the deployment to OPENBAO_TRANSIT only after finishing the migration checklist.')).toBeInTheDocument()
  })

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

  it('warns when env-managed mailbox secrets are still allowed in deployment config', () => {
    renderSection({
      secretManagementStatus: {
        envManagedMailboxSecretsAllowed: true,
        configuredEnvManagedSourceCount: 1,
        envManagedGoogleRefreshTokenConfigured: false
      }
    })

    expect(screen.getByText('Plaintext mailbox secrets still exist in deployment config')).toBeInTheDocument()
    expect(screen.getByText("InboxBridge can still read mailbox secrets directly from deployment environment settings. Move those source or Gmail credentials into InboxBridge's encrypted UI-managed storage, then disable env-managed mailbox secrets for this deployment.")).toBeInTheDocument()
  })

  it('warns when blocked env-managed mailbox secrets are still present in deployment config', () => {
    renderSection()

    expect(screen.getByText('Plaintext mailbox secrets still exist in deployment config')).toBeInTheDocument()
    expect(screen.getByText('InboxBridge is already blocking env-managed mailbox secrets, but plaintext mailbox secret values are still present in the deployment environment. Remove those stale .env values so backups, support bundles, and copied deployment files do not keep carrying unused credentials.')).toBeInTheDocument()
  })

  it('shows a success state once env-managed mailbox secrets are fully cleaned up', () => {
    renderSection({
      secretManagementStatus: {
        envManagedMailboxSecretsAllowed: false,
        configuredEnvManagedSourceCount: 0,
        envManagedGoogleRefreshTokenConfigured: false
      }
    })

    expect(screen.getByText('No plaintext mailbox secrets are currently detected in deployment config')).toBeInTheDocument()
    expect(screen.getByText("InboxBridge is already relying on encrypted UI-managed mailbox storage for this deployment, with no detected mailbox-secret fallback left in the deployment environment.")).toBeInTheDocument()
  })

  it('renders backend-assessed secret-management migration targets', () => {
    renderSection()

    expect(screen.getByText('Available migration targets')).toBeInTheDocument()
    expect(screen.getByText('Local key mode')).toBeInTheDocument()
    expect(screen.getByText('OpenBao transit mode')).toBeInTheDocument()
    expect(screen.getByText('Secret provider OPENBAO_TRANSIT requires SECRET_PROVIDER_OPENBAO_URL.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Local key modeCurrent mode/i }))

    expect(screen.getAllByText('Target key or provider path').length).toBeGreaterThan(0)
  })

  it('opens the backend-generated migration checklist for a target mode', async () => {
    const { onLoadSecretManagementMigrationGuide } = renderSection()

    fireEvent.click(screen.getByRole('button', { name: /OpenBao transit modeNeeds setup/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Open migration checklist' }))

    await waitFor(() => expect(onLoadSecretManagementMigrationGuide).toHaveBeenCalledWith('OPENBAO_TRANSIT'))
    expect(screen.getByText('Migrate to OpenBao transit mode')).toBeInTheDocument()
    expect(screen.getByText('Backend-validated preflight checks')).toBeInTheDocument()
    expect(screen.getByText('Set SECRET_PROVIDER_MODE=OPENBAO_TRANSIT in the server environment.')).toBeInTheDocument()
  })

  it('lets the operator continue into re-encryption once the target mode is already active and ready', async () => {
    const { onLoadSecretManagementMigrationGuide } = renderSection({
      secretManagementStatus: {
        mode: 'OPENBAO_TRANSIT',
        providerId: 'OPENBAO_TRANSIT',
        reencryptionReady: true,
        reencryptionRequirements: [
          {
            requirementId: 'provider-health',
            title: 'Active secret provider is healthy and writable',
            detail: 'The provider is ready for a fresh re-encryption run.',
            remediationSteps: [],
            configReferences: ['SECRET_PROVIDER_MODE'],
            actionTargetId: 'secret-management-provider-diagnostics',
            actionLabel: 'Review provider diagnostics',
            satisfied: true,
            blocking: true
          }
        ]
      },
      onLoadSecretManagementMigrationGuide: vi.fn().mockResolvedValue({
        title: 'Review the OpenBao transit operating checklist',
        summary: 'This mode is already active. Use this checklist to keep the current trust path healthy while you complete any remaining rotation work.',
        executionMethod: 'No provider switch is required. Follow the current rotation plan and re-encryption guidance already shown in Secret management.',
        currentMode: 'OPENBAO_TRANSIT',
        currentProviderId: 'OPENBAO_TRANSIT',
        targetMode: 'OPENBAO_TRANSIT',
        targetProviderId: 'OPENBAO_TRANSIT',
        targetReady: true,
        current: true,
        continueReady: true,
        checks: [{ checkId: 'target-ready', title: 'Target mode is configured and writable', satisfied: true, detail: 'InboxBridge can already use the selected target mode as an active encryption path.', configReferences: ['SECRET_PROVIDER_MODE'] }],
        beforeSwitchSteps: ['Keep the current provider path available until rotation is complete.'],
        switchSteps: ['No provider-mode switch is needed while this mode remains active.'],
        afterSwitchSteps: ['Run stored-secret re-encryption so InboxBridge rewrites encrypted data onto the new active provider path.'],
        postSwitchRequirements: [
          {
            requirementId: 'provider-health',
            title: 'Active secret provider is healthy and writable',
            detail: 'The provider is ready for a fresh re-encryption run.',
            remediationSteps: [],
            configReferences: ['SECRET_PROVIDER_MODE'],
            actionTargetId: 'secret-management-provider-diagnostics',
            actionLabel: 'Review provider diagnostics',
            satisfied: true,
            blocking: true
          }
        ]
      })
    })

    fireEvent.click(screen.getByRole('button', { name: /OpenBao transit modeNeeds setup/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Open migration checklist' }))

    await waitFor(() => expect(onLoadSecretManagementMigrationGuide).toHaveBeenCalledWith('OPENBAO_TRANSIT'))
    expect(screen.getByText('Post-switch re-encryption checks')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Open re-encryption dialog' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toHaveTextContent('Re-encrypt stored secrets')
    })
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
    expect(screen.getByText('Retry readiness checks')).toBeInTheDocument()
    expect(screen.getByText('Record the recovery snapshot for the latest failed or warning-state request before retrying re-encryption.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Record recovery snapshot' }))

    await waitFor(() => expect(onRecordSecretManagementRecoveryReview).toHaveBeenCalledTimes(1))
  })

  it('offers the recovery checklist for blocked re-encryption requests too', async () => {
    const { onLoadSecretManagementRecoveryGuide } = renderSection({
      secretManagementStatus: {
        reencryptionRequest: {
          requestFingerprint: 'BLOCKED|2026-04-16T08:00:00Z',
          status: 'BLOCKED',
          requestedAt: '2026-04-16T08:00:00Z',
          verificationPassed: false,
          message: 'Queued secret re-encryption was blocked because the active secret-management target changed.'
        }
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Open recovery checklist' }))

    await waitFor(() => expect(onLoadSecretManagementRecoveryGuide).toHaveBeenCalledTimes(1))
  })

  it('lets the operator reopen the re-encryption dialog once the recovery checklist says retry is ready', async () => {
    const { onLoadSecretManagementRecoveryGuide } = renderSection({
      secretManagementStatus: {
        reencryptionRequest: {
          requestFingerprint: 'BLOCKED|2026-04-16T08:00:00Z',
          status: 'BLOCKED',
          requestedAt: '2026-04-16T08:00:00Z',
          verificationPassed: false,
          message: 'Queued secret re-encryption was blocked because the active secret-management target changed.'
        }
      },
      onLoadSecretManagementRecoveryGuide: vi.fn().mockResolvedValue({
        title: 'Secret-management recovery checklist',
        summary: 'The reviewed target drifted, but the backend checks are clear again now.',
        triggerReason: 'The latest request was BLOCKED before execution.',
        currentMode: 'LOCAL',
        providerId: 'LOCAL',
        currentTarget: {
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v3',
          activeKeyId: 'v3'
        },
        latestRequestStatus: 'BLOCKED',
        latestRequestMessage: 'Queued secret re-encryption was blocked because the active target changed.',
        latestRequestTarget: {
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'v2'
        },
        retryReady: true,
        retryRequirements: [
          {
            requirementId: 'provider-health',
            title: 'Active secret provider is healthy and writable',
            detail: 'The provider is ready for a fresh request.',
            remediationSteps: [],
            configReferences: [],
            actionTargetId: null,
            actionLabel: null,
            satisfied: true,
            blocking: true
          }
        ],
        rollbackRecommended: false,
        containmentSteps: ['Save the updated target in the runbook.'],
        rollbackSteps: ['No rollback is currently required.'],
        validationSteps: ['Submit a fresh request against the current target.'],
        evidenceItems: ['The current active target and the stale queued target.']
      })
    })

    fireEvent.click(screen.getByRole('button', { name: 'Open recovery checklist' }))

    await waitFor(() => expect(onLoadSecretManagementRecoveryGuide).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: 'Open re-encryption dialog' }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toHaveTextContent('Re-encrypt stored secrets')
    })
  })

  it('lets an operator approve a queued re-encryption after the cooldown window elapses', async () => {
    const { onApproveSecretManagementReencryption } = renderSection({
      secretManagementStatus: {
        reencryptionRequest: {
          requestFingerprint: 'PENDING|2026-04-16T08:00:00Z',
          status: 'PENDING',
          requestedAt: '2026-04-16T08:00:00Z',
          executeAfter: '2026-04-16T08:10:00Z',
          approvalRequired: true,
          approvalReady: true,
          approvedAt: null,
          approvedByUsername: null,
          message: 'The cooldown window has elapsed. Review the queued plan and explicitly approve execution before InboxBridge runs it.',
          plannedPreview: {
            totalRecordsPendingUpdate: 3,
            totalSecretValuesPendingRewrite: 3
          }
        }
      }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Approve queued execution' }))

    await waitFor(() => expect(onApproveSecretManagementReencryption).toHaveBeenCalledTimes(1))
  })

  it('renders stale queued-target remediation when a blocked request no longer matches the active target', () => {
    renderSection({
      secretManagementStatus: {
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v3',
        activeKeyId: 'v3',
        reencryptionReady: false,
        reencryptionRequirements: [
          {
            requirementId: 'legacy-key-availability',
            title: 'Every stored secret is currently decryptable',
            detail: 'Some stored records already reference unavailable key material. Restore those keys before re-encrypting.',
            remediationSteps: [
              'Restore every missing legacy key or provider credential that still protects encrypted records.'
            ],
            configReferences: ['SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS'],
            actionTargetId: 'secret-management-key-usage',
            actionLabel: 'Review key usage',
            satisfied: false,
            blocking: true
          }
        ],
        reencryptionRequest: {
          requestFingerprint: 'BLOCKED|2026-04-16T08:00:00Z',
          status: 'BLOCKED',
          requestedAt: '2026-04-16T08:00:00Z',
          requestedTarget: {
            mode: 'LOCAL',
            providerId: 'LOCAL',
            activeKeyVersion: 'LOCAL:v2',
            activeKeyId: 'v2'
          },
          message: 'Queued secret re-encryption was blocked because the active secret-management target changed from v2 to v3.'
        }
      }
    })

    expect(screen.getByText('This queued request became stale')).toBeInTheDocument()
    expect(screen.getByText('Queued target')).toBeInTheDocument()
    expect(screen.getByText('Current active target')).toBeInTheDocument()
    expect(screen.getAllByText('v2').length).toBeGreaterThan(0)
    expect(screen.getAllByText('v3').length).toBeGreaterThan(0)
    expect(screen.getByText('The current backend requirements also show that some older records are no longer decryptable with the active configuration. Restore the previous key or provider path as a legacy decrypt path before retrying.')).toBeInTheDocument()
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
