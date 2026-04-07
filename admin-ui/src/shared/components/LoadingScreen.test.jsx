import { render, screen } from '@testing-library/react'
import LoadingScreen from './LoadingScreen'

describe('LoadingScreen', () => {
  it('renders the loading label inside the loading card shell', () => {
    const { container } = render(<LoadingScreen label="Loading InboxBridge…" />)

    expect(screen.getByText('Loading InboxBridge…')).toBeInTheDocument()
    expect(container.querySelector('.loading-screen-card')).toBeInTheDocument()
  })
})
