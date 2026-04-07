import { fireEvent, render, screen } from '@testing-library/react'
import PasswordField from './PasswordField'

describe('PasswordField', () => {
  it('toggles between hidden and visible password modes', () => {
    render(<PasswordField label="Password" value="Secret#123" onChange={vi.fn()} />)

    const input = screen.getByLabelText('Password')
    expect(input).toHaveAttribute('type', 'password')

    fireEvent.click(screen.getByRole('button', { name: 'Show Password' }))
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text')

    fireEvent.click(screen.getByRole('button', { name: 'Hide Password' }))
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
  })
})
