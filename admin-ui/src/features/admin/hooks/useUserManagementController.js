import { useCallback, useEffect, useRef, useState } from 'react'
import { apiErrorText } from '@/lib/api'
import { pollErrorNotification, translatedNotification } from '@/lib/notifications'
import { statsTimezoneHeader } from '@/lib/statsTimezone'

const DEFAULT_ADMIN_RESET_PASSWORD_FORM = { newPassword: '', confirmNewPassword: '' }
const DEFAULT_CREATE_USER_FORM = { username: '', password: '', confirmPassword: '', role: 'USER' }
const DEFAULT_SELECTED_USER = {
  id: null,
  username: '',
  role: 'USER',
  approved: false,
  active: false,
  gmailConfigured: false,
  passwordConfigured: false,
  mustChangePassword: false,
  passkeyCount: 0,
  emailAccountCount: 0
}
const DEFAULT_SELECTED_DESTINATION_CONFIG = {
  provider: '',
  deliveryMode: '',
  linked: false,
  host: '',
  port: null,
  authMethod: '',
  username: '',
  folder: ''
}
const DEFAULT_SELECTED_POLLING_SETTINGS = {
  defaultPollEnabled: false,
  pollEnabledOverride: null,
  effectivePollEnabled: false,
  defaultPollInterval: '',
  pollIntervalOverride: null,
  effectivePollInterval: '',
  defaultFetchWindow: null,
  fetchWindowOverride: null,
  effectiveFetchWindow: ''
}

function normalizeSelectedUserConfiguration(payload, fallbackUser) {
  const emailAccounts = Array.isArray(payload?.emailAccounts)
    ? payload.emailAccounts
    : Array.isArray(payload?.bridges)
      ? payload.bridges
      : []

  return {
    ...payload,
    user: {
      ...DEFAULT_SELECTED_USER,
      ...(fallbackUser || {}),
      ...(payload?.user || {})
    },
    destinationConfig: {
      ...DEFAULT_SELECTED_DESTINATION_CONFIG,
      ...(payload?.destinationConfig || {})
    },
    pollingSettings: {
      ...DEFAULT_SELECTED_POLLING_SETTINGS,
      ...(payload?.pollingSettings || {})
    },
    pollingStats: payload?.pollingStats || null,
    passkeys: Array.isArray(payload?.passkeys) ? payload.passkeys.filter(Boolean) : [],
    emailAccounts
  }
}

