import './PaneToggleButton.css'

/**
 * Compact pane control styled like a subtle window action, used for collapsing
 * and expanding sections without consuming header space.
 */
function PaneToggleButton({
  className = '',
  collapsed,
  collapseLabel = 'Collapse section',
  disabled = false,
  expandLabel = 'Expand section',
  isLoading = false,
  onClick
}) {
  const hint = collapsed ? expandLabel : collapseLabel

  return (
    <button
      aria-label={hint}
      className={`pane-toggle-button ${className}`.trim()}
      disabled={disabled || isLoading}
      onClick={onClick}
      title={hint}
      type="button"
    >
      <span className={`pane-toggle-glyph ${isLoading ? 'pane-toggle-loading' : ''}`}>
        {collapsed ? '+' : '-'}
      </span>
    </button>
  )
}

export default PaneToggleButton
