import { useEffect, useMemo, useState } from 'react'
import Banner from '@/shared/components/Banner'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import PasswordField from '@/shared/components/PasswordField'
import './SecretReencryptionDialog.css'

function formatAreaLabel(area, t) {
  const translationKey = `authSecurity.secretArea.${area}`
  const translated = t(translationKey)
  return translated === translationKey ? area : translated
}

function RequirementStatusIcon({ satisfied, t }) {
  const label = satisfied
    ? t('authSecurity.secretManagementRequirementSatisfied')
    : t('authSecurity.secretManagementRequirementNotSatisfied')
  const stroke = satisfied ? 'var(--accent)' : 'var(--danger)'

  return (
    <span
      aria-label={label}
      className={`secret-reencryption-requirement-status-icon ${satisfied ? 'is-satisfied' : 'is-unsatisfied'}`}
      role="img"
      title={label}
    >
      <svg aria-hidden="true" fill="none" height="22" viewBox="0 0 24 24" width="22">
        <circle cx="12" cy="12" fill="transparent" r="9" stroke={stroke} strokeWidth="1.8" />
        {satisfied ? (
          <path d="M7.5 12.4 10.4 15.2 16.7 8.9" stroke={stroke} strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" />
        ) : (
          <>
            <path d="M8.6 8.6 15.4 15.4" stroke={stroke} strokeLinecap="round" strokeWidth="2.2" />
            <path d="M15.4 8.6 8.6 15.4" stroke={stroke} strokeLinecap="round" strokeWidth="2.2" />
          </>
        )}
      </svg>
    </span>
  )
}

