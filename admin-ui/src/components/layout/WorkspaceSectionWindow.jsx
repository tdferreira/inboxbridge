import './WorkspaceSectionWindow.css'

function WorkspaceSectionWindow({
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
    <div className={`workspace-section-window ${dragging ? 'workspace-section-window-dragging' : ''}`.trim()} onPointerMove={onPointerMove}>
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
