import { useState } from 'react'
import LoadingButton from '@/shared/components/LoadingButton'
import Banner from '@/shared/components/Banner'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import SecretReencryptionDialog from './SecretReencryptionDialog'
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

function SecretManagementSection({
  collapsed,
  collapseLoading,
  locale = 'en',
  onCollapseToggle,
  onReencryptStoredSecrets,
  onVerifySecretManagementPasskey,
  onVerifySecretManagementPassword,
  onSecretReencryptOptionsChange,
  reauthPasskeyLoading = false,
  reauthPasswordLoading = false,
  reencryptionLoading = false,
  sectionLoading = false,
  session,
  secretManagementStatus,
  secretReencryptOptions,
  t
}) {
  const [showReencryptDialog, setShowReencryptDialog] = useState(false)
  const [reencryptionResult, setReencryptionResult] = useState(null)
  const keyUsage = Array.isArray(secretManagementStatus?.keyUsage) ? secretManagementStatus.keyUsage : []
  const providerComponents = Array.isArray(secretManagementStatus?.providerComponents) ? secretManagementStatus.providerComponents : []
  const rotationPlan = secretManagementStatus?.rotationPlan || null
  const reencryptionRequest = secretManagementStatus?.reencryptionRequest
  const pendingReencryption = reencryptionRequest?.status === 'PENDING'

  async function handleConfirmReencrypt() {
    const result = await onReencryptStoredSecrets?.()
    if (result) {
      setReencryptionResult(result)
    }
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
                    <div><span>{t('authSecurity.secretManagementRotationMethod')}</span><strong>{rotationPlan.requiresFullReencryption ? t('authSecurity.secretManagementRotationMethodFull') : t('authSecurity.secretManagementRotationMethodNone')}</strong></div>
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
                <Banner tone={pendingReencryption ? 'info' : reencryptionRequest?.verificationPassed ? 'success' : 'warning'}>
                  <div className="detail-stack">
                    <strong>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</strong>
                    <span>{t('authSecurity.secretManagementReencryptLatestRequestStatus', { status: reencryptionRequest.status })}</span>
                    {reencryptionRequest.executeAfter ? <span>{t('authSecurity.secretManagementReencryptLatestRequestExecuteAfter', { value: reencryptionRequest.executeAfter })}</span> : null}
                    {reencryptionRequest.message ? <span>{reencryptionRequest.message}</span> : null}
                  </div>
                </Banner>
              ) : null}
              <p className="section-copy">{t('authSecurity.secretManagementReencryptActionCopy')}</p>
            </div>
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
          onOptionsChange={onSecretReencryptOptionsChange}
          onVerifyPasskey={onVerifySecretManagementPasskey}
          onVerifyPassword={onVerifySecretManagementPassword}
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
    </>
  )
}

export default SecretManagementSection
