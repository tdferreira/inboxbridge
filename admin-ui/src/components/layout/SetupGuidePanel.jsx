import LoadingButton from '../common/LoadingButton'
import './SetupGuidePanel.css'

/**
 * Gives first-time operators and regular users a compact, in-app checklist for
 * the minimum steps needed to make InboxBridge usable.
 */
function SetupGuidePanel({ collapsed, onFocusSection, onPersistLayoutChange, onToggleCollapse, persistLayout, savingLayout, steps }) {
  return (
    <section className="surface-card setup-guide-panel" id="quick-setup-guide-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">Quick Setup Guide</div>
          <p className="section-copy">
            Follow these steps to get from a fresh startup to a working import flow.
          </p>
        </div>
        <div className="action-row">
          <label className="setup-guide-persist-toggle">
            <input
              checked={persistLayout}
              disabled={savingLayout}
              onChange={(event) => onPersistLayoutChange(event.target.checked)}
              type="checkbox"
            />
            <span>Remember layout on this account</span>
          </label>
          <LoadingButton className="secondary" isLoading={savingLayout && persistLayout} loadingLabel={collapsed ? 'Expanding…' : 'Collapsing…'} onClick={onToggleCollapse} type="button">
            {collapsed ? 'Expand' : 'Collapse'}
          </LoadingButton>
        </div>
      </div>
      {savingLayout ? <div className="section-copy">Saving layout preference…</div> : null}
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
