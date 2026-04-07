import { fireEvent, render, screen } from '@testing-library/react'
import WorkspaceSectionWindow from './WorkspaceSectionWindow'

describe('WorkspaceSectionWindow', () => {
  it('renders layout editing controls and wires the move handlers', () => {
    const onMoveUp = vi.fn()
    const onMoveDown = vi.fn()
    const onDragHandlePointerDown = vi.fn()

    render(
      <WorkspaceSectionWindow
        canMoveDown
        canMoveUp
        dragHandleLabel="Drag section"
        layoutEditing
        moveDownLabel="Move down"
        moveUpLabel="Move up"
        onDragHandlePointerDown={onDragHandlePointerDown}
        onMoveDown={onMoveDown}
        onMoveUp={onMoveUp}
        sectionId="destination"
      >
        <div>Section body</div>
      </WorkspaceSectionWindow>
    )

    fireEvent.pointerDown(screen.getByRole('button', { name: 'Drag section' }))
    fireEvent.click(screen.getByRole('button', { name: '↑' }))
    fireEvent.click(screen.getByRole('button', { name: '↓' }))

    expect(onDragHandlePointerDown).toHaveBeenCalledTimes(1)
    expect(onMoveUp).toHaveBeenCalledTimes(1)
    expect(onMoveDown).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Section body').closest('[data-workspace-section-window="true"]')).toHaveAttribute('data-section-id', 'destination')
  })

  it('hides toolbar controls outside layout editing mode', () => {
    const { container } = render(
      <WorkspaceSectionWindow sectionId="stats">
        <div>Stats body</div>
      </WorkspaceSectionWindow>
    )

    expect(container.querySelector('.workspace-section-window-toolbar')).not.toBeInTheDocument()
    expect(screen.getByText('Stats body')).toBeInTheDocument()
  })
})
