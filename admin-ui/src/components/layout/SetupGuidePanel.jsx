import CollapsibleSection from '../common/CollapsibleSection'
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
    <CollapsibleSection
      actions={dismissable ? (
        <button className="secondary" onClick={onDismiss} title={t('setup.dismissHint')} type="button">
          {t('setup.dismiss')}
        </button>
      ) : null}
      className="setup-guide-panel"
      collapsed={collapsed}
      collapseLoading={savingLayout}
      copy={t('setup.copy')}
      id="quick-setup-guide-section"
      onCollapseToggle={onToggleCollapse}
      sectionLoading={sectionLoading}
      statusContent={savingLayout ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
      t={t}
      title={t('setup.title')}
    >
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
    </CollapsibleSection>
  )
}

export default SetupGuidePanel
