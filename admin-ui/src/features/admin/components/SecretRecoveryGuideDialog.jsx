import ModalDialog from '@/shared/components/ModalDialog'

export default function SecretRecoveryGuideDialog({ guide, loading = false, onClose, t }) {
  const containmentSteps = Array.isArray(guide?.containmentSteps) ? guide.containmentSteps : []
  const rollbackSteps = Array.isArray(guide?.rollbackSteps) ? guide.rollbackSteps : []
  const validationSteps = Array.isArray(guide?.validationSteps) ? guide.validationSteps : []
  const evidenceItems = Array.isArray(guide?.evidenceItems) ? guide.evidenceItems : []

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
      </div>
    </ModalDialog>
  )
}
