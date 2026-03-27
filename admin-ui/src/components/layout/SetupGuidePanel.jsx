import PaneToggleButton from '../common/PaneToggleButton'
import './SetupGuidePanel.css'

/**
 * Gives first-time operators and regular users a compact, in-app checklist for
 * the minimum steps needed to make InboxBridge usable.
 */
function SetupGuidePanel({
  collapsed,
  dismissable = false,
  onDismiss,
  onFocusSection,
  onToggleCollapse,
  savingLayout,
  sectionLoading = false,
  steps,
  t
}) {
  return (
    <section className="surface-card setup-guide-panel section-with-corner-toggle" id="quick-setup-guide-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('setup.title')}</div>
          <p className="section-copy">
            {t('setup.copy')}
          </p>
        </div>
        {dismissable ? (
          <button className="secondary" onClick={onDismiss} title={t('setup.dismissHint')} type="button">
            {t('setup.dismiss')}
          </button>
        ) : null}
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={savingLayout} expandLabel={t('common.expandSection')} isLoading={savingLayout} onClick={onToggleCollapse} />
      {savingLayout ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}
      {!collapsed ? (
        <div className="setup-guide-grid">
          {steps.map((step) => (
            <a
              key={step.title}
              className={`setup-guide-link setup-guide-${step.status}`}
              href={`#${step.targetId}`}
              onClick={(event) => onFocusSection(event, step)}
            >
              <strong>{step.title}</strong><br />
              {step.description}
            </a>
          ))}
        </div>
      ) : null}
    </section>
  )
}

export default SetupGuidePanel
