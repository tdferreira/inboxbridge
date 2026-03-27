import { fireEvent, render, screen } from '@testing-library/react'
import HeroPanel from './HeroPanel'
import { translate } from '../../lib/i18n'

describe('HeroPanel', () => {
  it('renders translated header controls in portuguese', () => {
    const onOpenPreferences = vi.fn()
    const onOpenSecurityDialog = vi.fn()
    const onRefresh = vi.fn()
    const onSignOut = vi.fn()

    render(
      <HeroPanel
        language="pt-PT"
        loadingData={false}
        onOpenPreferences={onOpenPreferences}
        onOpenSecurityDialog={onOpenSecurityDialog}
        onRefresh={onRefresh}
        onSignOut={onSignOut}
        refreshLoading={false}
        session={{ username: 'admin', role: 'ADMIN' }}
        signOutLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('InboxBridge')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Atualizar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preferências' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Segurança' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Preferências' }))
    fireEvent.click(screen.getByRole('button', { name: 'Segurança' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sair' }))
    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(onOpenPreferences).toHaveBeenCalledTimes(1)
    expect(onOpenSecurityDialog).toHaveBeenCalledTimes(1)
    expect(onSignOut).toHaveBeenCalledTimes(1)
  })
})
