// Display hints only: OAuth authorization must never depend on host branding.
export function mailProviderLabelKey(account) {
  const host = (account.host || '').trim().toLowerCase().replace(/\.$/, '')
  const matchesDomain = (domain) => host === domain || host.endsWith(`.${domain}`)
  if (['gmail.com', 'googlemail.com'].some(matchesDomain)) return 'emailAccount.providerGmail'
  if (['office365.com', 'outlook.com', 'hotmail.com', 'live.com'].some(matchesDomain)) return 'emailAccount.providerMicrosoft'
  if (['yahoo.com', 'yahoo.co.jp'].some(matchesDomain)) return 'emailAccount.providerYahoo'
  if (host === '127.0.0.1' && account.port === 1143) return 'emailAccount.providerProton'
  return account.protocol === 'POP3' ? 'emailAccount.providerGenericPop3' : 'emailAccount.providerGenericImap'
}
