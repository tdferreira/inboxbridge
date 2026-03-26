export function buildSetupGuideState({ gmailMeta, myBridges = [], session, systemDashboard }) {
  if (!session) {
    return {
      steps: [],
      allStepsComplete: false
    }
  }

  const allBridges = [
    ...myBridges,
    ...(systemDashboard?.bridges || [])
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
      description: 'Sign in, then change your password if this is the bootstrap account or an admin-forced reset.',
      targetId: 'password-panel-section',
      sectionKey: null,
      status: sessionReady ? 'complete' : 'pending'
    },
    {
      title: '2. Configure the Gmail destination',
      description: gmailReady
        ? 'Gmail destination OAuth is stored and ready.'
        : 'Save the Gmail settings, then click Connect My Gmail OAuth.',
      targetId: 'gmail-destination-section',
      sectionKey: 'gmailDestinationCollapsed',
      status: gmailReady ? 'complete' : 'pending'
    },
    {
      title: '3. Add at least one source bridge',
      description: bridgeReady
        ? 'At least one source bridge is configured.'
        : 'Add a bridge in the UI or keep using system bridges from .env.',
      targetId: 'source-bridges-section',
      sectionKey: 'sourceBridgesCollapsed',
      status: bridgeReady ? 'complete' : 'pending'
    },
    {
      title: '4. Complete provider OAuth',
      description: oauthErrors.length > 0
        ? 'At least one OAuth-backed bridge is currently failing. Re-run the provider consent flow from the source bridges section.'
        : oauthReady
          ? 'OAuth-backed flows are configured and no current bridge OAuth errors are recorded.'
          : 'Use the provider-specific OAuth buttons, finish consent, and exchange the code in the callback page.',
      targetId: 'source-bridges-section',
      sectionKey: 'sourceBridgesCollapsed',
      status: oauthErrors.length > 0 ? 'error' : oauthReady ? 'complete' : 'pending'
    },
    {
      title: '5. Run and verify imports',
      description: importsErrored
        ? 'Recent bridge errors were recorded. Open the dashboard and inspect the latest error details.'
        : importsReady
          ? 'Imports have completed successfully and no current bridge errors are recorded.'
          : 'Trigger a poll, then confirm import counts and provider status from the dashboard.',
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
