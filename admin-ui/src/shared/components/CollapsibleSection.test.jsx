import { fireEvent, render, screen } from '@testing-library/react'
import CollapsibleSection from './CollapsibleSection'

const t = (key) => ({
  'common.collapseSection': 'Collapse section',
  'common.expandSection': 'Expand section',
  'common.refreshingSection': 'Refreshing section…'
})[key] || key

describe('CollapsibleSection', () => {
  it('renders the shared collapsible shell with actions, status content, and body content', () => {
    const onCollapseToggle = vi.fn()

    render(
      <CollapsibleSection
        actions={<button type="button">Launch</button>}
        collapsed={false}
        copy="Shared collapsible copy."
        id="collapsible-section"
        onCollapseToggle={onCollapseToggle}
        statusContent={<div>Shared status</div>}
        t={t}
        title="Collapsible Section"
      >
        <div>Section content</div>
      </CollapsibleSection>
    )

    expect(screen.getByText('Collapsible Section')).toBeInTheDocument()
    expect(screen.getByText('Shared collapsible copy.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Launch' })).toBeInTheDocument()
    expect(screen.getByText('Shared status')).toBeInTheDocument()
    expect(screen.getByText('Section content')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Collapse section'))
    expect(onCollapseToggle).toHaveBeenCalledTimes(1)
  })

  it('hides the body content while collapsed but still shows loading feedback', () => {
    render(
      <CollapsibleSection
        collapsed
        id="collapsed-section"
        onCollapseToggle={() => {}}
        sectionLoading
        t={t}
        title="Collapsed"
      >
        <div>Hidden body</div>
      </CollapsibleSection>
    )

    expect(screen.queryByText('Hidden body')).not.toBeInTheDocument()
    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
    expect(screen.getByLabelText('Expand section')).toBeInTheDocument()
  })

  it('can omit the collapse toggle for non-collapsible reuse cases', () => {
    render(
      <CollapsibleSection
        collapsed={false}
        id="non-collapsible-section"
        onCollapseToggle={() => {}}
        showCollapseToggle={false}
        t={t}
        title="Static"
      >
        <div>Always visible</div>
      </CollapsibleSection>
    )

    expect(screen.getByText('Always visible')).toBeInTheDocument()
    expect(screen.queryByLabelText('Collapse section')).not.toBeInTheDocument()
  })
})
