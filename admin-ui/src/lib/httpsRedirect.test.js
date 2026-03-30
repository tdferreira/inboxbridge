import { buildHttpsUrl, enforceHttpsIfNeeded } from './httpsRedirect'

describe('httpsRedirect', () => {
  it('builds an https URL from an http location', () => {
    expect(buildHttpsUrl({
      protocol: 'http:',
      host: 'localhost:3000',
      pathname: '/admin',
      search: '?tab=users',
      hash: '#sessions'
    })).toBe('https://localhost:3000/admin?tab=users#sessions')
  })

  it('does nothing when the location is already https', () => {
    const replace = vi.fn()

    expect(enforceHttpsIfNeeded({
      protocol: 'https:',
      host: 'localhost:3000',
      pathname: '/',
      search: '',
      hash: '',
      replace
    })).toBe(false)
    expect(replace).not.toHaveBeenCalled()
  })
})
