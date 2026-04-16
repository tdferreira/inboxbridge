import { useEffect, useState } from 'react'
import ModalDialog from '@/shared/components/ModalDialog'
import LoadingButton from '@/shared/components/LoadingButton'

const EMPTY_ITEMS = []

function preferredTargetLabel(target, fallback) {
  if (!target) {
    return fallback
  }
  return target.activeKeyId || target.activeKeyVersion || target.providerId || target.mode || fallback
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

export default function SecretRecoveryGuideDialog({
  guide,
  loading = false,
  onClose,
  onRecordRecoveryReview,
  onRetryReencryption,
  pending = false,
  secretManagementStatus,
  t
}) {
  const containmentSteps = Array.isArray(guide?.containmentSteps) ? guide.containmentSteps : EMPTY_ITEMS
  const rollbackSteps = Array.isArray(guide?.rollbackSteps) ? guide.rollbackSteps : EMPTY_ITEMS
  const validationSteps = Array.isArray(guide?.validationSteps) ? guide.validationSteps : EMPTY_ITEMS
  const evidenceItems = Array.isArray(guide?.evidenceItems) ? guide.evidenceItems : EMPTY_ITEMS
  const latestRecoveryReview = secretManagementStatus?.latestRecoveryReview || null
  const recentRecoveryReviews = Array.isArray(secretManagementStatus?.recentRecoveryReviews)
    ? secretManagementStatus.recentRecoveryReviews
    : EMPTY_ITEMS
  const retryRequirements = Array.isArray(guide?.retryRequirements) ? guide.retryRequirements : EMPTY_ITEMS
  const [expandedRequirementIds, setExpandedRequirementIds] = useState(() => new Set(
    retryRequirements
      .filter((requirement) => !requirement?.satisfied)
      .map((requirement) => requirement.requirementId)
  ))

  useEffect(() => {
    setExpandedRequirementIds((current) => {
      const next = new Set(current)
      let changed = false
      retryRequirements
        .filter((requirement) => !requirement?.satisfied)
        .forEach((requirement) => {
          if (!next.has(requirement.requirementId)) {
            next.add(requirement.requirementId)
            changed = true
          }
        })
      return changed ? next : current
    })
  }, [retryRequirements])

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

  function handleRetryReencryption() {
    onClose?.()
    onRetryReencryption?.()
  }

  return (
    <ModalDialog closeDisabled={loading} onClose={onClose} size="wide" title={guide?.title || t('authSecurity.secretManagementRecoveryGuideTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{guide?.summary || t('authSecurity.secretManagementRecoveryGuideLoading')}</p>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryTriggerTitle')}</strong>
          <span>{guide?.triggerReason || t('authSecurity.secretManagementRecoveryGuideLoading')}</span>
          {guide?.latestRequestMessage ? <span>{guide.latestRequestMessage}</span> : null}
        </div>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementRecoverySummaryTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <div><span>{t('authSecurity.secretManagementMode')}</span><strong>{guide?.currentMode || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementProvider')}</span><strong>{guide?.providerId || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementCurrentTarget')}</span><strong>{preferredTargetLabel(guide?.currentTarget, t('common.unavailable'))}</strong></div>
            <div><span>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</span><strong>{guide?.latestRequestStatus || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementQueuedTarget')}</span><strong>{preferredTargetLabel(guide?.latestRequestTarget, t('common.unavailable'))}</strong></div>
            <div><span>{t('authSecurity.secretManagementRecoveryRollbackRecommended')}</span><strong>{guide?.rollbackRecommended ? t('common.yes') : t('common.no')}</strong></div>
            <div><span>{t('authSecurity.secretManagementRecoveryRetryReady')}</span><strong>{guide?.retryReady ? t('common.yes') : t('common.no')}</strong></div>
          </div>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryRetryChecksTitle')}</strong>
          {retryRequirements.length > 0 ? (
            <div className="secret-reencryption-requirements-grid">
              {retryRequirements.map((requirement) => {
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
          ) : (
            <p className="section-copy">{t('authSecurity.secretManagementRecoveryRetryChecksEmpty')}</p>
          )}
          <p className="section-copy">
            {guide?.retryReady
              ? t('authSecurity.secretManagementRecoveryRetryReadyHelp')
              : t('authSecurity.secretManagementRecoveryRetryBlocked')}
          </p>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryContainmentTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {containmentSteps.map((step) => <li key={`containment:${step}`}>{step}</li>)}
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryRollbackTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {rollbackSteps.map((step) => <li key={`rollback:${step}`}>{step}</li>)}
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryValidationTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {validationSteps.map((step) => <li key={`validation:${step}`}>{step}</li>)}
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryEvidenceTitle')}</strong>
          <ul className="detail-stack secret-reencryption-detail-list">
            {evidenceItems.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementRecoveryLatestReviewTitle')}</strong>
          {latestRecoveryReview ? (
            <div className="muted-box detail-stack">
              <div className="polling-statistics-breakdown">
                <div><span>{t('authSecurity.secretManagementRecoveryReviewRecordedAt')}</span><strong>{latestRecoveryReview.reviewedAt || t('common.unavailable')}</strong></div>
                <div><span>{t('authSecurity.secretManagementRecoveryReviewRecordedBy')}</span><strong>{latestRecoveryReview.reviewedByUsername || latestRecoveryReview.reviewedByUserId || t('common.unavailable')}</strong></div>
                <div><span>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</span><strong>{latestRecoveryReview.latestRequestStatus || t('common.unavailable')}</strong></div>
                <div><span>{t('authSecurity.secretManagementRecoveryRollbackRecommended')}</span><strong>{latestRecoveryReview.rollbackRecommended ? t('common.yes') : t('common.no')}</strong></div>
              </div>
              {latestRecoveryReview.latestRequestMessage ? <span>{latestRecoveryReview.latestRequestMessage}</span> : null}
            </div>
          ) : (
            <p className="section-copy">{t('authSecurity.secretManagementRecoveryNoReviews')}</p>
          )}
        </div>

        {recentRecoveryReviews.length > 0 ? (
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementRecoveryRecentReviewsTitle')}</strong>
            <div className="secret-reencryption-requirements-grid">
              {recentRecoveryReviews.map((review) => (
                <article className="muted-box secret-reencryption-requirement-card" key={review.reviewId || review.reviewedAt}>
                  <div className="secret-reencryption-requirement-toggle">
                    <div className="secret-reencryption-requirement-copy">
                      <strong>{review.reviewedAt || t('common.unavailable')}</strong>
                      <span>{review.reviewedByUsername || review.reviewedByUserId || t('common.unavailable')}</span>
                    </div>
                    <div className="secret-reencryption-requirement-meta">
                      <span className={`status-pill ${review.rollbackRecommended ? 'tone-warn' : 'status-ok'}`}>
                        {review.rollbackRecommended ? t('common.yes') : t('common.no')}
                      </span>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        ) : null}

        <div className="action-row">
          <LoadingButton className="primary" onClick={() => onRecordRecoveryReview?.()} type="button">
            {pending
              ? t('authSecurity.secretManagementRecoveryRecordReviewLoading')
              : t('authSecurity.secretManagementRecoveryRecordReview')}
          </LoadingButton>
          {guide?.retryReady ? (
            <button className="secondary" onClick={handleRetryReencryption} type="button">
              {t('authSecurity.secretManagementRecoveryRetryAction')}
            </button>
          ) : null}
          <button className="secondary" onClick={onClose} type="button">
            {t('common.done')}
          </button>
        </div>
      </div>
    </ModalDialog>
  )
}
