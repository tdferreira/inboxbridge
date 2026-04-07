import './WorkspaceSectionWindow.css'

/**
 * Thin wrapper around a movable workspace section. It adds the temporary
 * layout-edit toolbar and the drag/move affordances without owning the
 * section's actual business UI.
 */
function WorkspaceSectionWindow({
  sectionId,
  dragHandleLabel,
  dragging = false,
  layoutEditing = false,
  canMoveDown,
  canMoveUp,
  children,
  moveDownLabel,
  moveUpLabel,
  onDragHandlePointerDown,
  onPointerMove,
  onMoveDown,
  onMoveUp
}) {
  return (
    <div
      className={`workspace-section-window ${dragging ? 'workspace-section-window-dragging' : ''}`.trim()}
      data-section-id={sectionId}
      data-workspace-section-window="true"
      onPointerMove={onPointerMove}
    >
      {layoutEditing ? (
        <div className="workspace-section-window-toolbar">
          <button
            aria-label={dragHandleLabel}
            className="workspace-section-window-drag-handle"
            onPointerDown={onDragHandlePointerDown}
            title={dragHandleLabel}
            type="button"
          >
            <span />
            <span />
            <span />
            <span />
            <span />
            <span />
          </button>
          <button
            className="secondary workspace-section-window-button"
            disabled={!canMoveUp}
            onClick={onMoveUp}
            title={moveUpLabel}
            type="button"
          >
            ↑
          </button>
          <button
            className="secondary workspace-section-window-button"
            disabled={!canMoveDown}
            onClick={onMoveDown}
            title={moveDownLabel}
            type="button"
          >
            ↓
          </button>
        </div>
      ) : null}
      {children}
    </div>
  )
}

export default WorkspaceSectionWindow
