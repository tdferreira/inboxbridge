import { fireEvent, render, screen } from '@testing-library/react'
import FloatingActionMenu from './FloatingActionMenu'

vi.mock('@/lib/floatingMenu', () => ({
  resolveFloatingMenuPosition: () => ({
    left: 16,
    top: 24,
    placement: 'bottom'
  })
}))

describe('FloatingActionMenu', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 100,
      height: 32,
      top: 20,
      left: 20,
      right: 120,
      bottom: 52
    })
  })

  it('opens the menu, exposes the closeMenu helper, and closes on outside click', () => {
    render(
      <FloatingActionMenu
        buttonLabel="More actions"
        menuContent={({ closeMenu }) => (
          <button onClick={closeMenu} type="button">Close from menu</button>
        )}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'More actions' }))

    expect(screen.getByRole('button', { name: 'Close from menu' })).toBeInTheDocument()

    fireEvent.mouseDown(document.body)

    expect(screen.queryByRole('button', { name: 'Close from menu' })).not.toBeInTheDocument()
  })

  it('closes when Escape is pressed', () => {
    render(
      <FloatingActionMenu
        buttonLabel="More actions"
        menuContent={() => <div>Menu body</div>}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'More actions' }))
    expect(screen.getByText('Menu body')).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByText('Menu body')).not.toBeInTheDocument()
  })
})
