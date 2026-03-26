import { authMethodLabel, formatDate, oauthProviderLabel, protocolLabel, roleLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from './formatters'

describe('formatters', () => {
  it('formats missing dates as Never', () => {
    expect(formatDate('')).toBe('Never')
  })

  it('maps statuses to tone classes', () => {
    expect(statusTone('SUCCESS')).toBe('tone-success')
    expect(statusTone('ERROR')).toBe('tone-error')
    expect(statusTone('PENDING')).toBe('tone-neutral')
  })

  it('renders translated labels for common enums', () => {
    expect(statusLabel('SUCCESS')).toBe('Success')
    expect(roleLabel('ADMIN')).toBe('Admin')
    expect(protocolLabel('IMAP')).toBe('IMAP')
    expect(authMethodLabel('PASSWORD')).toBe('Password')
    expect(oauthProviderLabel('MICROSOFT')).toBe('Microsoft')
    expect(triggerLabel('scheduler')).toBe('scheduler')
  })

  it('renders token storage labels for known modes', () => {
    expect(tokenStorageLabel('DATABASE')).toBe('Encrypted DB')
    expect(tokenStorageLabel('PASSWORD')).toBe('Password auth')
    expect(tokenStorageLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE')
  })
})
