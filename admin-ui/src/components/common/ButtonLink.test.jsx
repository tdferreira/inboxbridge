import { render, screen } from '@testing-library/react'
import ButtonLink from './ButtonLink'

describe('ButtonLink', () => {
  it('renders a navigational action with shared button styling classes', () => {
    render(<ButtonLink href="/remote">Open Remote Control</ButtonLink>)

    expect(screen.getByRole('link', { name: 'Open Remote Control' })).toHaveAttribute('href', '/remote')
    expect(screen.getByRole('link', { name: 'Open Remote Control' }).className).toContain('button-link')
    expect(screen.getByRole('link', { name: 'Open Remote Control' }).className).toContain('primary')
  })
})
