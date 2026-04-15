import { useEffect, useState } from 'react'
import Banner from '@/shared/components/Banner'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import './SecretReencryptionDialog.css'

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

function SecretRetirementDialog({
  onClose,
  onRecordRetirementReview,
  onVerifyRetirementCompletion,
  completionPending = false,
  pending = false,
  secretManagementStatus,
  t
}) {
  const requirements = Array.isArray(secretManagementStatus?.retirementRequirements)
    ? secretManagementStatus.retirementRequirements
    : []
  const [expandedRequirementIds, setExpandedRequirementIds] = useState(() => new Set(
    requirements
      .filter((requirement) => !requirement?.satisfied)
      .map((requirement) => requirement.requirementId)
  ))
  const requestState = secretManagementStatus?.reencryptionRequest || null
  const latestRequestStatus = requestState?.status || null
  const latestRetirementReview = secretManagementStatus?.latestRetirementReview || null
  const recentRetirementReviews = Array.isArray(secretManagementStatus?.recentRetirementReviews)
    ? secretManagementStatus.recentRetirementReviews
    : []
  const latestCompletion = latestRetirementReview?.completion || null

  useEffect(() => {
    setExpandedRequirementIds((current) => {
      const next = new Set(current)
      requirements
        .filter((requirement) => !requirement?.satisfied)
        .forEach((requirement) => next.add(requirement.requirementId))
      return next
    })
  }, [requirements])

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
    <ModalDialog onClose={onClose} size="wide" title={t('authSecurity.secretManagementRetirementDialogTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{t('authSecurity.secretManagementRetirementDialogIntro')}</p>

        <Banner tone={secretManagementStatus?.legacyKeyRetirementReady ? 'success' : 'warning'}>
          <div className="detail-stack">
            <strong>
              {secretManagementStatus?.legacyKeyRetirementReady
                ? t('authSecurity.secretManagementRetirementDialogReadyTitle')
                : t('authSecurity.secretManagementRetirementDialogBlockedTitle')}
            </strong>
            <span>
              {secretManagementStatus?.legacyKeyRetirementReady
                ? t('authSecurity.secretManagementRetirementDialogReadyBody')
                : t('authSecurity.secretManagementRetirementDialogBlockedBody')}
            </span>
          </div>
        </Banner>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementRetirementDialogSummaryTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{Array.isArray(secretManagementStatus?.configuredLegacyKeyIds) && secretManagementStatus.configuredLegacyKeyIds.length > 0 ? secretManagementStatus.configuredLegacyKeyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')}</strong></div>
            <div><span>{t('authSecurity.secretManagementRetirementStatus')}</span><strong>{t(secretManagementStatus?.safeToRetireLegacyKeys ? 'authSecurity.secretManagementSafeToRetire' : 'authSecurity.secretManagementKeepLegacyKeys')}</strong></div>
            <div><span>{t('authSecurity.secretManagementNonActiveRecords')}</span><strong>{secretManagementStatus?.nonActiveKeyRecordCount ?? 0}</strong></div>
            <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
            <div><span>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</span><strong>{latestRequestStatus || t('common.unavailable')}</strong></div>
          </div>
          {requestState?.executeAfter ? <span>{t('authSecurity.secretManagementReencryptLatestRequestExecuteAfter', { value: requestState.executeAfter })}</span> : null}
          {requestState?.message ? <span>{requestState.message}</span> : null}
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRetirementDialogRequirementsTitle')}</strong>
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

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRetirementLatestReviewTitle')}</strong>
          {latestRetirementReview ? (
            <div className="muted-box detail-stack">
              <div className="polling-statistics-breakdown">
                <div><span>{t('authSecurity.secretManagementRetirementReviewRecordedAt')}</span><strong>{latestRetirementReview.reviewedAt || t('common.unavailable')}</strong></div>
                <div><span>{t('authSecurity.secretManagementRetirementReviewRecordedBy')}</span><strong>{latestRetirementReview.reviewedByUsername || latestRetirementReview.reviewedByUserId || t('common.unavailable')}</strong></div>
                <div><span>{t('authSecurity.secretManagementRetirementStatus')}</span><strong>{t(latestRetirementReview.legacyKeyRetirementReady ? 'authSecurity.secretManagementSafeToRetire' : 'authSecurity.secretManagementKeepLegacyKeys')}</strong></div>
                <div><span>{t('authSecurity.secretManagementRetirementReviewRemainingBlocking')}</span><strong>{latestRetirementReview.blockingRequirementsRemaining ?? 0}</strong></div>
              </div>
              {latestCompletion ? (
                <div className="detail-stack">
                  <strong>{t('authSecurity.secretManagementRetirementCompletionTitle')}</strong>
                  <div className="polling-statistics-breakdown">
                    <div><span>{t('authSecurity.secretManagementRetirementCompletionVerifiedAt')}</span><strong>{latestCompletion.verifiedAt || t('common.unavailable')}</strong></div>
                    <div><span>{t('authSecurity.secretManagementRetirementCompletionVerifiedBy')}</span><strong>{latestCompletion.verifiedByUsername || latestCompletion.verifiedByUserId || t('common.unavailable')}</strong></div>
                    <div><span>{t('authSecurity.secretManagementRetirementCompletionStatus')}</span><strong>{latestCompletion.status || t('common.unavailable')}</strong></div>
                  </div>
                  {latestCompletion.message ? <span>{latestCompletion.message}</span> : null}
                </div>
              ) : (
                <span>{t('authSecurity.secretManagementRetirementCompletionNotRecorded')}</span>
              )}
            </div>
          ) : (
            <p className="section-copy">{t('authSecurity.secretManagementRetirementNoReviews')}</p>
          )}
        </div>

        {recentRetirementReviews.length > 0 ? (
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementRetirementRecentReviewsTitle')}</strong>
            <div className="secret-reencryption-requirements-grid">
              {recentRetirementReviews.map((review) => (
                <article className="muted-box secret-reencryption-requirement-card" key={review.reviewId || review.reviewedAt}>
                  <div className="secret-reencryption-requirement-toggle">
                    <div className="secret-reencryption-requirement-copy">
                      <strong>{review.reviewedAt || t('common.unavailable')}</strong>
                      <span>{review.reviewedByUsername || review.reviewedByUserId || t('common.unavailable')}</span>
                    </div>
                    <div className="secret-reencryption-requirement-meta">
                      <span className={`status-pill ${review.legacyKeyRetirementReady ? 'status-ok' : 'tone-warn'}`}>
                        {t(review.legacyKeyRetirementReady ? 'authSecurity.secretManagementSafeToRetire' : 'authSecurity.secretManagementKeepLegacyKeys')}
                      </span>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        ) : null}

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRetirementDialogStepsTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            <li>{t('authSecurity.secretManagementRetirementDialogStep1')}</li>
            <li>{t('authSecurity.secretManagementRetirementDialogStep2')}</li>
            <li>{t('authSecurity.secretManagementRetirementDialogStep3')}</li>
            <li>{t('authSecurity.secretManagementRetirementDialogStep4')}</li>
          </ol>
        </div>

        <div className="action-row">
          <LoadingButton
            className="secondary"
            disabled={!latestRetirementReview}
            onClick={() => onVerifyRetirementCompletion?.()}
            type="button"
          >
            {completionPending
              ? t('authSecurity.secretManagementRetirementVerifyCompletionLoading')
              : t('authSecurity.secretManagementRetirementVerifyCompletion')}
          </LoadingButton>
          <LoadingButton
            className="primary"
            onClick={() => onRecordRetirementReview?.()}
            type="button"
          >
            {pending
              ? t('authSecurity.secretManagementRetirementRecordReviewLoading')
              : t('authSecurity.secretManagementRetirementRecordReview')}
          </LoadingButton>
          <button className="secondary" onClick={onClose} type="button">
            {t('common.done')}
          </button>
        </div>
      </div>
    </ModalDialog>
  )
}

export default SecretRetirementDialog
