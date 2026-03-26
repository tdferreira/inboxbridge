export function buildPasswordChecks(currentPassword, newPassword, confirmNewPassword, t = (key) => key, options = {}) {
  const requireDifferent = options.requireDifferent ?? currentPassword !== ''
  const checks = [
    { label: t('passwordPolicy.minLength'), valid: newPassword.length >= 8 },
    { label: t('passwordPolicy.uppercase'), valid: /[A-Z]/.test(newPassword) },
    { label: t('passwordPolicy.lowercase'), valid: /[a-z]/.test(newPassword) },
    { label: t('passwordPolicy.number'), valid: /\d/.test(newPassword) },
    { label: t('passwordPolicy.special'), valid: /[^A-Za-z0-9]/.test(newPassword) },
    { label: t('passwordPolicy.repeatMatch'), valid: newPassword !== '' && newPassword === confirmNewPassword }
  ]

  if (requireDifferent) {
    checks.splice(5, 0, {
      label: t('passwordPolicy.different'),
      valid: currentPassword !== '' && newPassword !== '' && newPassword !== currentPassword
    })
  }

  return checks
}

export function canSubmitPasswordChange(currentPassword, newPassword, confirmNewPassword, options = {}) {
  const requireCurrentPassword = options.requireCurrentPassword ?? true
  const requireDifferent = options.requireDifferent ?? currentPassword !== ''
  const currentPasswordReady = requireCurrentPassword ? currentPassword !== '' : true
  return currentPasswordReady
    && buildPasswordChecks(currentPassword, newPassword, confirmNewPassword, undefined, { requireDifferent }).every((item) => item.valid)
}
