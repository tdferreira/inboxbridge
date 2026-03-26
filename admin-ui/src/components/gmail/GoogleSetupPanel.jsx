import './GoogleSetupPanel.css'

function GoogleSetupPanel({ gmailMeta }) {
  const redirectUri = gmailMeta?.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`

  return (
    <section className="surface-card google-setup-panel">
      <div className="section-title">Google Setup</div>
      {gmailMeta?.sharedClientConfigured ? (
        <div className="muted-box">
          <strong>Shared Google OAuth client available</strong><br />
          This deployment already has a shared Google client configured. You can leave Client ID and Client Secret blank here unless you want to override them for your own account.
        </div>
      ) : null}
      <div className="muted-box">
        <p>InboxBridge cannot automatically create a Google OAuth client ID / secret from a user Gmail account. Google ties those credentials to a Google Cloud project, not to the mailbox itself.</p>
      </div>
      <div className="muted-box">
        <strong>What the user must do</strong><br />
        1. Create or use a Google Cloud project.<br />
        2. Enable the Gmail API.<br />
        3. Create an OAuth client with redirect URI <code>{redirectUri}</code>.<br />
        4. Paste the client ID and secret here.<br />
        5. Click <strong>Connect My Gmail OAuth</strong>.
      </div>
      <div className="muted-box">
        <strong>Common error</strong><br />
        <code>403 org_internal</code> means the OAuth consent screen is set to <strong>Internal</strong>. Switch it to <strong>External</strong> and add test users if needed.
      </div>
    </section>
  )
}

export default GoogleSetupPanel
