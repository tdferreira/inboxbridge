function renumberStepTitle(title, index) {
  return String(title).replace(/^\d+\.\s*/, `${index}. `)
}

function isSetupGuideRelevantEmailAccount(emailAccount) {
  return emailAccount?.enabled !== false && emailAccount?.effectivePollEnabled !== false
}

export function buildSetupGuideState({ workspace = 'user', destinationMeta, myEmailAccounts = [], session, systemDashboard, systemOAuthSettings, users = [], t }) {
  if (!session) {
    return {
      steps: [],
      allStepsComplete: false
    }
  }

  if (workspace === 'admin') {
    const systemDashboardEmailAccounts = systemDashboard?.emailAccounts || systemDashboard?.bridges || []
    const visibleSystemEmailAccounts = session.username === 'admin'
      ? systemDashboardEmailAccounts
      : []
    const activeSystemEmailAccounts = visibleSystemEmailAccounts.filter(isSetupGuideRelevantEmailAccount)
    const sharedGoogleReady = Boolean(systemOAuthSettings?.googleClientId && systemOAuthSettings?.googleClientSecretConfigured && systemOAuthSettings?.googleRefreshTokenConfigured)
    const sharedMicrosoftReady = Boolean(systemOAuthSettings?.microsoftClientId && systemOAuthSettings?.microsoftClientSecretConfigured)
    const sharedOAuthReady = sharedGoogleReady || sharedMicrosoftReady
    const importsReady = sharedOAuthReady
      && (systemDashboard?.overall?.totalImportedMessages || 0) > 0
      && activeSystemEmailAccounts.every((emailAccount) => emailAccount.lastEvent?.status !== 'ERROR' && !emailAccount.lastEvent?.error)
    const importsErrored = activeSystemEmailAccounts.some((emailAccount) => emailAccount.lastEvent?.status === 'ERROR' || emailAccount.lastEvent?.error)
    const multiUserEnabled = systemOAuthSettings?.effectiveMultiUserEnabled !== false
    const hasAdditionalUser = users.some((user) => user.id !== session.id)
    const steps = [
      {
        title: t('setup.adminStep1Title'),
        description: sharedOAuthReady ? t('setup.adminStep1Ready') : t('setup.adminStep1Pending'),
        targetId: 'oauth-apps-section',
        sectionKey: 'oauthAppsCollapsed',
        status: sharedOAuthReady ? 'complete' : 'pending'
      }
    ]
    if (multiUserEnabled) {
      steps.push({
        title: t('setup.adminStep2Title'),
        description: hasAdditionalUser ? t('setup.adminStep2Ready') : t('setup.adminStep2Pending'),
        targetId: 'user-management-section',
        sectionKey: 'userManagementCollapsed',
        status: hasAdditionalUser ? 'complete' : 'pending'
      })
    }
    steps.push({
      title: t('setup.adminStep3Title'),
      description: !sharedOAuthReady
        ? t('setup.adminStep3Blocked')
        : importsErrored ? t('setup.adminStep3Error') : importsReady ? t('setup.adminStep3Ready') : t('setup.adminStep3Pending'),
      targetId: 'system-dashboard-section',
      sectionKey: 'systemDashboardCollapsed',
      status: !sharedOAuthReady ? 'pending' : importsErrored ? 'error' : importsReady ? 'complete' : 'pending'
    })

    return {
      steps: steps.map((step, index) => ({ ...step, title: renumberStepTitle(step.title, index + 1) })),
      allStepsComplete: steps.every((step) => step.status === 'complete')
    }
  }

  const systemDashboardEmailAccounts = systemDashboard?.emailAccounts || systemDashboard?.bridges || []
  const visibleSystemEmailAccounts = session.username === 'admin'
    ? systemDashboardEmailAccounts
    : []
  const allEmailAccounts = [
    ...myEmailAccounts,
    ...visibleSystemEmailAccounts
  ]
  const activeEmailAccounts = allEmailAccounts.filter(isSetupGuideRelevantEmailAccount)
  const oauthEmailAccounts = activeEmailAccounts.filter((emailAccount) => emailAccount.authMethod === 'OAUTH2')
  const emailAccountErrors = activeEmailAccounts.filter((emailAccount) => emailAccount.lastEvent?.status === 'ERROR' || emailAccount.lastEvent?.error)
  const oauthErrors = oauthEmailAccounts.filter((emailAccount) => emailAccount.lastEvent?.status === 'ERROR' || emailAccount.lastEvent?.error)
  const importedMessages = systemDashboard?.overall?.totalImportedMessages
    ?? allEmailAccounts.reduce((total, emailAccount) => total + (emailAccount.totalImportedMessages || 0), 0)
  const sessionReady = !session.mustChangePassword
  const destinationReady = Boolean(destinationMeta?.linked ?? destinationMeta?.refreshTokenConfigured)
  const emailAccountsReady = allEmailAccounts.length > 0
  const oauthReady = destinationReady && oauthEmailAccounts.every((emailAccount) => {
    if ('oauthRefreshTokenConfigured' in emailAccount) {
      return emailAccount.oauthRefreshTokenConfigured || emailAccount.tokenStorageMode === 'DATABASE' || emailAccount.tokenStorageMode === 'ENV'
    }
    return emailAccount.tokenStorageMode === 'DATABASE' || emailAccount.tokenStorageMode === 'ENV'
  })
  const importsReady = importedMessages > 0 && emailAccountErrors.length === 0
  const importsErrored = emailAccountErrors.length > 0

  const steps = [
    {
      title: t('setup.step1Title'),
      description: t('setup.step1Pending'),
      targetId: 'password-panel-section',
      sectionKey: null,
      status: sessionReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step2Title'),
      description: destinationReady
        ? t('setup.step2Ready')
        : t('setup.step2Pending'),
      targetId: 'destination-mailbox-section',
      sectionKey: 'destinationMailboxCollapsed',
      status: destinationReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step3Title'),
      description: emailAccountsReady
        ? t('setup.step3Ready')
        : t('setup.step3Pending'),
      targetId: 'source-email-accounts-section',
      sectionKey: 'sourceEmailAccountsCollapsed',
      status: emailAccountsReady ? 'complete' : 'pending'
    }
  ]

  if (oauthEmailAccounts.length > 0) {
    steps.push({
      title: t('setup.step4Title'),
      description: oauthErrors.length > 0
        ? t('setup.step4Error')
        : oauthReady
          ? t('setup.step4Ready')
          : t('setup.step4Pending'),
      targetId: 'source-email-accounts-section',
      sectionKey: 'sourceEmailAccountsCollapsed',
      status: oauthErrors.length > 0 ? 'error' : oauthReady ? 'complete' : 'pending'
    })
  }

  steps.push({
      title: t('setup.step5Title'),
      description: importsErrored
        ? t('setup.step5Error')
        : importsReady
          ? t('setup.step5Ready')
          : t('setup.step5Pending'),
      targetId: 'user-polling-section',
      sectionKey: 'userPollingCollapsed',
      status: importsErrored ? 'error' : importsReady ? 'complete' : 'pending'
    })

  const numberedSteps = steps.map((step, index) => ({
    ...step,
    title: renumberStepTitle(step.title, index + 1)
  }))

  return {
    steps: numberedSteps,
    allStepsComplete: steps.every((step) => step.status === 'complete')
  }
}
