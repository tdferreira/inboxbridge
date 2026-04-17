const SECRET_MANAGEMENT_CONFIG_GUIDANCE = {
  'SECRET_PROVIDER_MODE': {
    purpose: 'Chooses which secret-management backend InboxBridge should actively use for new encryption operations.',
    example: 'SECRET_PROVIDER_MODE=OPENBAO_TRANSIT'
  },
  'SECRET_PROVIDER_OPENBAO_URL': {
    purpose: 'Points InboxBridge to the HTTPS base URL of the OpenBao server that exposes the transit engine.',
    example: 'SECRET_PROVIDER_OPENBAO_URL=https://openbao.example.internal:8200'
  },
  'SECRET_PROVIDER_OPENBAO_TOKEN': {
    purpose: 'Provides the OpenBao token InboxBridge uses to encrypt, decrypt, and verify transit operations.',
    example: 'SECRET_PROVIDER_OPENBAO_TOKEN=hvs.C3l7nexampleoperatorsecret'
  },
  'SECRET_PROVIDER_OPENBAO_MOUNT': {
    purpose: 'Names the OpenBao transit mount where the encryption key is available.',
    example: 'SECRET_PROVIDER_OPENBAO_MOUNT=transit'
  },
  'SECRET_PROVIDER_OPENBAO_KEY': {
    purpose: 'Selects the OpenBao transit key that should wrap InboxBridge secrets.',
    example: 'SECRET_PROVIDER_OPENBAO_KEY=inboxbridge'
  },
  'SECRET_PROVIDER_VAULT_URL': {
    purpose: 'Points InboxBridge to the HTTPS base URL of the HashiCorp Vault server that exposes the transit engine.',
    example: 'SECRET_PROVIDER_VAULT_URL=https://vault.example.internal:8200'
  },
  'SECRET_PROVIDER_VAULT_TOKEN': {
    purpose: 'Provides the Vault token InboxBridge uses to encrypt, decrypt, and verify transit operations.',
    example: 'SECRET_PROVIDER_VAULT_TOKEN=hvs.C3l7nexampleoperatorsecret'
  },
  'SECRET_PROVIDER_VAULT_MOUNT': {
    purpose: 'Names the Vault transit mount where the encryption key is available.',
    example: 'SECRET_PROVIDER_VAULT_MOUNT=transit'
  },
  'SECRET_PROVIDER_VAULT_KEY': {
    purpose: 'Selects the Vault transit key that should wrap InboxBridge secrets.',
    example: 'SECRET_PROVIDER_VAULT_KEY=inboxbridge'
  },
  'SECRET_PROVIDER_SPLIT_SECONDARY_MODE': {
    purpose: 'Chooses which external transit backend should act as the secondary provider in split-key mode.',
    example: 'SECRET_PROVIDER_SPLIT_SECONDARY_MODE=OPENBAO_TRANSIT'
  },
  'SECRET_PROVIDER_OPENBAO_* or SECRET_PROVIDER_VAULT_*': {
    purpose: 'Represents the provider-specific URL, token, mount, and key properties required by the selected external transit backend.',
    example: 'SECRET_PROVIDER_OPENBAO_URL=https://openbao.example.internal:8200 plus the matching TOKEN, MOUNT, and KEY values'
  },
  'SECURITY_TOKEN_ENCRYPTION_KEY': {
    purpose: 'Stores the active local AES-GCM key material used in LOCAL mode or as the inner key in split-key mode.',
    example: 'SECURITY_TOKEN_ENCRYPTION_KEY=base64-encoded-32-byte-key'
  },
  'SECURITY_TOKEN_ENCRYPTION_KEY_ID': {
    purpose: 'Labels the currently active local key version so InboxBridge can track which records need rotation later.',
    example: 'SECURITY_TOKEN_ENCRYPTION_KEY_ID=v2'
  },
  'SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS': {
    purpose: 'Keeps older local keys available for decryption while records are still being migrated to the active key.',
    example: 'SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS=v1:base64-encoded-32-byte-key,v0:base64-encoded-32-byte-key'
  },
  'inboxbridge.security.secret-management.reauthentication-ttl': {
    purpose: 'Defines how long a successful step-up verification remains valid before the operator must re-authenticate again.',
    example: 'SECURITY_SECRET_REENCRYPTION_REAUTHENTICATION_TTL=PT10M'
  },
  'active provider / key settings': {
    purpose: 'Refers to the currently selected provider mode plus the key, token, URL, and mount values that make that active path usable.',
    example: 'For LOCAL use SECRET_PROVIDER_MODE=LOCAL with SECURITY_TOKEN_ENCRYPTION_KEY and SECURITY_TOKEN_ENCRYPTION_KEY_ID'
  },
  'provider key / transit path settings': {
    purpose: 'Refers to the provider-specific key name or transit path that identifies the active wrapping key.',
    example: 'SECRET_PROVIDER_OPENBAO_KEY=inboxbridge'
  },
  'legacy transit / provider credentials': {
    purpose: 'Refers to the previous provider credentials or key path that must stay available until all older ciphertext can still be decrypted.',
    example: 'Keep the previous Vault or OpenBao token, mount, and key settings available until the key-usage summary shows no older dependency'
  },
  'SECRET_PROVIDER_OPENBAO_URL / SECRET_PROVIDER_VAULT_URL': {
    purpose: 'Represents the external transit service URL for whichever split-key secondary provider you selected.',
    example: 'SECRET_PROVIDER_OPENBAO_URL=https://openbao.example.internal:8200'
  },
  'SECRET_PROVIDER_OPENBAO_TOKEN / SECRET_PROVIDER_VAULT_TOKEN': {
    purpose: 'Represents the provider token for whichever split-key secondary transit backend you selected.',
    example: 'SECRET_PROVIDER_OPENBAO_TOKEN=hvs.C3l7nexampleoperatorsecret'
  },
  'SECRET_PROVIDER_OPENBAO_MOUNT / SECRET_PROVIDER_VAULT_MOUNT': {
    purpose: 'Represents the transit mount name for whichever split-key secondary provider you selected.',
    example: 'SECRET_PROVIDER_OPENBAO_MOUNT=transit'
  },
  'SECRET_PROVIDER_OPENBAO_KEY / SECRET_PROVIDER_VAULT_KEY': {
    purpose: 'Represents the transit key name for whichever split-key secondary provider you selected.',
    example: 'SECRET_PROVIDER_OPENBAO_KEY=inboxbridge'
  }
}

const GENERIC_SECRET_MANAGEMENT_CONFIG_GUIDANCE = {
  purpose: 'Review this setting on the server side before switching providers or starting a re-encryption run.',
  example: 'Define it in your deployment environment or application configuration using the format expected by the selected secret provider.'
}

export function describeSecretManagementConfigReference(reference) {
  if (!reference) {
    return GENERIC_SECRET_MANAGEMENT_CONFIG_GUIDANCE
  }
  return SECRET_MANAGEMENT_CONFIG_GUIDANCE[reference] || GENERIC_SECRET_MANAGEMENT_CONFIG_GUIDANCE
}
