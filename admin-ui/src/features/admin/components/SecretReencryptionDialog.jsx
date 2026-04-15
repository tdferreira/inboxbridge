import { useMemo, useState } from 'react'
import Banner from '@/shared/components/Banner'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'

function SecretReencryptionDialog({
  onClose,
  onConfirm,
  onOptionsChange,
  pending = false,
  reencryptionResult = null,
  secretManagementStatus,
  secretReencryptOptions,
  t
}) {
  const [acknowledgements, setAcknowledgements] = useState({
    verifiedNewKey: false,
    preservedLegacyKeys: false,
    understoodRisk: false
  })

  const allAcknowledged = useMemo(
    () => Object.values(acknowledgements).every(Boolean),
    [acknowledgements]
  )
  const requirements = Array.isArray(secretManagementStatus?.reencryptionRequirements)
    ? secretManagementStatus.reencryptionRequirements
    : []
  const blockingRequirementsMet = requirements
    .filter((requirement) => requirement?.blocking)
    .every((requirement) => requirement?.satisfied)
  const pendingRequest = secretManagementStatus?.reencryptionRequest?.status === 'PENDING'
  const allowImmediateOverride = Boolean(secretManagementStatus?.immediateReencryptionOverrideAllowed)
  const verification = reencryptionResult?.verification || null
  const verificationMessages = Array.isArray(verification?.messages) ? verification.messages : []
  const operatorSaveItems = Array.isArray(verification?.operatorSaveItems) ? verification.operatorSaveItems : []
  const requestSubmitted = Boolean(reencryptionResult?.operationStatus)
  const requestScheduled = reencryptionResult?.operationStatus === 'SCHEDULED'
  const requestCompleted = reencryptionResult?.operationStatus === 'COMPLETED'
  const confirmDisabled = !allAcknowledged || !blockingRequirementsMet || pendingRequest || requestSubmitted

  function updateAcknowledgement(key, checked) {
    setAcknowledgements((current) => ({ ...current, [key]: checked }))
  }

  return (
    <ModalDialog closeDisabled={pending} onClose={onClose} title={t('authSecurity.secretManagementReencryptDialogTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{t('authSecurity.secretManagementReencryptDialogIntro')}</p>

        <Banner tone="warning">
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementReencryptDialogRiskTitle')}</strong>
            <span>{t('authSecurity.secretManagementReencryptDialogRiskBody')}</span>
          </div>
        </Banner>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogStatusTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{Array.isArray(secretManagementStatus?.configuredLegacyKeyIds) && secretManagementStatus.configuredLegacyKeyIds.length > 0 ? secretManagementStatus.configuredLegacyKeyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')}</strong></div>
            <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
          </div>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogStepsTitle')}</strong>
          <ol className="detail-stack">
            <li>{t('authSecurity.secretManagementReencryptDialogStep1')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep2')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep3')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep4')}</li>
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogRequirementsTitle')}</strong>
          <div className="detail-stack">
            {requirements.map((requirement) => (
              <div className="muted-box detail-stack" key={requirement.requirementId}>
                <strong>{requirement.title}</strong>
                <span>{requirement.detail}</span>
                <span>{requirement.satisfied ? t('authSecurity.secretManagementRequirementSatisfied') : t('authSecurity.secretManagementRequirementNotSatisfied')}</span>
              </div>
            ))}
          </div>
        </div>

        {pendingRequest ? (
          <Banner tone="info">
            <div className="detail-stack">
              <strong>{t('authSecurity.secretManagementReencryptPendingTitle')}</strong>
              <span>{t('authSecurity.secretManagementReencryptPendingBody', { executeAfter: secretManagementStatus?.reencryptionRequest?.executeAfter || '' })}</span>
            </div>
          </Banner>
        ) : null}

        {requestSubmitted ? (
          <Banner tone={requestScheduled ? 'info' : verification?.passed === false ? 'warning' : 'success'}>
            <div className="detail-stack">
              <strong>{reencryptionResult?.message || t('authSecurity.secretManagementReencryptLatestRequestTitle')}</strong>
              {reencryptionResult?.executeAfter ? (
                <span>{t('authSecurity.secretManagementReencryptLatestRequestExecuteAfter', { value: reencryptionResult.executeAfter })}</span>
              ) : null}
              {requestCompleted && verificationMessages.length > 0 ? (
                <div className="detail-stack">
                  {verificationMessages.map((message) => <span key={message}>{message}</span>)}
                </div>
              ) : null}
              {requestCompleted && operatorSaveItems.length > 0 ? (
                <div className="detail-stack">
                  <strong>{t('authSecurity.secretManagementReencryptSaveItemsTitle')}</strong>
                  <ul className="detail-stack">
                    {operatorSaveItems.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                </div>
              ) : null}
            </div>
          </Banner>
        ) : null}

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogFollowUpTitle')}</strong>
          <div className="polling-statistics-breakdown">
            {allowImmediateOverride ? (
              <label className="checkbox-row">
                <input
                  checked={Boolean(secretReencryptOptions?.immediateExecutionOverride)}
                  onChange={(event) => onOptionsChange?.((current) => ({
                    ...current,
                    immediateExecutionOverride: event.target.checked
                  }))}
                  type="checkbox"
                />
                <span>{t('authSecurity.secretManagementReencryptImmediateOverride')}</span>
              </label>
            ) : null}
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.revokeBrowserExtensionSessions)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  revokeBrowserExtensionSessions: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptRevokeExtensionSessions')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.revokeRemoteSessions)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  revokeRemoteSessions: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptRevokeRemoteSessions')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.clearCachedOAuthAccessTokens)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  clearCachedOAuthAccessTokens: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptClearCachedOAuthAccessTokens')}</span>
            </label>
          </div>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogChecklistTitle')}</strong>
          <div className="detail-stack">
            <label className="checkbox-row">
              <input
                checked={acknowledgements.verifiedNewKey}
                onChange={(event) => updateAcknowledgement('verifiedNewKey', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeNewKey')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={acknowledgements.preservedLegacyKeys}
                onChange={(event) => updateAcknowledgement('preservedLegacyKeys', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeLegacyKeys')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={acknowledgements.understoodRisk}
                onChange={(event) => updateAcknowledgement('understoodRisk', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeRisk')}</span>
            </label>
          </div>
        </div>

        <div className="action-row">
          <button className="secondary" disabled={pending} onClick={onClose} type="button">
            {t('common.cancel')}
          </button>
          <LoadingButton
            className="danger"
            disabled={confirmDisabled}
            isLoading={pending}
            loadingLabel={t('authSecurity.secretManagementReencryptLoading')}
            onClick={onConfirm}
            type="button"
          >
            {requestSubmitted ? t('common.done') : t('authSecurity.secretManagementReencrypt')}
          </LoadingButton>
        </div>
      </div>
    </ModalDialog>
  )
}

export default SecretReencryptionDialog
