import { fireEvent, render, screen } from '@testing-library/react'
import SecretManagementConfigReferenceList from './SecretManagementConfigReferenceList'

describe('SecretManagementConfigReferenceList', () => {
  it('shows concrete usage guidance and example values in the tooltip', () => {
    const t = (key) => {
      const values = {
        'authSecurity.secretManagementConfigUsedForLabel': 'Used for:',
        'authSecurity.secretManagementConfigExampleLabel': 'Example:'
      }
      return values[key] || key
    }

    render(<SecretManagementConfigReferenceList references={['SECRET_PROVIDER_OPENBAO_URL']} t={t} />)

    expect(screen.getByText('SECRET_PROVIDER_OPENBAO_URL')).toBeInTheDocument()

    fireEvent.focus(screen.getByRole('note', { name: /Used for: Points InboxBridge to the HTTPS base URL of the OpenBao server/i }))

    expect(screen.getByText(/Example: SECRET_PROVIDER_OPENBAO_URL=https:\/\/openbao.example.internal:8200/)).toBeInTheDocument()
  })
})
