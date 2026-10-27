import { mailProviderLabelKey } from './mailProviderLabel'

describe('mail provider display labels', () => {
  it.each([
    ['gmail.com', 'Gmail'], ['imap.gmail.com', 'Gmail'], ['googlemail.com', 'Gmail'],
    ['pop.googlemail.com', 'Gmail'], ['office365.com', 'Microsoft'], ['outlook.office365.com', 'Microsoft'],
    ['outlook.com', 'Microsoft'], ['imap.outlook.com', 'Microsoft'], ['hotmail.com', 'Microsoft'],
    ['imap.hotmail.com', 'Microsoft'], ['live.com', 'Microsoft'], ['imap.live.com', 'Microsoft'],
    ['yahoo.com', 'Yahoo'], ['imap.mail.yahoo.com', 'Yahoo'], ['yahoo.co.jp', 'Yahoo'],
    ['imap.mail.yahoo.co.jp', 'Yahoo'], [' IMAP.GMAIL.COM. ', 'Gmail']
  ])('recognizes the complete provider domain %s', (host, provider) => {
    expect(mailProviderLabelKey({ host })).toBe(`emailAccount.provider${provider}`)
  })

  it.each(['evil-live.com', 'live.com.attacker.example', 'attacker.example/live.com',
    'outlook.attacker.example', 'notgmail.example', 'yahoo.attacker.example', 'imap.example', '', null])(
    'does not brand an unrelated host %s as a provider', (host) => {
      expect(mailProviderLabelKey({ host })).toBe('emailAccount.providerGenericImap')
    })

  it('retains generic POP3 and the explicit local Proton Bridge hint', () => {
    expect(mailProviderLabelKey({ host: 'imap.example', protocol: 'POP3' })).toBe('emailAccount.providerGenericPop3')
    expect(mailProviderLabelKey({ host: '127.0.0.1', port: 1143 })).toBe('emailAccount.providerProton')
    expect(mailProviderLabelKey({ host: '127.0.0.1', port: 993 })).toBe('emailAccount.providerGenericImap')
  })
})
