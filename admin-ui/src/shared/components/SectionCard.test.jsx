import { render, screen } from '@testing-library/react'
import SectionCard from './SectionCard'

describe('SectionCard', () => {
  it('renders a reusable section shell with heading, copy, actions, and body content', () => {
    render(
      <SectionCard
        actions={<button type="button">Launch</button>}
        copy="This is shared descriptive copy."
        id="shared-section"
        title="Shared Section"
      >
        <div>Section body</div>
      </SectionCard>
    )

    expect(screen.getByText('Shared Section')).toBeInTheDocument()
    expect(screen.getByText('This is shared descriptive copy.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Launch' })).toBeInTheDocument()
    expect(screen.getByText('Section body')).toBeInTheDocument()
  })
})
