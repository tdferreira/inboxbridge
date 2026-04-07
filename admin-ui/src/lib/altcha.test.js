import { createHash } from 'node:crypto'
import { decodeAltchaPayload, solveAltchaChallenge } from './altcha'

describe('altcha', () => {
  it('solves and decodes a valid challenge payload', async () => {
    const salt = 'pepper'
    const number = 7
    const challenge = createHash('sha256').update(`${salt}${number}`).digest('hex')

    const payload = await solveAltchaChallenge({
      algorithm: 'SHA-256',
      challenge,
      maxNumber: 20,
      salt,
      signature: 'sig'
    })

    expect(decodeAltchaPayload(payload)).toEqual({
      algorithm: 'SHA-256',
      challenge,
      number,
      salt,
      signature: 'sig'
    })
  })

  it('rejects invalid challenges', async () => {
    await expect(solveAltchaChallenge({})).rejects.toThrow('Invalid ALTCHA challenge')
  })
})