function SecretReencryptionDialog({
  onClose,
  onConfirm,
  onOptionsChange,
  onVerifyPasskey,
  onVerifyPassword,
  pending = false,
  reauthPasskeyLoading = false,
  reauthPasswordLoading = false,
  reencryptionResult = null,
  session,
  secretManagementStatus,
  secretReencryptOptions,
  t
}) {
  const [acknowledgements, setAcknowledgements] = useState({
    verifiedNewKey: false,
    preservedLegacyKeys: false,
    understoodRisk: false
  })
  const [password, setPassword] = useState('')

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
  const persistedRequest = secretManagementStatus?.reencryptionRequest || null
  const latestVerification = reencryptionResult?.verification || persistedRequest?.verification || null
  const verificationMessages = Array.isArray(latestVerification?.messages) ? latestVerification.messages : []
  const operatorSaveItems = Array.isArray(latestVerification?.operatorSaveItems) ? latestVerification.operatorSaveItems : []
  const totalFullReencryptionCount = reencryptionResult?.totalFullReencryptionCount ?? persistedRequest?.totalFullReencryptionCount ?? 0
  const totalMetadataRewrapCount = reencryptionResult?.totalMetadataRewrapCount ?? persistedRequest?.totalMetadataRewrapCount ?? 0
  const preview = secretManagementStatus?.reencryptionPreview || null
  const previewAreas = Array.isArray(preview?.areas) ? preview.areas.filter((area) => (area?.recordsUpdated ?? 0) > 0) : []
  const latestRequestPreview = reencryptionResult ? preview : (persistedRequest?.plannedPreview || null)
  const latestPreviewAreas = Array.isArray(latestRequestPreview?.areas)
    ? latestRequestPreview.areas.filter((area) => (area?.recordsUpdated ?? 0) > 0)
    : []
  const latestOperationStatus = reencryptionResult?.operationStatus || persistedRequest?.status || null
  const latestRequestMessage = reencryptionResult?.message || persistedRequest?.message || null
  const latestExecuteAfter = reencryptionResult?.executeAfter || persistedRequest?.executeAfter || null
  const requestSubmitted = Boolean(latestOperationStatus)
  const requestScheduled = latestOperationStatus === 'SCHEDULED' || latestOperationStatus === 'PENDING'
  const requestCompleted = latestOperationStatus === 'COMPLETED'
  const requiresReauthentication = Boolean(secretManagementStatus?.reauthenticationRequired)
  const reauthenticationSatisfied = Boolean(secretManagementStatus?.reauthenticationSatisfied)
  const [expandedRequirementIds, setExpandedRequirementIds] = useState(() => new Set(
    requirements
      .filter((requirement) => !requirement?.satisfied)
      .map((requirement) => requirement.requirementId)
  ))
  const confirmDisabled = !allAcknowledged || !blockingRequirementsMet || pendingRequest || requestSubmitted || (requiresReauthentication && !reauthenticationSatisfied)

  useEffect(() => {
    setExpandedRequirementIds((current) => {
      const next = new Set(current)
      requirements
        .filter((requirement) => !requirement?.satisfied)
        .forEach((requirement) => next.add(requirement.requirementId))
      return next
    })
  }, [requirements])

  function updateAcknowledgement(key, checked) {
    setAcknowledgements((current) => ({ ...current, [key]: checked }))
  }

  function toggleRequirement(requirementId) {
    setExpandedRequirementIds((current) => {
      const next = new Set(current)
      if (next.has(requirementId)) {
        next.delete(requirementId)
      } else {
        next.add(requirementId)
      }
      return next
    })
  }

  function focusTarget(targetId) {
    if (!targetId || typeof document === 'undefined') {
      return
    }
    const target = document.getElementById(targetId)
    if (!target) {
      return
    }
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    if (typeof target.focus === 'function') {
      target.focus({ preventScroll: true })
    }
  }

  return (
    <ModalDialog closeDisabled={pending} onClose={onClose} size="wide" title={t('authSecurity.secretManagementReencryptDialogTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{t('authSecurity.secretManagementReencryptDialogIntro')}</p>

        <Banner tone="warning">
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementReencryptDialogRiskTitle')}</strong>
            <span>{t('authSecurity.secretManagementReencryptDialogRiskBody')}</span>
          </div>
        </Banner>

        <div className="muted-box detail-stack" id="secret-reencryption-key-status" tabIndex="-1">
          <strong>{t('authSecurity.secretManagementReencryptDialogStatusTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{Array.isArray(secretManagementStatus?.configuredLegacyKeyIds) && secretManagementStatus.configuredLegacyKeyIds.length > 0 ? secretManagementStatus.configuredLegacyKeyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')}</strong></div>
            <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
          </div>
        </div>

        {preview ? (
          <div className="muted-box detail-stack">
            <strong>{t('authSecurity.secretManagementReencryptPreviewTitle')}</strong>
            <span>{t('authSecurity.secretManagementReencryptPreviewCopy')}</span>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.secretManagementRotationTarget')}</span><strong>{preview.activeKeyVersion || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementReencryptPreviewRecords')}</span><strong>{preview.totalRecordsPendingUpdate ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementReencryptPreviewSecrets')}</span><strong>{preview.totalSecretValuesPendingRewrite ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementExecutionMethodFull')}</span><strong>{preview.totalFullReencryptionCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementExecutionMethodRewrap')}</span><strong>{preview.totalMetadataRewrapCount ?? 0}</strong></div>
            </div>
            {previewAreas.length > 0 ? (
              <div className="polling-statistics-breakdown">
                {previewAreas.map((area) => (
                  <div key={area.area}>
                    <span>{formatAreaLabel(area.area, t)}</span>
                    <strong>
                      {t('authSecurity.secretManagementReencryptPreviewAreaSummary', {
                        records: area.recordsUpdated,
                        secrets: area.secretValuesReencrypted
                      })}
                    </strong>
                  </div>
                ))}
              </div>
            ) : (
              <span>{t('authSecurity.secretManagementReencryptPreviewEmpty')}</span>
            )}
          </div>
        ) : null}

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogStepsTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            <li>{t('authSecurity.secretManagementReencryptDialogStep1')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep2')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep3')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep4')}</li>
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogRequirementsTitle')}</strong>
          <div className="secret-reencryption-requirements-grid">
            {requirements.map((requirement) => {
              const expanded = expandedRequirementIds.has(requirement.requirementId)
              const remediationSteps = Array.isArray(requirement.remediationSteps) ? requirement.remediationSteps : []
              const configReferences = Array.isArray(requirement.configReferences) ? requirement.configReferences : []

              return (
                <article
                  className={`muted-box secret-reencryption-requirement-card ${expanded ? 'expanded' : ''}`}
                  key={requirement.requirementId}
                >
                  <button
                    aria-expanded={expanded}
                    className="secret-reencryption-requirement-toggle"
                    onClick={() => toggleRequirement(requirement.requirementId)}
                    type="button"
                  >
                    <div className="secret-reencryption-requirement-copy">
                      <strong>{requirement.title}</strong>
                      <span>{requirement.detail}</span>
                    </div>
                    <div className="secret-reencryption-requirement-meta">
                      <RequirementStatusIcon satisfied={requirement.satisfied} t={t} />
                      <span className={`status-pill ${requirement.satisfied ? 'status-ok' : 'tone-bad'}`}>
                        {requirement.satisfied ? t('authSecurity.secretManagementRequirementSatisfied') : t('authSecurity.secretManagementRequirementNotSatisfied')}
                      </span>
                    </div>
                  </button>

                  {expanded ? (
                    <div className="secret-reencryption-requirement-body detail-stack">
                      {remediationSteps.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementStepsTitle')}</strong>
                          <ul className="detail-stack secret-reencryption-detail-list">
                            {remediationSteps.map((step) => <li key={step}>{step}</li>)}
                          </ul>
                        </div>
                      ) : null}
                      {configReferences.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementConfigTitle')}</strong>
                          <div className="secret-reencryption-config-list">
                            {configReferences.map((reference) => (
                              <code className="secret-reencryption-config-chip" key={reference}>{reference}</code>
                            ))}
                          </div>
                        </div>
                      ) : null}
                      {requirement.actionTargetId && requirement.actionLabel ? (
                        <div className="secret-reencryption-requirement-actions">
                          <button
                            className="secondary"
                            onClick={() => focusTarget(requirement.actionTargetId)}
                            type="button"
                          >
                            {requirement.actionLabel}
                          </button>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </article>
              )
            })}
          </div>
        </div>

        {pendingRequest ? (
          <Banner tone="info">
            <div className="detail-stack">
              <strong id="secret-reencryption-pending-request" tabIndex="-1">{t('authSecurity.secretManagementReencryptPendingTitle')}</strong>
              <span>{t('authSecurity.secretManagementReencryptPendingBody', { executeAfter: secretManagementStatus?.reencryptionRequest?.executeAfter || '' })}</span>
              {latestRequestPreview ? (
                <div className="polling-statistics-breakdown">
                  <div><span>{t('authSecurity.secretManagementReencryptPreviewRecords')}</span><strong>{latestRequestPreview.totalRecordsPendingUpdate ?? 0}</strong></div>
                  <div><span>{t('authSecurity.secretManagementReencryptPreviewSecrets')}</span><strong>{latestRequestPreview.totalSecretValuesPendingRewrite ?? 0}</strong></div>
                </div>
              ) : null}
            </div>
          </Banner>
        ) : null}

        {requestSubmitted ? (
          <Banner tone={requestScheduled ? 'info' : latestVerification?.passed === false ? 'warning' : 'success'}>
            <div className="detail-stack">
              <strong>{latestRequestMessage || t('authSecurity.secretManagementReencryptLatestRequestTitle')}</strong>
              {latestExecuteAfter ? (
                <span>{t('authSecurity.secretManagementReencryptLatestRequestExecuteAfter', { value: latestExecuteAfter })}</span>
              ) : null}
              {requestScheduled && latestRequestPreview ? (
                <div className="detail-stack">
                  <strong>{t('authSecurity.secretManagementReencryptQueuedPreviewTitle')}</strong>
                  <div className="polling-statistics-breakdown">
                    <div><span>{t('authSecurity.secretManagementReencryptPreviewRecords')}</span><strong>{latestRequestPreview.totalRecordsPendingUpdate ?? 0}</strong></div>
                    <div><span>{t('authSecurity.secretManagementReencryptPreviewSecrets')}</span><strong>{latestRequestPreview.totalSecretValuesPendingRewrite ?? 0}</strong></div>
                    <div><span>{t('authSecurity.secretManagementExecutionMethodFull')}</span><strong>{latestRequestPreview.totalFullReencryptionCount ?? 0}</strong></div>
                    <div><span>{t('authSecurity.secretManagementExecutionMethodRewrap')}</span><strong>{latestRequestPreview.totalMetadataRewrapCount ?? 0}</strong></div>
                  </div>
                  {latestPreviewAreas.length > 0 ? (
                    <div className="polling-statistics-breakdown">
                      {latestPreviewAreas.map((area) => (
                        <div key={`queued-${area.area}`}>
                          <span>{formatAreaLabel(area.area, t)}</span>
                          <strong>{t('authSecurity.secretManagementReencryptPreviewAreaSummary', { records: area.recordsUpdated, secrets: area.secretValuesReencrypted })}</strong>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
              ) : null}
              {requestCompleted && verificationMessages.length > 0 ? (
                <div className="detail-stack">
                  {verificationMessages.map((message) => <span key={message}>{message}</span>)}
                </div>
              ) : null}
              {requestCompleted ? (
                <div className="polling-statistics-breakdown">
                  <div><span>{t('authSecurity.secretManagementExecutionMethodFull')}</span><strong>{totalFullReencryptionCount}</strong></div>
                  <div><span>{t('authSecurity.secretManagementExecutionMethodRewrap')}</span><strong>{totalMetadataRewrapCount}</strong></div>
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

        {requiresReauthentication ? (
          <div className="detail-stack" id="secret-reencryption-reauthentication" tabIndex="-1">
            <strong>{t('authSecurity.secretManagementReauthenticationTitle')}</strong>
            <Banner tone={reauthenticationSatisfied ? 'success' : 'warning'}>
              <div className="detail-stack">
                <span>
                  {reauthenticationSatisfied
                    ? t('authSecurity.secretManagementReauthenticationSatisfied', {
                        expiresAt: secretManagementStatus?.reauthenticationExpiresAt || ''
                      })
                    : t('authSecurity.secretManagementReauthenticationRequired')}
                </span>
              </div>
            </Banner>
            {!reauthenticationSatisfied ? (
              <div className="detail-stack">
                {session?.hasPassword ? (
                  <div className="detail-stack">
                    <PasswordField
                      autoComplete="current-password"
                      label={t('authSecurity.secretManagementReauthenticationPasswordLabel')}
                      onChange={(event) => setPassword(event.target.value)}
                      value={password}
                    />
                    <LoadingButton
                      className="secondary"
                      disabled={!password.trim()}
                      isLoading={reauthPasswordLoading}
                      loadingLabel={t('authSecurity.secretManagementReauthenticationVerifying')}
                      onClick={async () => {
                        const result = await onVerifyPassword?.(password)
                        if (result) {
                          setPassword('')
                        }
                      }}
                      type="button"
                    >
                      {t('authSecurity.secretManagementReauthenticationVerifyPassword')}
                    </LoadingButton>
                  </div>
                ) : null}
                {session?.passkeyCount > 0 ? (
                  <LoadingButton
                    className="secondary"
                    isLoading={reauthPasskeyLoading}
                    loadingLabel={t('authSecurity.secretManagementReauthenticationVerifying')}
                    onClick={onVerifyPasskey}
                    type="button"
                  >
                    {t('authSecurity.secretManagementReauthenticationVerifyPasskey')}
                  </LoadingButton>
                ) : null}
              </div>
            ) : null}
          </div>
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
