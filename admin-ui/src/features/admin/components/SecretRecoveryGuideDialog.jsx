import ModalDialog from '@/shared/components/ModalDialog'
import LoadingButton from '@/shared/components/LoadingButton'

export default function SecretRecoveryGuideDialog({
  guide,
  loading = false,
  onClose,
  onRecordRecoveryReview,
  pending = false,
  secretManagementStatus,
  t
}) {
  const containmentSteps = Array.isArray(guide?.containmentSteps) ? guide.containmentSteps : []
  const rollbackSteps = Array.isArray(guide?.rollbackSteps) ? guide.rollbackSteps : []
  const validationSteps = Array.isArray(guide?.validationSteps) ? guide.validationSteps : []
  const evidenceItems = Array.isArray(guide?.evidenceItems) ? guide.evidenceItems : []
  const latestRecoveryReview = secretManagementStatus?.latestRecoveryReview || null
  const recentRecoveryReviews = Array.isArray(secretManagementStatus?.recentRecoveryReviews)
    ? secretManagementStatus.recentRecoveryReviews
    : []

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
            <div><span>{t('authSecurity.secretManagementReencryptLatestRequestTitle')}</span><strong>{guide?.latestRequestStatus || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementRecoveryRollbackRecommended')}</span><strong>{guide?.rollbackRecommended ? t('common.yes') : t('common.no')}</strong></div>
          </div>
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
          <button className="secondary" onClick={onClose} type="button">
            {t('common.done')}
          </button>
        </div>
      </div>
    </ModalDialog>
  )
}
