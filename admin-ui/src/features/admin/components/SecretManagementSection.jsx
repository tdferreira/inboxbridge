import { useState } from 'react'
import LoadingButton from '@/shared/components/LoadingButton'
import Banner from '@/shared/components/Banner'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import SecretMigrationGuideDialog from './SecretMigrationGuideDialog'
import SecretRecoveryGuideDialog from './SecretRecoveryGuideDialog'
import SecretReencryptionDialog from './SecretReencryptionDialog'
import SecretRetirementDialog from './SecretRetirementDialog'
import './SecretManagementSection.css'

function formatKeyIds(keyIds, t) {
  return Array.isArray(keyIds) && keyIds.length > 0 ? keyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')
}

function formatKeyUsageAreas(areas, t) {
  return String(areas || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
    .map((value) => {
      const translationKey = `authSecurity.secretArea.${value}`
      const translated = t(translationKey)
      return translated === translationKey ? value : translated
    })
    .join(', ')
}

function componentTone(component) {
  if (component?.writable) {
    return 'status-ok'
  }
  if (component?.healthy) {
    return 'tone-warn'
  }
  return 'tone-bad'
}

function rotationMethodLabel(rotationPlan, t) {
  if (!rotationPlan?.rotationNeeded) {
    return t('authSecurity.secretManagementRotationMethodNone')
  }
  if (rotationPlan?.requiresFullReencryption) {
    return t('authSecurity.secretManagementRotationMethodFull')
  }
  if (rotationPlan?.metadataRewrapSupported) {
    return t('authSecurity.secretManagementRotationMethodRewrap')
  }
  return t('authSecurity.secretManagementRotationMethodNone')
}

function modeAssessmentLabel(mode, t) {
  const translationKey = `authSecurity.secretManagementMode${mode}`
  const translated = t(translationKey)
  return translated === translationKey ? mode : translated
}

function preferredTargetLabel(target, fallback) {
  if (!target) {
    return fallback
  }
  return target.activeKeyId || target.activeKeyVersion || target.providerId || target.mode || fallback
}

function SecretManagementSection({
  collapsed,
  collapseLoading,
  locale = 'en',
  onCollapseToggle,
  onExportSecretManagementReport,
  onRecordSecretManagementRecoveryReview,
  onRecordSecretManagementRetirementReview,
  onVerifySecretManagementRetirementCompletion,
  onApproveSecretManagementReencryption,
  onReencryptStoredSecrets,
  onLoadSecretManagementMigrationGuide,
  onLoadSecretManagementRecoveryGuide,
  onVerifySecretManagementPasskey,
  onVerifySecretManagementPassword,
  onSecretReencryptOptionsChange,
  exportReportLoading = false,
  recoveryReviewLoading = false,
  retirementReviewLoading = false,
  retirementCompletionLoading = false,
  reauthPasskeyLoading = false,
  reauthPasswordLoading = false,
  reencryptionApprovalLoading = false,
  reencryptionLoading = false,
  sectionLoading = false,
  session,
  secretManagementStatus,
  secretReencryptOptions,
  t
}) {
  const [showReencryptDialog, setShowReencryptDialog] = useState(false)
  const [showRetirementDialog, setShowRetirementDialog] = useState(false)
  const [showMigrationGuideDialog, setShowMigrationGuideDialog] = useState(false)
  const [migrationGuideLoading, setMigrationGuideLoading] = useState(false)
  const [migrationGuide, setMigrationGuide] = useState(null)
  const [showRecoveryGuideDialog, setShowRecoveryGuideDialog] = useState(false)
  const [recoveryGuideLoading, setRecoveryGuideLoading] = useState(false)
  const [recoveryGuide, setRecoveryGuide] = useState(null)
  const [reencryptionResult, setReencryptionResult] = useState(null)
  const keyUsage = Array.isArray(secretManagementStatus?.keyUsage) ? secretManagementStatus.keyUsage : []
  const providerComponents = Array.isArray(secretManagementStatus?.providerComponents) ? secretManagementStatus.providerComponents : []
  const modeAssessments = Array.isArray(secretManagementStatus?.modeAssessments) ? secretManagementStatus.modeAssessments : []
  const rotationPlan = secretManagementStatus?.rotationPlan || null
  const reencryptionRequest = secretManagementStatus?.reencryptionRequest
  const pendingReencryption = reencryptionRequest?.status === 'PENDING'
  const blockedReencryption = reencryptionRequest?.status === 'BLOCKED'
  const approvalReady = Boolean(reencryptionRequest?.approvalReady)
  const canApproveDirectly = approvalReady
    && (!secretManagementStatus?.reauthenticationRequired || secretManagementStatus?.reauthenticationSatisfied)
  const latestRequestPreview = reencryptionRequest?.plannedPreview || null
  const recoveryGuideAvailable = Boolean(reencryptionRequest && (reencryptionRequest.status === 'FAILED' || (reencryptionRequest.status === 'COMPLETED' && reencryptionRequest.verificationPassed === false)))
  const requestedTarget = reencryptionRequest?.requestedTarget || null
  const currentTarget = {
    mode: secretManagementStatus?.mode,
    providerId: secretManagementStatus?.providerId,
    activeKeyVersion: secretManagementStatus?.activeKeyVersion,
    activeKeyId: secretManagementStatus?.activeKeyId
  }
  const legacyKeyAvailabilityRequirement = Array.isArray(secretManagementStatus?.reencryptionRequirements)
    ? secretManagementStatus.reencryptionRequirements.find((requirement) => requirement?.requirementId === 'legacy-key-availability')
    : null

  async function handleConfirmReencrypt() {
    const result = await onReencryptStoredSecrets?.()
    if (result) {
      setReencryptionResult(result)
    }
  }

  async function handleApproveQueuedReencryption() {
    await onApproveSecretManagementReencryption?.()
  }

  async function handleOpenMigrationGuide(targetMode) {
    setMigrationGuide(null)
    setMigrationGuideLoading(true)
    setShowMigrationGuideDialog(true)
    const guide = await onLoadSecretManagementMigrationGuide?.(targetMode)
    setMigrationGuide(guide)
    setMigrationGuideLoading(false)
  }

  async function handleOpenRecoveryGuide() {
    setRecoveryGuide(null)
    setRecoveryGuideLoading(true)
    setShowRecoveryGuideDialog(true)
    const guide = await onLoadSecretManagementRecoveryGuide?.()
    setRecoveryGuide(guide)
    setRecoveryGuideLoading(false)
  }

  return (
    <>
      <CollapsibleSection
        className="system-dashboard-section"
        collapsed={collapsed}
        collapseLoading={collapseLoading}
        copy={t('authSecurity.secretManagementSectionCopy')}
        id="secret-management-section"
        onCollapseToggle={onCollapseToggle}
        sectionLoading={sectionLoading}
        t={t}
        title={t('authSecurity.secretManagementTitle')}
      >
        <div className="polling-statistics-grid">
          <article className="surface-card polling-statistics-card" id="secret-management-summary" tabIndex="-1">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementSummaryTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementCopy')}</p>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.secretManagementMode')}</span><strong>{secretManagementStatus?.mode || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementProvider')}</span><strong>{secretManagementStatus?.providerId || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementProtectedRecords')}</span><strong>{secretManagementStatus?.protectedRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementNonActiveRecords')}</span><strong>{secretManagementStatus?.nonActiveKeyRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementEnvPolicy')}</span><strong>{t(secretManagementStatus?.envManagedMailboxSecretsAllowed ? 'authSecurity.secretManagementEnvPolicyAllowed' : 'authSecurity.secretManagementEnvPolicyBlocked')}</strong></div>
              <div><span>{t('authSecurity.secretManagementEnvSourceCount')}</span><strong>{secretManagementStatus?.configuredEnvManagedSourceCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementEnvGoogleRefreshToken')}</span><strong>{t(secretManagementStatus?.envManagedGoogleRefreshTokenConfigured ? 'authSecurity.secretManagementEnvGoogleRefreshTokenConfigured' : 'authSecurity.secretManagementEnvGoogleRefreshTokenNotConfigured')}</strong></div>
              <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{formatKeyIds(secretManagementStatus?.configuredLegacyKeyIds, t)}</strong></div>
              <div><span>{t('authSecurity.secretManagementRetirementStatus')}</span><strong>{t(secretManagementStatus?.safeToRetireLegacyKeys ? 'authSecurity.secretManagementSafeToRetire' : 'authSecurity.secretManagementKeepLegacyKeys')}</strong></div>
            </div>
            <p className="section-copy">{t('authSecurity.secretManagementEnvPolicyHelp')}</p>
          </article>

          <article className="surface-card polling-statistics-card" id="secret-management-provider-diagnostics" tabIndex="-1">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementProviderDiagnosticsTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementProviderDiagnosticsCopy')}</p>
            {providerComponents.length === 0 ? (
              <p className="section-copy">{t('authSecurity.secretManagementProviderDiagnosticsEmpty')}</p>
            ) : (
              <div className="detail-stack">
                {providerComponents.map((component) => (
                  <div className="muted-box detail-stack" key={component.componentId}>
                    <div className="secret-management-provider-component-header">
                      <strong>{component.title}</strong>
                      <span className={`status-pill ${componentTone(component)}`}>
                        {component.writable
                          ? t('authSecurity.secretManagementProviderComponentWritable')
                          : component.healthy
                            ? t('authSecurity.secretManagementProviderComponentReadOnly')
                            : t('authSecurity.secretManagementProviderComponentUnavailable')}
                      </span>
                    </div>
                    <span>{component.detail}</span>
                    {Array.isArray(component.configReferences) && component.configReferences.length > 0 ? (
                      <div className="secret-management-config-list">
                        {component.configReferences.map((reference) => (
                          <code className="secret-management-config-chip" key={`${component.componentId}:${reference}`}>{reference}</code>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </article>

          <article className="surface-card polling-statistics-card" id="secret-management-mode-assessments" tabIndex="-1">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementModeAssessmentsTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementModeAssessmentsCopy')}</p>
            {modeAssessments.length === 0 ? (
              <p className="section-copy">{t('authSecurity.secretManagementProviderDiagnosticsEmpty')}</p>
            ) : (
              <div className="detail-stack">
                {modeAssessments.map((assessment) => {
                  const tone = assessment.current
                    ? 'status-ok'
                    : assessment.writable
                      ? 'tone-warn'
                      : 'tone-bad'
                  const statusLabel = assessment.current
                    ? t('authSecurity.secretManagementModeAssessmentCurrent')
                    : assessment.writable
                      ? t('authSecurity.secretManagementModeAssessmentReady')
                      : t('authSecurity.secretManagementModeAssessmentNeedsAttention')
                  return (
                    <div className="muted-box detail-stack" key={assessment.mode}>
                      <div className="secret-management-provider-component-header">
                        <strong>{modeAssessmentLabel(assessment.mode, t)}</strong>
                        <span className={`status-pill ${tone}`}>{statusLabel}</span>
                      </div>
                      <span>{assessment.statusMessage}</span>
                      <div className="polling-statistics-breakdown">
                        <div><span>{t('authSecurity.secretManagementProvider')}</span><strong>{assessment.providerId || t('common.unavailable')}</strong></div>
                        <div><span>{t('authSecurity.secretManagementModeAssessmentTarget')}</span><strong>{assessment.activeKeyId || assessment.activeKeyVersion || t('common.unavailable')}</strong></div>
                      </div>
                      {Array.isArray(assessment.configReferences) && assessment.configReferences.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementConfigTitle')}</strong>
                          <div className="secret-management-config-list">
                            {assessment.configReferences.map((reference) => (
                              <code className="secret-management-config-chip" key={`${assessment.mode}:${reference}`}>{reference}</code>
                            ))}
                          </div>
                        </div>
                      ) : null}
                      {Array.isArray(assessment.remediationSteps) && assessment.remediationSteps.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementStepsTitle')}</strong>
                          <ul className="detail-stack secret-reencryption-detail-list">
                            {assessment.remediationSteps.map((step) => <li key={`${assessment.mode}:${step}`}>{step}</li>)}
                          </ul>
                        </div>
                      ) : null}
                      <div>
                        <button className="secondary" onClick={() => handleOpenMigrationGuide(assessment.mode)} type="button">
                          {t('authSecurity.secretManagementMigrationGuideAction')}
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </article>

          <article className="surface-card polling-statistics-card" id="secret-management-rotation-plan" tabIndex="-1">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementRotationPlanTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementRotationPlanCopy')}</p>
            {rotationPlan ? (
              <div className="detail-stack">
                <div className="secret-management-provider-component-header">
                  <strong>{rotationPlan.title}</strong>
                  <span className={`status-pill ${rotationPlan.rotationNeeded ? 'tone-warn' : 'status-ok'}`}>
                    {rotationPlan.rotationNeeded
                      ? t('authSecurity.secretManagementRotationPlanPending')
                      : t('authSecurity.secretManagementRotationPlanClear')}
                  </span>
                </div>
                <div className="muted-box detail-stack">
                  <span>{rotationPlan.summary}</span>
                  <div className="polling-statistics-breakdown">
                    <div><span>{t('authSecurity.secretManagementRotationTarget')}</span><strong>{rotationPlan.targetKeyVersion || t('common.unavailable')}</strong></div>
                    <div><span>{t('authSecurity.secretManagementRotationAffectedRecords')}</span><strong>{rotationPlan.affectedRecordCount ?? 0}</strong></div>
                    <div><span>{t('authSecurity.secretManagementRotationUnavailableRecords')}</span><strong>{rotationPlan.unavailableRecordCount ?? 0}</strong></div>
                    <div><span>{t('authSecurity.secretManagementRotationMethod')}</span><strong>{rotationMethodLabel(rotationPlan, t)}</strong></div>
                  </div>
                  <strong>{t('authSecurity.secretManagementRotationRecommendedAction')}</strong>
                  <span>{rotationPlan.recommendedAction}</span>
                  {Array.isArray(rotationPlan.impactedAreas) && rotationPlan.impactedAreas.length > 0 ? (
                    <div className="secret-management-config-list">
                      {rotationPlan.impactedAreas.map((area) => (
                        <span className="status-pill tone-neutral" key={area}>
                          {formatKeyUsageAreas(area, t) || area}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            ) : (
              <p className="section-copy">{t('authSecurity.secretManagementRotationPlanEmpty')}</p>
            )}
          </article>

          <article className="surface-card polling-statistics-card" id="secret-management-key-usage" tabIndex="-1">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementKeyUsageTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementKeyUsageCopy')}</p>
            {keyUsage.length === 0 ? (
              <p className="section-copy">{t('authSecurity.secretManagementKeyUsageEmpty')}</p>
            ) : (
              <div className="polling-statistics-breakdown">
                {keyUsage.map((usage) => (
                  <div key={usage.keyVersion}>
                    <span>
                      {usage.keyVersion}
                      {' · '}
                      {formatKeyUsageAreas(usage.areas, t)}
                    </span>
                    <strong>
                      {t(usage.availableForDecryption ? 'authSecurity.secretManagementKeyAvailable' : 'authSecurity.secretManagementKeyUnavailable')}
                      {' · '}
                      {t('authSecurity.secretManagementKeyRecordCount', { count: usage.recordCount })}
                    </strong>
                  </div>
                ))}
              </div>
            )}
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementActionsTitle')}</div>
            <div className="detail-stack">
              <p className="section-copy">{t('authSecurity.secretManagementReencryptHelp')}</p>
              <Banner tone="warning">
                <strong>{t('authSecurity.secretManagementReencryptWarning')}</strong>
              </Banner>
              {reencryptionRequest ? (
                <Banner tone={blockedReencryption ? 'warning' : pendingReencryption ? (approvalReady ? 'warning' : 'info') : reencryptionRequest?.verificationPassed ? 'success' : 'warning'}>
                  <div className="detail-stack">
                    <strong>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</strong>
                    <span>{t('authSecurity.secretManagementReencryptLatestRequestStatus', { status: reencryptionRequest.status })}</span>
                    {reencryptionRequest.executeAfter ? <span>{t('authSecurity.secretManagementReencryptLatestRequestExecuteAfter', { value: reencryptionRequest.executeAfter })}</span> : null}
                    {reencryptionRequest.message ? <span>{reencryptionRequest.message}</span> : null}
                    {requestedTarget ? (
                      <div className="polling-statistics-breakdown">
                        <div><span>{t('authSecurity.secretManagementQueuedTarget')}</span><strong>{preferredTargetLabel(requestedTarget, t('common.unavailable'))}</strong></div>
                        <div><span>{t('authSecurity.secretManagementCurrentTarget')}</span><strong>{preferredTargetLabel(currentTarget, t('common.unavailable'))}</strong></div>
                      </div>
                    ) : null}
                    {blockedReencryption ? (
                      <div className="detail-stack">
                        <strong>{t('authSecurity.secretManagementQueuedTargetDriftTitle')}</strong>
                        <span>{t('authSecurity.secretManagementQueuedTargetDriftBody')}</span>
                        {legacyKeyAvailabilityRequirement && !legacyKeyAvailabilityRequirement.satisfied ? (
                          <span>{t('authSecurity.secretManagementQueuedTargetDriftLegacyKeyWarning')}</span>
                        ) : null}
                      </div>
                    ) : null}
                    {reencryptionRequest.approvedAt ? (
                      <span>{t('authSecurity.secretManagementReencryptLatestRequestApprovedBy', { user: reencryptionRequest.approvedByUsername || t('common.unavailable'), value: reencryptionRequest.approvedAt })}</span>
                    ) : null}
                    {pendingReencryption && latestRequestPreview ? (
                      <div className="polling-statistics-breakdown">
                        <div><span>{t('authSecurity.secretManagementReencryptPreviewRecords')}</span><strong>{latestRequestPreview.totalRecordsPendingUpdate ?? 0}</strong></div>
                        <div><span>{t('authSecurity.secretManagementReencryptPreviewSecrets')}</span><strong>{latestRequestPreview.totalSecretValuesPendingRewrite ?? 0}</strong></div>
                      </div>
                    ) : null}
                    {!pendingReencryption ? (
                      <div className="polling-statistics-breakdown">
                        <div><span>{t('authSecurity.secretManagementExecutionMethodFull')}</span><strong>{reencryptionRequest.totalFullReencryptionCount ?? 0}</strong></div>
                        <div><span>{t('authSecurity.secretManagementExecutionMethodRewrap')}</span><strong>{reencryptionRequest.totalMetadataRewrapCount ?? 0}</strong></div>
                      </div>
                    ) : null}
                    {recoveryGuideAvailable ? (
                      <div>
                        <button className="secondary" onClick={() => handleOpenRecoveryGuide()} type="button">
                          {t('authSecurity.secretManagementRecoveryGuideAction')}
                        </button>
                      </div>
                    ) : null}
                    {canApproveDirectly ? (
                      <div>
                        <LoadingButton
                          className="secondary"
                          isLoading={reencryptionApprovalLoading}
                          loadingLabel={t('authSecurity.secretManagementReencryptApproveLoading')}
                          onClick={handleApproveQueuedReencryption}
                          type="button"
                        >
                          {t('authSecurity.secretManagementReencryptApprove')}
                        </LoadingButton>
                      </div>
                    ) : null}
                  </div>
                </Banner>
              ) : null}
              <p className="section-copy">{t('authSecurity.secretManagementReencryptActionCopy')}</p>
            </div>
            <div className="button-row">
              <LoadingButton
                className="secondary"
                onClick={() => setShowRetirementDialog(true)}
                type="button"
              >
                {t('authSecurity.secretManagementRetirementReview')}
              </LoadingButton>
              <LoadingButton
                className="secondary"
                onClick={() => onExportSecretManagementReport?.()}
                type="button"
              >
                {exportReportLoading ? t('authSecurity.secretManagementExportReportLoading') : t('authSecurity.secretManagementExportReport')}
              </LoadingButton>
              <LoadingButton
                className="secondary"
                disabled={!secretManagementStatus?.secureStorageConfigured}
                onClick={() => {
                  setReencryptionResult(null)
                  setShowReencryptDialog(true)
                }}
                type="button"
              >
                {reencryptionLoading ? t('authSecurity.secretManagementReencryptLoading') : t('authSecurity.secretManagementReencrypt')}
              </LoadingButton>
            </div>
          </article>
        </div>
      </CollapsibleSection>

      {showReencryptDialog ? (
        <SecretReencryptionDialog
          locale={locale}
          onClose={() => {
            if (!reencryptionLoading) {
              setReencryptionResult(null)
              setShowReencryptDialog(false)
            }
          }}
          onConfirm={handleConfirmReencrypt}
          onApproveQueuedReencryption={handleApproveQueuedReencryption}
          onOptionsChange={onSecretReencryptOptionsChange}
          onVerifyPasskey={onVerifySecretManagementPasskey}
          onVerifyPassword={onVerifySecretManagementPassword}
          approvalPending={reencryptionApprovalLoading}
          pending={reencryptionLoading}
          reauthPasskeyLoading={reauthPasskeyLoading}
          reauthPasswordLoading={reauthPasswordLoading}
          reencryptionResult={reencryptionResult}
          session={session}
          secretManagementStatus={secretManagementStatus}
          secretReencryptOptions={secretReencryptOptions}
          t={t}
        />
      ) : null}
      {showMigrationGuideDialog ? (
        <SecretMigrationGuideDialog
          guide={migrationGuide}
          loading={migrationGuideLoading}
          onClose={() => {
            setShowMigrationGuideDialog(false)
            setMigrationGuideLoading(false)
            setMigrationGuide(null)
          }}
          t={t}
        />
      ) : null}
      {showRecoveryGuideDialog ? (
        <SecretRecoveryGuideDialog
          guide={recoveryGuide}
          loading={recoveryGuideLoading}
          onRecordRecoveryReview={onRecordSecretManagementRecoveryReview}
          onClose={() => {
            setShowRecoveryGuideDialog(false)
            setRecoveryGuideLoading(false)
            setRecoveryGuide(null)
          }}
          pending={recoveryReviewLoading}
          secretManagementStatus={secretManagementStatus}
          t={t}
        />
      ) : null}
      {showRetirementDialog ? (
        <SecretRetirementDialog
          onClose={() => setShowRetirementDialog(false)}
          onRecordRetirementReview={onRecordSecretManagementRetirementReview}
          onVerifyRetirementCompletion={onVerifySecretManagementRetirementCompletion}
          completionPending={retirementCompletionLoading}
          pending={retirementReviewLoading}
          secretManagementStatus={secretManagementStatus}
          t={t}
        />
      ) : null}
    </>
  )
}

export default SecretManagementSection
