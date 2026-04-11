import { createHash } from 'node:crypto'
import { decodeAltchaPayload, solveAltchaChallenge } from './altcha'

describe('altcha', () => {
  it('solves and decodes a valid challenge payload', async () => {
    const nonce = '00112233445566778899aabbccddeeff'
    const salt = '0f0e0d0c0b0a09080706050403020100'
    const counter = 0
    const password = Buffer.concat([Buffer.from(nonce, 'hex'), Buffer.from([0, 0, 0, counter])])
    const derivedKey = createHash('sha256').update(Buffer.concat([Buffer.from(salt, 'hex'), password])).digest('hex')

    const payload = await solveAltchaChallenge({
      parameters: {
        algorithm: 'SHA-256',
        cost: 1,
        keyLength: 32,
        keyPrefix: derivedKey.slice(0, 2),
        nonce,
        salt
      },
      signature: 'sig'
    })

    expect(decodeAltchaPayload(payload)).toEqual({
      challenge: {
        parameters: {
          algorithm: 'SHA-256',
          cost: 1,
          keyLength: 32,
          keyPrefix: derivedKey.slice(0, 2),
          nonce,
          salt
        },
        signature: 'sig'
      },
      solution: expect.objectContaining({
        counter,
        derivedKey
      })
    })
  })

  it('rejects invalid challenges', async () => {
    await expect(solveAltchaChallenge({})).rejects.toThrow('Invalid ALTCHA challenge')
  })
})
