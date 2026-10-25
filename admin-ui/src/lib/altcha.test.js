import { createHash, pbkdf2Sync } from 'node:crypto'
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

  it('solves PBKDF2 challenges with the unsigned 32-bit counter expected by the server', async () => {
    const nonce = '00112233445566778899aabbccddeeff'
    const salt = '0f0e0d0c0b0a09080706050403020100'
    const counter = 7
    const counterBuffer = Buffer.alloc(4)
    counterBuffer.writeUInt32BE(counter)
    const password = Buffer.concat([Buffer.from(nonce, 'hex'), counterBuffer])
    const derivedKey = pbkdf2Sync(password, Buffer.from(salt, 'hex'), 250, 32, 'sha256').toString('hex')
    const parameters = {
      algorithm: 'PBKDF2/SHA-256',
      cost: 250,
      keyLength: 32,
      keyPrefix: derivedKey.slice(0, 8),
      nonce,
      salt,
      expiresAt: 2000000000.5
    }

    const payload = decodeAltchaPayload(await solveAltchaChallenge({ parameters, signature: 'sig' }))

    expect(payload.challenge).toEqual({ parameters, signature: 'sig' })
    expect(payload.solution).toEqual(expect.objectContaining({ counter, derivedKey }))
  })

  it('rejects invalid challenges', async () => {
    await expect(solveAltchaChallenge({})).rejects.toThrow('Invalid ALTCHA challenge')
  })
})
