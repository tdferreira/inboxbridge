import { render, screen } from '@testing-library/react'
import RemoteControlLaunchSection from './RemoteControlLaunchSection'

const t = (key) => ({
  'remote.launchpadTitle': 'InboxBridge Go',
  'remote.launchpadCopy': 'Use InboxBridge Go to trigger inbox fetches quickly from phones, tablets, laptops, or shared devices without opening the full workspace.',
  'remote.launchpadAction': 'Open InboxBridge Go',
  'remote.launchpadNote': 'InboxBridge Go uses its own scoped session and is designed for quick poll-now actions when you are away from the main My InboxBridge dashboard.'
})[key] || key

describe('RemoteControlLaunchSection', () => {
  it('renders the remote launch card through shared section and link primitives', () => {
    const { container } = render(<RemoteControlLaunchSection t={t} />)

    expect(screen.getByText('InboxBridge Go')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open InboxBridge Go' })).toHaveAttribute('href', '/remote')
    expect(screen.getByText('InboxBridge Go uses its own scoped session and is designed for quick poll-now actions when you are away from the main My InboxBridge dashboard.')).toBeInTheDocument()
    expect(container.querySelector('.section-card-shell')).toBeInTheDocument()
    expect(container.querySelector('.section-with-corner-toggle')).not.toBeInTheDocument()
  })
})
