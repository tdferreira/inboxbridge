import { render, screen } from '@testing-library/react'
import PaneToggleButton from './PaneToggleButton'

describe('PaneToggleButton', () => {
  it('shows compact collapse and expand glyphs with accessible labels', () => {
    const { rerender } = render(<PaneToggleButton collapsed={false} onClick={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Collapse section' })).toHaveTextContent('-')
    expect(screen.getByRole('button', { name: 'Collapse section' })).toHaveAttribute('title', 'Collapse section')

    rerender(<PaneToggleButton collapsed onClick={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Expand section' })).toHaveTextContent('+')
  })
})
