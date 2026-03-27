import { languageOptions, translationCatalog } from './i18n'

describe('i18n catalog', () => {
  it('defines the critical UI labels for every supported locale', () => {
    const criticalKeys = [
      'auth.title',
      'auth.signIn',
      'auth.openRegister',
      'hero.refresh',
      'hero.security',
      'hero.signOut',
      'hero.language',
      'setup.title',
      'setup.step1Title',
      'gmail.title',
      'googleSetup.title',
      'userPolling.title',
      'system.title',
      'bridges.title',
      'bridges.add',
      'bridge.edit',
      'bridge.delete',
      'users.title',
      'users.create',
      'users.accountSection',
      'users.mailFetchersSection',
      'password.title',
      'password.change',
      'passkey.title',
      'passkey.register',
      'common.copyError',
      'common.dismissNotification',
      'common.focusSection'
    ]

    languageOptions
      .filter((locale) => locale !== 'en')
      .forEach((locale) => {
        criticalKeys.forEach((key) => {
          expect(translationCatalog[locale]?.[key], `${locale} is missing ${key}`).toBeTruthy()
        })
      })
  })
})
