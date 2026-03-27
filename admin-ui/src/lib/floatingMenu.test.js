import { resolveFloatingMenuPosition } from './floatingMenu'

describe('resolveFloatingMenuPosition', () => {
  it('places the menu below the anchor when there is enough space', () => {
    expect(resolveFloatingMenuPosition(
      { top: 100, bottom: 140, right: 300 },
      { width: 220, height: 160 },
      1200,
      900
    )).toEqual(expect.objectContaining({
      placement: 'bottom',
      left: 80,
      top: 148
    }))
  })

  it('flips the menu above the anchor when below space is insufficient', () => {
    expect(resolveFloatingMenuPosition(
      { top: 760, bottom: 800, right: 300 },
      { width: 220, height: 180 },
      1200,
      900
    )).toEqual(expect.objectContaining({
      placement: 'top',
      left: 80,
      top: 572
    }))
  })
})
