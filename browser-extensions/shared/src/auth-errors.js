const INVALID_EXTENSION_AUTH_ERROR = 'INBOXBRIDGE_INVALID_EXTENSION_AUTH'

export function createInvalidExtensionAuthError(message = 'The saved InboxBridge sign-in is no longer valid.') {
  const error = new Error(message)
  error.code = INVALID_EXTENSION_AUTH_ERROR
  return error
}

export function isInvalidExtensionAuthError(error) {
  return error?.code === INVALID_EXTENSION_AUTH_ERROR
}
