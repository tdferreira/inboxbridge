function AuthSecurityHelpLinks({ docsUrl, termsUrl, t }) {
  return (
    <>
      <div className="auth-security-provider-links">
        <a href={docsUrl} rel="noreferrer" target="_blank">{t('authSecurity.providerDocsLink')}</a>
      </div>
      <div className="auth-security-provider-links">
        <a href={termsUrl} rel="noreferrer" target="_blank">{t('authSecurity.providerTermsLink')}</a>
      </div>
    </>
  )
}

export default AuthSecurityHelpLinks
