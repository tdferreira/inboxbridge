import fs from 'node:fs'
import path from 'node:path'
import { pollErrorNotification, resolveNotificationContent, translatedNotification } from './notifications'
import { languageOptions, translationCatalog } from './i18n'

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

  it('resolves the email-account save and delete notification aliases in every locale', () => {
    languageOptions.forEach((locale) => {
      expect(resolveNotificationContent(
        translatedNotification('notifications.emailAccountSaved', { emailAccountId: 'outlook-main' }),
        locale
      ), `${locale} is missing notifications.emailAccountSaved`).toBeTruthy()

      expect(resolveNotificationContent(
        translatedNotification('notifications.emailAccountDeleted', { emailAccountId: 'outlook-main' }),
        locale
      ), `${locale} is missing notifications.emailAccountDeleted`).toBeTruthy()
    })
  })

  it('defines every literal translated notification key used in the frontend for every locale', () => {
    const srcRoot = path.resolve(import.meta.dirname, '..')
    const files = []
    const translatedNotificationKeys = new Set()

    function collectFiles(directory) {
      for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        const entryPath = path.join(directory, entry.name)
        if (entry.isDirectory()) {
          collectFiles(entryPath)
          continue
        }
        if (/\.(js|jsx)$/.test(entry.name)) {
          files.push(entryPath)
        }
      }
    }

    collectFiles(srcRoot)

    for (const file of files) {
      const source = fs.readFileSync(file, 'utf8')
      const matches = source.matchAll(/translatedNotification\(\s*['"]([^'"]+)['"]/g)
      for (const match of matches) {
        translatedNotificationKeys.add(match[1])
      }
    }

    languageOptions.forEach((locale) => {
      translatedNotificationKeys.forEach((key) => {
        expect(
          resolveNotificationContent(translatedNotification(key), locale),
          `${locale} does not resolve ${key}`
        ).not.toBe(key)
      })
    })
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
