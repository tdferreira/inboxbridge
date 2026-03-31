import PaneToggleButton from './PaneToggleButton'

function CollapsibleSection({
  actions = null,
  children = null,
  className = '',
  collapsed = false,
  collapseLoading = false,
  copy = '',
  id,
  onCollapseToggle,
  sectionLoading = false,
  showCollapseToggle = true,
  statusContent = null,
  tabIndex = '-1',
  t,
  title
}) {
  return (
    <section className={`surface-card section-card-shell section-with-corner-toggle ${className}`.trim()} id={id} tabIndex={tabIndex}>
      <div className="panel-header">
        <div>
          <div className="section-title">{title}</div>
          {copy ? <p className="section-copy">{copy}</p> : null}
        </div>
        {actions ? <div className="panel-header-actions section-card-actions">{actions}</div> : null}
      </div>
      {showCollapseToggle ? (
        <PaneToggleButton
          className="pane-toggle-button-corner"
          collapseLabel={t('common.collapseSection')}
          collapsed={collapsed}
          disabled={collapseLoading}
          expandLabel={t('common.expandSection')}
          isLoading={collapseLoading}
          onClick={onCollapseToggle}
        />
      ) : null}
      {statusContent}
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}
      {!collapsed ? <div className="section-card-body">{children}</div> : null}
    </section>
  )
}

export default CollapsibleSection
