import { fireEvent, render, screen } from '@testing-library/react'
import InfoHint from './InfoHint'

describe('InfoHint', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 20,
      height: 20,
      top: 40,
      left: 40,
      right: 60,
      bottom: 60
    })
  })

  it('shows the floating tooltip on focus and hides it on Escape', () => {
    render(<InfoHint text="Helpful guidance" />)

    fireEvent.focus(screen.getByRole('note', { name: 'Helpful guidance' }))

    expect(screen.getByText('Helpful guidance')).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByText('Helpful guidance')).not.toBeInTheDocument()
  })

  it('shows the tooltip on hover and hides it on blur', () => {
    render(<InfoHint text="Another hint" />)

    const anchor = screen.getByRole('note', { name: 'Another hint' })
    fireEvent.mouseEnter(anchor)
    expect(screen.getByText('Another hint')).toBeInTheDocument()

    fireEvent.blur(anchor)
    expect(screen.queryByText('Another hint')).not.toBeInTheDocument()
  })
})
