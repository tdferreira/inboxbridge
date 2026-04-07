import { render, screen } from '@testing-library/react'
import FormField from './FormField'

describe('FormField', () => {
  it('wraps fields in a label by default and shows the help hint', () => {
    render(
      <FormField helpText="Helpful text" label="Username">
        <input aria-label="Username input" />
      </FormField>
    )

    expect(screen.getByText('Username')).toBeInTheDocument()
    expect(screen.getByRole('note', { name: 'Helpful text' })).toBeInTheDocument()
    expect(screen.getByLabelText('Username input')).toBeInTheDocument()
  })

  it('supports an external label when wrapWithLabel is disabled', () => {
    render(
      <FormField inputId="external-input" label="Server hostname" wrapWithLabel={false}>
        <input id="external-input" />
      </FormField>
    )

    expect(screen.getByText('Server hostname').closest('label')).toHaveAttribute('for', 'external-input')
  })
})
