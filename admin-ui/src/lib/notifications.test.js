import { pollErrorNotification, resolveNotificationContent, translatedNotification } from './notifications'

describe('notifications helpers', () => {
  it('resolves translated notification descriptors in the active locale', () => {
    expect(resolveNotificationContent(translatedNotification('notifications.pollStarted'), 'en')).toBe('Running the global polling now…')
    expect(resolveNotificationContent(translatedNotification('notifications.pollStarted'), 'pt-PT')).toBe('A executar agora a verificação global…')
  })

  it('resolves nested translated params in the active locale', () => {
    const content = translatedNotification('notifications.emailAccountSavedStartingProviderOAuth', {
      emailAccountId: 'outlook-main',
      provider: translatedNotification('oauthProvider.microsoft')
    })

    expect(resolveNotificationContent(content, 'en')).toBe('Source email account outlook-main saved. Opening Microsoft…')
    expect(resolveNotificationContent(content, 'pt-PT')).toBe('Conta de email de origem outlook-main guardada. A abrir Microsoft…')
  })

  it('resolves poll error descriptors in the active locale', () => {
    const content = pollErrorNotification('Source outlook-main is cooling down until 2026-03-28T06:22:31.605711Z.')

    expect(resolveNotificationContent(content, 'en')).toContain('Source outlook-main is cooling down until')
    expect(resolveNotificationContent(content, 'pt-PT')).toContain('A fonte outlook-main está em pausa até')
  })

  it('joins multiple poll errors in the active locale', () => {
    const content = pollErrorNotification([
      { code: 'source_cooling_down', sourceId: 'outlook-main', value: '2026-03-28T06:22:31.605711Z' },
      { code: 'gmail_account_not_linked', sourceId: 'gmail-main' }
    ])

    expect(resolveNotificationContent(content, 'pt-PT')).toContain('A fonte outlook-main está em pausa até')
    expect(resolveNotificationContent(content, 'pt-PT')).toContain('A fonte gmail-main não pode ser executada')
  })
})
