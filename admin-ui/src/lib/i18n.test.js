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
      'workspace.user',
      'workspace.admin',
      'hero.language',
      'setup.title',
      'setup.step1Title',
      'preferences.editLayout',
      'preferences.showQuickSetup',
      'preferences.dragSection',
      'destination.title',
      'destination.provider',
      'destination.dialogCopy',
      'destination.saveAndAuthenticate',
      'destinationPreset.gmail.description',
      'destinationPreset.outlook.description',
      'gmail.title',
      'googleSetup.title',
      'userPolling.title',
      'system.title',
      'emailAccounts.title',
      'emailAccounts.add',
      'emailAccounts.testConnection',
      'emailAccount.edit',
      'emailAccount.delete',
      'users.title',
      'users.create',
      'users.accountSection',
      'users.destinationSection',
      'users.mailFetchersSection',
      'password.title',
      'password.change',
      'passkey.title',
      'passkey.register',
      'common.copyError',
      'common.oauthRefreshTokenMissingError',
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
