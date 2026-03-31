import WorkspaceSectionWindow from './WorkspaceSectionWindow'
import { computeWorkspaceDropTargetIndex } from '../../lib/workspaceDrag'

function WorkspaceSectionList({
  dragState,
  layoutEditing,
  moveSection,
  orderedIds,
  sections,
  setDragState,
  t,
  workspaceKey
}) {
  function handlePointerMove(event) {
    if (!dragState || dragState.workspaceKey !== workspaceKey) {
      return
    }
    const windows = Array.from(
      event.currentTarget.querySelectorAll('[data-workspace-section-window="true"]')
    )
    if (!windows.length) {
      return
    }
    const nextTargetIndex = computeWorkspaceDropTargetIndex(windows, event.clientY, dragState.targetIndex)
    setDragState((current) => current && current.workspaceKey === workspaceKey
      ? { ...current, targetIndex: nextTargetIndex }
      : current)
  }

  function renderDropPlaceholder(key) {
    return (
      <div className="workspace-section-drop-placeholder" key={key}>
        <div className="workspace-section-drop-placeholder-inner" />
      </div>
    )
  }

  const visibleSections = orderedIds
    .map((sectionId) => sections.find((entry) => entry.id === sectionId))
    .filter(Boolean)
    .map((section) => ({ section, content: section.render() }))
    .filter((entry) => Boolean(entry.content))
  const draggedIndex = dragState?.workspaceKey === workspaceKey
    ? visibleSections.findIndex(({ section }) => section.id === dragState.draggedId)
    : -1

  const renderedSections = []
  visibleSections.forEach(({ section, content }, index) => {
    if (
      dragState?.workspaceKey === workspaceKey &&
      dragState.targetIndex === index &&
      index !== draggedIndex
    ) {
      renderedSections.push(renderDropPlaceholder(`${workspaceKey}-${section.id}-placeholder-before`))
    }
    renderedSections.push(
      <WorkspaceSectionWindow
        canMoveDown={index < visibleSections.length - 1}
        canMoveUp={index > 0}
        dragHandleLabel={t('preferences.dragSection')}
        dragging={dragState?.workspaceKey === workspaceKey && dragState.draggedId === section.id}
        key={section.id}
        layoutEditing={layoutEditing}
        moveDownLabel={t('preferences.moveSectionDown')}
        moveUpLabel={t('preferences.moveSectionUp')}
        onDragHandlePointerDown={(event) => {
          if (!layoutEditing) {
            return
          }
          event.preventDefault()
          setDragState({
            workspaceKey,
            draggedId: section.id,
            targetIndex: index
          })
        }}
        onPointerMove={undefined}
        onMoveDown={() => moveSection(workspaceKey, section.id, 'down')}
        onMoveUp={() => moveSection(workspaceKey, section.id, 'up')}
        sectionId={section.id}
      >
        {content}
      </WorkspaceSectionWindow>
    )
  })

  if (dragState?.workspaceKey === workspaceKey && dragState.targetIndex === visibleSections.length) {
    renderedSections.push(renderDropPlaceholder(`${workspaceKey}-placeholder-end`))
  }

  return (
    <div className="workspace-section-list" data-workspace-key={workspaceKey} onPointerMove={handlePointerMove}>
      {renderedSections}
    </div>
  )
}

export default WorkspaceSectionList
