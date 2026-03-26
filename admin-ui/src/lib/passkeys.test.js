import { parseCreateOptions, parseGetOptions, serializeCredential } from './passkeys'

describe('passkeys helpers', () => {
  it('hydrates wrapped WebAuthn option payloads and serializes credentials', () => {
    const createOptions = parseCreateOptions(JSON.stringify({
      publicKey: {
        challenge: 'Y2hhbGxlbmdl',
        user: { id: 'dXNlci1oYW5kbGU', name: 'alice', displayName: 'alice' },
        excludeCredentials: [{ id: 'Y3JlZC0x', type: 'public-key' }]
      }
    }))
    const getOptions = parseGetOptions(JSON.stringify({
      publicKey: {
        challenge: 'Y2hhbGxlbmdl',
        allowCredentials: [{ id: 'Y3JlZC0x', type: 'public-key' }]
      }
    }))

    const credentialJson = serializeCredential({
      id: 'cred-id',
      rawId: new Uint8Array([1, 2, 3]).buffer,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      response: {
        clientDataJSON: new Uint8Array([4, 5]).buffer,
        attestationObject: new Uint8Array([6, 7]).buffer,
        getTransports: () => ['internal']
      },
      getClientExtensionResults: () => ({ credProps: { rk: true } })
    })

    expect(createOptions.challenge).toBeInstanceOf(ArrayBuffer)
    expect(createOptions.user.id).toBeInstanceOf(ArrayBuffer)
    expect(getOptions.allowCredentials[0].id).toBeInstanceOf(ArrayBuffer)
    expect(JSON.parse(credentialJson)).toMatchObject({
      id: 'cred-id',
      type: 'public-key',
      authenticatorAttachment: 'platform'
    })
  })

  it('still accepts an already-unwrapped publicKey object', () => {
    const createOptions = parseCreateOptions(JSON.stringify({
      challenge: 'Y2hhbGxlbmdl',
      user: { id: 'dXNlci1oYW5kbGU', name: 'alice', displayName: 'alice' },
      excludeCredentials: []
    }))

    expect(createOptions.challenge).toBeInstanceOf(ArrayBuffer)
    expect(createOptions.user.id).toBeInstanceOf(ArrayBuffer)
  })
})
