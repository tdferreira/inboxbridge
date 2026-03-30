import WorkspaceSectionWindow from './WorkspaceSectionWindow'

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

  const renderedSections = []
  visibleSections.forEach(({ section, content }, index) => {
    if (dragState?.workspaceKey === workspaceKey && dragState.targetIndex === index) {
      renderedSections.push(renderDropPlaceholder(`${workspaceKey}-${section.id}-placeholder-before`))
    }
    renderedSections.push(
      <WorkspaceSectionWindow
        canMoveDown={index < orderedIds.length - 1}
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
        onPointerMove={(event) => {
          if (!dragState || dragState.workspaceKey !== workspaceKey) {
            return
          }
          const bounds = event.currentTarget.getBoundingClientRect()
          const nextTargetIndex = event.clientY < (bounds.top + bounds.height / 2) ? index : index + 1
          setDragState((current) => current && current.workspaceKey === workspaceKey
            ? { ...current, targetIndex: nextTargetIndex }
            : current)
        }}
        onMoveDown={() => moveSection(workspaceKey, section.id, 'down')}
        onMoveUp={() => moveSection(workspaceKey, section.id, 'up')}
      >
        {content}
      </WorkspaceSectionWindow>
    )
  })

  if (dragState?.workspaceKey === workspaceKey && dragState.targetIndex === visibleSections.length) {
    renderedSections.push(renderDropPlaceholder(`${workspaceKey}-placeholder-end`))
  }

  return renderedSections
}

export default WorkspaceSectionList
