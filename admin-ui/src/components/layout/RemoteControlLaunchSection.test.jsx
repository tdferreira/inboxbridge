import { render, screen } from '@testing-library/react'
import RemoteControlLaunchSection from './RemoteControlLaunchSection'

const t = (key) => ({
  'remote.launchpadTitle': 'Remote control',
  'remote.launchpadCopy': 'Use the lightweight remote page to trigger polling quickly from phones, tablets, laptops, or shared devices without opening the full workspace.',
  'remote.launchpadAction': 'Open Remote Control',
  'remote.launchpadNote': 'The remote page uses its own scoped session and is designed for quick poll-now actions when you are away from the main My InboxBridge dashboard.'
})[key] || key

describe('RemoteControlLaunchSection', () => {
  it('renders the remote launch card through shared section and link primitives', () => {
    render(<RemoteControlLaunchSection t={t} />)

    expect(screen.getByText('Remote control')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Remote Control' })).toHaveAttribute('href', '/remote')
    expect(screen.getByText('The remote page uses its own scoped session and is designed for quick poll-now actions when you are away from the main My InboxBridge dashboard.')).toBeInTheDocument()
  })
})
