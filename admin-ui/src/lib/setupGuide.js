export function buildSetupGuideState({ gmailMeta, myBridges = [], session, systemDashboard, t }) {
  if (!session) {
    return {
      steps: [],
      allStepsComplete: false
    }
  }

  const visibleSystemBridges = session.username === 'admin'
    ? (systemDashboard?.bridges || [])
    : []
  const allBridges = [
    ...myBridges,
    ...visibleSystemBridges
  ]
  const oauthBridges = allBridges.filter((bridge) => bridge.authMethod === 'OAUTH2')
  const bridgeErrors = allBridges.filter((bridge) => bridge.lastEvent?.status === 'ERROR' || bridge.lastEvent?.error)
  const oauthErrors = oauthBridges.filter((bridge) => bridge.lastEvent?.status === 'ERROR' || bridge.lastEvent?.error)
  const importedMessages = systemDashboard?.overall?.totalImportedMessages
    ?? allBridges.reduce((total, bridge) => total + (bridge.totalImportedMessages || 0), 0)
  const sessionReady = !session.mustChangePassword
  const gmailReady = Boolean(gmailMeta?.refreshTokenConfigured)
  const bridgeReady = allBridges.length > 0
  const oauthReady = gmailReady && oauthBridges.every((bridge) => {
    if ('oauthRefreshTokenConfigured' in bridge) {
      return bridge.oauthRefreshTokenConfigured || bridge.tokenStorageMode === 'DATABASE' || bridge.tokenStorageMode === 'ENV'
    }
    return bridge.tokenStorageMode === 'DATABASE' || bridge.tokenStorageMode === 'ENV'
  })
  const importsReady = importedMessages > 0 && bridgeErrors.length === 0
  const importsErrored = bridgeErrors.length > 0

  const steps = [
    {
      title: '1. Secure your session',
      title: t('setup.step1Title'),
      description: t('setup.step1Pending'),
      targetId: 'password-panel-section',
      sectionKey: null,
      status: sessionReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step2Title'),
      description: gmailReady
        ? t('setup.step2Ready')
        : t('setup.step2Pending'),
      targetId: 'gmail-destination-section',
      sectionKey: 'gmailDestinationCollapsed',
      status: gmailReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step3Title'),
      description: bridgeReady
        ? t('setup.step3Ready')
        : t('setup.step3Pending'),
      targetId: 'source-bridges-section',
      sectionKey: 'sourceBridgesCollapsed',
      status: bridgeReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step4Title'),
      description: oauthErrors.length > 0
        ? t('setup.step4Error')
        : oauthReady
          ? t('setup.step4Ready')
          : t('setup.step4Pending'),
      targetId: 'source-bridges-section',
      sectionKey: 'sourceBridgesCollapsed',
      status: oauthErrors.length > 0 ? 'error' : oauthReady ? 'complete' : 'pending'
    },
    {
      title: t('setup.step5Title'),
      description: importsErrored
        ? t('setup.step5Error')
        : importsReady
          ? t('setup.step5Ready')
          : t('setup.step5Pending'),
      targetId: session.role === 'ADMIN' ? 'system-dashboard-section' : 'source-bridges-section',
      sectionKey: session.role === 'ADMIN' ? 'systemDashboardCollapsed' : 'sourceBridgesCollapsed',
      status: importsErrored ? 'error' : importsReady ? 'complete' : 'pending'
    }
  ]

  return {
    steps,
    allStepsComplete: steps.every((step) => step.status === 'complete')
  }
}
