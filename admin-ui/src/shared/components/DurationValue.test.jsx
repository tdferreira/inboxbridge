import { render, screen } from '@testing-library/react'
import DurationValue from './DurationValue'

describe('DurationValue', () => {
  it('renders the raw value and a human-readable hover title', () => {
    render(<DurationValue locale="en" value="PT0.25S" />)

    expect(screen.getByText('PT0.25S')).toBeInTheDocument()
    expect(screen.getByTitle('PT0.25S = 250 milliseconds')).toBeInTheDocument()
  })
})
