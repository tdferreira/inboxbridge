import { formatDate, statusTone, tokenStorageLabel } from './formatters'

describe('formatters', () => {
  it('formats missing dates as Never', () => {
    expect(formatDate('')).toBe('Never')
  })

  it('maps statuses to tone classes', () => {
    expect(statusTone('SUCCESS')).toBe('tone-success')
    expect(statusTone('ERROR')).toBe('tone-error')
    expect(statusTone('PENDING')).toBe('tone-neutral')
  })

  it('renders token storage labels for known modes', () => {
    expect(tokenStorageLabel('DATABASE')).toBe('Encrypted DB')
    expect(tokenStorageLabel('PASSWORD')).toBe('Password auth')
    expect(tokenStorageLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE')
  })
})
