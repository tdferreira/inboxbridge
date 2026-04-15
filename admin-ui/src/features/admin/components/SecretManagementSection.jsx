import { useState } from 'react'
import LoadingButton from '@/shared/components/LoadingButton'
import Banner from '@/shared/components/Banner'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import SecretReencryptionDialog from './SecretReencryptionDialog'

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
