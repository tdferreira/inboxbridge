function SectionCard({
  actions = null,
  children = null,
  className = '',
  copy = '',
  id,
  tabIndex = '-1',
  title
}) {
  return (
    <section className={`surface-card section-card-shell ${className}`.trim()} id={id} tabIndex={tabIndex}>
      <div className="panel-header">
        <div>
          <div className="section-title">{title}</div>
          {copy ? <p className="section-copy">{copy}</p> : null}
        </div>
        {actions ? <div className="panel-header-actions section-card-actions">{actions}</div> : null}
      </div>
      {children ? <div className="section-card-body">{children}</div> : null}
    </section>
  )
}

export default SectionCard
