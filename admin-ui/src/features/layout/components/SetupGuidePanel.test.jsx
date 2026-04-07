import { render, screen } from '@testing-library/react'
import SetupGuidePanel from './SetupGuidePanel'
import { translate } from '@/lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('SetupGuidePanel', () => {
  it('renders role-aware bootstrap guidance and live setup status', () => {
    render(
      <SetupGuidePanel
        collapsed={false}
        onFocusSection={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onToggleCollapse={vi.fn()}
        persistLayout={false}
        savingLayout={false}
        steps={[
          {
            title: '1. Secure your session',
            description: 'Change your password.',
            status: 'pending',
            targetId: 'password-panel-section'
          },
          {
            title: '2. Connect your destination mailbox',
            description: 'Ready to continue.',
            status: 'complete',
            targetId: 'destination-mailbox-section'
          },
          {
            title: '4. Complete provider OAuth',
            description: 'Bridge failed.',
            status: 'error',
            targetId: 'source-email-accounts-section'
          }
        ]}
        t={t}
      />
    )

    expect(screen.getByText('Quick Setup Guide')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /secure your session/i })).toHaveAttribute('href', '#password-panel-section')
    expect(screen.getByRole('link', { name: /connect your destination mailbox/i })).toHaveClass('setup-guide-complete')
    expect(screen.getByRole('link', { name: /complete provider oauth/i })).toHaveClass('setup-guide-error')
    expect(screen.queryByLabelText(/remember layout on this account/i)).not.toBeInTheDocument()
  })

  it('renders translated setup guide copy in portuguese', () => {
    render(
      <SetupGuidePanel
        collapsed={false}
        onFocusSection={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onToggleCollapse={vi.fn()}
        persistLayout={false}
        savingLayout={false}
        steps={[
          {
            title: translate('pt-PT', 'setup.step1Title'),
            description: translate('pt-PT', 'setup.step1Pending'),
            status: 'pending',
            targetId: 'password-panel-section'
          }
        ]}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Guia rápido de configuração')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /1\. Proteja a sessão/i })).toBeInTheDocument()
    expect(screen.queryByLabelText('Memorizar disposição nesta conta')).not.toBeInTheDocument()
  })
})
