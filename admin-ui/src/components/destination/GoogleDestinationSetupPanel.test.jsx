import { render, screen } from '@testing-library/react'
import GoogleDestinationSetupPanel from './GoogleDestinationSetupPanel'
import { translate } from '../../lib/i18n'

describe('GoogleDestinationSetupPanel', () => {
  it('renders translated setup guidance in portuguese', () => {
    render(
      <GoogleDestinationSetupPanel
        destinationMeta={{
          sharedClientConfigured: true,
          defaultRedirectUri: 'https://localhost:3000/api/google-oauth/callback'
        }}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Configuração Google')).toBeInTheDocument()
    expect(screen.getByText('Cliente OAuth Google partilhado disponível')).toBeInTheDocument()
    expect(screen.getByText(/A Google associa essas credenciais a um projeto Google Cloud/i)).toBeInTheDocument()
    expect(screen.getByText(/3\. Criar um cliente OAuth com o URI de redirecionamento https:\/\/localhost:3000\/api\/google-oauth\/callback\./i)).toBeInTheDocument()
  })
})