export function useUserManagementController({
  authOptions,
  closeConfirmation,
  errorText,
  loadAppData,
  loadAuthOptions,
  openConfirmation,
  pushNotification,
  session,
  t,
  withPending
}) {
  const [adminResetPasswordForm, setAdminResetPasswordForm] = useState(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
  const [createUserForm, setCreateUserForm] = useState(DEFAULT_CREATE_USER_FORM)
  const [passwordResetTarget, setPasswordResetTarget] = useState(null)
  const [selectedUserConfig, setSelectedUserConfig] = useState(null)
  const [selectedUserId, setSelectedUserId] = useState(null)
  const [selectedUserLoading, setSelectedUserLoading] = useState(false)
  const [showCreateUserDialog, setShowCreateUserDialog] = useState(false)
  const [showPasswordResetDialog, setShowPasswordResetDialog] = useState(false)
  const [users, setUsers] = useState([])
  const usersRef = useRef([])

  const duplicateCreateUsername = (() => {
    const normalized = createUserForm.username.trim().toLowerCase()
    if (!normalized) {
      return false
    }
    return users.some((user) => user.username.toLowerCase() === normalized)
  })()

  function applyLoadedUsers(usersPayload) {
    if (!Array.isArray(usersPayload)) {
      usersRef.current = []
      setUsers([])
      setSelectedUserId(null)
      setSelectedUserConfig(null)
      return
    }
    usersRef.current = usersPayload
    setUsers(usersPayload)
    const availableUserIds = new Set(usersPayload.map((user) => user.id))
    setSelectedUserId((current) => (current && availableUserIds.has(current) ? current : null))
    setSelectedUserConfig((current) => (current?.user?.id && availableUserIds.has(current.user.id) ? current : null))
  }

  function resetUserManagementState() {
    setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
    setCreateUserForm(DEFAULT_CREATE_USER_FORM)
    setPasswordResetTarget(null)
    setSelectedUserConfig(null)
    setSelectedUserId(null)
    setSelectedUserLoading(false)
    setShowCreateUserDialog(false)
    setShowPasswordResetDialog(false)
    setUsers([])
  }

  const loadSelectedUserConfiguration = useCallback(async (userId) => {
    if (!session || session.role !== 'ADMIN' || !authOptions.multiUserEnabled || !userId) {
      return
    }
    setSelectedUserLoading(true)
    try {
      const response = await fetch(`/api/admin/users/${userId}/configuration`, {
        headers: statsTimezoneHeader()
      })
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadUserConfiguration')))
      }
      const payload = await response.json()
      setSelectedUserConfig(normalizeSelectedUserConfiguration(
        payload,
        usersRef.current.find((user) => user.id === userId)
      ))
    } catch (err) {
      pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadUserConfiguration'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadUserConfiguration'), targetId: 'user-management-section', tone: 'error' })
    } finally {
      setSelectedUserLoading(false)
    }
  }, [authOptions.multiUserEnabled, errorText, pushNotification, session])

  async function createUser(event) {
    event.preventDefault()
    const normalizedUsername = createUserForm.username.trim()
    if (users.some((user) => user.username.toLowerCase() === normalizedUsername.toLowerCase())) {
      pushNotification({ autoCloseMs: null, message: translatedNotification('users.duplicateUsername', { username: normalizedUsername }), targetId: 'user-management-section', tone: 'error' })
      return false
    }
    return withPending('createUser', async () => {
      try {
        const response = await fetch('/api/admin/users', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: normalizedUsername,
            password: createUserForm.password,
            role: createUserForm.role
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('createUser')))
        }
        const payload = await response.json()
        setCreateUserForm(DEFAULT_CREATE_USER_FORM)
        setShowCreateUserDialog(false)
        pushNotification({ message: translatedNotification('notifications.userCreated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.createUser'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.createUser'), targetId: 'user-management-section', tone: 'error' })
        return false
      }
    })
  }

  function toggleExpandedUser(userId) {
    setSelectedUserConfig((current) => current && current.user?.id === userId && selectedUserId === userId ? null : current)
    setSelectedUserId((current) => current === userId ? null : userId)
  }

  async function updateUser(userId, patch, successMessage) {
    return withPending(`updateUser:${userId}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${userId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(patch)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('updateUser')))
        }
        const payload = await response.json()
        pushNotification({ message: successMessage || translatedNotification('notifications.userUpdated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.updateUser'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.updateUser'), targetId: 'user-management-section', tone: 'error' })
        return false
      }
    })
  }

  function requestToggleMultiUserMode(enabled) {
    const turningOn = Boolean(enabled)
    openConfirmation({
      actionKey: 'multiUserModeSave',
      body: turningOn
        ? t('users.switchToMultiUserConfirmBody')
        : t('users.switchToSingleUserConfirmBody', { username: session?.username || 'admin' }),
      confirmLabel: turningOn ? t('users.switchToMultiUser') : t('users.switchToSingleUser'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('multiUserModeSave', async () => {
          try {
            const response = await fetch('/api/admin/users/mode', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ multiUserEnabled: enabled })
            })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('saveMultiUserMode')))
            }
            await response.json()
            closeConfirmation()
            setSelectedUserId(null)
            setSelectedUserConfig(null)
            await loadAuthOptions()
            await loadAppData()
            pushNotification({
              message: enabled ? translatedNotification('notifications.multiUserEnabled') : translatedNotification('notifications.singleUserEnabled'),
              targetId: 'user-management-section',
              tone: 'success'
            })
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMultiUserMode'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMultiUserMode'),
              targetId: 'user-management-section',
              tone: 'error'
            })
          }
        })
      },
      title: turningOn ? t('users.switchToMultiUserConfirmTitle') : t('users.switchToSingleUserConfirmTitle')
    })
  }

  function requestToggleUserActive(user) {
    if (!user) {
      return
    }
    const actionKey = `updateUser:${user.id}`
    const active = Boolean(user.active)
    openConfirmation({
      actionKey,
      body: active
        ? t('users.suspendConfirmBody', { username: user.username })
        : t('users.reactivateConfirmBody', { username: user.username }),
      confirmLabel: active ? t('users.suspend') : t('users.reactivate'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        const success = await updateUser(
          user.id,
          { active: !active },
          active
            ? translatedNotification('notifications.userSuspended', { username: user.username })
            : translatedNotification('notifications.userReactivated', { username: user.username })
        )
        if (success) {
          closeConfirmation()
        }
      },
      title: active ? t('users.suspendConfirmTitle') : t('users.reactivateConfirmTitle')
    })
  }

  function requestForcePasswordChange(user) {
    if (!user) {
      return
    }
    openConfirmation({
      actionKey: `updateUser:${user.id}`,
      body: t('users.forcePasswordChangeConfirmBody', { username: user.username }),
      confirmLabel: t('users.confirmAction'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        const success = await updateUser(
          user.id,
          { mustChangePassword: true },
          translatedNotification('notifications.forcedPasswordReset', { username: user.username })
        )
        if (success) {
          closeConfirmation()
        }
      },
      title: t('users.forcePasswordChangeConfirmTitle')
    })
  }

  async function resetSelectedUserPassword(event) {
    event.preventDefault()
    if (!passwordResetTarget) {
      return
    }
    await withPending(`resetPassword:${passwordResetTarget.id}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${passwordResetTarget.id}/password-reset`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(adminResetPasswordForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('resetPassword')))
        }
        const payload = await response.json()
        setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
        setShowPasswordResetDialog(false)
        setPasswordResetTarget(null)
        pushNotification({ autoCloseMs: null, message: translatedNotification('notifications.temporaryPasswordSet', { username: payload.username }), targetId: 'user-management-section', tone: 'warning' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPassword'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPassword'), targetId: 'user-management-section', tone: 'error' })
      }
    })
  }

  async function resetUserPasskeys(user) {
    if (!user) {
      return
    }
    openConfirmation({
      actionKey: `resetPasskeys:${user.id}`,
      body: t('users.resetPasskeysConfirmBody', { username: user.username }),
      confirmLabel: t('users.resetPasskeys'),
      confirmLoadingLabel: t('users.resetPasskeysLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`resetPasskeys:${user.id}`, async () => {
          try {
            const response = await fetch(`/api/admin/users/${user.id}/passkeys`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('resetPasskeys')))
            }
            const payload = await response.json()
            closeConfirmation()
            pushNotification({ message: translatedNotification('notifications.passkeysRemoved', { count: payload.deleted, username: user.username }), targetId: 'user-management-section', tone: 'success' })
            await loadAppData()
            await loadSelectedUserConfiguration(user.id)
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPasskeys'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPasskeys'), targetId: 'user-management-section', tone: 'error' })
          }
        })
      },
      title: t('users.resetPasskeysConfirmTitle')
    })
  }

  function requestDeleteUser(user) {
    if (!user) {
      return
    }
    openConfirmation({
      actionKey: `deleteUser:${user.id}`,
      body: t('users.deleteConfirmBody', { username: user.username }),
      confirmLabel: t('users.delete'),
      confirmLoadingLabel: t('users.deleteLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`deleteUser:${user.id}`, async () => {
          try {
            const response = await fetch(`/api/admin/users/${user.id}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('deleteUser')))
            }
            closeConfirmation()
            if (selectedUserId === user.id) {
              setSelectedUserId(null)
              setSelectedUserConfig(null)
            }
            await loadAppData()
            pushNotification({ message: translatedNotification('notifications.userDeleted', { username: user.username }), targetId: 'user-management-section', tone: 'success' })
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.deleteUser'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.deleteUser'), targetId: 'user-management-section', tone: 'error' })
          }
        })
      },
      title: t('users.deleteConfirmTitle')
    })
  }

  useEffect(() => {
    setShowPasswordResetDialog(false)
    setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
    setPasswordResetTarget(null)
  }, [selectedUserId])

  return {
    adminResetPasswordForm,
    applyLoadedUsers,
    closeCreateUserDialog: () => {
      setShowCreateUserDialog(false)
      setCreateUserForm(DEFAULT_CREATE_USER_FORM)
    },
    closePasswordResetDialog: () => {
      setShowPasswordResetDialog(false)
      setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
      setPasswordResetTarget(null)
    },
    createUser,
    createUserForm,
    duplicateCreateUsername,
    loadSelectedUserConfiguration,
    openCreateUserDialog: () => {
      setCreateUserForm(DEFAULT_CREATE_USER_FORM)
      setShowCreateUserDialog(true)
    },
    openResetPasswordDialog: (user) => {
      setPasswordResetTarget(user)
      setShowPasswordResetDialog(true)
    },
    passwordResetTarget,
    requestDeleteUser,
    requestForcePasswordChange,
    requestToggleMultiUserMode,
    requestToggleUserActive,
    resetSelectedUserPassword,
    resetUserManagementState,
    resetUserPasskeys,
    selectedUserConfig,
    selectedUserId,
    selectedUserLoading,
    setAdminResetPasswordForm,
    setCreateUserForm,
    setShowPasswordResetDialog,
    showCreateUserDialog,
    showPasswordResetDialog,
    toggleExpandedUser,
    updateUser,
    users
  }
}
