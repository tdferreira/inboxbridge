import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import WorkspaceSectionList from './WorkspaceSectionList'

function TestHarness() {
  const [dragState, setDragState] = useState(null)
  const sections = [
    { id: 'one', render: () => <section>Section One</section> },
    { id: 'two', render: () => <section>Section Two</section> }
  ]

  return (
    <>
      <output data-testid="drag-state">{JSON.stringify(dragState)}</output>
      <WorkspaceSectionList
        dragState={dragState}
        layoutEditing
        moveSection={vi.fn()}
        orderedIds={['one', 'two']}
        sections={sections}
        setDragState={setDragState}
        t={(key) => key}
        workspaceKey="user"
      />
    </>
  )
}

describe('WorkspaceSectionList', () => {
  it('starts a drag session from the section drag handle', () => {
    const { container } = render(<TestHarness />)

    const dragHandle = screen.getAllByRole('button', { name: 'preferences.dragSection' })[0]
    fireEvent.pointerDown(dragHandle, { clientY: 20 })

    expect(screen.getByTestId('drag-state').textContent).toContain('"targetIndex":0')

    const windows = container.querySelectorAll('[data-workspace-section-window="true"]')
    windows[0].getBoundingClientRect = () => ({ top: 0, height: 100 })
    windows[1].getBoundingClientRect = () => ({ top: 120, height: 100 })

    fireEvent.pointerMove(container.querySelector('.workspace-section-list'), { clientY: 180 })

    expect(screen.getByTestId('drag-state').textContent).toContain('"draggedId":"one"')
  })

  it('disables the down arrow for the last visible section', () => {
    const sections = [
      { id: 'one', render: () => <section>Section One</section> },
      { id: 'hidden', render: () => null },
      { id: 'two', render: () => <section>Section Two</section> }
    ]

    const { container } = render(
      <WorkspaceSectionList
        dragState={null}
        layoutEditing
        moveSection={vi.fn()}
        orderedIds={['one', 'hidden', 'two']}
        sections={sections}
        setDragState={vi.fn()}
        t={(key) => key}
        workspaceKey="user"
      />
    )

    const windows = container.querySelectorAll('[data-workspace-section-window="true"]')
    const lastWindowButtons = windows[1].querySelectorAll('.workspace-section-window-button')

    expect(lastWindowButtons[0]).not.toBeDisabled()
    expect(lastWindowButtons[1]).toBeDisabled()
  })

  it('does not render a placeholder at the dragged section original slot before the pointer moves', () => {
    const { container } = render(<TestHarness />)

    const dragHandle = screen.getAllByRole('button', { name: 'preferences.dragSection' })[0]
    fireEvent.pointerDown(dragHandle, { clientY: 20 })

    expect(container.querySelectorAll('.workspace-section-drop-placeholder')).toHaveLength(0)
  })
})
