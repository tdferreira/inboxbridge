import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import OAuthCallbackPage from './OAuthCallbackPage'
import { jsonResponse } from '@/test/appTestHelpers'

describe('OAuthCallbackPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('renders the Google callback flow in Portuguese and exchanges automatically', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(jsonResponse({
      storedInDatabase: true,
      usingEnvironmentFallback: false,
      replacedExistingAccount: false,
      sameLinkedAccount: true,
      previousGrantRevoked: false,
      credentialKey: 'db:GOOGLE:gmail-destination',
      scope: 'gmail.insert gmail.labels',
      tokenType: 'Bearer',
      accessTokenExpiresAt: '2026-04-08T10:00:00Z',
      nextStep: 'Stored securely in encrypted storage.'
    }))
    vi.stubGlobal('fetch', fetchSpy)

    render(
      <MemoryRouter initialEntries={['/oauth/google/callback?code=code-123&state=state-1&lang=pt-PT']}>
        <OAuthCallbackPage provider="google" />
      </MemoryRouter>
    )

    expect(await screen.findByText('Concluir Google OAuth')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith('/api/google-oauth/exchange', expect.objectContaining({
        method: 'POST'
      }))
    })
    expect(await screen.findByText('A troca do Google OAuth terminou com sucesso. O InboxBridge está a levá-lo de volta à aplicação.')).toBeInTheDocument()
    expect(screen.getByText(/Armazenamento:\s+Armazenamento cifrado/)).toBeInTheDocument()
    expect(screen.getByText(/Mesma conta ligada:\s+Sim/)).toBeInTheDocument()
    expect(screen.getByText(/Conta ligada anterior substituída:\s+Não/)).toBeInTheDocument()
    expect(screen.getByText(/Consentimento anterior revogado:\s+Não/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar regresso automático' }))
    expect(screen.getByText('O regresso automático foi cancelado. Pode permanecer nesta página e analisar o resultado da troca.')).toBeInTheDocument()
  })

  it('renders provider errors without attempting the Microsoft exchange', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)

    render(
      <MemoryRouter initialEntries={['/oauth/microsoft/callback?error=access_denied&error_description=user+denied&lang=en']}>
        <OAuthCallbackPage provider="microsoft" />
      </MemoryRouter>
    )

    expect(await screen.findByText('Complete Microsoft OAuth')).toBeInTheDocument()
    expect(screen.getByText('Microsoft OAuth did not receive the required consent. Retry the flow and approve every requested mailbox permission.')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Return to InboxBridge' })).toBeInTheDocument()
  })
})
